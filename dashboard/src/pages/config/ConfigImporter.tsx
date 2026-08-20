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

import React, {useCallback, useState} from 'react';
import {useExecutePromise} from "@ahoo-wang/fetcher-react";
import {configApiClient} from "../../services/clients.ts";
import {UploadCloud} from "lucide-react";
import {ImportPolicySelector} from "./ImportPolicySelector.tsx";
import type {ImportResponse} from "../../generated";
import {toast} from 'sonner';
import {Button} from '@/components/ui/button';
import {Label} from '@/components/ui/label';

interface ConfigImporterProps {
    namespace: string;
    onSuccess: () => void;
    onCancel: () => void;
}

const ACCEPT = '.zip,application/zip';

export const ConfigImporter: React.FC<ConfigImporterProps> = ({namespace, onSuccess, onCancel}) => {
    const [policy, setPolicy] = useState('skip');
    const [file, setFile] = useState<File>();
    const [dragOver, setDragOver] = useState(false);
    const {loading, execute} = useExecutePromise<ImportResponse>({
        onSuccess: (result) => {
            toast.success(`Total: ${result.total}, succeeded: ${result.succeeded}.`);
            onSuccess();
        },
        onError: () => {
            toast.error('Import config failed');
        },
    });
    const handleFinish = () => {
        if (!file) {
            toast.error('Please select a ZIP file.');
            return;
        }
        const formData = new FormData();
        formData.append('policy', policy);
        formData.append('importZip', file);
        execute(() => {
            return configApiClient.importZip(namespace, {
                body: formData
            })
        });
    };
    const onDrop = useCallback((event: React.DragEvent<HTMLLabelElement>) => {
        event.preventDefault();
        setDragOver(false);
        const dropped = event.dataTransfer.files?.[0];
        if (dropped && /\.zip$/i.test(dropped.name)) {
            setFile(dropped);
        } else if (dropped) {
            toast.error('Please select a ZIP file.');
        }
    }, []);
    return (
        <form className="space-y-5" onSubmit={(event) => {event.preventDefault(); handleFinish();}}>
            <div className="space-y-2">
                <Label>Import Policy</Label>
                <ImportPolicySelector value={policy} onChange={setPolicy}/>
            </div>
            <Label
                htmlFor="import-zip-input"
                onDragOver={event => {
                    event.preventDefault();
                    setDragOver(true);
                }}
                onDragLeave={() => setDragOver(false)}
                onDrop={onDrop}
                className={[
                    'flex min-h-48 cursor-pointer flex-col items-center justify-center rounded-xl border border-dashed bg-muted/25 px-6 text-center transition-colors',
                    'hover:border-primary hover:bg-primary/5',
                    dragOver && 'border-primary bg-primary/10',
                ].join(' ')}
                aria-label="Choose or drop a ZIP file"
            >
                <UploadCloud className="mb-3 size-9 text-primary"/>
                <span className="font-medium">{file?.name ?? 'Choose a ZIP file'}</span>
                <span className="mt-1 text-xs font-normal text-muted-foreground">
                    {file ? 'ZIP ready to upload' : 'Click to choose or drop a Nacos ZIP'}
                </span>
                <input
                    id="import-zip-input"
                    type="file"
                    accept={ACCEPT}
                    className="sr-only"
                    onChange={event => setFile(event.target.files?.[0])}
                />
            </Label>
            <div className="flex gap-2">
                <Button type="submit" loading={loading}>Submit</Button>
                <Button type="button" variant="outline" onClick={onCancel}>Cancel</Button>
            </div>
        </form>
    );
};