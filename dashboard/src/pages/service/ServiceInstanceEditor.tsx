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
import {Editor} from '@monaco-editor/react';
import {Button} from '@/components/ui/button';
import {Input} from '@/components/ui/input';
import {Switch} from '@/components/ui/switch';
import {Form, FormControl, FormField, FormItem, FormLabel, FormMessage} from '@/components/ui/form';
import type {InstanceDto, ServiceInstance} from '@/generated';
import {serviceApiClient} from '@/services/clients';
import {useTheme} from '@/theme/ThemeProvider';
import {SchemaSelector} from './SchemaSelector';

const formSchema = z.object({
    schema: z.string().min(1, 'Please input schema!'),
    host: z.string().min(1, 'Please input host!'),
    port: z.coerce.number({error: 'Please input port!'}).min(1, 'Please input port!'),
    weight: z.preprocess(
        (value) => (value === '' || value === null ? undefined : value),
        z.coerce.number().optional(),
    ),
    isEphemeral: z.boolean(),
    metadata: z.string(),
});

type InstanceFormValues = z.input<typeof formSchema>;
type InstanceFormOutput = z.output<typeof formSchema>;

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
    const {resolvedTheme} = useTheme();
    const isEdit = !!initialValues;
    const form = useForm<InstanceFormValues, unknown, InstanceFormOutput>({
        resolver: zodResolver(formSchema),
        defaultValues: {
            schema: initialValues?.schema ?? '',
            host: initialValues?.host ?? '',
            port: initialValues?.port ?? '',
            weight: initialValues?.weight ?? '',
            isEphemeral: initialValues?.isEphemeral ?? false,
            metadata: JSON.stringify(initialValues?.metadata ?? {}, null, 2),
        },
    });

    const onSubmit = async (values: InstanceFormOutput) => {
        try {
            const body = {
                ...values,
                metadata: JSON.parse(values.metadata) as Record<string, string>,
            } as InstanceDto;
            await serviceApiClient.register(namespace, serviceId, {body});
            toast.success('Save instance success!');
            form.reset();
            onSuccess();
        } catch {
            toast.error('Failed to save instance');
        }
    };

    return (
        <Form {...form}>
            <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
                <FormField
                    control={form.control}
                    name="schema"
                    render={({field}) => (
                        <FormItem>
                            <FormLabel>Schema</FormLabel>
                            <FormControl>
                                <SchemaSelector
                                    value={field.value || undefined}
                                    onChange={field.onChange}
                                    disabled={isEdit}
                                />
                            </FormControl>
                            <FormMessage/>
                        </FormItem>
                    )}
                />
                <FormField
                    control={form.control}
                    name="host"
                    render={({field}) => (
                        <FormItem>
                            <FormLabel>Host</FormLabel>
                            <FormControl>
                                <Input disabled={isEdit} {...field} />
                            </FormControl>
                            <FormMessage/>
                        </FormItem>
                    )}
                />
                <FormField
                    control={form.control}
                    name="port"
                    render={({field}) => (
                        <FormItem>
                            <FormLabel>Port</FormLabel>
                            <FormControl>
                                <Input
                                    type="number"
                                    disabled={isEdit}
                                    {...field}
                                    value={field.value as string | number | undefined}
                                />
                            </FormControl>
                            <FormMessage/>
                        </FormItem>
                    )}
                />
                <FormField
                    control={form.control}
                    name="weight"
                    render={({field}) => (
                        <FormItem>
                            <FormLabel>Weight</FormLabel>
                            <FormControl>
                                <Input
                                    type="number"
                                    disabled={isEdit}
                                    {...field}
                                    value={field.value as string | number | undefined}
                                />
                            </FormControl>
                            <FormMessage/>
                        </FormItem>
                    )}
                />
                <FormField
                    control={form.control}
                    name="isEphemeral"
                    render={({field}) => (
                        <FormItem>
                            <FormLabel>Is Ephemeral?</FormLabel>
                            <FormControl>
                                <Switch checked={field.value} onCheckedChange={field.onChange}/>
                            </FormControl>
                            <FormMessage/>
                        </FormItem>
                    )}
                />
                <FormField
                    control={form.control}
                    name="metadata"
                    render={({field}) => (
                        <FormItem>
                            <FormLabel>Metadata</FormLabel>
                            <FormControl>
                                <Editor
                                    height="200px"
                                    language="json"
                                    theme={resolvedTheme === 'dark' ? 'vs-dark' : 'vs'}
                                    value={field.value}
                                    onChange={(value) => field.onChange(value ?? '')}
                                    options={{minimap: {enabled: false}}}
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
