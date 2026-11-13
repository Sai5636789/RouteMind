/**
 * The latest known position of one vehicle, as served by
 * GET /api/v1/tracking/vehicles and broadcast on /topic/vehicles.
 * A vehicle with no entry here has no tracked position — the map must
 * fall back to its depot/stop placement, never invent a point.
 */
export interface VehiclePosition {
  vehicleId: string;
  latitude: number;
  longitude: number;
  speedKmh: number;
  /** Compass heading of travel; null when stationary at a waypoint. */
  headingDegrees: number | null;
  /** The delivery stop the vehicle is heading to, if any. */
  orderId: string | null;
  updatedAt: string;
}
