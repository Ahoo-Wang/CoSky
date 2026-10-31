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

import {useState} from 'react';
import Editor from '@monaco-editor/react';
import {useQuery} from '@ahoo-wang/fetcher-react';
import dayjs from 'dayjs';
import {configs, ConfigFormat} from '@/services/resources';
import type {ConfigResource} from '@/services/resources';
import {CONFIG_FORMAT_OPTIONS, editorLanguage, formatOfName} from '@/pages/config/formats';
import {Button} from '@/components/ui/button';
import {DefinitionList} from '@/components/ui/definition-list';
import {Input} from '@/components/ui/input';
import {Label} from '@/components/ui/label';
import {OptionsSelect} from '@/components/ui/options-select';
import {Separator} from '@/components/ui/separator';
import {Skeleton} from '@/components/ui/skeleton';
import {runAction} from '@/lib/actions';

interface ConfigEditorProps {
    namespace: string;
    /** The Config to edit; a new one otherwise. */
    name?: string;
    onSuccess: () => void;
    onCancel: () => void;
}

export function ConfigEditor({namespace, name, onSuccess, onCancel}: ConfigEditorProps) {
    const [newName, setNewName] = useState('');
    const [format, setFormat] = useState<ConfigFormat>(ConfigFormat.CONFIG_FORMAT_YAML);
    const [content, setContent] = useState('');
    const [saving, setSaving] = useState(false);
    const {result: config, error, execute: reload} = useQuery<string | undefined, ConfigResource | null>({
        query: name,
        execute: async (query, _, abortController) => query ? configs.get(namespace, query, abortController) : null,
        onSuccess: loaded => {
            if (loaded) {
                setFormat(loaded.format);
                setContent(loaded.content);
            }
        },
    });

    const handleSubmit = async () => {
        setSaving(true);
        const saved = await runAction(
            // The revision it was read at: someone else's change since then must not be overwritten.
            () => configs.put(namespace, name ?? newName.trim(), {format, content}, config?.revision, config?.labels),
            {success: 'Config saved', failure: 'Failed to save config'},
        );
        setSaving(false);
        if (saved) {
            onSuccess();
        }
    };

    if (name && error) {
        return (
            <div role="alert" className="space-y-4 rounded-xl border border-destructive/30 bg-destructive/5 p-6 text-sm">
                <p>Failed to load this configuration. Nothing has been changed.</p>
                <Button variant="outline" onClick={() => reload()}>Retry</Button>
            </div>
        );
    }
    if (name && !config) {
        return <Skeleton className="h-96 w-full"/>;
    }
    return (
        <form className="space-y-5" onSubmit={event => {
            event.preventDefault();
            void handleSubmit();
        }}>
            {!name && (
                <div className="space-y-2">
                    <Label htmlFor="config-name">Name</Label>
                    <Input id="config-name" value={newName} required placeholder="application.yaml" onChange={event => {
                        setNewName(event.target.value);
                        setFormat(formatOfName(event.target.value));
                    }}/>
                </div>
            )}
            <div className="space-y-2">
                <Label>Format</Label>
                <OptionsSelect
                    ariaLabel="Format"
                    options={CONFIG_FORMAT_OPTIONS}
                    value={format}
                    onChange={value => setFormat(value as ConfigFormat)}
                />
            </div>
            {config && (
                <DefinitionList items={[
                    {label: 'Name', value: config.name},
                    {label: 'SHA-256', value: config.sha256},
                    {label: 'Updated', value: dayjs(config.updatedAt).format('YYYY-MM-DD HH:mm:ss')},
                    {label: 'Updated by', value: config.updatedBy},
                ]}/>
            )}
            <Separator/>
            <Editor
                height="60vh"
                theme="vs-dark"
                language={editorLanguage(format)}
                value={content}
                onChange={value => setContent(value ?? '')}
                options={{ariaLabel: 'Config content editor', minimap: {enabled: false}}}
            />
            <Separator/>
            <div className="flex gap-2">
                <Button type="submit" loading={saving}>Submit</Button>
                <Button type="button" variant="outline" onClick={onCancel}>Cancel</Button>
            </div>
        </form>
    );
}
