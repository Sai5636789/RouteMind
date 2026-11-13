/** A point on the Earth's surface, as the routing API takes and returns it. */
export interface GeoPoint {
  latitude: number;
  longitude: number;
}

/**
 * One road route between waypoints, as returned by the routing API.
 * The geometry follows real roads in waypoint order; distance and
 * duration are the router's own estimates for the driving profile.
 */
export interface RoadRoute {
  geometry: GeoPoint[];
  distanceMetres: number;
  durationSeconds: number;
}

/** Travel-time / distance matrix. A null entry means that pair is unroutable. */
export interface DistanceMatrix {
  points: GeoPoint[];
  durationsSeconds: (number | null)[][];
  distancesMetres: (number | null)[][];
}

/** Whether the road router can be reached, and where it lives. */
export interface RoutingStatus {
  available: boolean;
  baseUrl: string;
  profile: string;
}
