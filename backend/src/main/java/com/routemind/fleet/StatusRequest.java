package com.routemind.fleet;

import jakarta.validation.constraints.NotNull;

/** Small payload for the status-only endpoints, so callers do not have to send a full replacement. */
public record StatusRequest(@NotNull VehicleStatus status) {
}
