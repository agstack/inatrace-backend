# Plot geometry properties

The upload endpoint accepts GeoJSON points and polygons. Coordinates use
longitude, latitude order and are rounded to six decimal places with HALF_UP
when stored. Polygon area is computed from the original coordinates with the
Mapbox Turf spherical model, divided by 10,000 square metres per hectare, then
truncated to two decimal places. A point has no polygon area or unit. Export
appends the initial coordinate to polygon rings, including already closed rings.

`PlotGeometryPropertiesTest` generates bounded rectangles with latitudes
between -50 and 50 degrees, longitudes between -120 and 120 degrees, and
widths/heights from 0.00001 to 0.005 degrees. The independent rectangle
integral uses a sphere of radius 6,378,137 m, matching the deployed Turf model.
The comparison tolerance is the larger of 0.0001 square metres or 1e-7 of
the expected area. Generated rings are rotated and reversed; serialization is
reopened using the real GeoJSON parser. A known rectangle explicitly rejects
the historical tenfold hectare result. jqwik uses the qualified 1.8.5 engine,
reports reproducible seeds and stores its replay database under `target`.

The integration test sends eight synthetic rectangles through the real upload,
reloads the resulting plots and compares stored area and coordinates against
the independent reference. It also checks exported feature count, all four
vertices in cyclic or reversed boundary order, ring closure and the presence of
the `geoID` property. Separate rectangles straddling 100 m² verify that
hectares truncate respectively to 0.00 and 0.01. Existing
tests cover a point, invalid JSON, unsupported LineString and foreign-tenant
access. The AgStack client is mocked and the tests check the number of calls,
including no call for an unauthorized upload.

An independent committed-request test sends a valid point followed by a null
feature, an empty, short or unclosed outer polygon ring, or a polygon with a
hole. These inputs now return HTTP 400 and the preceding point is rolled back.
Holes are rejected because the current plot model persists only the outer ring;
previously area subtracted holes while persistence silently discarded them.
The application's custom transaction manager rolls back checked API errors.
A null geometry and unsupported
LineString or MultiPolygon in a collection are currently ignored, while a
valid point in the same collection is committed. The test removes only its
own fixture by ID. A self-intersecting closed bow-tie polygon is currently
accepted and persisted; this is not a comprehensive validity check and requires
separate product/domain review. These examples do not establish behavior for every
malformed ring or unsupported geometry. Export topology is checked for
the generated rectangles; other shapes and arbitrary concave rings are not covered.

The qualified jqwik engine was also used by the pending spreadsheet PR #134.
Final validation: `mvn -q -DargLine=-Dapi.version=1.43 verify` and
`git diff --check` passed. The three geometry-related classes reported 30
tests/properties and approximately 2.81 seconds combined, excluding shared
Spring and MySQL startup. In this run, the generated point and rectangle
properties each checked 100 cases; jqwik reported seeds 8725214293453577493,
7422184220919917126 and 8200990824617192615 (the boolean property is
exhaustive and checks both cases). Failed examples and seeds can be replayed
using jqwik's output and `target/jqwik-database`.
