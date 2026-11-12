export interface ApiHealth {
  status: string;
  service: string;
  timestamp: string;
}

export interface DependencyHealth {
  status: string;
  components: Record<string, { status: string }>;
}
