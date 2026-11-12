export type VehicleStatus = 'AVAILABLE' | 'ON_ROUTE' | 'MAINTENANCE' | 'OUT_OF_SERVICE';

export interface Vehicle {
  id: string;
  plateNumber: string;
  model: string;
  capacityKg: number;
  status: VehicleStatus;
  depotId: string | null;
  createdAt: string;
  updatedAt: string;
}

/** Create payload: status is set by the backend, never by the form. */
export interface VehicleCreate {
  plateNumber: string;
  model: string;
  capacityKg: number;
  depotId?: string | null;
}

export interface VehicleUpdate extends VehicleCreate {
  status: VehicleStatus;
}

export const VEHICLE_STATUSES: VehicleStatus[] = [
  'AVAILABLE',
  'ON_ROUTE',
  'MAINTENANCE',
  'OUT_OF_SERVICE',
];
