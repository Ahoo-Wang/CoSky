import {ServiceInstanceDto} from './ServiceInstanceDto';

export class Instances {
  static of(): ServiceInstanceDto {
    return {
      instanceId: '',
      serviceId: '',
      schema: '',
      ip: '',
      port: 80,
      weight: 1,
      ephemeral: true,
      ttlAt: -1,
      metadata: new Map<string, string>()
    };
  }
}
