package com.routemind.orders;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** Write model for an order. Orders are always created unassigned, in CREATED. */
public record OrderRequest(
        @NotBlank @Size(max = 100) String customerName,
        @NotBlank String deliveryAddress,
        @NotNull @DecimalMin(value = "-90.0", message = "must be between -90 and 90")
        @DecimalMax(value = "90.0", message = "must be between -90 and 90") Double latitude,
        @NotNull @DecimalMin(value = "-180.0", message = "must be between -180 and 180")
        @DecimalMax(value = "180.0", message = "must be between -180 and 180") Double longitude,
        @NotNull @DecimalMin(value = "0.01", message = "must be greater than 0") BigDecimal weightKg) {
}
