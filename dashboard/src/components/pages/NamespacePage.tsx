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
import {Table, Button, Input, Modal, Form, message, Popconfirm} from 'antd';
import {PlusOutlined, DeleteOutlined} from '@ant-design/icons';
import {NamespaceApiClient} from '../../generated';
import {useNamespaces} from "../../hooks/useNamespaces.ts";

const namespaceApiClient = new NamespaceApiClient();

export const NamespacePage: React.FC = () => {
    const {namespaces, loading, reload} = useNamespaces();
    const [modalVisible, setModalVisible] = useState(false);
    const [form] = Form.useForm();

    const handleAdd = async (values: { namespace: string }) => {
        try {
            await namespaceApiClient.setNamespace(values.namespace);
            message.success('Namespace added successfully');
            setModalVisible(false);
            form.resetFields();
            reload();
        } catch (error) {
            console.error('Failed to add namespace:', error);
            message.error('Failed to add namespace');
        }
    };

    const handleDelete = async (namespace: string) => {
        try {
            await namespaceApiClient.removeNamespace(namespace);
            message.success('Namespace deleted successfully');
            reload();
        } catch (error) {
            console.error('Failed to delete namespace:', error);
            message.error('Failed to delete namespace');
        }
    };

    const columns = [
        {
            title: 'Namespace',
            dataIndex: 'namespace',
            key: 'namespace',
        },
        {
            title: 'Action',
            key: 'action',
            render: (_: any, record: { namespace: string }) => (
                <Popconfirm
                    title="Are you sure to delete this namespace?"
                    onConfirm={() => handleDelete(record.namespace)}
                    okText="Yes"
                    cancelText="No"
                >
                    <Button type="link" danger icon={<DeleteOutlined/>}>
                        Delete
                    </Button>
                </Popconfirm>
            ),
        },
    ];

    const dataSource = namespaces.map((ns) => ({namespace: ns, key: ns}));

    return (
        <div>
            <div style={{marginBottom: 16, display: 'flex', justifyContent: 'space-between'}}>
                <h2>Namespace</h2>
                <Button type="primary" icon={<PlusOutlined/>} onClick={() => setModalVisible(true)}>
                    Add Namespace
                </Button>
            </div>
            <Table columns={columns} dataSource={dataSource} loading={loading}/>

            <Modal
                title="Add Namespace"
                open={modalVisible}
                onCancel={() => {
                    setModalVisible(false);
                    form.resetFields();
                }}
                onOk={() => form.submit()}
            >
                <Form form={form} layout="vertical" onFinish={handleAdd}>
                    <Form.Item
                        name="namespace"
                        label="Namespace"
                        rules={[{required: true, message: 'Please input namespace!'}]}
                    >
                        <Input placeholder="Enter namespace"/>
                    </Form.Item>
                </Form>
            </Modal>
        </div>
    );
};
