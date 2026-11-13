package com.routemind.dispatch;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Set (or change) the driver for one vehicle's route on a DRAFT plan. */
public record AssignDriverRequest(
        @NotNull UUID vehicleId,
        @NotNull UUID driverId) {
}
