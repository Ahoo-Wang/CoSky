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

import {Table, Button, message, Popconfirm} from 'antd';
import {PlusOutlined, DeleteOutlined} from '@ant-design/icons';
import {useNamespaces} from "../../hooks/useNamespaces.ts";
import {useDrawer} from '../../contexts/DrawerContext.tsx';
import {NamespaceForm} from './NamespaceForm.tsx';
import {isSystemNamespace} from "./namespaces.ts";
import {namespaceApiClient} from "../../services/clients.ts";

export function NamespacePage() {
    const {namespaces, loading, reload} = useNamespaces();
    const {openDrawer, closeDrawer} = useDrawer();

    const handleAdd = () => {
        openDrawer(
            <NamespaceForm
                onSubmit={handleSubmit}
                onCancel={closeDrawer}
            />,
            {
                title: 'Add Namespace',
                width: 500,
            }
        );
    };

    const handleSubmit = async (values: { namespace: string }) => {
        try {
            await namespaceApiClient.setNamespace(values.namespace);
            message.success('Namespace added successfully');
            closeDrawer();
            reload();
        } catch (error) {
            message.error('Failed to add namespace');
        }
    };

    const handleDelete = async (namespace: string) => {
        try {
            await namespaceApiClient.removeNamespace(namespace);
            message.success('Namespace deleted successfully');
            reload();
        } catch (error) {
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
                    <Button type="link" danger icon={<DeleteOutlined/>} disabled={isSystemNamespace(record.namespace)}>
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
                <Button type="primary" icon={<PlusOutlined/>} onClick={handleAdd}>
                    Add Namespace
                </Button>
            </div>
            <Table columns={columns} dataSource={dataSource} loading={loading}/>
        </div>
    );
};
