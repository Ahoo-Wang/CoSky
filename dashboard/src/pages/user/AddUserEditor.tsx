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

import {useForm} from 'react-hook-form';
import {zodResolver} from '@hookform/resolvers/zod';
import {z} from 'zod';
import {toast} from 'sonner';
import {Button} from '@/components/ui/button';
import {Input} from '@/components/ui/input';
import {Form, FormControl, FormField, FormItem, FormLabel, FormMessage} from '@/components/ui/form';
import {userApiClient} from '@/services/clients';
import {RoleMultiSelect} from './RoleMultiSelect';

const schema = z.object({
    username: z.string().min(1, 'Please input username!'),
    password: z.string().min(1, 'Please input password!'),
    roles: z.array(z.string()),
});
type Values = z.infer<typeof schema>;

interface UserFormProps {
    roleSelectorOptions: string[];
    onSuccess: () => void;
    onCancel: () => void;
}

export function AddUserEditor({roleSelectorOptions, onSuccess, onCancel}: UserFormProps) {
    const form = useForm<Values>({
        resolver: zodResolver(schema),
        defaultValues: {username: '', password: '', roles: []},
    });

    const onSubmit = async (values: Values) => {
        try {
            await userApiClient.addUser(values.username, {body: values});
            toast.success('Add user success!');
        } catch {
            toast.error('Failed to add user');
        }
        try {
            await userApiClient.bindRole(values.username, {body: values.roles});
            toast.success('Bind role success!');
        } catch {
            toast.error('Failed to bind role');
        }
        onSuccess();
        form.reset();
    };

    return (
        <Form {...form}>
            <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
                <FormField
                    control={form.control}
                    name="username"
                    render={({field}) => (
                        <FormItem>
                            <FormLabel>Username</FormLabel>
                            <FormControl>
                                <Input {...field} />
                            </FormControl>
                            <FormMessage/>
                        </FormItem>
                    )}
                />
                <FormField
                    control={form.control}
                    name="password"
                    render={({field}) => (
                        <FormItem>
                            <FormLabel>Password</FormLabel>
                            <FormControl>
                                <Input type="password" {...field} />
                            </FormControl>
                            <FormMessage/>
                        </FormItem>
                    )}
                />
                <FormField
                    control={form.control}
                    name="roles"
                    render={({field}) => (
                        <FormItem>
                            <FormLabel>Roles</FormLabel>
                            <FormControl>
                                <RoleMultiSelect
                                    value={field.value ?? []}
                                    onChange={field.onChange}
                                    options={roleSelectorOptions}
                                    placeholder="Select Roles"
                                />
                            </FormControl>
                            <FormMessage/>
                        </FormItem>
                    )}
                />
                <div className="flex gap-2">
                    <Button type="submit" disabled={form.formState.isSubmitting}>
                        Submit
                    </Button>
                    <Button type="button" variant="outline" onClick={onCancel}>
                        Cancel
                    </Button>
                </div>
            </form>
        </Form>
    );
}
