package com.routemind.fleet;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

/** Full replacement of a vehicle. Status is part of the update, unlike create. */
public record VehicleUpdateRequest(
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9-]{2,12}$",
                message = "must be 2-12 letters, digits or hyphens") String plateNumber,
        @NotBlank @Size(max = 100) String model,
        @NotNull @DecimalMin(value = "0.01", message = "must be greater than 0") BigDecimal capacityKg,
        @NotNull VehicleStatus status,
        UUID depotId) {
}
