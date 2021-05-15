export interface ServiceInstanceDto {
  instanceId: string;
  serviceId: string;
  schema: string;
  ip: string;
  port: number;
  weight: number;
  ephemeral: boolean;
  ttlAt?: number;
  metadata: object;

}
