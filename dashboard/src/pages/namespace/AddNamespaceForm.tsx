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
import {Plus} from 'lucide-react';
import {namespaces} from '@/services/resources';
import {Button} from '@/components/ui/button';
import {Input} from '@/components/ui/input';
import {runAction} from '@/lib/actions';

interface AddNamespaceFormProps {
    onSuccess: () => void;
}

export function AddNamespaceForm({onSuccess}: AddNamespaceFormProps) {
    const [name, setName] = useState('');
    const [description, setDescription] = useState('');
    const [saving, setSaving] = useState(false);

    const handleSubmit = async () => {
        setSaving(true);
        const created = await runAction(
            () => namespaces.create(name.trim(), description),
            {success: 'Namespace added', failure: 'Failed to add namespace'},
        );
        setSaving(false);
        if (created) {
            setName('');
            setDescription('');
            onSuccess();
        }
    };

    return (
        <form className="flex flex-wrap items-center gap-2" onSubmit={event => {
            event.preventDefault();
            void handleSubmit();
        }}>
            <Input value={name} onChange={event => setName(event.target.value)} placeholder="Enter namespace" required
                   aria-label="Namespace name" className="w-48"/>
            <Input value={description} onChange={event => setDescription(event.target.value)} placeholder="Description"
                   aria-label="Namespace description" className="w-56"/>
            <Button type="submit" loading={saving}><Plus/>Add Namespace</Button>
        </form>
    );
}
