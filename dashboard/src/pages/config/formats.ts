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

import {ConfigFormat} from '@/services/resources';

export const CONFIG_FORMAT_OPTIONS = [
    {label: 'YAML', value: ConfigFormat.CONFIG_FORMAT_YAML},
    {label: 'Properties', value: ConfigFormat.CONFIG_FORMAT_PROPERTIES},
    {label: 'JSON', value: ConfigFormat.CONFIG_FORMAT_JSON},
    {label: 'Text', value: ConfigFormat.CONFIG_FORMAT_TEXT},
];

const EXTENSIONS: Record<string, ConfigFormat> = {
    yaml: ConfigFormat.CONFIG_FORMAT_YAML,
    yml: ConfigFormat.CONFIG_FORMAT_YAML,
    properties: ConfigFormat.CONFIG_FORMAT_PROPERTIES,
    json: ConfigFormat.CONFIG_FORMAT_JSON,
};

const LANGUAGES: Partial<Record<ConfigFormat, string>> = {
    [ConfigFormat.CONFIG_FORMAT_YAML]: 'yaml',
    [ConfigFormat.CONFIG_FORMAT_PROPERTIES]: 'ini',
    [ConfigFormat.CONFIG_FORMAT_JSON]: 'json',
};

/**
 * The format a name suggests, by the rule the server applies to an imported file.
 */
export function formatOfName(name: string): ConfigFormat {
    const extension = name.includes('.') ? name.slice(name.lastIndexOf('.') + 1).toLowerCase() : '';
    return EXTENSIONS[extension] ?? ConfigFormat.CONFIG_FORMAT_TEXT;
}

export function formatLabel(format: ConfigFormat): string {
    return CONFIG_FORMAT_OPTIONS.find(option => option.value === format)?.label ?? 'Text';
}

/**
 * The Monaco language of a format; anything else shows as plain text.
 */
export function editorLanguage(format: ConfigFormat): string {
    return LANGUAGES[format] ?? 'plaintext';
}
