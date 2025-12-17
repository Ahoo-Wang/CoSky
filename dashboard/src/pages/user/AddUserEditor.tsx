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

import {Form, Input, Button, Space, Select, App} from 'antd';
import {useExecutePromise} from "@ahoo-wang/fetcher-react";
import {userApiClient} from "../../services/clients.ts";

export interface UserFormValues {
    username: string;
    password: string;
    roles: string[];
}

interface UserFormProps {
    roleSelectorOptions: { label: string, value: string }[];
    onSuccess: () => void;
    onCancel: () => void;
}

export function AddUserEditor({roleSelectorOptions, onSuccess, onCancel}: UserFormProps) {
    const {message} = App.useApp()
    const [form] = Form.useForm();
    const {loading: addUserLoading, execute: addUser} = useExecutePromise({
        onSuccess: () => {
            message.success('Add user success!');
        },
        onError: () => {
            message.error('Failed to add user');
        }
    })
    const {loading: bindRoleLoading, execute: bindRole} = useExecutePromise({
        onSuccess: () => {
            message.success('Bind role success!');
        },
        onError: () => {
            message.error('Failed to bind role');
        }
    })
    const handleFinish = async (values: UserFormValues) => {
        await addUser(() => {
            return userApiClient.addUser(values.username, {body: values})
        })
        await bindRole(() => {
            return userApiClient.bindRole(values.username, {body: values.roles})
        })
        onSuccess();
        form.resetFields();
    };
    return (
        <Form form={form} layout="vertical" onFinish={handleFinish}>
            <Form.Item
                name="username"
                label="Username"
                rules={[{required: true, message: 'Please input username!'}]}
            >
                <Input/>
            </Form.Item>
            <Form.Item
                name="password"
                label="Password"
                rules={[{required: true, message: 'Please input password!'}]}
            >
                <Input.Password/>
            </Form.Item>
            <Form.Item name="roles" label="Roles">
                <Select mode="multiple" placeholder="Select Roles" options={roleSelectorOptions}/>
            </Form.Item>
            <Form.Item>
                <Space>
                    <Button type="primary" htmlType="submit" loading={addUserLoading || bindRoleLoading}>
                        Submit
                    </Button>
                    <Button onClick={onCancel}>
                        Cancel
                    </Button>
                </Space>
            </Form.Item>
        </Form>
    );
}
