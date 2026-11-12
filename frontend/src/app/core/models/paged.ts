/** Shape of every list endpoint, matching the backend's PagedResponse. */
export interface Paged<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
