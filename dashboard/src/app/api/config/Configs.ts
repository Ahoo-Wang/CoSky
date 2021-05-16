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

  static extAsLang(configExt: string): string {
    switch (configExt) {
      case 'yml': {
        return 'yaml';
      }
      default:
        return configExt;
    }
  }
}

export type ConfigExt = 'text' | 'json' | 'xml' | 'yaml' | 'properties';

export class ConfigName {
  name: string;
  ext: string;

  constructor(name: string, ext: string) {
    this.name = name;
    this.ext = ext;
  }

  static of(configId: string): ConfigName {
    const idx = configId.lastIndexOf('.');
    if (idx < 0) {
      return new ConfigName(configId, '');
    }
    const name = configId.substring(0, idx);
    const ext = configId.substring(idx + 1);

    return new ConfigName(name, ext);
  }

  toId(): string {
    if (this.ext.length === 0) {
      return this.name;
    }
    return `${this.name}.${this.ext}`;
  }
}

