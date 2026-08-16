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

import {useEffect} from 'react';
import {useFieldArray, useForm} from 'react-hook-form';
import {zodResolver} from '@hookform/resolvers/zod';
import {z} from 'zod';
import {Plus, Trash2} from 'lucide-react';
import {toast} from 'sonner';
import {useQuery} from '@ahoo-wang/fetcher-react';
import {Button} from '@/components/ui/button';
import {Input} from '@/components/ui/input';
import {Textarea} from '@/components/ui/textarea';
import {Separator} from '@/components/ui/separator';
import {Form, FormControl, FormField, FormItem, FormLabel, FormMessage} from '@/components/ui/form';
import type {ResourceActionDto, RoleDto} from '@/generated';
import {roleApiClient} from '@/services/clients';
import {NamespaceSelector} from '@/components/namespace/NamespaceSelector';
import {ResourceActionSelector} from './ResourceActionSelector';
import {EMPTY_ARRAY} from './EmptyArray';

const resourceActionBindItemSchema = z.object({
    namespace: z.string().min(1, 'Missing namespace'),
    action: z.string().min(1, 'Missing action'),
});

const schema = z.object({
    name: z.string().min(1, 'Please input role name!'),
    desc: z.string().min(1, 'Please input role description!'),
    resourceActionBind: z.array(resourceActionBindItemSchema),
});
type Values = z.infer<typeof schema>;

interface RoleEditorProps {
    initialValues?: RoleDto;
    onSuccess: () => void;
    onCancel: () => void;
}

export function RoleEditor({initialValues, onSuccess, onCancel}: RoleEditorProps) {
    const {result: resourceActionBind = EMPTY_ARRAY} = useQuery<string, ResourceActionDto[]>({
        initialQuery: initialValues?.name,
        execute: (query, _, abortController) => {
            return roleApiClient.getResourceBind(query, {abortController});
        },
    });
    const form = useForm<Values>({
        resolver: zodResolver(schema),
        defaultValues: {
            name: initialValues?.name ?? '',
            desc: initialValues?.desc ?? '',
            resourceActionBind: [],
        },
    });
    const {fields, append, remove} = useFieldArray({
        control: form.control,
        name: 'resourceActionBind',
    });

    useEffect(() => {
        if (!initialValues) {
            return;
        }
        form.reset({
            name: initialValues.name,
            desc: initialValues.desc ?? '',
            resourceActionBind: resourceActionBind,
        });
    }, [initialValues, form, resourceActionBind]);

    const onSubmit = async (values: Values) => {
        try {
            await roleApiClient.saveRole(values.name, {body: values});
            toast.success('Save role success!');
            onSuccess();
            form.reset();
        } catch {
            toast.error('Failed to save role');
        }
    };

    return (
        <Form {...form}>
            <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
                <FormField
                    control={form.control}
                    name="name"
                    render={({field}) => (
                        <FormItem>
                            <FormLabel>Role Name</FormLabel>
                            <FormControl>
                                <Input disabled={!!initialValues} {...field} />
                            </FormControl>
                            <FormMessage/>
                        </FormItem>
                    )}
                />
                <FormField
                    control={form.control}
                    name="desc"
                    render={({field}) => (
                        <FormItem>
                            <FormLabel>Description</FormLabel>
                            <FormControl>
                                <Textarea rows={4} {...field} />
                            </FormControl>
                            <FormMessage/>
                        </FormItem>
                    )}
                />
                <div className="flex items-center gap-4 py-2">
                    <Separator className="flex-1"/>
                    <span className="text-sm font-medium text-muted-foreground">Resource Bind</span>
                    <Separator className="flex-1"/>
                </div>
                {fields.map((rowField, index) => (
                    <div key={rowField.id} className="flex items-start gap-2">
                        <FormField
                            control={form.control}
                            name={`resourceActionBind.${index}.namespace`}
                            render={({field}) => (
                                <FormItem className="flex-1">
                                    <FormControl>
                                        <NamespaceSelector value={field.value} onChange={field.onChange}
                                                           className="w-full min-w-[200px]"/>
                                    </FormControl>
                                    <FormMessage/>
                                </FormItem>
                            )}
                        />
                        <FormField
                            control={form.control}
                            name={`resourceActionBind.${index}.action`}
                            render={({field}) => (
                                <FormItem className="flex-1">
                                    <FormControl>
                                        <ResourceActionSelector value={field.value} onChange={field.onChange}/>
                                    </FormControl>
                                    <FormMessage/>
                                </FormItem>
                            )}
                        />
                        <Button
                            type="button"
                            variant="ghost"
                            size="icon"
                            aria-label="Remove resource bind"
                            onClick={() => remove(index)}
                        >
                            <Trash2 className="h-4 w-4"/>
                        </Button>
                    </div>
                ))}
                <Button
                    type="button"
                    variant="outline"
                    className="w-full"
                    onClick={() => append({namespace: '', action: ''})}
                >
                    <Plus className="mr-1 h-4 w-4"/>
                    Add permissions
                </Button>
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
