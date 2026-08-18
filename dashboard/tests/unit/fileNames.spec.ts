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
import {getFileNameWithExt, getFullFileName} from '../../src/pages/config/fileNames.ts';

test('splits the final extension from configuration names', () => {
    expect(getFileNameWithExt('application.prod.yaml')).toEqual({name: 'application.prod', ext: 'yaml'});
    expect(getFileNameWithExt('README')).toEqual({name: 'README', ext: ''});
    expect(getFileNameWithExt('.env')).toEqual({name: '', ext: 'env'});
    expect(getFileNameWithExt('logging.')).toEqual({name: 'logging', ext: ''});
});

test('joins configuration name and extension', () => {
    expect(getFullFileName('application', 'yaml')).toBe('application.yaml');
});
