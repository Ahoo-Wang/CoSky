import {StatDto} from './StatDto';

export class Stats {

  static of(): StatDto {
    return {
      namespaces: 0,
      services: 0,
      instances: 0,
      configs: 0
    };
  }
}
