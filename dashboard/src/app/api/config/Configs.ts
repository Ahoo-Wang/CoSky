import {ConfigDto} from './ConfigDto';
import {ConfigHistoryDto} from './ConfigHistoryDto';

export class Configs {
  static of(): ConfigDto {
    return {
      configId: '',
      data: '',
      hash: '',
      version: 0,
      createTime: 0
    };
  }

  static ofHistory(): ConfigHistoryDto {
    return {
      configId: '',
      data: '',
      hash: '',
      version: 0,
      createTime: 0,
      op: '',
      opTime: 0
    };
  }
}
