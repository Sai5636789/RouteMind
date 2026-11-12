package com.routemind.fleet;

/** Only ACTIVE drivers can be assigned to an order. */
public enum DriverStatus {
    ACTIVE,
    ON_LEAVE,
    INACTIVE
}
