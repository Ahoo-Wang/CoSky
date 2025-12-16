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

import {Table, Button, Space, message, Popconfirm} from 'antd';
import {DeleteOutlined, AppstoreAddOutlined} from '@ant-design/icons';
import {useNamespaceContext} from '../../contexts/NamespaceContext.tsx';
import {useQuery} from '@ahoo-wang/fetcher-react';
import {serviceApiClient} from "../../services/clients.ts";
import {ServiceStat} from "../../generated";
import {ServiceInstanceTable} from "./ServiceInstanceTable.tsx";
import {AddServiceForm} from "./AddServiceForm.tsx";
import {ServiceInstanceEditor} from "./ServiceInstanceEditor.tsx";
import {useDrawer} from "../../contexts/DrawerContext.tsx";

export function ServicePage() {
    const {currentNamespace} = useNamespaceContext();
    const {result: services = [], loading, execute: loadServices} = useQuery<string, ServiceStat[]>({
        query: currentNamespace,
        execute: (namespace, _, abortController) => {
            return serviceApiClient.getServiceStats(namespace, {abortController});
        },
    });

    const {openDrawer, closeDrawer} = useDrawer();

    const handleDeleteService = async (serviceId: string) => {
        try {
            await serviceApiClient.removeService(currentNamespace, serviceId);
            message.success('Service deleted successfully');
            loadServices();
        } catch (error) {
            message.error('Failed to delete service');
        }
    };

    const handleAddInstance = (serviceId: string) => {
        openDrawer(
            <ServiceInstanceEditor
                namespace={currentNamespace}
                serviceId={serviceId}
                onSubmit={closeDrawer}
                onCancel={closeDrawer}
            />,
            {
                title: `Add [${serviceId}] Instance`,
                width: 500,
            }
        );
    };


    const expandedRowRender = (record: ServiceStat) => {
        return (
            <ServiceInstanceTable namespace={currentNamespace} serviceId={record.serviceId}/>
        );
    };

    const columns = [
        {
            title: 'Service ID',
            dataIndex: 'serviceId',
            key: 'serviceId'
        },
        {
            title: 'Instance Count',
            dataIndex: 'instanceCount',
            key: 'instanceCount',
            sorter: (a: ServiceStat, b: ServiceStat) => a.instanceCount - b.instanceCount,
        },
        {
            title: 'Action',
            key: 'action',
            render: (_: any, record: ServiceStat) => (
                <Space>
                    <Button
                        type="primary"
                        icon={<AppstoreAddOutlined/>}
                        onClick={() => handleAddInstance(record.serviceId)}
                    >
                        Add instance
                    </Button>
                    <Popconfirm
                        title="Are you sure to delete this service?"
                        onConfirm={() => handleDeleteService(record.serviceId)}
                        okText="Yes"
                        cancelText="No"
                    >
                        <Button type="link" danger icon={<DeleteOutlined/>}>
                            Delete
                        </Button>
                    </Popconfirm>
                </Space>
            ),
        },
    ];

    return (
        <div>
            <div style={{marginBottom: 16, display: 'flex', justifyContent: 'space-between'}}>
                <h2>Service</h2>
                <AddServiceForm namespace={currentNamespace} onSubmit={loadServices}/>
            </div>
            <Table
                columns={columns}
                dataSource={services}
                loading={loading}
                rowKey='serviceId'
                expandable={{
                    expandedRowRender,
                    rowExpandable: (record) => record.instanceCount > 0,
                }}
            />
        </div>
    );
}
