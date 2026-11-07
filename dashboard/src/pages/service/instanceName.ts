/*
 * Copyright [2021-present] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *      http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

const ILLEGAL_NAME_CHARACTER = /[^A-Za-z0-9._-]/g;
const MAX_NAME_LENGTH = 253;
const FNV_OFFSET_BASIS = 0xcbf29ce484222325n;
const FNV_PRIME = 0x100000001b3n;
const DIGEST_LENGTH = 16;

/**
 * 64-bit FNV-1a, as the client computes it. The value holds legal name characters only, all ASCII, so a character is
 * a byte.
 */
const digestOf = (value: string) => {
    let hash = FNV_OFFSET_BASIS;
    for (const character of value) {
        hash = BigInt.asUintN(64, (hash ^ BigInt(character.charCodeAt(0))) * FNV_PRIME);
    }
    return hash.toString(16).padStart(DIGEST_LENGTH, '0');
};

/**
 * As the client names an instance (`NamingRules.defaultInstanceName`), so that one endpoint has one name. A name that
 * is too long keeps its port, and a digest of the whole name before it, so that two endpoints of a long host do not
 * share one.
 */
export const defaultInstanceName = (service: string, host: string, port: string | number) => {
    const name = `${service}-${host}-${port}`.replace(ILLEGAL_NAME_CHARACTER, '-');
    if (name.length <= MAX_NAME_LENGTH) {
        return name;
    }
    const tail = `-${digestOf(name)}-${port}`.replace(ILLEGAL_NAME_CHARACTER, '-');
    return name.slice(0, MAX_NAME_LENGTH - tail.length) + tail;
};
