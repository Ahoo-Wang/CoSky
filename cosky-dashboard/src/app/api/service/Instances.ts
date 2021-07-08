/*
 *
 *  * Copyright [2021-2021] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *      http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

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
