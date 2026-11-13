package com.routemind.dispatch;

import java.util.Map;
import java.util.UUID;

/**
 * Create a dispatch plan from the current optimization. {@code
 * driverAssignments} maps a vehicle id to the driver for that vehicle's
 * route; a route may be left without a driver at creation, but approval
 * requires every route to have one.
 */
public record CreatePlanRequest(Map<UUID, UUID> driverAssignments) {
}
