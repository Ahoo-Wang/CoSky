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

import React, {useState} from 'react';
import {Button, Descriptions, Divider, Input, message, Skeleton, Space} from 'antd';
import Editor from '@monaco-editor/react';
import {ConfigFormatSelector} from "./ConfigFormatSelector.tsx";
import {useExecutePromise, useQuery} from "@ahoo-wang/fetcher-react";
import {configApiClient} from "../../services/clients.ts";
import {getFileNameWithExt, getFullFileName} from "./fileNames.ts";
import dayjs from "dayjs";
import {Config} from "../../generated";

interface ConfigEditFormProps {
    namespace: string;
    configId?: string;
    onSuccess: () => void;
    onCancel: () => void;
}

export const ConfigEditor: React.FC<ConfigEditFormProps> = ({namespace, configId, onSuccess, onCancel}) => {
    const fileNameWithExt = getFileNameWithExt(configId ?? '.yaml');
    const [fileName, setFileName] = useState<string>(fileNameWithExt.name);
    const [fileExt, setFileExt] = useState<string>(fileNameWithExt.ext);
    const [configData, setConfigData] = useState<string>();
    const {loading, result: config} = useQuery<string, Config>({
        query: configId,
        execute: (query, attributes, abortController) => {
            return configApiClient.getConfig(namespace, query, attributes, abortController);
        },
        onSuccess: (config) => {
            setConfigData(config.data)
        }
    })
    const {loading: loadingSave, execute: saveConfig} = useExecutePromise({
        propagateError: true,
        onSuccess: () => {
            message.success('Config saved successfully');
            onSuccess();
        },
        onError: () => {
            message.error('Config save failed');
        }
    })

    const handleSubmit = () => {
        if (!fileName) {
            message.error('Please enter file name!')
            return;
        }
        const fullFileName = getFullFileName(fileName, fileExt)
        saveConfig(() => {
            return configApiClient.setConfig(namespace, fullFileName, {
                body: configData
            })
        })
    }
    if (configId && loading) {
        return (
            <Skeleton/>
        )
    }
    return (
        <>
            {!configId && (
                <Space.Compact block>
                    <Input disabled={!!configId} placeholder="Enter file name!" value={fileName} onChange={(e) => {
                        setFileName(e.target.value);
                    }}/>
                    <ConfigFormatSelector disabled={!!configId}
                                          value={fileExt}
                                          onChange={(value) => {
                                              setFileExt(value)
                                          }}
                                          defaultValue={'yaml'}
                                          style={{width: 150}}/>
                </Space.Compact>
            )}
            {config && (
                <Descriptions bordered>
                    <Descriptions.Item label="File Name" span={24}>{config.configId}</Descriptions.Item>
                    <Descriptions.Item label="Hash" span={24}>{config.hash}</Descriptions.Item>
                    <Descriptions.Item
                        label="Last Update Time">{dayjs(config.createTime * 1000).format('YYYY-MM-DD HH:mm:ss')}</Descriptions.Item>
                    <Descriptions.Item label="Version">{config.version}</Descriptions.Item>
                </Descriptions>
            )}
            <Divider>Config Data</Divider>
            <Editor
                height="60vh"
                theme="vs-dark"
                language={fileExt}
                value={configData}
                onChange={setConfigData}
                options={{
                    minimap: {enabled: false},
                }}
            />
            <Divider></Divider>
            <Space>
                <Button type="primary"
                        onClick={handleSubmit}
                        loading={loadingSave}
                >
                    Submit
                </Button>
                <Button onClick={onCancel}>
                    Cancel
                </Button>
            </Space>
        </>

    );
};
