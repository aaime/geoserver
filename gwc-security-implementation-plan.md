# GWC Security Parameter Filter — Implementation Plan

> **Note:** This plan is subject to change as implementation reveals unexpected constraints,
> API gaps, or design issues. Update this file when significant deviations occur.

| | |
|---|---|
| **Status** | In progress |
| **Design doc** | `gwc-security-parameter-filter-design.md` |
| **Module** | `gs-gwc` (`src/gwc/`) |
| **New package** | `org.geoserver.gwc.security` |

---

## Key implementation notes (pre-coding findings)

- `StorageBroker.getCachedParameters()` returns `Set<Map<String,String>>` (not keyed by parametersId).
  Phase 2 invalidation uses `deleteByParameters(layerName, map)` — no parametersId lookup needed.
- `GeoServerTileLayer` inherits `getModifiableParameters()` from GWC's `TileLayer` — override it.
- `SecurityParameterFilter` instances are **synthetic**: injected transiently via
  `getParameterFilters()` override, never stored in `GeoServerTileLayerInfo`, never shown in UI.
- OGC API Dispatcher also calls `DispatcherCallback` — single `SecurityKeyDispatcherCallback`
  implementation covers all tile-serving paths (OWS + OGC API).
- `SimplifyingFilterVisitor` is a GeoTools class (already on classpath).

---

## Phase 1 — Security key fingerprinting

### Step 1 — `AccessLimits.securityTags` field
**File:** `src/main/src/main/java/org/geoserver/security/AccessLimits.java`

- Add `@Nullable Set<String> securityTags` field, null by default
- Add getter/setter
- Exclude from `equals()`/`hashCode()` — tags are invalidation metadata, not content fingerprint

---

### Step 2 — `NormalizingFilterVisitor`
**File:** `org.geoserver.gwc.security.NormalizingFilterVisitor`

Extends `org.geotools.filter.visitor.SimplifyingFilterVisitor`. Adds:

- **Comparison swap:** for any `BinaryComparisonOperator` with `Literal` left + `PropertyName` right,
  swap operands and invert operator (`<` → `>`, `<=` → `>=`, etc.)
- **IN-list sort:** sort literal values in membership tests lexicographically
- **AND/OR operand sort:** sort operands by (class name, then ECQL string of children) for stable ordering

No Spring wiring — used directly by `AccessLimitsKeyBuilder`.

---

### Step 3 — `ParameterValueKeySerializer<T>` SPI + built-in serializers
**File:** `org.geoserver.gwc.security.ParameterValueKeySerializer` (interface)

```java
public interface ParameterValueKeySerializer<T extends GeneralParameterValue> {
    Class<T> getValueType();
    String toKey(T value);
}
```

Built-in implementations in `org.geoserver.gwc.security.serializers`:

| Class | Handles | Serialization |
|---|---|---|
| `FilterKeySerializer` | `Parameter<Filter>` | `ECQL.toCQL()` after `NormalizingFilterVisitor` |
| `GeometryKeySerializer` | `Parameter<Geometry>` / `Parameter<MultiPolygon>` | CRS authority code + `:` + `geometry.norm().toText()` |
| `NumericKeySerializer` | `Parameter<Number>` | decimal string |
| `BooleanKeySerializer` | `Parameter<Boolean>` | `"true"` / `"false"` |
| `StringKeySerializer` | `Parameter<String>` | value as-is |

---

### Step 4 — `IgnorableParameterRegistry`
**File:** `org.geoserver.gwc.security.IgnorableParameterRegistry`

Spring singleton bean:

- Built-in ignorable descriptor names: `USE_JAI_IMAGEREAD`, `ALLOW_MULTITHREADING`,
  `MAX_ALLOWED_TILES`, `SUGGESTED_TILE_SIZE`
- Constructor reads `gwc.security.params.ignorable` system property (comma-separated),
  adds entries, logs each at CONFIG level
- `boolean isIgnorable(GeneralParameterValue)` checks descriptor name

---

### Step 5 — `AccessLimitsKeyBuilder`
**File:** `org.geoserver.gwc.security.AccessLimitsKeyBuilder`

Spring singleton bean. Injected with `List<ParameterValueKeySerializer<?>>` + `IgnorableParameterRegistry`.

- `String buildKey(AccessLimits)` — returns `null` for unrestricted access (design §3.2):
  - RAM returns `null`
  - RAM returns `AccessLimits` with `readFilter = Filter.INCLUDE` and no geometry
- Walks instanceof hierarchy most-specific-first:
  `VectorAccessLimits` → `CoverageAccessLimits` → `DataAccessLimits` → base `AccessLimits`
- Builds JSON per design §3.3 table; omits null/absent fields
- `CoverageAccessLimits.params` handling (Tier 1/2/3 per §3.4): throws named exception for unknown types
- Duplicate `ParameterValueKeySerializer` registrations: fail-fast at construction with descriptive error
- Layer group path: `String buildKey(List<Pair<String,AccessLimits>>)` — JSON array in composition order,
  each entry includes `"layer"` field with qualified name

---

### Step 6 — `SecurityKeyHolder`
**File:** `org.geoserver.gwc.security.SecurityKeyHolder`

```java
public class SecurityKeyHolder {
    private static final ThreadLocal<String> KEY = new ThreadLocal<>();
    private static final ThreadLocal<String> TAGS = new ThreadLocal<>();

    public static void set(String key, String tags) { KEY.set(key); TAGS.set(tags); }
    public static String getKey() { return KEY.get(); }
    public static String getTags() { return TAGS.get(); }
    public static void clear() { KEY.remove(); TAGS.remove(); }
}
```

---

### Step 7 — `SecurityParameterFilter`
**File:** `org.geoserver.gwc.security.SecurityParameterFilter`

Extends `org.geowebcache.filter.parameters.ParameterFilter`:

- `public static final String ACCESS_LIMITS_KEY = "ACCESS_LIMITS_KEY"` — changing orphans existing caches
- `public static final String SECURITY_TAGS_KEY = "SECURITY_TAGS_KEY"` — same warning
- Constructor takes the key constant as argument
- `apply(String value)` returns value unchanged (computed upstream)
- Check GWC base class contract for `getDefaultValue()` and `getLegalValues()` return values

---

### Step 8 — `SecurityKeyDispatcherCallback`
**File:** `org.geoserver.gwc.security.SecurityKeyDispatcherCallback`

Spring singleton bean, implements `org.geoserver.ows.DispatcherCallback`.
Injected with `ResourceAccessManager`, `AccessLimitsKeyBuilder`, `Catalog`.

`operationDispatched()`:
1. Detect tile-serving operation (WMTS GetTile, TMS, OGC API Tiles, WMS GetMap eligible for caching)
2. Resolve layer/layer group from operation context
3. **Single layer:** call RAM → `buildKey(AccessLimits)`
4. **Layer group:** iterate `LayerGroupInfo.layers()`, call RAM per constituent,
   build composite key + union `securityTags` across all constituents
5. If key non-null: `SecurityKeyHolder.set(key, sortedTags)`

`finished()`: `SecurityKeyHolder.clear()` unconditionally (called in finally by dispatcher).

All other `DispatcherCallback` methods: return arguments unchanged / no-op.

---

### Step 9 — `GeoServerTileLayer.getModifiableParameters()` override
**File:** `src/gwc/src/main/java/org/geoserver/gwc/layer/GeoServerTileLayer.java`

Override `public Map<String,String> getModifiableParameters(Map<String,?> map, String encoding)`:

1. Strip `ACCESS_LIMITS_KEY` and `SECURITY_TAGS_KEY` from incoming `map` — client injection prevention
2. Call `super.getModifiableParameters(sanitizedMap, encoding)`
3. Read `SecurityKeyHolder.getKey()`: if non-null, put `ACCESS_LIMITS_KEY → key` into result
4. Read `SecurityKeyHolder.getTags()`: if non-null/non-empty, put `SECURITY_TAGS_KEY → tags`
5. Return enriched result

---

### Step 10 — Auto-activation of `SecurityParameterFilter` (synthetic, UI-hidden)
**File:** `src/gwc/src/main/java/org/geoserver/gwc/layer/GeoServerTileLayer.java`

Override `getParameterFilters()`:

- Call `super.getParameterFilters()` (reads from `GeoServerTileLayerInfo`)
- If `GWCConfig.securityEnabled == true`, append two `SecurityParameterFilter` instances
  (`ACCESS_LIMITS_KEY` and `SECURITY_TAGS_KEY`) to returned list
- These are **not** stored in `GeoServerTileLayerInfo` — purely transient
- Ensure the GWC configuration UI does not display them:
  find where parameter filters are listed in `src/web/gwc/` and exclude entries
  with keys matching `SecurityParameterFilter.ACCESS_LIMITS_KEY` / `SECURITY_TAGS_KEY`

---

### Step 11 — Spring wiring
Register in `src/gwc/src/main/resources/applicationContext.xml` (or annotation scanning if module uses it):

- `IgnorableParameterRegistry` (singleton)
- All 5 serializer beans (singleton)
- `AccessLimitsKeyBuilder` (singleton, injected with serializer list + registry)
- `SecurityKeyDispatcherCallback` (singleton, injected with RAM + builder + catalog)

---

### Step 12 — Integration tests
Scenarios to cover:

| Scenario | What to assert |
|---|---|
| Vector layer, user with CQL filter | `ACCESS_LIMITS_KEY` present, differs from unrestricted user |
| Raster layer, user with geometry mask | `ACCESS_LIMITS_KEY` present, differs from unrestricted user |
| Layer group, per-constituent access | composite key encodes each layer's limits in order |
| Two users, identical restrictions | same `ACCESS_LIMITS_KEY` (cache shared) |
| Client injects `ACCESS_LIMITS_KEY` | stripped, replaced by ThreadLocal value |
| Unrestricted user | no `ACCESS_LIMITS_KEY` in params |
| `securityTags` set | `SECURITY_TAGS_KEY` injected alongside `ACCESS_LIMITS_KEY` |
| `securityTags` absent | `SECURITY_TAGS_KEY` not injected |
| Unknown `GeneralParameterValue` type | throws at request time with descriptor name in message |

---

## Phase 2 — Security-driven tile invalidation

*Start after Phase 1 is complete and stable.*

### Step 13 — `SecurityConfigurationChangeEvent`
**File:** `org.geoserver.gwc.security.SecurityConfigurationChangeEvent`

Plain value object (not a Spring event):

```java
public class SecurityConfigurationChangeEvent {
    private final Set<LayerInfo> affectedLayers; // null = all layers
    private final String targetTag;              // null = all security-keyed tiles for layer
    // constructor, getters only
}
```

---

### Step 14 — SPI interfaces
**Files:** `org.geoserver.gwc.security.SecurityCacheInvalidationSource`,
`org.geoserver.gwc.security.SecurityCacheInvalidationListener`

```java
public interface SecurityCacheInvalidationSource {
    void register(SecurityCacheInvalidationListener listener);
}
public interface SecurityCacheInvalidationListener {
    void onSecurityConfigChange(SecurityConfigurationChangeEvent event);
}
```

---

### Step 15 — `SecurityCacheInvalidator`
**File:** `org.geoserver.gwc.security.SecurityCacheInvalidator`

Spring singleton bean, implements `SecurityCacheInvalidationListener`, `InitializingBean`.
Injected with `StorageBroker`, `TileLayerDispatcher`, `List<SecurityCacheInvalidationSource>`.

`afterPropertiesSet()`: registers self with all sources.

`onSecurityConfigChange(event)`:
1. Determine layer name set:
   - `affectedLayers == null` → all tile layers via `TileLayerDispatcher`
   - else map each `LayerInfo` to its tile layer name(s)
2. For each layer name:
   a. `getCachedParameters(layerName)` → `Set<Map<String,String>>`
   b. Filter maps that contain `ACCESS_LIMITS_KEY`
   c. If `targetTag != null`: further filter where `SECURITY_TAGS_KEY` value
      contains `targetTag` (comma-split, `Set.contains`)
   d. `deleteByParameters(layerName, map)` for each match
3. Tiles cached without `ACCESS_LIMITS_KEY` (unrestricted) are never touched

---

## Dependency order

```
Step 1 (AccessLimits.securityTags)
Step 2 (NormalizingFilterVisitor)
Step 3 (ParameterValueKeySerializer + serializers)
Step 4 (IgnorableParameterRegistry)
     ↓
Step 5 (AccessLimitsKeyBuilder)  ←  depends on 2, 3, 4
Step 6 (SecurityKeyHolder)
Step 7 (SecurityParameterFilter)
     ↓
Step 8 (SecurityKeyDispatcherCallback)  ←  depends on 5, 6
Step 9 (GeoServerTileLayer.getModifiableParameters)  ←  depends on 6, 7
Step 10 (auto-activate SecurityParameterFilter)  ←  depends on 7
Step 11 (Spring wiring)  ←  depends on 4, 5, 8
Step 12 (integration tests)
     ↓
Step 13 (SecurityConfigurationChangeEvent)
Step 14 (SPI interfaces)
Step 15 (SecurityCacheInvalidator)  ←  depends on 13, 14
```

Steps 1–4 can be coded in parallel. Steps 2, 3, 4 have no mutual dependencies.
