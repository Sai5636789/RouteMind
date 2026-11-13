package com.routemind.tracking;

import java.util.List;
import java.util.UUID;

/**
 * A vehicle's progress along a route: an ordered list of waypoints
 * (depot first, then delivery stops in creation order). The simulator
 * advances it by a travelled distance each tick; the vehicle moves
 * along the current segment, crosses into the next one when the segment
 * ends, and stops at the final waypoint. Pure logic — no database, no
 * Redis — so it is unit tested without any infrastructure.
 */
public final class RouteProgress {

    /** One waypoint: a point, and the order it is a stop for (null for the depot). */
    public record Waypoint(GeoPoint point, UUID orderId) {
    }

    /** The result of one advance: where the vehicle is, where it is heading, and whether it arrived. */
    public record Step(GeoPoint position, Double headingDegrees, UUID targetOrderId, boolean finished) {
    }

    private final List<Waypoint> waypoints;
    private final List<Double> segmentLengths;
    private int segmentIndex;
    private double distanceIntoSegment;
    private boolean finished;

    public RouteProgress(List<Waypoint> waypoints) {
        if (waypoints == null || waypoints.size() < 2) {
            throw new IllegalArgumentException("A route needs at least two waypoints");
        }
        this.waypoints = List.copyOf(waypoints);
        this.segmentLengths = new java.util.ArrayList<>(waypoints.size() - 1);
        for (int i = 0; i < waypoints.size() - 1; i++) {
            segmentLengths.add(GeoMath.distanceMetres(waypoints.get(i).point(), waypoints.get(i + 1).point()));
        }
    }

    public List<Waypoint> waypoints() {
        return waypoints;
    }

    public boolean isFinished() {
        return finished;
    }

    /** The waypoint the vehicle is currently heading to, or null once it has arrived. */
    public UUID targetOrderId() {
        return finished ? null : waypoints.get(segmentIndex + 1).orderId();
    }

    /**
     * Moves the vehicle forward by the given distance in metres and
     * returns its new position. A zero-length segment (two waypoints at
     * the same coordinates) is skipped, never divided by.
     */
    public Step advance(double distanceMetres) {
        if (distanceMetres < 0) {
            throw new IllegalArgumentException("Distance must not be negative, was " + distanceMetres);
        }
        if (finished) {
            return currentStep();
        }
        double remaining = distanceMetres;
        while (remaining > 0 && !finished) {
            double segmentRemaining = segmentLengths.get(segmentIndex) - distanceIntoSegment;
            if (segmentRemaining <= 0) {
                // Zero-length segment: step straight into the next one.
                advanceSegment();
                continue;
            }
            if (remaining < segmentRemaining) {
                distanceIntoSegment += remaining;
                remaining = 0;
            } else {
                remaining -= segmentRemaining;
                advanceSegment();
            }
        }
        return currentStep();
    }

    private void advanceSegment() {
        distanceIntoSegment = 0;
        if (segmentIndex >= segmentLengths.size() - 1) {
            finished = true;
        } else {
            segmentIndex++;
        }
    }

    private Step currentStep() {
        GeoPoint last = waypoints.get(waypoints.size() - 1).point();
        if (finished) {
            return new Step(last, lastHeading(), null, true);
        }
        GeoPoint from = waypoints.get(segmentIndex).point();
        GeoPoint to = waypoints.get(segmentIndex + 1).point();
        double heading = GeoMath.bearingDegrees(from, to);
        GeoPoint position = GeoMath.destination(from, heading, distanceIntoSegment);
        return new Step(position, heading, waypoints.get(segmentIndex + 1).orderId(), false);
    }

    private Double lastHeading() {
        if (segmentLengths.isEmpty()) {
            return null;
        }
        int lastSegment = segmentLengths.size() - 1;
        GeoPoint from = waypoints.get(lastSegment).point();
        GeoPoint to = waypoints.get(lastSegment + 1).point();
        return segmentLengths.get(lastSegment) == 0 ? null : GeoMath.bearingDegrees(from, to);
    }
}
