package com.routemind.tracking;

/**
 * Great-circle geometry on a spherical Earth (mean radius 6,371 km).
 * The GPS simulator moves vehicles along straight segments between
 * waypoints; over the few kilometres of a delivery route the spherical
 * formulae and a flat approximation agree to centimetres, but the
 * spherical versions cost nothing and stay correct everywhere.
 */
public final class GeoMath {

    /** Mean Earth radius in metres. */
    public static final double EARTH_RADIUS_METRES = 6_371_000;

    private GeoMath() {
    }

    /** Distance between two points in metres (haversine). */
    public static double distanceMetres(GeoPoint from, GeoPoint to) {
        double lat1 = Math.toRadians(from.latitude());
        double lat2 = Math.toRadians(to.latitude());
        double dLat = Math.toRadians(to.latitude() - from.latitude());
        double dLng = Math.toRadians(to.longitude() - from.longitude());
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * EARTH_RADIUS_METRES * Math.asin(Math.min(1, Math.sqrt(a)));
    }

    /** Initial bearing from one point to another, in degrees clockwise from north (0..360). */
    public static double bearingDegrees(GeoPoint from, GeoPoint to) {
        double lat1 = Math.toRadians(from.latitude());
        double lat2 = Math.toRadians(to.latitude());
        double dLng = Math.toRadians(to.longitude() - from.longitude());
        double y = Math.sin(dLng) * Math.cos(lat2);
        double x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLng);
        return (Math.toDegrees(Math.atan2(y, x)) + 360) % 360;
    }

    /** Point reached by travelling a distance from a start point along a bearing. */
    public static GeoPoint destination(GeoPoint from, double bearingDegrees, double distanceMetres) {
        double angular = distanceMetres / EARTH_RADIUS_METRES;
        double bearing = Math.toRadians(bearingDegrees);
        double lat1 = Math.toRadians(from.latitude());
        double lng1 = Math.toRadians(from.longitude());
        double lat2 = Math.asin(Math.sin(lat1) * Math.cos(angular)
                + Math.cos(lat1) * Math.sin(angular) * Math.cos(bearing));
        double lng2 = lng1 + Math.atan2(Math.sin(bearing) * Math.sin(angular) * Math.cos(lat1),
                Math.cos(angular) - Math.sin(lat1) * Math.sin(lat2));
        return new GeoPoint(Math.toDegrees(lat2), Math.toDegrees(lng2));
    }
}
