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
import {Form, FormControl, FormField, FormItem, FormLabel, FormMessage} from '@/components/ui/form';
import {serviceApiClient} from '@/services/clients';

const schema = z.object({
    serviceId: z.string().min(1, 'Please input serviceId!'),
});
type Values = z.infer<typeof schema>;

export interface AddServiceFormProps {
    namespace: string;
    onSuccess: () => void;
}

export function AddServiceForm({namespace, onSuccess}: AddServiceFormProps) {
    const form = useForm<Values>({
        resolver: zodResolver(schema),
        defaultValues: {serviceId: ''},
    });

    const onSubmit = async (values: Values) => {
        try {
            await serviceApiClient.setService(namespace, values.serviceId);
            toast.success('Add service success!');
            form.reset();
            onSuccess();
        } catch {
            toast.error('Add service failed!');
        }
    };

    return (
        <Form {...form}>
            <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
                <FormField
                    control={form.control}
                    name="serviceId"
                    render={({field}) => (
                        <FormItem>
                            <FormLabel>Service ID</FormLabel>
                            <FormControl>
                                <Input placeholder="Enter serviceId" {...field} />
                            </FormControl>
                            <FormMessage/>
                        </FormItem>
                    )}
                />
                <Button type="submit" disabled={form.formState.isSubmitting}>
                    <Plus className="mr-1 h-4 w-4"/>
                    Add Service
                </Button>
            </form>
        </Form>
    );
}
