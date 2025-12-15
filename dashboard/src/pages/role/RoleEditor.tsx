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
import {Form, Input, Button, Space} from 'antd';
import {RoleDto} from "../../generated";

interface RoleEditorProps {
    initialValues?: RoleDto;
    onSubmit: (values: RoleDto) => void;
    onCancel: () => void;
}

export const RoleEditor: React.FC<RoleEditorProps> = ({initialValues, onSubmit, onCancel}) => {
    const [form] = Form.useForm<RoleDto>();

    useEffect(() => {
        if (initialValues) {
            form.setFieldsValue(initialValues);
        } else {
            form.resetFields();
        }
    }, [initialValues, form]);

    const handleFinish = async (values: RoleDto) => {
        onSubmit(values);
        form.resetFields();
    };

    return (
        <Form form={form} layout="vertical" onFinish={handleFinish}>
            <Form.Item
                name="name"
                label="Role Name"
                rules={[{required: true, message: 'Please input role name!'}]}
            >
                <Input disabled={initialValues !== undefined}/>
            </Form.Item>
            <Form.Item name="desc" label="Description">
                <Input.TextArea rows={4}/>
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
    );
};
