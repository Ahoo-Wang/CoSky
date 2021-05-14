import {ConfigDto} from './ConfigDto';

export interface ConfigHistoryDto extends ConfigDto {
  op: string;
  opTime: number;
}
