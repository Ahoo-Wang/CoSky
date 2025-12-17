import {useExecutePromise, useQuery} from "@ahoo-wang/fetcher-react";
import {configApiClient} from "../../services/clients.ts";
import {Config, ConfigHistory} from "../../generated";
import {App, Button, Descriptions, Divider, Popconfirm, Skeleton} from "antd";
import {DiffEditor} from "@monaco-editor/react";
import {getFileNameWithExt} from "./fileNames.ts";
import dayjs from "dayjs";

export interface ConfigVersionDifferProps {
    namespace: string;
    configId: string;
    version: number;
    onSuccess: () => void
}

export function ConfigVersionDiffer({namespace, configId, version, onSuccess}: ConfigVersionDifferProps) {
    const {message} = App.useApp()
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
            <Descriptions bordered>
                <Descriptions.Item label={"File Name"} span="filled">{configId}</Descriptions.Item>
                <Descriptions.Item label={"Hash"} span="filled">{versionConfig?.hash}</Descriptions.Item>
                <Descriptions.Item
                    label={"History Version"}>{versionConfig?.version}</Descriptions.Item>
                <Descriptions.Item
                    label={"Operation"}>{versionConfig?.op}</Descriptions.Item>
                <Descriptions.Item
                    label={"Create Time"}>{dayjs((versionConfig?.createTime ?? 0) * 1000).format('YYYY-MM-DD HH:mm:ss')}</Descriptions.Item>
                <Descriptions.Item
                    label={"Operation Time"}>{dayjs((versionConfig?.opTime ?? 0) * 1000).format('YYYY-MM-DD HH:mm:ss')}</Descriptions.Item>
            </Descriptions>
            <Divider>History({version}) VS Current({currentConfig?.version})</Divider>
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
            <Divider/>
            <Popconfirm title="Are you sure to rollbak to version?"
                        onConfirm={handleRollback}
            >
                <Button type={"primary"} block loading={rollbackLoading}>Rollback To Version[{version}]</Button>
            </Popconfirm>

        </>
    )
}