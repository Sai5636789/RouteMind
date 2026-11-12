package com.routemind.fleet;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Write model for a driver. Status defaults to ACTIVE on create. */
public record DriverRequest(
        @NotBlank @Size(max = 50) String firstName,
        @NotBlank @Size(max = 50) String lastName,
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9-]{3,30}$",
                message = "must be 3-30 letters, digits or hyphens") String licenseNumber,
        @Pattern(regexp = "^\\+?[0-9 ()-]{7,20}$",
                message = "must be a valid phone number") String phone) {
}
