package com.routemind.orders;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Assignment payload: an order takes both a vehicle and a driver, never one without the other. */
public record AssignOrderRequest(@NotNull UUID vehicleId, @NotNull UUID driverId) {
}
