package com.abelium.inatrace.components.company;

import com.mapbox.geojson.Feature;
import com.mapbox.geojson.FeatureCollection;
import com.mapbox.geojson.Point;
import com.mapbox.geojson.Polygon;
import com.mapbox.turf.TurfMeasurement;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PlotGeometryPropertiesTest {
    // Spherical rectangle integral; radius matches the model used by the installed Turf SDK.
    private static final double EARTH_RADIUS_METRES = 6_378_137;

    static double rectangleArea(double west, double south, double east, double north) {
        return EARTH_RADIUS_METRES * EARTH_RADIUS_METRES * Math.toRadians(east - west)
                * (Math.sin(Math.toRadians(north)) - Math.sin(Math.toRadians(south)));
    }

    static List<Point> rectangle(double west, double south, double east, double north) {
        return List.of(Point.fromLngLat(west, south), Point.fromLngLat(east, south),
                Point.fromLngLat(east, north), Point.fromLngLat(west, north), Point.fromLngLat(west, south));
    }

    @Property(tries = 100)
    void pointGeoJsonPreservesLongitudeAndLatitude(
            @ForAll @IntRange(min = -120000000, max = 120000000) int longitudeMicrodegrees,
            @ForAll @IntRange(min = -50000000, max = 50000000) int latitudeMicrodegrees) {
        double longitude = longitudeMicrodegrees / 1_000_000d;
        double latitude = latitudeMicrodegrees / 1_000_000d;
        Feature feature = Feature.fromGeometry(Point.fromLngLat(longitude, latitude));
        Point decoded = (Point) Feature.fromJson(feature.toJson()).geometry();
        assertEquals(longitude, decoded.longitude());
        assertEquals(latitude, decoded.latitude());
    }

    @Property(tries = 100)
    void rectanglesMatchIndependentSphericalAreaAndSurviveRingTransformations(
            @ForAll @IntRange(min = -50, max = 50) int latitude,
            @ForAll @IntRange(min = -120, max = 120) int longitude,
            @ForAll @IntRange(min = 10, max = 5000) int width,
            @ForAll @IntRange(min = 10, max = 5000) int height,
            @ForAll @IntRange(min = 0, max = 3) int offset) {
        double east = longitude + width / 1_000_000d;
        double north = latitude + height / 1_000_000d;
        List<Point> ring = rectangle(longitude, latitude, east, north);
        double expected = rectangleArea(longitude, latitude, east, north);
        double actual = TurfMeasurement.area(Polygon.fromLngLats(List.of(ring)));
        assertEquals(expected, actual, Math.max(0.0001, expected * 1e-7));
        List<Point> rotated = new ArrayList<>(ring.subList(0, 4));
        Collections.rotate(rotated, offset);
        Collections.reverse(rotated);
        rotated.add(rotated.get(0));
        assertEquals(expected, TurfMeasurement.area(Polygon.fromLngLats(List.of(rotated))),
                Math.max(0.0001, expected * 1e-7));
        FeatureCollection parsed = FeatureCollection.fromJson(FeatureCollection.fromFeature(
                Feature.fromGeometry(Polygon.fromLngLats(List.of(ring)))).toJson());
        Polygon parsedPolygon = (Polygon) parsed.features().get(0).geometry();
        assertEquals(longitude, parsedPolygon.coordinates().get(0).get(0).longitude());
        assertEquals(latitude, parsedPolygon.coordinates().get(0).get(0).latitude());
    }

    @Property(tries = 20)
    void knownRectangleRejectsTenfoldHectareDivisor(@ForAll boolean reverse) {
        List<Point> ring = new ArrayList<>(rectangle(30.06, -1.941, 30.061, -1.94));
        if (reverse) Collections.reverse(ring);
        double expected = Math.floor(rectangleArea(30.06, -1.941, 30.061, -1.94) / 100) / 100;
        double hectares = Math.floor(TurfMeasurement.area(Polygon.fromLngLats(List.of(ring))) / 100) / 100;
        assertEquals(expected, hectares, 0.000001);
        assertNotEquals(expected, Math.floor(TurfMeasurement.area(Polygon.fromLngLats(List.of(ring))) / 10) / 100);
    }
}
