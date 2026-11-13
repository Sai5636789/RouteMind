package com.routemind.optimization;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Route-optimization settings. The solver is pure computation over a
 * travel-time / distance matrix, so it works with the OSRM matrix when
 * the router is up and with a straight-line matrix otherwise — the
 * matrix source is always reported with the result, never hidden.
 */
@ConfigurationProperties("routemind.optimization")
public record OptimizationProperties(
        boolean enabled,
        Duration solverTimeLimit,
        int maxOrders,
        int maxVehicles,
        /** Average speed assumed for straight-line duration estimates. */
        double straightLineSpeedKmh) {

    public OptimizationProperties {
        if (solverTimeLimit == null || solverTimeLimit.isZero() || solverTimeLimit.isNegative()) {
            throw new IllegalArgumentException("routemind.optimization.solver-time-limit must be positive");
        }
        if (maxOrders < 1) {
            throw new IllegalArgumentException("routemind.optimization.max-orders must be at least 1");
        }
        if (maxVehicles < 1) {
            throw new IllegalArgumentException("routemind.optimization.max-vehicles must be at least 1");
        }
        if (straightLineSpeedKmh <= 0) {
            throw new IllegalArgumentException("routemind.optimization.straight-line-speed-kmh must be positive");
        }
    }
}
