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

import React, {useEffect} from 'react';
import {useExecutePromise} from "@ahoo-wang/fetcher-react";
import {App, Button, Form, Space} from "antd";
import {configApiClient} from "../../services/clients.ts";
import Dragger from "antd/es/upload/Dragger";
import {InboxOutlined} from "@ant-design/icons";
import {ImportPolicySelector} from "./ImportPolicySelector.tsx";
import {UploadChangeParam} from "antd/es/upload/interface";
import {ImportResponse} from "../../generated";

interface ConfigImporterProps {
    namespace: string;
    onSuccess: () => void;
    onCancel: () => void;
}

export const ConfigImporter: React.FC<ConfigImporterProps> = ({namespace, onSuccess, onCancel}) => {
    const {message} = App.useApp()
    const [form] = Form.useForm();
    const {loading, execute} = useExecutePromise<ImportResponse>({
        onSuccess: (result) => {
            message.success(`ToTal : ${result.total} , Succeeded : ${result.succeeded} . `)
            onSuccess();
        },
        onError: () => {
            message.error('Import config failed')
        },
    })
    const handleFinish = (values: { policy: string; importZip: UploadChangeParam<File> }) => {
        const formData = new FormData();
        formData.append('policy', values.policy);
        formData.append('importZip', values.importZip.file);
        execute(() => {
            return configApiClient.importZip(namespace, {
                body: formData
            })
        })
    };
    useEffect(() => {
        form.setFieldValue('policy', 'skip')
    }, [form]);
    return (
        <Form form={form} onFinish={handleFinish}>
            <Form.Item name="policy" label='Import Policy'>
                <ImportPolicySelector/>
            </Form.Item>
            <Form.Item name="importZip"
                       rules={[
                           {
                               required: true,
                               message: 'Please select a file!'
                           }
                       ]}>
                <Dragger multiple={false} maxCount={1} accept=".zip" beforeUpload={() => false}>
                    <p className="ant-upload-drag-icon">
                        <InboxOutlined/>
                    </p>
                    <p className="ant-upload-text">Click or drag ZIP-file to this area to upload</p>
                </Dragger>
            </Form.Item>
            <Form.Item>
                <Space>
                    <Button type="primary" htmlType="submit" loading={loading}>
                        Submit
                    </Button>
                    <Button onClick={onCancel}>
                        Cancel
                    </Button>
                </Space>
            </Form.Item>
        </Form>
    );
};
