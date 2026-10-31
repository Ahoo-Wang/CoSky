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
import type {FormEvent} from 'react';
import Editor from '@monaco-editor/react';
import {toast} from 'sonner';
import {instances} from '@/services/resources';
import type {InstanceResource} from '@/services/resources';
import {Button} from '@/components/ui/button';
import {Input} from '@/components/ui/input';
import {Label} from '@/components/ui/label';
import {Separator} from '@/components/ui/separator';
import {runAction} from '@/lib/actions';

const ILLEGAL_NAME_CHARACTER = /[^A-Za-z0-9._-]/g;

interface ServiceInstanceEditorProps {
    namespace: string;
    /** The service it belongs to, when that is already decided. */
    service?: string;
    existing?: InstanceResource;
    onSuccess: () => void;
    onCancel: () => void;
}

function parseMetadata(text: string): Record<string, string> | undefined {
    try {
        const parsed = JSON.parse(text) as unknown;
        const isObject = parsed !== null && typeof parsed === 'object' && !Array.isArray(parsed);
        return isObject && Object.values(parsed).every(value => typeof value === 'string')
            ? parsed as Record<string, string>
            : undefined;
    } catch {
        return undefined;
    }
}

/**
 * An instance added here has no lease, so it stays until someone deletes it. Applications register their own.
 */
export function ServiceInstanceEditor({namespace, service, existing, onSuccess, onCancel}: ServiceInstanceEditorProps) {
    const initialMetadata = JSON.stringify(existing?.metadata ?? {}, null, 2);
    const [metadata, setMetadata] = useState(initialMetadata);
    const [saving, setSaving] = useState(false);

    const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        const values = Object.fromEntries(new FormData(event.currentTarget)) as Record<string, string>;
        const parsedMetadata = parseMetadata(metadata);
        if (!parsedMetadata) {
            toast.error('Metadata must be a JSON object of strings.');
            return;
        }
        const name = existing?.name ?? (values.name.trim()
            || `${values.host}-${values.port}`.replace(ILLEGAL_NAME_CHARACTER, '-'));
        setSaving(true);
        const saved = await runAction(
            () => instances.put(namespace, name, {
                service: service ?? values.service,
                scheme: values.scheme,
                host: values.host,
                port: Number(values.port),
                weight: Number(values.weight),
                metadata: parsedMetadata,
            }, existing),
            {success: 'Instance saved', failure: 'Failed to save instance'},
        );
        setSaving(false);
        if (saved) {
            onSuccess();
        }
    };

    return (
        <form className="space-y-5" onSubmit={handleSubmit}>
            <div className="grid gap-4 sm:grid-cols-2">
                {!service && (
                    <div className="space-y-2 sm:col-span-2">
                        <Label htmlFor="instance-service">Service</Label>
                        <Input id="instance-service" name="service" placeholder="order" required/>
                    </div>
                )}
                {!existing && (
                    <div className="space-y-2 sm:col-span-2">
                        <Label htmlFor="instance-name">Name</Label>
                        <Input id="instance-name" name="name" placeholder="Defaults to host and port"/>
                    </div>
                )}
                <div className="space-y-2">
                    <Label htmlFor="instance-scheme">Scheme</Label>
                    <Input id="instance-scheme" name="scheme" required defaultValue={existing?.scheme ?? 'http'}/>
                </div>
                <div className="space-y-2">
                    <Label htmlFor="instance-host">Host</Label>
                    <Input id="instance-host" name="host" required placeholder="192.168.0.10 or api.internal"
                           defaultValue={existing?.host}/>
                </div>
                <div className="space-y-2">
                    <Label htmlFor="instance-port">Port</Label>
                    <Input id="instance-port" name="port" type="number" min={1} max={65535} required placeholder="8080"
                           defaultValue={existing?.port}/>
                </div>
                <div className="space-y-2">
                    <Label htmlFor="instance-weight">Weight</Label>
                    <Input id="instance-weight" name="weight" type="number" min={0} required
                           defaultValue={existing?.weight ?? 1}/>
                </div>
            </div>
            <Separator/>
            <div className="h-[30vh] min-h-52 sm:h-[38vh] sm:max-h-96">
                <Editor
                    height="100%"
                    theme="vs-dark"
                    defaultLanguage="json"
                    defaultValue={initialMetadata}
                    onChange={value => setMetadata(value || '{}')}
                    options={{ariaLabel: 'Instance metadata editor', minimap: {enabled: false}}}
                />
            </div>
            <Separator/>
            <div className="flex gap-2">
                <Button type="submit" loading={saving}>Submit</Button>
                <Button type="button" variant="outline" onClick={onCancel}>Cancel</Button>
            </div>
        </form>
    );
}
