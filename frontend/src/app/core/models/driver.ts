export type DriverStatus = 'ACTIVE' | 'ON_LEAVE' | 'INACTIVE';

export interface Driver {
  id: string;
  firstName: string;
  lastName: string;
  licenseNumber: string;
  phone: string | null;
  status: DriverStatus;
  createdAt: string;
  updatedAt: string;
}

/** Create payload: status defaults to ACTIVE on the backend. */
export interface DriverCreate {
  firstName: string;
  lastName: string;
  licenseNumber: string;
  phone?: string | null;
}

export interface DriverUpdate extends DriverCreate {
  status: DriverStatus;
}

export const DRIVER_STATUSES: DriverStatus[] = ['ACTIVE', 'ON_LEAVE', 'INACTIVE'];
