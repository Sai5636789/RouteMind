import { OptimizationResult } from './optimization';
import { OrderStatus } from './order';
import { VehicleStatus } from './vehicle';

/** A vehicle breakdown, backed by the backend (`POST /api/v1/incidents`). */

export type IncidentStatus = 'OPEN' | 'RESOLVED';

/** One order a breakdown affected: the status it had when it lost its vehicle, and the vehicle it was rerouted to, if any. */
export interface AffectedOrder {
  orderId: string;
  customerName: string | null;
  previousStatus: OrderStatus;
  reassignedVehicleId: string | null;
}

export interface Incident {
  id: string;
  vehicleId: string;
  vehiclePlateNumber: string;
  vehicleStatus: VehicleStatus;
  reason: string | null;
  status: IncidentStatus;
  reportedAt: string;
  resolvedAt: string | null;
  affectedOrders: AffectedOrder[];
  createdAt: string;
  updatedAt: string;
}

/** What the dispatcher sends to report a breakdown. The reason is optional. */
export interface BreakdownRequest {
  vehicleId: string;
  reason?: string | null;
}

/** One order a reroute placed on a new vehicle. */
export interface RerouteAssignment {
  orderId: string;
  customerName: string;
  vehicleId: string;
  plateNumber: string;
  driverId: string;
}

/**
 * What a reroute did: the optimizer's plan in full, what was actually
 * saved, and the orders that could not be placed and are still waiting.
 */
export interface RerouteResult {
  incidentId: string;
  vehicleId: string;
  vehiclePlateNumber: string;
  matrixSource: 'OSRM' | 'STRAIGHT_LINE';
  optimization: OptimizationResult;
  assignments: RerouteAssignment[];
  stillWaitingOrderIds: string[];
}
