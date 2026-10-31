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

import {expect, test} from '@playwright/test';
import {ConfigFormat} from '../../src/generated/types.ts';
import {editorLanguage, formatLabel, formatOfName} from '../../src/pages/config/formats.ts';

test('a name suggests the format the server gives an imported file', () => {
    expect(formatOfName('application.yaml')).toBe(ConfigFormat.CONFIG_FORMAT_YAML);
    expect(formatOfName('a/b/APP.YML')).toBe(ConfigFormat.CONFIG_FORMAT_YAML);
    expect(formatOfName('app.properties')).toBe(ConfigFormat.CONFIG_FORMAT_PROPERTIES);
    expect(formatOfName('app.json')).toBe(ConfigFormat.CONFIG_FORMAT_JSON);
    expect(formatOfName('notes.txt')).toBe(ConfigFormat.CONFIG_FORMAT_TEXT);
    expect(formatOfName('no-extension')).toBe(ConfigFormat.CONFIG_FORMAT_TEXT);
});

test('every format has a label and an editor language', () => {
    expect(formatLabel(ConfigFormat.CONFIG_FORMAT_PROPERTIES)).toBe('Properties');
    expect(formatLabel(ConfigFormat.CONFIG_FORMAT_UNSPECIFIED)).toBe('Text');
    expect(editorLanguage(ConfigFormat.CONFIG_FORMAT_PROPERTIES)).toBe('ini');
    expect(editorLanguage(ConfigFormat.CONFIG_FORMAT_TEXT)).toBe('plaintext');
});
