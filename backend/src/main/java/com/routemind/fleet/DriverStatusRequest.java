package com.routemind.fleet;

import jakarta.validation.constraints.NotNull;

/** Status-only payload for drivers, mirroring the vehicle status endpoint. */
public record DriverStatusRequest(@NotNull DriverStatus status) {
}
