package com.routemind.tracking;

import com.routemind.common.ResourceNotFoundException;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import java.util.List;
import java.util.UUID;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only latest vehicle positions, served from Redis. A vehicle with
 * no stored position has not been tracked yet and answers 404 for its
 * own endpoint — the absence is the answer, not an invented point.
 * These are GET endpoints, so they are temporarily public with the other
 * read endpoints until JWT login lands (see SecurityConfig).
 */
@RestController
@RequestMapping("/api/v1/tracking")
@Validated
public class TrackingController {

    private final VehiclePositionStore positions;

    public TrackingController(VehiclePositionStore positions) {
        this.positions = positions;
    }

    @GetMapping("/vehicles")
    List<VehiclePositionResponse> latest() {
        return positions.findAll().stream().map(VehiclePositionResponse::from).toList();
    }

    @GetMapping("/vehicles/{vehicleId}")
    VehiclePositionResponse latestForVehicle(@PathVariable UUID vehicleId) {
        return positions.findByVehicleId(vehicleId)
                .map(VehiclePositionResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle position", vehicleId));
    }

    @GetMapping("/vehicles/nearby")
    List<VehiclePositionResponse> nearby(
            @RequestParam @DecimalMin("-90") @DecimalMax("90") double latitude,
            @RequestParam @DecimalMin("-180") @DecimalMax("180") double longitude,
            @RequestParam(defaultValue = "5") @DecimalMin("0.1") @DecimalMax("100") double radiusKm) {
        return positions.findNearby(latitude, longitude, radiusKm).stream()
                .map(VehiclePositionResponse::from).toList();
    }
}
