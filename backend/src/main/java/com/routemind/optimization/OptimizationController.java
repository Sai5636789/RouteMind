package com.routemind.optimization;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Route-optimization endpoints. Optimization is a read-only computation —
 * it assigns nothing and writes nothing — so the explicit-input endpoints
 * take POST with a JSON body, and the {@code -current} endpoints optimize
 * the live dispatch state (first depot, AVAILABLE vehicles, CREATED
 * orders). A run the solver cannot complete answers 503; bad input
 * answers 400. Saving an optimized plan as assignments is the dispatch
 * workflow's job, not this controller's.
 */
@RestController
@RequestMapping("/api/v1/optimization")
public class OptimizationController {

    private final OptimizationService optimization;

    public OptimizationController(OptimizationService optimization) {
        this.optimization = optimization;
    }

    public record VehicleRequest(
            @NotNull UUID id,
            @NotBlank String plateNumber,
            @NotNull @Positive BigDecimal capacityKg) {
    }

    public record OrderRequest(
            @NotNull UUID id,
            @NotNull @DecimalMin("-90") @DecimalMax("90") Double latitude,
            @NotNull @DecimalMin("-180") @DecimalMax("180") Double longitude,
            @NotNull @Positive BigDecimal weightKg) {
    }

    public record OptimizeRequest(
            @NotNull @DecimalMin("-90") @DecimalMax("90") Double depotLatitude,
            @NotNull @DecimalMin("-180") @DecimalMax("180") Double depotLongitude,
            @NotNull @Size(min = 1, max = 10) List<@Valid VehicleRequest> vehicles,
            @NotNull @Size(min = 1, max = 50) List<@Valid OrderRequest> orders) {
    }

    @PostMapping("/optimize")
    @ResponseStatus(HttpStatus.OK)
    public OptimizationResult optimize(@Valid @RequestBody OptimizeRequest request) {
        return optimization.optimize(toInput(request));
    }

    @PostMapping("/optimize-current")
    @ResponseStatus(HttpStatus.OK)
    public OptimizationResult optimizeCurrent() {
        return optimization.optimizeCurrent();
    }

    @PostMapping("/compare")
    @ResponseStatus(HttpStatus.OK)
    public OptimizationService.ComparisonResult compare(@Valid @RequestBody OptimizeRequest request) {
        return optimization.compare(toInput(request));
    }

    @PostMapping("/compare-current")
    @ResponseStatus(HttpStatus.OK)
    public OptimizationService.ComparisonResult compareCurrent() {
        return optimization.compareCurrent();
    }

    private static OptimizationInput toInput(OptimizeRequest request) {
        return new OptimizationInput(
                request.depotLatitude(),
                request.depotLongitude(),
                request.vehicles().stream()
                        .map(vehicle -> new OptimizationInput.VehicleInput(
                                vehicle.id(), vehicle.plateNumber(), vehicle.capacityKg()))
                        .toList(),
                request.orders().stream()
                        .map(order -> new OptimizationInput.OrderInput(
                                order.id(), order.latitude(), order.longitude(), order.weightKg()))
                        .toList());
    }
}
