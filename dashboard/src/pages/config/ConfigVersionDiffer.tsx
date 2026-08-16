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
import dayjs from 'dayjs';
import {DiffEditor} from '@monaco-editor/react';
import {toast} from 'sonner';
import {useExecutePromise, useQuery} from '@ahoo-wang/fetcher-react';
import {Button} from '@/components/ui/button';
import {Skeleton} from '@/components/ui/skeleton';
import {Separator} from '@/components/ui/separator';
import {ConfirmDialog} from '@/components/feedback/ConfirmDialog';
import {DescriptionList} from '@/components/feedback/DescriptionList';
import {configApiClient} from '@/services/clients';
import {useTheme} from '@/theme/ThemeProvider';
import type {Config, ConfigHistory} from '@/generated';
import {getFileNameWithExt} from './fileNames';

export interface ConfigVersionDifferProps {
    namespace: string;
    configId: string;
    version: number;
    onSuccess: () => void;
}

export function ConfigVersionDiffer({namespace, configId, version, onSuccess}: ConfigVersionDifferProps) {
    const {resolvedTheme} = useTheme();
    const {loading: currentLoading, result: currentConfig} = useQuery<string, Config>({
        query: configId,
        execute: (configId, _, abortController) => {
            return configApiClient.getConfig(namespace, configId, {abortController});
        },
    });
    const {loading: versionLoading, result: versionConfig} = useQuery<string, ConfigHistory>({
        query: configId,
        execute: (configId, _, abortController) => {
            return configApiClient.getConfigHistory(namespace, configId, version, {abortController});
        },
    });
    const {loading: rollbackLoading, execute: rollback} = useExecutePromise({
        onSuccess: () => {
            toast.success('Rollback success');
            onSuccess();
        },
        onError: () => {
            toast.error('Rollback failed');
        },
    });
    const [confirmOpen, setConfirmOpen] = useState(false);

    const handleRollback = async () => {
        await rollback(() => {
            return configApiClient.rollback(namespace, configId, version);
        });
    };

    const fileNameWithExt = getFileNameWithExt(configId);
    if (currentLoading || versionLoading) {
        return (
            <Skeleton className="h-64 w-full"/>
        );
    }
    return (
        <div className="space-y-4">
            <DescriptionList items={[
                {label: 'File Name', value: configId},
                {label: 'Hash', value: versionConfig?.hash},
                {label: 'History Version', value: versionConfig?.version},
                {label: 'Operation', value: versionConfig?.op},
                {
                    label: 'Create Time',
                    value: dayjs((versionConfig?.createTime ?? 0) * 1000).format('YYYY-MM-DD HH:mm:ss'),
                },
                {
                    label: 'Operation Time',
                    value: dayjs((versionConfig?.opTime ?? 0) * 1000).format('YYYY-MM-DD HH:mm:ss'),
                },
            ]}/>
            <div className="flex items-center gap-4 py-2">
                <Separator className="flex-1"/>
                <span className="text-sm font-medium text-muted-foreground">
                    History({version}) VS Current({currentConfig?.version})
                </span>
                <Separator className="flex-1"/>
            </div>
            <DiffEditor
                key={`diff-${namespace}-${configId}-${version}`}
                height="60vh"
                theme={resolvedTheme === 'dark' ? 'vs-dark' : 'vs'}
                language={fileNameWithExt.ext}
                original={versionConfig?.data || ''}
                modified={currentConfig?.data || ''}
                keepCurrentOriginalModel
                keepCurrentModifiedModel
                options={{
                    readOnly: true,
                    minimap: {enabled: false},
                }}
            />
            <Separator/>
            <Button className="w-full" disabled={rollbackLoading} onClick={() => setConfirmOpen(true)}>
                Rollback To Version[{version}]
            </Button>
            <ConfirmDialog
                open={confirmOpen}
                onOpenChange={setConfirmOpen}
                title="Are you sure to rollbak to version?"
                onConfirm={() => {
                    setConfirmOpen(false);
                    void handleRollback();
                }}
            />
        </div>
    );
}
