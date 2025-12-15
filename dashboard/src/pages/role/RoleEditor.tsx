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
import {Form, Input, Button, Space, Divider, message} from 'antd';
import {ResourceActionDto, RoleDto, SaveRoleRequest} from "../../generated";
import {useExecutePromise, useQuery} from "@ahoo-wang/fetcher-react";
import {roleApiClient} from "../../services/clients.ts";
import {MinusCircleOutlined, PlusOutlined} from "@ant-design/icons";
import {NamespaceSelector} from "../../components/namespace/NamespaceSelector.tsx";
import {ResourceActionSelector} from "./ResourceActionSelector.tsx";

interface RoleEditorProps {
    initialValues?: RoleDto;
    onSubmit: (values: RoleDto) => void;
    onCancel: () => void;
}

export interface RoleEditorFormValues extends RoleDto, SaveRoleRequest {

}

export const EMPTY_ARRAY = []
export const RoleEditor: React.FC<RoleEditorProps> = ({initialValues, onSubmit, onCancel}) => {
    const {result = EMPTY_ARRAY} = useQuery<string, ResourceActionDto[]>({
        initialQuery: initialValues?.name,
        execute: (query, attributes, abortController) => {
            return roleApiClient.getResourceBind(query, attributes, abortController);
        }
    })
    const {loading: saveLoading, execute: save} = useExecutePromise({
        onSuccess: () => {
            message.success('Save role success!');
        },
        onError: () => {
            message.error('Failed to save role');
        }
    })
    const handleFinish = async (values: RoleEditorFormValues) => {
        await save(() => {
            return roleApiClient.saveRole(values.name, {
                body: values
            })
        })
        onSubmit(values);
        form.resetFields();
    };
    const [form] = Form.useForm<RoleEditorFormValues>();

    useEffect(() => {
        if (initialValues) {
            const formValues = {
                name: initialValues.name,
                desc: initialValues.desc ?? "",
                resourceActionBind: result
            }
            form.setFieldsValue(formValues);
        } else {
            form.resetFields();
        }
    }, [initialValues, form, result]);


    return (
        <Form form={form} layout="vertical" onFinish={handleFinish}>
            <Form.Item
                name="name"
                label="Role Name"
                rules={[{required: true, message: 'Please input role name!'}]}
            >
                <Input disabled={initialValues !== undefined}/>
            </Form.Item>
            <Form.Item name="desc" label="Description"
                       rules={[{required: true, message: 'Please input role description!'}]}>
                <Input.TextArea rows={4}/>
            </Form.Item>
            <Divider>Resource Bind</Divider>
            <Form.List name="resourceActionBind">
                {(fields, {add, remove}) => (
                    <>
                        {fields.map(({key, name, ...restField}) => (
                            <Space key={key} style={{display: 'flex', marginBottom: 8}} align="baseline">
                                <Form.Item
                                    {...restField}
                                    name={[name, 'namespace']}
                                    rules={[{required: true, message: 'Missing namespace'}]}
                                >
                                    <NamespaceSelector style={{minWidth: '200px'}}></NamespaceSelector>
                                </Form.Item>
                                <Form.Item
                                    {...restField}
                                    name={[name, 'action']}
                                    rules={[{required: true, message: 'Missing action'}]}
                                >
                                    <ResourceActionSelector style={{minWidth: '200px'}}/>
                                </Form.Item>
                                <MinusCircleOutlined onClick={() => remove(name)}/>
                            </Space>
                        ))}
                        <Form.Item>
                            <Button type="dashed" onClick={() => add()} block icon={<PlusOutlined/>}>
                                Add permissions
                            </Button>
                        </Form.Item>
                    </>
                )}
            </Form.List>
            <Form.Item>
                <Space>
                    <Button type="primary" htmlType="submit" loading={saveLoading}>
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
