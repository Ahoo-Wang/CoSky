import {useExecutePromise, useQuery} from "@ahoo-wang/fetcher-react";
import {configApiClient} from "../../services/clients.ts";
import {Config, ConfigHistory} from "../../generated";
import {Button, Descriptions, Divider, message, Popconfirm, Skeleton} from "antd";
import {DiffEditor} from "@monaco-editor/react";
import {getFileNameWithExt} from "./fileNames.ts";

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
            message.success('Rollback success');
            onSuccess()
        },
        onError: () => {
            message.error('Rollback failed')
        }
    })

    const handleRollback = async () => {
        await rollback(() => {
            return configApiClient.rollback(namespace, configId, version)
        });
    }
    const fileNameWithExt = getFileNameWithExt(configId);
    if (currentLoading || versionLoading) {
        return (
            <Skeleton/>
        )
    }
    return (
        <>
            <Descriptions>

            </Descriptions>
            <Divider>History({version}) VS Current</Divider>
            <DiffEditor
                height="500px"
                theme="vs-dark"
                language={fileNameWithExt.ext}
                original={versionConfig?.data}
                modified={currentConfig?.data}
                options={{
                    minimap: {enabled: false},
                }}
            />
            <Divider/>
            <Popconfirm title="Are you sure to rollbak to version?"
                        onConfirm={handleRollback}
            >
                <Button type={"primary"} block loading={rollbackLoading}>Rollback To Version[{version}]</Button>
            </Popconfirm>

        </>
    )
}