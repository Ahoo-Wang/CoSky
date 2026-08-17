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
import {useExecutePromise} from "@ahoo-wang/fetcher-react";
import {userApiClient} from "../../services/clients.ts";
import {toast} from 'sonner';
import {Button} from '@/components/ui/button';
import {Input} from '@/components/ui/input';
import {Label} from '@/components/ui/label';
import {MultiSelect} from '@/components/ui/multi-select';

export interface UserFormValues {
    username: string;
    password: string;
    roles: string[];
}

interface UserFormProps {
    roleSelectorOptions: { label: string, value: string }[];
    onSuccess: () => void;
    onCancel: () => void;
}

export function AddUserEditor({roleSelectorOptions, onSuccess, onCancel}: UserFormProps) {
    const [roles, setRoles] = useState<string[]>([]);
    const {loading, execute: saveUser} = useExecutePromise({
        onSuccess: () => {
            toast.success('Add user success!');
            toast.success('Bind role success!');
            onSuccess();
            setRoles([]);
        },
        onError: (error) => {
            toast.error(error instanceof Error && error.message === 'User already exists.'
                ? error.message
                : 'Failed to add user');
        }
    })
    const handleFinish = async (values: UserFormValues) => {
        await saveUser(async () => {
            const created = await userApiClient.addUser(values.username, {body: {password: values.password}});
            if (!created) {
                throw new Error('User already exists.');
            }
            await userApiClient.bindRole(values.username, {body: values.roles});
        });
    };
    const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        const values = Object.fromEntries(new FormData(event.currentTarget)) as unknown as Omit<UserFormValues, 'roles'>;
        handleFinish({...values, roles});
    };
    return (
        <form className="space-y-5" onSubmit={handleSubmit}>
            <div className="space-y-2">
                <Label htmlFor="new-username">Username</Label>
                <Input id="new-username" name="username" autoComplete="off" required/>
            </div>
            <div className="space-y-2">
                <Label htmlFor="new-user-password">Password</Label>
                <Input id="new-user-password" name="password" type="password" autoComplete="new-password" required/>
            </div>
            <div className="space-y-2">
                <Label htmlFor="new-user-roles">Roles</Label>
                <MultiSelect id="new-user-roles" options={roleSelectorOptions} value={roles} onChange={setRoles}/>
            </div>
            <div className="flex gap-2">
                <Button type="submit" loading={loading}>Submit</Button>
                <Button type="button" variant="outline" onClick={onCancel}>Cancel</Button>
            </div>
        </form>
    );
}
