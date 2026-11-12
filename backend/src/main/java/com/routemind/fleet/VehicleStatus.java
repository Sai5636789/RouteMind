package com.routemind.fleet;

/**
 * A vehicle is dispatchable only while AVAILABLE. ON_ROUTE is set by the
 * dispatch workflow when a route is assigned; MAINTENANCE and OUT_OF_SERVICE
 * take it out of the pool until a dispatcher returns it to service.
 */
public enum VehicleStatus {
    AVAILABLE,
    ON_ROUTE,
    MAINTENANCE,
    OUT_OF_SERVICE
}
