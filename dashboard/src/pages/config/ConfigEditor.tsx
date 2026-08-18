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

import React, {useRef, useState} from 'react';
import Editor from '@monaco-editor/react';
import {ConfigFormatSelector} from "./ConfigFormatSelector.tsx";
import {useExecutePromise, useQuery} from "@ahoo-wang/fetcher-react";
import {configApiClient} from "../../services/clients.ts";
import {getFileNameWithExt, getFullFileName} from "./fileNames.ts";
import dayjs from "dayjs";
import type {Config} from "../../generated";
import {toast} from 'sonner';
import {Button} from '@/components/ui/button';
import {DefinitionList} from '@/components/ui/definition-list';
import {Input} from '@/components/ui/input';
import {Label} from '@/components/ui/label';
import {Separator} from '@/components/ui/separator';
import {Skeleton} from '@/components/ui/skeleton';

interface ConfigEditFormProps {
    namespace: string;
    configId?: string;
    onSuccess: () => void;
    onCancel: () => void;
}

export const ConfigEditor: React.FC<ConfigEditFormProps> = ({namespace, configId, onSuccess, onCancel}) => {
    const fileNameWithExt = getFileNameWithExt(configId ?? '.yaml');
    const [fileName, setFileName] = useState<string>(fileNameWithExt.name);
    const [fileNameError, setFileNameError] = useState('');
    const [fileExt, setFileExt] = useState<string>(fileNameWithExt.ext);
    const [configData, setConfigData] = useState('');
    const fileNameInput = useRef<HTMLInputElement>(null);
    const {loading, error, result: config, execute: loadConfig} = useQuery<string, Config>({
        query: configId,
        execute: (query, attributes, abortController) => {
            return configApiClient.getConfig(namespace, query, attributes, abortController);
        },
        onSuccess: (config) => {
            setConfigData(config.data)
        }
    })
    const {loading: loadingSave, execute: saveConfig} = useExecutePromise({
        onSuccess: () => {
            toast.success('Config saved successfully');
            onSuccess();
        },
        onError: () => {
            toast.error('Config save failed');
        }
    })

    const handleSubmit = () => {
        const normalizedFileName = fileName.trim();
        if (!normalizedFileName) {
            setFileNameError('Please enter file name!');
            fileNameInput.current?.focus();
            return;
        }
        setFileNameError('');
        const fullFileName = getFullFileName(normalizedFileName, fileExt)
        saveConfig(() => {
            return configApiClient.setConfig(namespace, fullFileName, {
                body: configData
            })
        })
    }
    if (configId && error) {
        return (
            <div role="alert" className="space-y-4 rounded-xl border border-destructive/30 bg-destructive/5 p-6 text-sm">
                <p>Failed to load this configuration. Nothing has been changed.</p>
                <Button variant="outline" onClick={() => loadConfig()}>Retry</Button>
            </div>
        )
    }
    if (configId && (loading || !config)) {
        return (
            <Skeleton className="h-96 w-full"/>
        )
    }
    return (
        <div className="space-y-5">
            {!configId && (
                <div className="space-y-2">
                    <Label htmlFor="config-file-name">Config ID</Label>
                    <div className="flex">
                        <Input ref={fileNameInput} id="config-file-name" className="rounded-r-none" disabled={!!configId} placeholder="Enter file name" value={fileName}
                               aria-invalid={!!fileNameError} aria-describedby={fileNameError ? 'config-file-name-error' : undefined}
                               onChange={(e) => {
                            setFileName(e.target.value);
                            if (fileNameError) setFileNameError('');
                        }}/>
                        <ConfigFormatSelector disabled={!!configId}
                                              value={fileExt}
                                              onChange={(value) => {
                                                  setFileExt(value)
                                              }}
                                              defaultValue="yaml"
                                              className="w-40"
                                              triggerClassName="rounded-l-none"/>
                    </div>
                    {fileNameError && <p id="config-file-name-error" role="alert" className="text-sm text-destructive">{fileNameError}</p>}
                </div>
            )}
            {config && (
                <DefinitionList items={[
                    {label: 'File Name', value: config.configId},
                    {label: 'Hash', value: config.hash},
                    {label: 'Last Update Time', value: dayjs(config.createTime * 1000).format('YYYY-MM-DD HH:mm:ss')},
                    {label: 'Version', value: config.version},
                ]}/>
            )}
            <div className="flex items-center gap-3"><Separator className="flex-1"/><span className="text-xs font-medium uppercase tracking-wider text-muted-foreground">Config Data</span><Separator className="flex-1"/></div>
            <Editor
                height="60vh"
                theme="vs-dark"
                language={fileExt}
                value={configData}
                onChange={value => setConfigData(value ?? '')}
                options={{
                    ariaLabel: 'Config data editor',
                    minimap: {enabled: false},
                }}
            />
            <Separator/>
            <div className="flex gap-2">
                <Button onClick={handleSubmit} loading={loadingSave}>Submit</Button>
                <Button variant="outline" onClick={onCancel}>Cancel</Button>
            </div>
        </div>

    );
};
