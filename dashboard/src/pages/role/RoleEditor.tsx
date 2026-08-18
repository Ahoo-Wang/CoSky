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

import {useRef, useState} from 'react';
import type {FormEvent} from 'react';
import {Plus, Trash2} from 'lucide-react';
import {toast} from 'sonner';
import {useExecutePromise, useQuery} from '@ahoo-wang/fetcher-react';
import type {ResourceActionDto, RoleDto, SaveRoleRequest} from '../../generated';
import {roleApiClient} from '../../services/clients.ts';
import {NamespaceSelector} from '../../components/namespace/NamespaceSelector.tsx';
import {ResourceActionSelector} from './ResourceActionSelector.tsx';
import {Button} from '@/components/ui/button';
import {Input} from '@/components/ui/input';
import {Label} from '@/components/ui/label';
import {Separator} from '@/components/ui/separator';

interface RoleEditorProps {
    initialValues?: RoleDto;
    onSuccess: () => void;
    onCancel: () => void;
}

const EMPTY_RESOURCE_ACTIONS: ResourceActionDto[] = [];

export function RoleEditor({initialValues, onSuccess, onCancel}: RoleEditorProps) {
    const [name, setName] = useState(initialValues?.name ?? '');
    const [desc, setDesc] = useState(initialValues?.desc ?? '');
    const [bindings, setBindings] = useState<ResourceActionDto[]>([]);
    const [bindingError, setBindingError] = useState('');
    const bindingsContainer = useRef<HTMLDivElement>(null);
    const {result = EMPTY_RESOURCE_ACTIONS} = useQuery<string, ResourceActionDto[]>({
        initialQuery: initialValues?.name,
        execute: (query, attributes, abortController) => roleApiClient.getResourceBind(query, attributes, abortController),
    });
    const {loading, execute: save} = useExecutePromise({
        onSuccess: () => {
            toast.success('Save role success!');
            onSuccess();
        },
        onError: () => {
            toast.error('Failed to save role');
        },
    });
    const [previousResult, setPreviousResult] = useState(result);
    if (previousResult !== result) {
        setPreviousResult(result);
        setBindings(result.map(binding => ({...binding})));
    }

    const updateBinding = (index: number, key: keyof ResourceActionDto, value: string) => {
        setBindingError('');
        setBindings(current => current.map((binding, bindingIndex) => bindingIndex === index
            ? {...binding, [key]: value}
            : binding));
    };

    const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        if (bindings.some(binding => !binding.namespace || !binding.action)) {
            setBindingError('Complete every permission binding before saving.');
            requestAnimationFrame(() => bindingsContainer.current?.querySelector<HTMLButtonElement>('[aria-invalid="true"]')?.focus());
            return;
        }
        setBindingError('');
        const body: SaveRoleRequest = {desc, resourceActionBind: bindings};
        await save(() => roleApiClient.saveRole(name, {body}));
    };

    return (
        <form className="space-y-5" onSubmit={handleSubmit}>
            <div className="space-y-2">
                <Label htmlFor="role-name">Role Name</Label>
                <Input id="role-name" value={name} onChange={event => setName(event.target.value)} disabled={!!initialValues} required/>
            </div>
            <div className="space-y-2">
                <Label htmlFor="role-description">Description</Label>
                <textarea
                    id="role-description"
                    value={desc}
                    onChange={event => setDesc(event.target.value)}
                    rows={4}
                    required
                    className="w-full resize-y rounded-lg border border-input bg-transparent px-3 py-2 text-sm outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50"
                />
            </div>
            <div className="flex items-center gap-3"><Separator className="flex-1"/><span className="text-xs font-medium uppercase tracking-wider text-muted-foreground">Resource Bind</span><Separator className="flex-1"/></div>
            <div ref={bindingsContainer} className="space-y-3">
                {bindings.map((binding, index) => (
                    <div key={`${index}-${binding.namespace}-${binding.action}`} className="grid gap-2 rounded-xl border p-3 sm:grid-cols-[1fr_1fr_auto]">
                        <NamespaceSelector value={binding.namespace} onChange={value => updateBinding(index, 'namespace', value)}
                                           aria-invalid={!!bindingError && !binding.namespace} aria-describedby={bindingError ? 'role-binding-error' : undefined}/>
                        <ResourceActionSelector value={binding.action} onChange={value => updateBinding(index, 'action', value)}
                                                aria-invalid={!!bindingError && !binding.action} aria-describedby={bindingError ? 'role-binding-error' : undefined}/>
                        <Button type="button" variant="ghost" size="icon" className="text-destructive" onClick={() => {
                            setBindingError('');
                            setBindings(current => current.filter((_, bindingIndex) => bindingIndex !== index));
                        }} aria-label="Remove permission">
                            <Trash2/>
                        </Button>
                    </div>
                ))}
                {bindingError && <p id="role-binding-error" role="alert" className="text-sm text-destructive">{bindingError}</p>}
                <Button type="button" variant="outline" className="w-full border-dashed" onClick={() => {
                    setBindingError('');
                    setBindings(current => [...current, {namespace: '', action: ''}]);
                }}>
                    <Plus/> Add permission
                </Button>
            </div>
            <div className="flex gap-2">
                <Button type="submit" loading={loading}>Submit</Button>
                <Button type="button" variant="outline" onClick={onCancel}>Cancel</Button>
            </div>
        </form>
    );
}
