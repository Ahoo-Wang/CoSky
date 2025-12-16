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

import {Form, Input, Button, message} from 'antd';
import {useExecutePromise} from "@ahoo-wang/fetcher-react";
import {namespaceApiClient} from "../../services/clients.ts";
import {PlusOutlined} from "@ant-design/icons";

interface NamespaceFormProps {
    onSubmit: (namespace: string) => void;
}

export function AddNamespaceForm({onSubmit}: NamespaceFormProps) {
    const [form] = Form.useForm();
    const {loading, execute: addNamespace} = useExecutePromise<string>({
        onSuccess: (namespace) => {
            message.success('Add namespace success!');
            onSubmit(namespace);
            form.resetFields();
        },
        onError: () => {
            message.error('Failed to add namespace');
        }
    })
    const handleFinish = async (values: { namespace: string }) => {
        await addNamespace(async () => {
            await namespaceApiClient.setNamespace(values.namespace)
            return values.namespace;
        })
    };

    return (
        <Form form={form} layout="inline" onFinish={handleFinish}>
            <Form.Item
                name="namespace"
                rules={[{required: true, message: 'Please input namespace!'}]}
            >
                <Input placeholder="Enter namespace"/>
            </Form.Item>
            <Form.Item>
                <Button type="primary" htmlType="submit" loading={loading} icon={<PlusOutlined/>}>
                    Add Namespace
                </Button>
            </Form.Item>
        </Form>
    );
}
