import { OrderStatus } from './order';

/** A dispatch plan, backed by the backend (`POST /api/v1/dispatch/...`). */

export type DispatchPlanStatus = 'DRAFT' | 'APPROVED' | 'REJECTED' | 'COMPLETED' | 'CANCELLED';

export type DispatchStopStatus = 'PENDING' | 'PICKED_UP' | 'DELIVERED' | 'FAILED' | 'CANCELLED';

export type DeliveryStatus = 'PICKED_UP' | 'DELIVERED' | 'FAILED';

export interface DispatchStop {
  stopId: string;
  orderId: string;
  customerName: string | null;
  deliveryAddress: string | null;
  stopIndex: number;
  status: DispatchStopStatus;
  orderStatus: OrderStatus | null;
  needsReassignment: boolean;
}

export interface DispatchRoute {
  vehicleId: string;
  plateNumber: string | null;
  driverId: string | null;
  driverName: string | null;
  totalDistanceMetres: number;
  totalDurationSeconds: number;
  totalWeightKg: number;
  stops: DispatchStop[];
}

export interface DispatchPlan {
  id: string;
  status: DispatchPlanStatus;
  matrixSource: 'OSRM' | 'STRAIGHT_LINE';
  totalDistanceMetres: number;
  totalDurationSeconds: number;
  totalWeightKg: number;
  unassignedOrderIds: string[];
  routes: DispatchRoute[];
  decidedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export const DISPATCH_PLAN_STATUSES: DispatchPlanStatus[] = [
  'DRAFT',
  'APPROVED',
  'REJECTED',
  'COMPLETED',
  'CANCELLED',
];

/** One stop on a driver's current route, with the plan it belongs to. */
export interface DriverStop {
  planId: string;
  stopId: string;
  vehicleId: string;
  plateNumber: string | null;
  orderId: string;
  customerName: string;
  deliveryAddress: string;
  latitude: number;
  longitude: number;
  stopIndex: number;
  status: DispatchStopStatus;
  orderStatus: OrderStatus;
}
