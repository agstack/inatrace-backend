package com.abelium.inatrace.components.company;

import java.util.List;

/**
 * Shared validation for geographic coordinates coming from spreadsheets and GeoJSON uploads.
 *
 * <p>GeoJSON uses {@code [longitude, latitude]}, while the spreadsheet parser stores points as
 * {@code [latitude, longitude]}. Callers pass latitude first; this class deliberately does not
 * guess or swap axes.</p>
 */
final class GeoCoordinateValidator {

	private GeoCoordinateValidator() {
	}

	static void validate(double latitude, double longitude) {
		if (!Double.isFinite(latitude) || !Double.isFinite(longitude)) {
			throw new IllegalArgumentException("Coordinates must be finite numbers");
		}
		if (latitude < -90 || latitude > 90) {
			throw new IllegalArgumentException("Latitude out of range: " + latitude);
		}
		if (longitude < -180 || longitude > 180) {
			throw new IllegalArgumentException("Longitude out of range: " + longitude);
		}
	}

	/** Validates {@code [latitude, longitude]} pairs and returns the same list for chaining. */
	static List<double[]> validateAll(List<double[]> points) {
		if (points == null) {
			throw new IllegalArgumentException("No coordinates found");
		}
		for (double[] point : points) {
			if (point == null || point.length < 2) {
				throw new IllegalArgumentException("Coordinates must contain latitude and longitude");
			}
			validate(point[0], point[1]);
		}
		return points;
	}
}
