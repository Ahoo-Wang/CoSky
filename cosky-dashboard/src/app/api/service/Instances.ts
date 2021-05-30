import {ServiceInstanceDto} from './ServiceInstanceDto';
import {KeyValuePair} from '../../model/KeyValuePair';


export class Instances {
  static of(): ServiceInstanceDto {
    return {
      instanceId: '',
      serviceId: '',
      schema: '',
      host: '',
      port: 80,
      weight: 1,
      ephemeral: true,
      ttlAt: -1,
      metadata: new Object()
    };
  }

  static metadataAsKeyValueArray(metadata: Map<string, string>): KeyValuePair<string, string> [] {
    const keyValueArray: KeyValuePair<string, string>[] = [];
    metadata.forEach(((value, key) => {
      keyValueArray.push({key, value});
    }));
    return keyValueArray;
  }

  static keyValueArrayAsMetadata(keyValueArray: KeyValuePair<string, string> []): Map<string, string> {
    const metadata = new Map<string, string>();
    keyValueArray.forEach(keyValuePair => {
      metadata.set(keyValuePair.key, keyValuePair.value);
    });
    return metadata;
  }

}
