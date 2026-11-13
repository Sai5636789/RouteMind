export type OrderStatus = 'CREATED' | 'ASSIGNED' | 'IN_TRANSIT' | 'DELIVERED' | 'CANCELLED';

export interface Order {
  id: string;
  customerName: string;
  deliveryAddress: string;
  latitude: number;
  longitude: number;
  weightKg: number;
  status: OrderStatus;
  assignedVehicleId: string | null;
  assignedDriverId: string | null;
  /** True while the order waits for a new vehicle after a breakdown took its previous one. */
  needsReassignment: boolean;
  createdAt: string;
  updatedAt: string;
}

/** Create payload: orders are always created unassigned, in CREATED. */
export interface OrderCreate {
  customerName: string;
  deliveryAddress: string;
  latitude: number;
  longitude: number;
  weightKg: number;
}

export const ORDER_STATUSES: OrderStatus[] = [
  'CREATED',
  'ASSIGNED',
  'IN_TRANSIT',
  'DELIVERED',
  'CANCELLED',
];
