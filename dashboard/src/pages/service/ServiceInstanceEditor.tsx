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

import type {FormEvent} from 'react';
import type {InstanceDto, ServiceInstance} from "../../generated";
import {useExecutePromise} from "@ahoo-wang/fetcher-react";
import {useState} from "react";
import {serviceApiClient} from "../../services/clients.ts";
import Editor from "@monaco-editor/react";
import {SchemaSelector} from "./SchemaSelector.tsx";
import {toast} from 'sonner';
import {Button} from '@/components/ui/button';
import {Input} from '@/components/ui/input';
import {Label} from '@/components/ui/label';
import {Separator} from '@/components/ui/separator';
import {Switch} from '@/components/ui/switch';

interface ServiceInstanceFormProps {
    namespace: string;
    serviceId: string;
    initialValues?: ServiceInstance;
    onSuccess: () => void;
    onCancel: () => void;
}

export function ServiceInstanceEditor({
                                          namespace,
                                          serviceId,
                                          initialValues,
                                          onSuccess,
                                          onCancel
                                      }: ServiceInstanceFormProps) {
    const [metadata, setMetadata] = useState(JSON.stringify(initialValues?.metadata || {}, null, 2));
    const [schema, setSchema] = useState(initialValues?.schema ?? 'http');
    const [isEphemeral, setIsEphemeral] = useState(initialValues?.isEphemeral ?? true);
    const {loading, execute} = useExecutePromise({
        onSuccess: () => {
            toast.success('Save instance success!');
            onSuccess();
        },
        onError: () => {
            toast.error('Failed to save instance');
        }
    })

    const handleFinish = async (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        const values = Object.fromEntries(new FormData(event.currentTarget));
        let parsedMetadata: Record<string, string>;
        try {
            const parsed = JSON.parse(metadata) as unknown;
            if (!parsed || Array.isArray(parsed) || typeof parsed !== 'object' || Object.values(parsed).some(value => typeof value !== 'string')) {
                throw new Error();
            }
            parsedMetadata = parsed as Record<string, string>;
        } catch {
            toast.error('Metadata must be a valid JSON object.');
            return;
        }
        return await execute(() => {
            return serviceApiClient.register(namespace, serviceId, {
                body: {
                    schema,
                    host: initialValues?.host ?? String(values.host),
                    port: initialValues?.port ?? Number(values.port),
                    weight: initialValues?.weight ?? Number(values.weight || 0),
                    isEphemeral,
                    metadata: parsedMetadata,
                } as InstanceDto
            })
        })
    };
    return (
        <form className="space-y-5" onSubmit={handleFinish}>
            <div className="grid gap-4 sm:grid-cols-2">
                <div className="space-y-2">
                    <Label>Schema</Label>
                    <SchemaSelector value={schema} onChange={setSchema} disabled={!!initialValues}/>
                </div>
                <div className="space-y-2">
                    <Label htmlFor="instance-host">Host</Label>
                    <Input id="instance-host" name="host" defaultValue={initialValues?.host} disabled={!!initialValues} required/>
                </div>
                <div className="space-y-2">
                    <Label htmlFor="instance-port">Port</Label>
                    <Input id="instance-port" name="port" type="number" min={1} max={65535} defaultValue={initialValues?.port} disabled={!!initialValues} required/>
                </div>
                <div className="space-y-2">
                    <Label htmlFor="instance-weight">Weight</Label>
                    <Input id="instance-weight" name="weight" type="number" min={0} defaultValue={initialValues?.weight ?? 1} disabled={!!initialValues}/>
                </div>
                <div className="flex items-center justify-between gap-4 rounded-lg border px-3 py-2.5 sm:col-span-2">
                    <Label htmlFor="instance-ephemeral">Ephemeral instance</Label>
                    <Switch id="instance-ephemeral" checked={isEphemeral} onCheckedChange={setIsEphemeral}/>
                </div>
            </div>
            <div className="flex items-center gap-3"><Separator className="flex-1"/><span className="text-xs font-medium uppercase tracking-wider text-muted-foreground">Metadata</span><Separator className="flex-1"/></div>
            <Editor
                height="50vh"
                theme="vs-dark"
                defaultLanguage="json"
                defaultValue={JSON.stringify(initialValues?.metadata || {}, null, 2)}
                onChange={(value) => setMetadata(value || '{}')}
                options={{
                    ariaLabel: 'Instance metadata editor',
                    minimap: {enabled: false},
                }}
            />
            <Separator/>
            <div className="flex gap-2">
                <Button type="submit" loading={loading}>Submit</Button>
                <Button type="button" variant="outline" onClick={onCancel}>Cancel</Button>
            </div>
        </form>
    );
}
