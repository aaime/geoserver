Configuration of OGC API - Features module
------------------------------------------

The service operates as an additional protocol for sharing vector data along side Web Feature Service.

Service configuration
''''''''''''''''''''''

The service is configured using:

* The existing :ref:`wfs` settings to define title, abstract, and output formats.
  
  This is why the service page is titled ``GeoServer Web Feature Service`` by default.

* Contact information defined in :ref:`config_contact`.

* Extra links can be added on a per-service or per-collection basis as indicated in :ref:`ogcapi_links`.

Conformance class configuration
'''''''''''''''''''''''''''''''

OGC API - Features is a modular service that allows you to enable or disable specific conformance classes based on your requirements.
The configuration is done through the GeoServer web interface, and allows to tailor the service to your needs.

Each set of conformance classes is represented in a table, where only the configurable classes
are displayed (e.g. the HTML and JSON conformance classes cannot be disabled, thus are not enumerated
in the table).

The table reports checkboxes for enabling or disabling the conformance classes. If a conformance
class was not configured before, an "unset" checkbox is displayed, and for that one, the default
behavior is applied:

* For standard conformance classes, the default is to enable them.
* For community and draft conformance classes, the default is to disable them.

In terms of classification, the table have a different type, "core" for the core class (part 1),
and "extension" for all optional extension.conformance

The table also reports a "level":

* "Standard": an official OGC standard conformance class.
* "Implementing": an official OGC standard conformance class that is not yet fully implemented and compliant.
* "Draft Standard": an official OGC standard conformance class that is still in draft status.
* "Community": a conformance class that is provided by a non OGC source (e.g., GeoServer itself) and is finalized.
* "Community Draft": same as above, but still in draft status.
* "Retired standard": a standard, official or otherwise, that has been retired and should be migrated away from.

Feature Service conformance classes
'''''''''''''''''''''''''''''''''''

The OGC API - Feature service is modular, allowing you to enable/disable the functionality you wish to include.

* Feature API basic conformance classes

  .. figure:: img/feature-service-configuration.png
     
     Feature Service Configuration

* CQL2 Filter conformances.
  
  Both the Text and JSON formats for CQL2 are available and may be enabled or disabled.

  .. figure:: img/cql2-configuration.png
     
     CQL2 Filter configuration

* Control of ECQL Filter conformances

  .. figure:: img/ecql-configuration.png
     
     ECQL Filter configuration

For more information see :doc:`status`.

