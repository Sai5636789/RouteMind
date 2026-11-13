export interface Depot {
  id: string;
  name: string;
  address: string | null;
  latitude: number;
  longitude: number;
  createdAt: string;
  updatedAt: string;
}
