import {StatDto} from './StatDto';

export class Stats {

  static of(): StatDto {
    return {
      namespaces: 0,
      services: {
        total: 0,
        health: 0
      },
      instances: 0,
      configs: 0
    };
  }
}
