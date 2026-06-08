# GeoWebCache Security Parameter Filter — Design Document

| | |
|---|---|
| **Status** | Draft |
| **Author** | GeoServer Core Team |
| **Date** | June 2026 |
| **Component** | GeoWebCache / GeoServer Security |

---

## 1. Problem

GWC cache keys are built from layer name, CRS, zoom level, tile coordinates, and declared parameter filters (e.g. `STYLES`, `ELEVATION`). This works for public data but breaks when GeoServer's `ResourceAccessManager` (RAM) is in play. Advanced RAM implementations can inject per-user CQL filters, clip geometries, or restrict raster regions — two users with different rules get different tile content for the same coordinates, but GWC serves the cached tile to both, leaking data. The fix is a new parameter filter type that folds the user's effective access restrictions into the cache key.

---

## 2. Design principles

**Cache by security outcome, not identity.** The cache key is a fingerprint of the effective `AccessLimits`, not the username or role. Two users with identical restrictions share tiles. RAMs that attach `securityTags` to `AccessLimits` introduce an additional partitioning dimension: tags set at role or group granularity preserve sharing; tags at finer granularity (e.g. per-user) further partition cache entries — an intentional trade-off for precise invalidation, not a goal in itself.

**Explicit failure over silent misconfiguration.** Unknown `GeneralParameterValue` types (§3.4 Tier 3) cause a request failure with a descriptive error rather than a silently wrong cache key.

**Zero overhead for unrestricted access.** If the RAM returns no restrictions, no security parameter is added and the request hits the normal tile cache. The filter only activates when the RAM signals content-affecting restrictions.

**Stale tile cleanup.** When security rules change, some cached tile sets may no longer correspond to any active access configuration. Disk quota reclaims space as pressure mounts. RAMs that need faster cleanup can participate via explicit invalidation directives (Phase 2).

**Extensible.** Third-party RAMs can contribute serializers and invalidation logic via Spring beans.

---

## 3. Phase 1 — Security key fingerprinting

### 3.1 Overview

GWC `ParameterFilter` implementations add extra dimensions to the cache key. `SecurityParameterFilter` registers `ACCESS_LIMITS_KEY` as an additional tile dimension in the same way as, e.g., `StyleParameterFilter` registers `STYLES`. The key difference is that `ACCESS_LIMITS_KEY` is never present in the incoming request — it is computed from the current user's `AccessLimits` and injected before GWC processes it. The request flow is:

1. A tile request arrives (WMTS, TMS, OGC API Tiles, or WMS GetMap served from cache).
2. `SecurityKeyDispatcherCallback` fires via GeoServer's `DispatcherCallback` mechanism, which both the OWS Dispatcher and the OGC API Dispatcher invoke. It resolves the layer name from the operation context, calls the RAM to obtain `AccessLimits`, and stores the computed canonical key in the `SecurityKeyHolder` thread-local.
3. `GeoServerTileLayer.getModifiableParameters()` is called by GWC during tile lookup. It strips any client-supplied `ACCESS_LIMITS_KEY` from the request parameters, reads the key from `SecurityKeyHolder`, adds `ACCESS_LIMITS_KEY → key` to the sanitised parameter map, and delegates to the GWC base implementation.
4. GWC's `TileLayer.getModifiableParameters()` sees `ACCESS_LIMITS_KEY` and calls `SecurityParameterFilter.apply(key)`, which passes the value through unchanged.
5. GWC derives a tile identifier from the full parameter map via `ParametersUtils.getId()` (SHA1). The raw `ACCESS_LIMITS_KEY` value is stored in `parameters-<id>.properties` for human inspection.
6. `SecurityKeyDispatcherCallback.finished()` clears the thread-local.

If the user has unrestricted access, `SecurityKeyHolder` is not populated and `GeoServerTileLayer` does not inject `ACCESS_LIMITS_KEY`, so the request hits the normal tile cache.

### 3.2 When the security parameter is not added

The parameter is omitted when:

- The RAM returns `null` for the layer (no restriction).
- The RAM returns `AccessLimits` with `readFilter = Filter.INCLUDE` and no geometry restrictions (full read access).

It is to be noted that catalog mode (`HIDE`, `CHALLENGE`, `MIXED`) is irrelevant here. Requests denied by `HIDE` or `CHALLENGE` are rejected before tile production and never reach the filter.

### 3.3 AccessLimitsKeyBuilder

`AccessLimitsKeyBuilder` turns an `AccessLimits` instance into a stable, human-readable JSON string used as the `ACCESS_LIMITS_KEY` parameter value. It walks the class hierarchy via `instanceof` (most-specific-first — e.g. `VectorAccessLimits` before `DataAccessLimits`) and collects semantically significant fields into a JSON object. Null or absent fields are omitted. For layer groups the value is a JSON array of per-layer objects in composition order, each including a `"layer"` field with the qualified name.

The JSON string is stored as-is in `parameters-<id>.properties` (escaped by `java.util.Properties`). To inspect it: copy the value, unescape `\n` and `\=`, paste into any JSON formatter.

**Fields are taken from the `equals()` contract, not `hashCode()`** — `VectorAccessLimits.hashCode()` is known to omit geometry fields. Filters are normalized before serialization (see Appendix A). The table below lists the JSON field name for each `AccessLimits` field.

It is to be noted that very large `AccessLimits` (complex geometries, long filters) produce large key strings stored in `parameters-<id>.properties`. This is not a caching concern — a RAM returning highly complex restrictions will encounter performance problems at filter application and raster masking long before key size matters.

Fields included:

| Class | Field | JSON field name | Serialization |
|---|---|---|---|
| `DataAccessLimits` | `readFilter` | `"readFilter"` | ECQL via `ECQL.toCQL()`, after normalization (see Appendix A) |
| `VectorAccessLimits` | `readAttributes` | `"readAttributes"` | lexicographically sorted property names (including namespace URI as returned by `PropertyName`), comma-separated |
| `VectorAccessLimits` | `clipVectorFilter` | `"clipVectorFilter"` | CRS authority code + `:` + normalized WKT (`Geometry.norm()`) |
| `VectorAccessLimits` | `intersectVectorFilter` | `"intersectVectorFilter"` | CRS authority code + `:` + normalized WKT (`Geometry.norm()`) |
| `CoverageAccessLimits` | `rasterFilter` | `"rasterFilter"` | CRS authority code + `:` + normalized WKT (`Geometry.norm()`) |
| `CoverageAccessLimits` | `params` | `"params"` | see section 3.4 |

`securityTags` is a mutable `Set<String>` on the base `AccessLimits` class, null by default. It is stored as a separate `SECURITY_TAGS_KEY` parameter (comma-separated sorted string), not embedded in `ACCESS_LIMITS_KEY`. In particular, this keeps `ACCESS_LIMITS_KEY` a pure content fingerprint and makes tag-based invalidation a direct string membership check with no JSON parsing. `SECURITY_TAGS_KEY` is only injected when `ACCESS_LIMITS_KEY` is also present. For layer groups, the composite `securityTags` is the union of all constituent layers' tag sets. **Both `ACCESS_LIMITS_KEY` and `SECURITY_TAGS_KEY` are public string constants on `SecurityParameterFilter` — changing them would orphan existing caches.**

### 3.4 Handling GeneralParameterValue in CoverageAccessLimits

`CoverageAccessLimits.params` is an array of `GeneralParameterValue` with types not known in advance. **A wrong fingerprint means two users with different restrictions share tiles — a data leak.** So: serialize accurately or fail. Three tiers:

**Tier 1 — fingerprinted.** Types known to affect tile content:

- `Parameter<Filter>` — ECQL via `ECQL.toCQL()`, after normalization (see Appendix A) (e.g. ImageMosaic `FILTER`)
- `Parameter<Geometry>` / `Parameter<MultiPolygon>` — CRS authority code + `:` + normalized WKT (`Geometry.norm()`) (e.g. `GEOMETRY_MASK`, `CLIP_TO_GEOMETRY`)
- `Parameter<Number>` — decimal string
- `Parameter<Boolean>` — `"true"` / `"false"`
- `Parameter<String>` — value as-is

**Tier 2 — ignorable.** Parameters that affect read performance but not tile output, silently skipped:

- `USE_JAI_IMAGEREAD` — deferred JAI loading
- `ALLOW_MULTITHREADING` — parallel source loading
- `MAX_ALLOWED_TILES` — operational limit
- `SUGGESTED_TILE_SIZE` — internal tiling hint

The ignorable list can be extended at deployment time via system property `gwc.security.params.ignorable` (comma-separated descriptor names). At startup, a CONFIG-level log entry is emitted for each parameter added via this property.

**Tier 3 — unknown, throws.** Any `GeneralParameterValue` not matching a serializer and not ignorable throws an exception naming the descriptor. The operator must either contribute a serializer (section 3.5) or add the parameter to the ignorable list. The exception is thrown at request time, not startup — wrong configuration produces request failures, not silent data leaks.

### 3.5 ParameterValueKeySerializer SPI

Allow to handle custom `GeneralParameterValue` types by contributing a Spring bean:

```java
public interface ParameterValueKeySerializer<T extends GeneralParameterValue> {
    Class<T> getValueType();
    String toKey(T value);
}
```

`toKey()` must be deterministic and must produce different strings for inputs that result in different tile content, and identical strings for inputs that result in identical tile content. GeoServer collects all such beans at startup. Duplicate registrations for the same type fail with a descriptive error.

### 3.6 SecurityKeyDispatcherCallback

`SecurityKeyDispatcherCallback` implements `DispatcherCallback`. Both the OWS Dispatcher and the OGC API Dispatcher collect all `DispatcherCallback` beans from the Spring context, so a single implementation covers all tile-serving paths.

In `operationDispatched()` it:
1. Identifies whether this is a tile-serving operation (WMTS/TMS GetTile, OGC API tile endpoint, or a WMS GetMap eligible for caching).
2. Resolves the resource backing the request. For a single layer, calls the RAM once. For a `LayerGroupInfo`, `getAccessLimits(Authentication, LayerGroupInfo)` returns only catalog-mode limits — content fingerprinting requires iterating constituent layers via `LayerGroupInfo.layers()` (same expansion used by rendering), calling the RAM for each, and composing results. The composite fingerprint encodes each layer's name and serialized `AccessLimits` in composition order. Multi-layer WMS requests are not eligible for GWC caching unless they target a configured `LayerGroup` tile layer.
3. Gets the current user from `SecurityContextHolder`.
4. If any constituent has content-affecting restrictions, calls `AccessLimitsKeyBuilder` and stores the result in `SecurityKeyHolder`.

In `finished()` it unconditionally clears `SecurityKeyHolder`.

### 3.7 GeoServerTileLayer integration

`GeoServerTileLayer.getModifiableParameters()` overrides the GWC base method. It always strips any client-supplied `ACCESS_LIMITS_KEY` and `SECURITY_TAGS_KEY` from the incoming request parameter map first — **a client injecting crafted values must never influence cache key selection**. It then reads `SecurityKeyHolder`: if a key is present, it adds `ACCESS_LIMITS_KEY → key` to the sanitised parameter map; if the computed `AccessLimits` also carries non-empty `securityTags`, it adds `SECURITY_TAGS_KEY → sorted-comma-joined-tags`. Both are then passed to `super.getModifiableParameters()`. If no key is present (unrestricted access), it delegates without adding either parameter.

### 3.8 SecurityParameterFilter

`SecurityParameterFilter` extends GWC's `ParameterFilter`. A single class covers both security parameter dimensions: `ACCESS_LIMITS_KEY` (content fingerprint) and `SECURITY_TAGS_KEY` (invalidation tags). **Both are public string constants — they identify stored tile sets and changing them would orphan existing caches.** The class is registered once per constant when "Enable Data Security" is on. Its `apply()` method returns the value unchanged — values are computed and injected upstream by `GeoServerTileLayer`.

`SECURITY_TAGS_KEY` is only injected when `ACCESS_LIMITS_KEY` is also present: tags without restrictions is a nonsensical state.

It is to be noted that these filters are managed automatically — administrators do not add them manually. Both are activated on all tile layers when the global [**Enable Data Security**](https://docs.geoserver.org/main/en/user/geowebcache/webadmin/defaults/#enable-data-security) option is turned on in the GWC defaults page (the same option that today controls whether GWC checks layer accessibility at all).

### 3.9 Phase 1 deliverables

- `securityTags` field on `AccessLimits` — mutable `Set<String>`, null by default; stored as separate `SECURITY_TAGS_KEY` parameter (not embedded in `ACCESS_LIMITS_KEY`) to allow targeted cache invalidation (see §4.4)
- `AccessLimitsKeyBuilder` — canonical key text computation
- `SecurityKeyHolder` — thread-local carrying the key across the request
- `SecurityKeyDispatcherCallback` — computes and stores the key at operation dispatch time; handles layer group constituent iteration
- `ParameterValueKeySerializer<T>` — SPI interface for custom `GeneralParameterValue` types
- built-in serializers for `Filter`, `Geometry`, and primitive types
- `IgnorableParameterRegistry` — built-in ignorable list plus system property override (with startup CONFIG-level logging)
- `SecurityParameterFilter` — GWC `ParameterFilter` subclass covering both `ACCESS_LIMITS_KEY` and `SECURITY_TAGS_KEY` dimensions (both are public string constants on this class)
- `GeoServerTileLayer.getModifiableParameters()` override — strips any client-supplied security parameters, injects `ACCESS_LIMITS_KEY` and (if `securityTags` non-empty) `SECURITY_TAGS_KEY` from thread-local
- automatic activation logic for both `SecurityParameterFilter` instances on all tile layers when the global "Enable Data Security" option is on
- integration tests covering vector, raster, layer group, and mixed access limit scenarios

---

## 4. Phase 2 — Security-driven tile invalidation

### 4.1 Motivation

Phase 1 prevents incorrect cache hits but leaves stale tiles when rules change. Disk quota handles cleanup eventually. Phase 2 allows RAM implementations to drive immediate invalidation. GWC has no visibility into security rule semantics — only the RAM knows what to drop.

### 4.2 SecurityConfigurationChangeEvent

A plain value object carrying the set of layers whose security restrictions have changed:

```java
public class SecurityConfigurationChangeEvent {
    /**
     * Layers whose security restrictions have changed.
     * null means the scope is unknown — all security-keyed tiles across all layers must be dropped.
     * Non-null means only the listed layers are affected.
     * The source is responsible for expanding workspace-scoped changes to individual LayerInfo objects.
     */
    Set<LayerInfo> getAffectedLayers();

    /**
     * Optional tag for targeted invalidation within the affected layers.
     * When non-null, only tiles whose SECURITY_TAGS_KEY value contains this tag
     * (comma-separated membership) are deleted. When null, all security-keyed tiles
     * for the affected layers are deleted regardless of their tags.
     */
    String getTargetTag();
}
```

The event carries only security domain information — no GWC or tile concepts. The source is responsible for resolving workspace-scoped changes to individual `LayerInfo` instances; the invalidation executor does not need catalog access.

### 4.3 SecurityCacheInvalidationSource and SecurityCacheInvalidationListener

Any Spring bean can act as an invalidation source by implementing this interface. It is intentionally separate from `ResourceAccessManager`: a RAM that cannot be modified can have a companion bean act on its behalf.

```java
public interface SecurityCacheInvalidationSource {
    void register(SecurityCacheInvalidationListener listener);
}

public interface SecurityCacheInvalidationListener {
    void onSecurityConfigChange(SecurityConfigurationChangeEvent event);
}
```

Sources own their change-detection mechanism entirely — Hibernate events, polling, REST callbacks, or anything else. GWC registers `SecurityCacheInvalidator` with all `SecurityCacheInvalidationSource` beans found in the Spring context at startup. When the source detects a rule change it calls all registered listeners directly.

### 4.4 SecurityCacheInvalidator

`SecurityCacheInvalidator` translates `SecurityConfigurationChangeEvent` instances into GWC tile deletions. It calls `StorageBroker` directly (the REST truncation API has no endpoint accepting a raw `parametersId`):

1. `StorageBroker.getCachedParameters(layerName)` returns the full parameter map for every cached parameter combination, keyed by `parametersId`.
2. The executor filters to entries whose parameter map contains `ACCESS_LIMITS_KEY`. If `getTargetTag()` is non-null, it further filters to entries where `SECURITY_TAGS_KEY` contains the target tag (comma-separated string membership check — no JSON parsing needed). If `getTargetTag()` is null, all entries carrying `ACCESS_LIMITS_KEY` are matched.
3. Each matching `parametersId` is deleted via `StorageBroker.deleteByParametersId(layerName, parametersId)`.

- If `getAffectedLayers()` is non-null, step 1 is called for each listed layer only.
- If `getAffectedLayers()` is null, step 1 is called for every tile layer in the catalog.

Tiles cached without `ACCESS_LIMITS_KEY` (unrestricted access) are never touched.

### 4.5 Participation models

| Scenario | How invalidation is wired |
|---|---|
| GeoFence | Implements `SecurityCacheInvalidationSource` alongside the RAM. Registers GWC as a listener at startup. At evaluate time, tags each `AccessLimits` with the IDs of the rules that shaped it (e.g. `"geofence:rule:42"`) via `securityTags`. On rule change, determines scope from the rule's targeting and emits an event with `targetTag="geofence:rule:{id}"` — only tiles shaped by that rule are deleted, leaving unrelated security-keyed tiles intact. |
| Geometry-from-local-layer RAM | A separate `CatalogListener` bean implements `SecurityCacheInvalidationSource`, watches the geometry source layer for data changes, and calls listeners with `affectedLayers=null`. No modification to the RAM. |
| GeoServer built-in security | No participation needed. Built-in rules either grant or deny whole-layer access; `ACCESS_LIMITS_KEY` is never injected, so there are no security-keyed tiles to invalidate. |

### 4.6 Applicability

This design works only in **fully live, user-driven environments** where all security configuration changes flow through the running GeoServer/GeoFence instance. It fails silently in common operational scenarios:

- **Environment promotion** — security rules edited in staging and promoted to production via database backup/restore produce no notifications. Stale tiles survive.
- **Direct database manipulation** — bulk rule imports, SQL patches, or migration scripts bypass any event mechanism.
- **Clustered deployments with per-node local disk cache** — each node runs its own `SecurityCacheInvalidator`. A notification received by one node does not propagate to others; tiles are invalidated on that node only. Deployments using a shared BlobStore (S3, Azure, NFS, GWC clustering) are not affected — invalidation by any node is immediately visible to all.

In these scenarios disk quota remains the sole cleanup mechanism. Deployments with a hard SLA on stale tile expiry should combine explicit invalidation with a disk quota policy tuned to their acceptable staleness window.

### 4.7 Phase 2 deliverables

- `SecurityConfigurationChangeEvent` — value object carrying affected `LayerInfo` set (null = full sweep) and optional `targetTag` for tag-targeted invalidation within affected layers
- `SecurityCacheInvalidationSource` — SPI; any Spring bean can implement it
- `SecurityCacheInvalidationListener` — callback interface implemented by `SecurityCacheInvalidator`
- `SecurityCacheInvalidator` — implements `SecurityCacheInvalidationListener`; translates events to GWC tile deletions via `StorageBroker`; registers itself with all `SecurityCacheInvalidationSource` beans at startup
- operator documentation covering participation models, applicability limits, and the TOCTOU window

---

## 5. Known limitations

**Stale tiles after rule changes.** When security configuration changes, some tile sets may no longer correspond to any active access configuration. Disk quota reclaims these over time. For RAMs that don't implement `SecurityCacheInvalidationSource` this is the only cleanup mechanism — it works, but without time guarantees. RAMs that populate `securityTags` on `AccessLimits` can emit targeted invalidation events (by tag) that avoid full-layer tile sweeps; RAMs that do not set tags fall back to sweeping all security-keyed tiles for the affected layers.

**`GeneralParameterValue` coverage.** Built-in serializers cover all parameter types used by GeoServer's bundled readers. Custom readers with exotic types need a contributed `ParameterValueKeySerializer`; missing one throws at request time.

**Cache warming after full sweep.** Broad invalidation drops all security-keyed tiles. Rebuilding the cache is an operational concern, not addressed here.

**Invalidation scan cost grows with distinct security configurations.** `SecurityCacheInvalidator` calls `StorageBroker.getCachedParameters(layerName)`, which reads every `parameters-<id>.properties` file for the layer — one file read (FileBlobStore) or one object GET (S3BlobStore) per distinct cached parameter combination. For deployments with many users and per-user geometry restrictions this can accumulate a large number of parameter sets per layer, making the scan slow. This cost is paid only on security configuration changes, not on tile requests. No reverse index exists in GWC today; if scan latency becomes unacceptable, the mitigation is a shorter disk quota TTL to limit accumulation.

**`securityTags` fragmentation (low probability).** Two users with identical restrictions but different `securityTags` get separate cache entries. In particular, this requires RAMs that assign per-user or per-rule tags to users whose effective restrictions happen to be identical — a configuration smell (e.g. duplicate GeoFence rules with the same filter). Tiles are always correct; only cache efficiency is affected. RAMs can mitigate by tagging at the appropriate granularity: role name rather than rule ID for role-scoped rules, rule ID only for genuinely user-specific ones.

**Geometry coordinate drift causes cache fragmentation.** `Geometry.norm()` canonicalizes vertex and ring order but does not reduce coordinate precision. Floating-point variation from reprojection on each RAM call can produce slightly different WKT strings for the same mask, creating orphaned tile sets. RAM implementations should cache reprojected geometries rather than recomputing them. The serialized key includes the CRS authority code to prevent a separate collision: identical WKTs in different CRS represent different restrictions.

**Phase 2 invalidation is not atomic.** Tiles written during the invalidation window under a stale fingerprint — due to RAM-side rule caching lag, e.g. GeoFence maintains its own in-process rule cache with a TTL — may survive until evicted by a subsequent invalidation or disk quota. **Disk quota is therefore a complementary safety net even when Phase 2 invalidation is active, not a fallback of last resort.**

**Tile seeding of restricted layers is not supported.** Seeding runs under administrator credentials, which typically have no restrictions. No `ACCESS_LIMITS_KEY` is injected, seeded tiles land in the unrestricted cache, and restricted users always get cache misses. Pre-warming the cache for restricted access configurations is not possible.

---

## 6. Package structure

```
org.geoserver.gwc.security
  AccessLimitsKeyBuilder                    (Phase 1)
  SecurityKeyHolder                         (Phase 1)
  SecurityKeyDispatcherCallback             (Phase 1)
  SecurityParameterFilter                   (Phase 1)
  IgnorableParameterRegistry                (Phase 1)
  ParameterValueKeySerializer               (Phase 1, SPI)
  serializers/
    FilterKeySerializer                     (Phase 1)
    GeometryKeySerializer                   (Phase 1)
    NumericKeySerializer                    (Phase 1)
    BooleanKeySerializer                    (Phase 1)
    StringKeySerializer                     (Phase 1)
  SecurityConfigurationChangeEvent          (Phase 2)
  SecurityCacheInvalidationSource           (Phase 2, SPI)
  SecurityCacheInvalidationListener         (Phase 2)
  SecurityCacheInvalidator                  (Phase 2)

org.geoserver.gwc.layer
  GeoServerTileLayer  (getModifiableParameters override, Phase 1)
```

---

## 7. RAM implementation examples

### 7.1 Default security subsystem

GeoServer's built-in security grants or denies whole-layer access, always returning `null` or `AccessLimits` with `readFilter = Filter.INCLUDE` and no geometry. Both cases fall into the "no parameter added" path (§3.2) — `ACCESS_LIMITS_KEY` is never injected and no `SecurityCacheInvalidationSource` is needed.

### 7.2 GeoFence

GeoFence is a rule-based access control system that produces combinations of CQL filters, geometry restrictions, and attribute limitations. Its outputs map directly onto the `AccessLimits` hierarchy (`VectorAccessLimits`, `CoverageAccessLimits`) and are fingerprinted correctly by `AccessLimitsKeyBuilder` without any GeoFence-specific code.

**Phase 1.** No changes needed in GeoFence. The fingerprinting works transparently. When rules change, orphaned tile sets are reclaimed by disk quota (see section 5).

**Phase 2.** GeoFence should tag each `AccessLimits` it returns with the IDs of the rules that contributed to the decision (e.g. `"geofence:rule:42"`) via `AccessLimits.securityTags`. It should also implement `SecurityCacheInvalidationSource`, using the changed rule's targeting to determine `affectedLayers` and setting `targetTag` to the rule's identifier:

- Rule targets a specific layer → `affectedLayers` = that `LayerInfo`, `targetTag` = `"geofence:rule:{id}"`
- Rule targets a workspace with wildcard layer → enumerate the workspace's layers from the GeoServer catalog → `affectedLayers` = those layers, `targetTag` = `"geofence:rule:{id}"`
- Rule is fully wildcarded (no workspace/layer filter) → `affectedLayers = null`, `targetTag` = `"geofence:rule:{id}"`
- Priority-only reorder (no change to layer targeting) → `affectedLayers = null`, `targetTag = null` (rule interactions make scoping unresolvable; full sweep required)

Deployments with high tile regeneration cost can batch rule changes and issue a single event at the end of the batch. GeoFence is not required to implement `SecurityCacheInvalidationSource` — without it, disk quota handles orphan cleanup as described in section 5.

---

## Appendix A — Filter normalization

ECQL serialization via `ECQL.toCQL()` is more compact and readable than OGC Filter 1.1 XML, producing shorter `ACCESS_LIMITS_KEY` values that are easier to inspect in `parameters-<id>.properties` files. It does not produce a canonical form for all semantically equivalent filters. In particular, `AccessLimitsKeyBuilder` applies a stable normalization pass before calling `ECQL.toCQL()` to reduce fingerprint fragmentation.

### A.1 NormalizingFilterVisitor

A single `NormalizingFilterVisitor` subclassing `SimplifyingFilterVisitor` applies stable normalization intended to reduce cache fragmentation — not a complete canonicalization. The parent handles constant folding, double-negation elimination, and `AND`/`OR` flattening. The subclass adds:

**Comparison operand normalization.** For any `BinaryComparisonOperator` with a `Literal` on the left and a `PropertyName` on the right, swap and invert the operator:

- `13 = x` → `x = 13`
- `13 < x` → `x > 13`

**IN-list value sort.** Literal values in membership tests (e.g. `PropertyIsEqualTo` chains, `InFunction`) are sorted lexicographically.

**AND/OR operand sort.** Operands of `And` and `Or` nodes are sorted by a total order: class name lexicographically, then recursively by the ECQL representation of child nodes. This reduces cache fragmentation when the same semantic filter is produced with different operand ordering across requests. The Filter hierarchy is visitor-based with a fixed known interface set, so maintaining the sort order is practical.

---

## 8. Alternatives considered and discarded

**RAM-provided stable key strings.** Allowing RAM implementations to return a pre-computed stable string directly instead of `AccessLimits` would bypass `AccessLimitsKeyBuilder`. Rejected: it pushes canonicalization, ECQL formatting, WKT normalization, and collision-avoidance responsibility onto every RAM implementor. `AccessLimitsKeyBuilder` exists precisely to own those concerns. It also prevents cross-RAM key collisions in multi-RAM deployments. The `ParameterValueKeySerializer` SPI already handles the legitimate extension case (custom `GeneralParameterValue` types) without leaking key mechanics to the RAM.

**Direct RAM call inside `GeoServerTileLayer.getModifiableParameters()`.** Arguments in its favour: fewer classes, no lifecycle management. Arguments against: WMTS goes through the OWS Dispatcher, so `DispatcherCallback` covers all tile-serving paths; `finished()` is called in a `finally` block, so thread-local leaks on exceptions are not a concern. The `DispatcherCallback` approach is retained for its clean separation of concerns.

**OGC Filter 1.1 XML as filter serialization format.** Less compact and readable than ECQL. Discarded in favour of `ECQL.toCQL()`.

**Direct `ApplicationEventPublisher.publishEvent()` with `SecurityCacheInvalidator` as `@EventListener`.** Using a generic Spring event mechanism for a very specific security-cache concern creates too many potential listeners and lacks an explicit contract for implementors. The SPI approach is more targeted.

**WKB instead of WKT for geometry serialization.** Not human-readable in `parameters-<id>.properties` files. `Geometry.norm()` + WKT is sufficient.
