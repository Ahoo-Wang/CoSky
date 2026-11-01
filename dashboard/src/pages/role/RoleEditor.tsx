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
import {Plus, Trash2} from 'lucide-react';
import {Kind, roles, Verb} from '@/services/resources';
import type {RoleResource} from '@/services/resources';
import {Button} from '@/components/ui/button';
import {Input} from '@/components/ui/input';
import {Label} from '@/components/ui/label';
import {MultiSelect} from '@/components/ui/multi-select';
import {Separator} from '@/components/ui/separator';
import {runAction} from '@/lib/actions';
import {enumLabel, splitList} from '@/lib/utils';

const KIND_OPTIONS = [
    Kind.KIND_NAMESPACE,
    Kind.KIND_CONFIG,
    Kind.KIND_INSTANCE,
    Kind.KIND_SERVICE_ACCOUNT,
    Kind.KIND_ROLE,
    Kind.KIND_ROLE_BINDING,
].map(value => ({value, label: enumLabel(value, 'KIND_')}));

const VERB_OPTIONS = [Verb.VERB_GET, Verb.VERB_WATCH, Verb.VERB_PUT, Verb.VERB_DELETE]
    .map(value => ({value, label: enumLabel(value, 'VERB_')}));

interface RuleDraft {
    id: number;
    kinds: Kind[];
    verbs: Verb[];
    namespaces: string;
    names: string;
}

interface RoleEditorProps {
    existing?: RoleResource;
    onSuccess: () => void;
    onCancel: () => void;
}

export function RoleEditor({existing, onSuccess, onCancel}: RoleEditorProps) {
    const [name, setName] = useState(existing?.name ?? '');
    const [description, setDescription] = useState(existing?.description ?? '');
    const [rules, setRules] = useState<RuleDraft[]>(() => (existing?.rules ?? []).map((rule, id) => ({
        id,
        kinds: rule.kinds,
        verbs: rule.verbs,
        namespaces: rule.namespaces.join(', '),
        names: rule.names.join(', '),
    })));
    const [nextId, setNextId] = useState(rules.length);
    const [saving, setSaving] = useState(false);

    const updateRule = (id: number, change: Partial<RuleDraft>) => {
        setRules(current => current.map(rule => rule.id === id ? {...rule, ...change} : rule));
    };

    const addRule = () => {
        setRules(current => [...current, {id: nextId, kinds: [], verbs: [], namespaces: '*', names: ''}]);
        setNextId(nextId + 1);
    };

    const handleSubmit = async () => {
        setSaving(true);
        const saved = await runAction(
            () => roles.put(name.trim(), {
                description,
                rules: rules.map(rule => ({
                    kinds: rule.kinds,
                    verbs: rule.verbs,
                    namespaces: splitList(rule.namespaces),
                    names: splitList(rule.names),
                })),
            }, existing),
            {success: 'Role saved', failure: 'Failed to save role'},
        );
        setSaving(false);
        if (saved) {
            onSuccess();
        }
    };

    return (
        <form className="space-y-5" onSubmit={event => {
            event.preventDefault();
            void handleSubmit();
        }}>
            <div className="grid gap-4 sm:grid-cols-2">
                <div className="space-y-2">
                    <Label htmlFor="role-name">Name</Label>
                    <Input id="role-name" value={name} required disabled={!!existing}
                           onChange={event => setName(event.target.value)}/>
                </div>
                <div className="space-y-2">
                    <Label htmlFor="role-description">Description</Label>
                    <Input id="role-description" value={description}
                           onChange={event => setDescription(event.target.value)}/>
                </div>
            </div>
            <Separator/>
            {rules.map((rule, index) => (
                <fieldset key={rule.id} className="grid gap-3 rounded-lg border p-4 sm:grid-cols-2">
                    <legend className="px-1 text-sm font-medium">Rule {index + 1}</legend>
                    <div className="space-y-2">
                        <Label>Kinds</Label>
                        <MultiSelect aria-label={`Kinds of rule ${index + 1}`} className="w-full" options={KIND_OPTIONS}
                                     value={rule.kinds} placeholder="Select kinds"
                                     onChange={kinds => updateRule(rule.id, {kinds: kinds as Kind[]})}/>
                    </div>
                    <div className="space-y-2">
                        <Label>Verbs</Label>
                        <MultiSelect aria-label={`Verbs of rule ${index + 1}`} className="w-full" options={VERB_OPTIONS}
                                     value={rule.verbs} placeholder="Select verbs"
                                     onChange={verbs => updateRule(rule.id, {verbs: verbs as Verb[]})}/>
                    </div>
                    <div className="space-y-2">
                        <Label htmlFor={`rule-${rule.id}-namespaces`}>Namespaces</Label>
                        <Input id={`rule-${rule.id}-namespaces`} value={rule.namespaces} placeholder="prod, staging, or *"
                               onChange={event => updateRule(rule.id, {namespaces: event.target.value})}/>
                    </div>
                    <div className="space-y-2">
                        <Label htmlFor={`rule-${rule.id}-names`}>Names</Label>
                        <Input id={`rule-${rule.id}-names`} value={rule.names} placeholder="Empty for all; order-* for a prefix"
                               onChange={event => updateRule(rule.id, {names: event.target.value})}/>
                    </div>
                    <div className="sm:col-span-2">
                        <Button type="button" variant="ghost" size="sm" className="text-destructive"
                                onClick={() => setRules(current => current.filter(item => item.id !== rule.id))}>
                            <Trash2/> Remove rule
                        </Button>
                    </div>
                </fieldset>
            ))}
            <Button type="button" variant="outline" onClick={addRule}><Plus/> Add rule</Button>
            <Separator/>
            <div className="flex gap-2">
                <Button type="submit" loading={saving}>Submit</Button>
                <Button type="button" variant="outline" onClick={onCancel}>Cancel</Button>
            </div>
        </form>
    );
}
