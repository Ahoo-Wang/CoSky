import {useExecutePromise, useQuery} from "@ahoo-wang/fetcher-react";
import {configApiClient} from "../../services/clients.ts";
import type {Config, ConfigHistory} from "../../generated";
import {DiffEditor} from "@monaco-editor/react";
import {getFileNameWithExt} from "./fileNames.ts";
import dayjs from "dayjs";
import {toast} from 'sonner';
import {ConfirmButton} from '@/components/ui/confirm-button';
import {Badge} from '@/components/ui/badge';
import {DefinitionList} from '@/components/ui/definition-list';
import {Separator} from '@/components/ui/separator';
import {Skeleton} from '@/components/ui/skeleton';

export interface ConfigVersionDifferProps {
    namespace: string;
    configId: string;
    version: number;
    onSuccess: () => void
}

export function ConfigVersionDiffer({namespace, configId, version, onSuccess}: ConfigVersionDifferProps) {
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
            onSuccess()
        },
        onError: () => {
            toast.error('Rollback failed');
        }
    })

    const handleRollback = async () => {
        await rollback(() => {
            return configApiClient.rollback(namespace, configId, version)
        });
    }
    const isCurrent = versionConfig?.version !== undefined && versionConfig.version === currentConfig?.version;
    const fileNameWithExt = getFileNameWithExt(configId);
    if (currentLoading || versionLoading) {
        return (
            <Skeleton className="h-96 w-full"/>
        )
    }
    return (
        <div className="space-y-5">
            <DefinitionList items={[
                {label: 'File Name', value: configId},
                {label: 'Hash', value: versionConfig?.hash},
                {
                    label: 'History Version',
                    value: (
                        <span className="inline-flex items-center gap-2">
                            {versionConfig?.version}
                            {isCurrent && <Badge variant="secondary">Current</Badge>}
                        </span>
                    ),
                },
                {label: 'Operation', value: versionConfig?.op},
                {label: 'Create Time', value: dayjs((versionConfig?.createTime ?? 0) * 1000).format('YYYY-MM-DD HH:mm:ss')},
                {label: 'Operation Time', value: dayjs((versionConfig?.opTime ?? 0) * 1000).format('YYYY-MM-DD HH:mm:ss')},
            ]}/>
            <div className="flex items-center gap-3"><Separator className="flex-1"/><span className="text-xs font-medium uppercase tracking-wider text-muted-foreground">History ({version}) vs Current ({currentConfig?.version})</span><Separator className="flex-1"/></div>
            <DiffEditor
                key={`diff-${namespace}-${configId}-${version}`}
                height="60vh"
                theme="vs-dark"
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
            {isCurrent && (
                <p className="text-center text-xs text-muted-foreground">This version is currently active — there is nothing to roll back.</p>
            )}
            <ConfirmButton title="Rollback to this version?"
                        description={`Configuration “${configId}” will change from version ${currentConfig?.version} to history version ${version}. A new audit event will be recorded.`}
                        onConfirm={handleRollback}
                        loading={rollbackLoading}
                        disabled={isCurrent}
                        className="w-full"
            >
                Rollback to version {version}
            </ConfirmButton>

        </div>
    )
}
