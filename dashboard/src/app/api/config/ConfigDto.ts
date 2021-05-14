import {ConfigVersionDto} from './ConfigVersionDto';

export interface ConfigDto extends ConfigVersionDto {
  data: string;
  hash: string;
  createTime: number;
}
