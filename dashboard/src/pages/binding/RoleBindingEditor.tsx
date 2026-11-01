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
import {roleBindings, SubjectType} from '@/services/resources';
import type {RoleBindingResource} from '@/services/resources';
import {Button} from '@/components/ui/button';
import {Input} from '@/components/ui/input';
import {Label} from '@/components/ui/label';
import {OptionsSelect} from '@/components/ui/options-select';
import {Separator} from '@/components/ui/separator';
import {runAction} from '@/lib/actions';
import {enumLabel, splitList} from '@/lib/utils';

const SUBJECT_TYPE_OPTIONS = [SubjectType.TYPE_SERVICE_ACCOUNT, SubjectType.TYPE_USER, SubjectType.TYPE_GROUP]
    .map(value => ({value, label: enumLabel(value, 'TYPE_')}));

interface SubjectDraft {
    id: number;
    type: SubjectType;
    name: string;
}

interface RoleBindingEditorProps {
    existing?: RoleBindingResource;
    roleNames: string[];
    onSuccess: () => void;
    onCancel: () => void;
}

export function RoleBindingEditor({existing, roleNames, onSuccess, onCancel}: RoleBindingEditorProps) {
    const [name, setName] = useState(existing?.name ?? '');
    const [role, setRole] = useState(existing?.role ?? '');
    const [namespaces, setNamespaces] = useState(existing?.namespaces.join(', ') ?? '*');
    const [subjects, setSubjects] = useState<SubjectDraft[]>(() =>
        (existing?.subjects ?? []).map((subject, id) => ({id, ...subject})));
    const [nextId, setNextId] = useState(subjects.length);
    const [saving, setSaving] = useState(false);

    const updateSubject = (id: number, change: Partial<SubjectDraft>) => {
        setSubjects(current => current.map(subject => subject.id === id ? {...subject, ...change} : subject));
    };

    const addSubject = () => {
        setSubjects(current => [...current, {id: nextId, type: SubjectType.TYPE_SERVICE_ACCOUNT, name: ''}]);
        setNextId(nextId + 1);
    };

    const handleSubmit = async () => {
        setSaving(true);
        const saved = await runAction(
            () => roleBindings.put(name.trim(), {
                role,
                subjects: subjects.map(({type, name: subjectName}) => ({type, name: subjectName.trim()})),
                namespaces: splitList(namespaces),
            }, existing),
            {success: 'Role binding saved', failure: 'Failed to save role binding'},
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
                    <Label htmlFor="binding-name">Name</Label>
                    <Input id="binding-name" value={name} required disabled={!!existing}
                           onChange={event => setName(event.target.value)}/>
                </div>
                <div className="space-y-2">
                    <Label>Role</Label>
                    <OptionsSelect
                        ariaLabel="Role"
                        placeholder="Select a role"
                        options={roleNames.map(value => ({value, label: value}))}
                        value={role}
                        onChange={setRole}
                    />
                </div>
                <div className="space-y-2 sm:col-span-2">
                    <Label htmlFor="binding-namespaces">Namespaces</Label>
                    <Input id="binding-namespaces" value={namespaces} required placeholder="prod, staging, or *"
                           onChange={event => setNamespaces(event.target.value)}/>
                </div>
            </div>
            <Separator/>
            {subjects.map((subject, index) => (
                <div key={subject.id} className="flex flex-wrap items-center gap-2">
                    <OptionsSelect
                        ariaLabel={`Type of subject ${index + 1}`}
                        className="w-48"
                        options={SUBJECT_TYPE_OPTIONS}
                        value={subject.type}
                        onChange={type => updateSubject(subject.id, {type: type as SubjectType})}
                    />
                    <Input aria-label={`Name of subject ${index + 1}`} className="w-64" value={subject.name} required
                           onChange={event => updateSubject(subject.id, {name: event.target.value})}/>
                    <Button type="button" variant="ghost" size="sm" className="text-destructive"
                            aria-label={`Remove subject ${index + 1}`}
                            onClick={() => setSubjects(current => current.filter(item => item.id !== subject.id))}>
                        <Trash2/>
                    </Button>
                </div>
            ))}
            <Button type="button" variant="outline" onClick={addSubject}><Plus/> Add subject</Button>
            <Separator/>
            <div className="flex gap-2">
                <Button type="submit" loading={saving}>Submit</Button>
                <Button type="button" variant="outline" onClick={onCancel}>Cancel</Button>
            </div>
        </form>
    );
}
