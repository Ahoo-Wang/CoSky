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
import {Plus} from 'lucide-react';
import {Button} from '@/components/ui/button';
import {Input} from '@/components/ui/input';
import {Form, FormControl, FormField, FormItem, FormMessage} from '@/components/ui/form';
import {namespaceApiClient} from '@/services/clients';

const schema = z.object({
    namespace: z.string().min(1, 'Namespace is required')
        .regex(/^[a-zA-Z][a-zA-Z0-9_.-]*$/, 'Must start with a letter and contain only letters, digits, _, ., -'),
});
type Values = z.infer<typeof schema>;

export function AddNamespaceForm({onSuccess}: { onSuccess: () => void }) {
    const form = useForm<Values>({
        resolver: zodResolver(schema),
        defaultValues: {namespace: ''},
    });

    const onSubmit = async (values: Values) => {
        try {
            await namespaceApiClient.setNamespace(values.namespace);
            toast.success('Namespace added successfully');
            form.reset();
            onSuccess();
        } catch {
            toast.error('Failed to add namespace');
        }
    };

    return (
        <Form {...form}>
            <form onSubmit={form.handleSubmit(onSubmit)} className="flex items-start gap-2">
                <FormField
                    control={form.control}
                    name="namespace"
                    render={({field}) => (
                        <FormItem className="w-64">
                            <FormControl>
                                <Input placeholder="New namespace" {...field} />
                            </FormControl>
                            <FormMessage/>
                        </FormItem>
                    )}
                />
                <Button type="submit" disabled={form.formState.isSubmitting}>
                    <Plus className="mr-1 h-4 w-4"/>
                    Add
                </Button>
            </form>
        </Form>
    );
}
