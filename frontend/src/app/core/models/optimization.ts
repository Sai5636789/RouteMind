/** Route optimization, backed by the backend (`POST /api/v1/optimization/...`). */

/** One vehicle's optimized route, in visit order. */
export interface OptimizedRoute {
  vehicleId: string;
  plateNumber: string;
  orderIds: string[];
  totalDistanceMetres: number;
  totalDurationSeconds: number;
  totalWeightKg: number;
}

/**
 * One optimization run. `matrixSource` is `OSRM` when the road router
 * supplied the travel times, or `STRAIGHT_LINE` when the router was
 * unreachable and the backend fell back to straight-line distances.
 */
export interface OptimizationResult {
  matrixSource: 'OSRM' | 'STRAIGHT_LINE';
  solverStatus: string;
  solveTimeMillis: number;
  routes: OptimizedRoute[];
  unassignedOrderIds: string[];
  totalDistanceMetres: number;
  totalDurationSeconds: number;
  totalWeightKg: number;
}

/** Optimized plan next to the creation-order plan, and what was saved. */
export interface OptimizationComparison {
  optimized: OptimizationResult;
  baseline: OptimizationResult;
  savedDistanceMetres: number;
  savedDurationSeconds: number;
}
