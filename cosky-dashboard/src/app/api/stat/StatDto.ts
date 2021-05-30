export interface StatDto {
  namespaces: number;
  services: Services;
  instances: number;
  configs: number;
}

export interface Services {
  total: number;
  health: number;
}
