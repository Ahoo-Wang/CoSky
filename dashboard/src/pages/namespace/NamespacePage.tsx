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

import {Table, Button, Popconfirm, App} from 'antd';
import {DeleteOutlined} from '@ant-design/icons';
import {isSystemNamespace} from "./namespaces.ts";
import {namespaceApiClient} from "../../services/clients.ts";
import {AddNamespaceForm} from "./AddNamespaceForm.tsx";
import {useNamespacesContext} from "../../contexts/namespace/NamespacesContext.tsx";
import {useCurrentNamespaceContext} from "../../contexts/namespace/CurrentNamespaceContext.tsx";

export function NamespacePage() {
    const {message} = App.useApp()
    const {currentNamespace} = useCurrentNamespaceContext()
    const {namespaces, loading, refresh} = useNamespacesContext();

    const handleDelete = async (namespace: string) => {
        try {
            await namespaceApiClient.removeNamespace(namespace);
            message.success('Namespace deleted successfully');
            refresh();
        } catch (error) {
            message.error('Failed to delete namespace');
        }
    };

    const isDisabled = (namespace: string) => {
        return isSystemNamespace(namespace) || currentNamespace === namespace;
    };

    const columns = [
        {
            title: 'Namespace',
            key: 'namespace',
        },
        {
            title: 'Action',
            key: 'action',
            render: (_: string, record: string) => (
                <Popconfirm
                    title="Are you sure to delete this namespace?"
                    onConfirm={() => handleDelete(record)}
                    okText="Yes"
                    cancelText="No"
                >
                    <Button type="link" danger icon={<DeleteOutlined/>} disabled={isDisabled(record)}>
                        Delete
                    </Button>
                </Popconfirm>
            ),
        },
    ];


    return (
        <div>
            <div style={{
                marginBottom: 24,
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
            }}>
                <h2 style={{
                    margin: 0,
                    fontSize: '28px',
                    fontWeight: 600,
                    color: '#262626',
                    letterSpacing: '-0.5px',
                }}>Namespace</h2>
                <AddNamespaceForm onSuccess={refresh}/>
            </div>
            <Table
                columns={columns}
                dataSource={namespaces}
                loading={loading}
                rowKey={(record) => record}
                style={{
                    background: '#fff',
                    borderRadius: 12,
                    overflow: 'hidden',
                    boxShadow: '0 2px 8px rgba(0, 0, 0, 0.08)',
                }}
            />
        </div>
    );
};
