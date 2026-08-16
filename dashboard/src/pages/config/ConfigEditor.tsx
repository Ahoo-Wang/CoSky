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

import dayjs from 'dayjs';
import {useForm} from 'react-hook-form';
import {zodResolver} from '@hookform/resolvers/zod';
import {z} from 'zod';
import {toast} from 'sonner';
import {Editor} from '@monaco-editor/react';
import {useExecutePromise, useQuery} from '@ahoo-wang/fetcher-react';
import {Button} from '@/components/ui/button';
import {Input} from '@/components/ui/input';
import {Skeleton} from '@/components/ui/skeleton';
import {Separator} from '@/components/ui/separator';
import {Form, FormControl, FormField, FormItem, FormMessage} from '@/components/ui/form';
import {DescriptionList} from '@/components/feedback/DescriptionList';
import {configApiClient} from '@/services/clients';
import {useTheme} from '@/theme/ThemeProvider';
import type {Config} from '@/generated';
import {ConfigFormatSelector} from './ConfigFormatSelector';
import {getFileNameWithExt, getFullFileName} from './fileNames';

const formSchema = z.object({
    fileName: z.string().min(1, 'Please enter file name!'),
    fileExt: z.string(),
    configData: z.string(),
});

type ConfigFormValues = z.infer<typeof formSchema>;

export interface ConfigEditorProps {
    namespace: string;
    configId?: string;
    onSuccess: () => void;
    onCancel: () => void;
}

export function ConfigEditor({namespace, configId, onSuccess, onCancel}: ConfigEditorProps) {
    const {resolvedTheme} = useTheme();
    const fileNameWithExt = getFileNameWithExt(configId ?? '.yaml');
    const form = useForm<ConfigFormValues>({
        resolver: zodResolver(formSchema),
        defaultValues: {
            fileName: fileNameWithExt.name,
            fileExt: fileNameWithExt.ext,
            configData: '',
        },
    });
    const fileExt = form.watch('fileExt');
    const {loading, result: config} = useQuery<string, Config>({
        query: configId,
        execute: (query, _, abortController) => {
            return configApiClient.getConfig(namespace, query, {abortController});
        },
        onSuccess: (config) => {
            form.setValue('configData', config.data);
        },
    });
    const {loading: loadingSave, execute: saveConfig} = useExecutePromise({
        propagateError: true,
        onSuccess: () => {
            toast.success('Config saved successfully');
            onSuccess();
        },
        onError: () => {
            toast.error('Config save failed');
        },
    });

    const onSubmit = (values: ConfigFormValues) => {
        const fullFileName = getFullFileName(values.fileName, values.fileExt);
        saveConfig(() => {
            return configApiClient.setConfig(namespace, fullFileName, {
                body: values.configData,
            });
        });
    };

    if (configId && loading) {
        return (
            <Skeleton className="h-64 w-full"/>
        );
    }

    return (
        <Form {...form}>
            <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
                {!configId && (
                    <div className="flex gap-2">
                        <FormField
                            control={form.control}
                            name="fileName"
                            render={({field}) => (
                                <FormItem className="flex-1">
                                    <FormControl>
                                        <Input placeholder="Enter file name!" {...field} />
                                    </FormControl>
                                    <FormMessage/>
                                </FormItem>
                            )}
                        />
                        <FormField
                            control={form.control}
                            name="fileExt"
                            render={({field}) => (
                                <FormItem className="w-[150px]">
                                    <FormControl>
                                        <ConfigFormatSelector
                                            value={field.value || undefined}
                                            onChange={field.onChange}
                                        />
                                    </FormControl>
                                    <FormMessage/>
                                </FormItem>
                            )}
                        />
                    </div>
                )}
                {config && (
                    <DescriptionList items={[
                        {label: 'File Name', value: config.configId},
                        {label: 'Hash', value: config.hash},
                        {
                            label: 'Last Update Time',
                            value: dayjs(config.createTime * 1000).format('YYYY-MM-DD HH:mm:ss'),
                        },
                        {label: 'Version', value: config.version},
                    ]}/>
                )}
                <div className="flex items-center gap-4 py-2">
                    <Separator className="flex-1"/>
                    <span className="text-sm font-medium text-muted-foreground">Config Data</span>
                    <Separator className="flex-1"/>
                </div>
                <FormField
                    control={form.control}
                    name="configData"
                    render={({field}) => (
                        <FormItem>
                            <FormControl>
                                <Editor
                                    height="60vh"
                                    theme={resolvedTheme === 'dark' ? 'vs-dark' : 'vs'}
                                    language={fileExt}
                                    value={field.value}
                                    onChange={(value) => field.onChange(value ?? '')}
                                    options={{
                                        minimap: {enabled: false},
                                    }}
                                />
                            </FormControl>
                            <FormMessage/>
                        </FormItem>
                    )}
                />
                <Separator/>
                <div className="flex gap-2">
                    <Button type="submit" disabled={loadingSave}>
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
