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
import {useExecutePromise} from '@ahoo-wang/fetcher-react';
import {Button} from '@/components/ui/button';
import {Form, FormControl, FormField, FormItem, FormLabel, FormMessage} from '@/components/ui/form';
import {FileDropzone} from '@/components/form/FileDropzone';
import {configApiClient} from '@/services/clients';
import type {ImportResponse} from '@/generated';
import {ImportPolicySelector} from './ImportPolicySelector';

const formSchema = z.object({
    policy: z.string().min(1),
    importZip: z.instanceof(File, {error: 'Please select a file!'}),
});

type ImporterFormValues = z.infer<typeof formSchema>;

export interface ConfigImporterProps {
    namespace: string;
    onSuccess: () => void;
    onCancel: () => void;
}

export function ConfigImporter({namespace, onSuccess, onCancel}: ConfigImporterProps) {
    const {loading, execute} = useExecutePromise<ImportResponse>({
        onSuccess: (result) => {
            toast.success(`ToTal : ${result.total} , Succeeded : ${result.succeeded} . `);
            onSuccess();
        },
        onError: () => {
            toast.error('Import config failed');
        },
    });
    const form = useForm<ImporterFormValues>({
        resolver: zodResolver(formSchema),
        defaultValues: {
            policy: 'skip',
            importZip: undefined,
        },
    });

    const onSubmit = (values: ImporterFormValues) => {
        const formData = new FormData();
        formData.append('policy', values.policy);
        formData.append('importZip', values.importZip);
        execute(() => {
            return configApiClient.importZip(namespace, {
                body: formData,
            });
        });
    };

    return (
        <Form {...form}>
            <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
                <FormField
                    control={form.control}
                    name="policy"
                    render={({field}) => (
                        <FormItem>
                            <FormLabel>Import Policy</FormLabel>
                            <FormControl>
                                <ImportPolicySelector
                                    value={field.value || undefined}
                                    onChange={field.onChange}
                                />
                            </FormControl>
                            <FormMessage/>
                        </FormItem>
                    )}
                />
                <FormField
                    control={form.control}
                    name="importZip"
                    render={({field}) => (
                        <FormItem>
                            <FormControl>
                                <FileDropzone
                                    accept=".zip"
                                    hint="Click or drag ZIP-file to this area to upload"
                                    onFile={field.onChange}
                                />
                                <p className="text-xs text-muted-foreground">
                                    Support Nacos config format.
                                </p>
                            </FormControl>
                            <FormMessage/>
                        </FormItem>
                    )}
                />
                <div className="flex gap-2">
                    <Button type="submit" disabled={loading}>
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
