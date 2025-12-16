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

import {Form, Input, InputNumber, Button, Space} from 'antd';
import {ServiceInstance} from "../../generated";
import {useExecutePromise} from "@ahoo-wang/fetcher-react";

interface ServiceInstanceFormProps {
    serviceId: string;
    initialValues?: ServiceInstance;
    onSubmit: (values: ServiceInstance) => void;
    onCancel: () => void;
}

export function ServiceInstanceEditor({serviceId, onSubmit, onCancel}: ServiceInstanceFormProps) {
    const [form] = Form.useForm();
    const {execute} = useExecutePromise()
    const handleFinish = async (values: any) => {
        await onSubmit(values);
        form.resetFields();
    };

    return (
        <div>
            <h3 style={{marginBottom: 16}}>Add Instance to {serviceId}</h3>
            <Form form={form} layout="vertical" onFinish={handleFinish}>
                <Form.Item name="schema" label="Schema" initialValue="http">
                    <Input/>
                </Form.Item>
                <Form.Item
                    name="host"
                    label="Host"
                    rules={[{required: true, message: 'Please input host!'}]}
                >
                    <Input/>
                </Form.Item>
                <Form.Item
                    name="port"
                    label="Port"
                    rules={[{required: true, message: 'Please input port!'}]}
                >
                    <InputNumber style={{width: '100%'}}/>
                </Form.Item>
                <Form.Item name="weight" label="Weight" initialValue={1}>
                    <InputNumber style={{width: '100%'}}/>
                </Form.Item>
                <Form.Item>
                    <Space>
                        <Button type="primary" htmlType="submit">
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
};
