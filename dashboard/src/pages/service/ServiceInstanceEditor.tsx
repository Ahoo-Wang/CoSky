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

import {Form, Input, InputNumber, Button, Space, message, Switch, Divider} from 'antd';
import {ServiceInstance} from "../../generated";
import {useExecutePromise} from "@ahoo-wang/fetcher-react";
import {useEffect, useState} from "react";
import {serviceApiClient} from "../../services/clients.ts";
import Editor from "@monaco-editor/react";
import {SchemaSelector} from "./SchemaSelector.tsx";

interface ServiceInstanceFormProps {
    namespace: string;
    serviceId: string;
    initialValues?: ServiceInstance;
    onSubmit: () => void;
    onCancel: () => void;
}

const formItemLayout = {
    labelCol: {
        xs: {span: 12},
        sm: {span: 6},
    },
};

export function ServiceInstanceEditor({
                                          namespace,
                                          serviceId,
                                          initialValues,
                                          onSubmit,
                                          onCancel
                                      }: ServiceInstanceFormProps) {

    const [metadata, setMetadata] = useState(JSON.stringify(initialValues?.metadata || {}, null, 2));

    const [form] = Form.useForm();
    const {loading, execute} = useExecutePromise({
        onSuccess: () => {
            message.success('Save instance success!');
            onSubmit();
            form.resetFields();
        },
        onError: () => {
            message.error('Failed to save instance');
        }
    })

    useEffect(() => {
        if (initialValues) {
            form.setFieldsValue(initialValues);
        } else {
            form.resetFields();
        }
    }, [initialValues, form]);
    const handleFinish = async (values: any) => {
        return await execute(() => {
            return serviceApiClient.register(namespace, serviceId, {
                body: {
                    ...values,
                    metadata: JSON.parse(metadata)
                }
            })
        })
    };
    return (
        <div>
            <Form {...formItemLayout} form={form} onFinish={handleFinish}>
                <Form.Item name="schema" label="Schema"
                           rules={[{required: true, message: 'Please input schema!'}]}
                >
                    <SchemaSelector disabled={!!initialValues}/>
                </Form.Item>
                <Form.Item
                    name="host"
                    label="Host"
                    rules={[{required: true, message: 'Please input host!'}]}
                >
                    <Input disabled={!!initialValues}/>
                </Form.Item>
                <Form.Item
                    name="port"
                    label="Port"
                    rules={[{required: true, message: 'Please input port!'}]}
                >
                    <InputNumber disabled={!!initialValues}/>
                </Form.Item>

                <Form.Item name="weight" label="Weight">
                    <InputNumber disabled={!!initialValues}/>
                </Form.Item>
                <Form.Item name="isEphemeral" label="Is Ephemeral?">
                    <Switch/>
                </Form.Item>
                <Divider>Metadata</Divider>
                <Editor
                    height="500px"
                    theme="vs-dark"
                    defaultLanguage="json"
                    defaultValue={JSON.stringify(initialValues?.metadata || {}, null, 2)}
                    onChange={(value) => setMetadata(value || '{}')}
                    options={{
                        minimap: {enabled: false},
                    }}
                />
                <Divider></Divider>
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
        </div>
    );
}
