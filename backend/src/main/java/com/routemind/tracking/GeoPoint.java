package com.routemind.tracking;

/**
 * A point on the Earth's surface. Latitude is -90..90, longitude -180..180;
 * anything outside that is a programming error, so the constructor rejects
 * it instead of silently producing nonsense bearings and distances.
 */
public record GeoPoint(double latitude, double longitude) {

    public GeoPoint {
        if (latitude < -90 || latitude > 90) {
            throw new IllegalArgumentException("Latitude must be between -90 and 90, was " + latitude);
        }
        if (longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("Longitude must be between -180 and 180, was " + longitude);
        }
    }
}
