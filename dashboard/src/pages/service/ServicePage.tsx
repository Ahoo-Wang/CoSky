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

import {useState} from 'react';
import {Table, Button, Input, Space, message, Popconfirm} from 'antd';
import {DeleteOutlined, AppstoreAddOutlined} from '@ant-design/icons';
import {useNamespaceContext} from '../../contexts/NamespaceContext.tsx';
import {useQuery} from '@ahoo-wang/fetcher-react';
import {useDrawer} from '../../contexts/DrawerContext.tsx';
import {ServiceInstanceEditor} from './ServiceInstanceEditor.tsx';
import {serviceApiClient} from "../../services/clients.ts";

export function ServicePage() {
    const {currentNamespace} = useNamespaceContext();
    const {result: services = [], loading, setQuery} = useQuery<string, any[]>({
        query: currentNamespace,
        execute: (namespace, _, abortController) => {
            return serviceApiClient.getServiceStats(namespace, {abortController});
        },
    });
    const [instances, setInstances] = useState<Record<string, any[]>>({});
    const [currentServiceId, setCurrentServiceId] = useState<string>('');
    const [expandedRowKeys, setExpandedRowKeys] = useState<string[]>([]);
    const [searchValue, setSearchValue] = useState('');
    const {openDrawer, closeDrawer} = useDrawer();

    const loadServices = () => {
        setQuery(currentNamespace);
    };

    const loadInstances = async (serviceId: string) => {
        try {
            const result = await serviceApiClient.getInstances(currentNamespace, serviceId);
            setInstances((prev) => ({...prev, [serviceId]: result || []}));
        } catch (error) {
            console.error('Failed to load instances:', error);
        }
    };

    const handleAddService = async (serviceId: string) => {
        if (serviceId && serviceId.trim()) {
            try {
                await serviceApiClient.setService(currentNamespace, serviceId);
                message.success('Service added successfully');
                loadServices();
            } catch (error) {
                console.error('Failed to add service:', error);
                message.error('Failed to add service');
            }
        }
    };

    const handleDeleteService = async (serviceId: string) => {
        try {
            await serviceApiClient.removeService(currentNamespace, serviceId);
            message.success('Service deleted successfully');
            loadServices();
        } catch (error) {
            console.error('Failed to delete service:', error);
            message.error('Failed to delete service');
        }
    };

    const handleAddInstance = (serviceId: string) => {
        setCurrentServiceId(serviceId);
        openDrawer(
            <ServiceInstanceEditor
                serviceId={serviceId}
                onSubmit={handleSubmitInstance}
                onCancel={closeDrawer}
            />,
            {
                title: 'Add Instance',
                width: 500,
            }
        );
    };

    const handleSubmitInstance = async (values: any) => {
        try {
            await serviceApiClient.register(currentNamespace, currentServiceId, {body: values});
            message.success('Instance added successfully');
            closeDrawer();
            loadInstances(currentServiceId);
        } catch (error) {
            console.error('Failed to add instance:', error);
            message.error('Failed to add instance');
        }
    };

    const handleDeleteInstance = async (serviceId: string, instanceId: string) => {
        try {
            await serviceApiClient.deregister(currentNamespace, serviceId, instanceId);
            message.success('Instance deleted successfully');
            loadInstances(serviceId);
        } catch (error) {
            console.error('Failed to delete instance:', error);
            message.error('Failed to delete instance');
        }
    };

    const handleExpand = (expanded: boolean, record: any) => {
        if (expanded) {
            loadInstances(record.serviceId);
            setExpandedRowKeys([...expandedRowKeys, record.serviceId]);
        } else {
            setExpandedRowKeys(expandedRowKeys.filter((key) => key !== record.serviceId));
        }
    };

    const instanceColumns = [
        {title: 'Instance ID', dataIndex: 'instanceId', key: 'instanceId'},
        {title: 'Host', dataIndex: 'host', key: 'host'},
        {title: 'Port', dataIndex: 'port', key: 'port'},
        {title: 'Weight', dataIndex: 'weight', key: 'weight'},
        {
            title: 'Action',
            key: 'action',
            render: (_: any, record: any) => (
                <Popconfirm
                    title="Are you sure to delete this instance?"
                    onConfirm={() => handleDeleteInstance(currentServiceId, record.instanceId)}
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

    const expandedRowRender = (record: any) => {
        const serviceInstances = instances[record.serviceId] || [];
        return (
            <Table
                columns={instanceColumns}
                dataSource={serviceInstances.map((inst: any) => ({...inst, key: inst.instanceId}))}
                pagination={false}
            />
        );
    };

    const columns = [
        {
            title: 'Service ID',
            dataIndex: 'serviceId',
            key: 'serviceId',
            filteredValue: searchValue ? [searchValue] : null,
            onFilter: (value: any, record: any) =>
                record.serviceId?.toLowerCase().includes(value.toLowerCase()),
        },
        {
            title: 'Instance Count',
            dataIndex: 'instanceCount',
            key: 'instanceCount',
            sorter: (a: any, b: any) => (a.instanceCount || 0) - (b.instanceCount || 0),
        },
        {
            title: 'Action',
            key: 'action',
            render: (_: any, record: any) => (
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

    const dataSource = services.map((service: any) => ({
        ...service,
        key: service.serviceId || service,
        serviceId: service.serviceId || service,
    }));

    return (
        <div>
            <div style={{marginBottom: 16, display: 'flex', justifyContent: 'space-between'}}>
                <h2>Service</h2>
                <Space>
                    <Input.Search
                        placeholder="Search Service ID"
                        allowClear
                        onSearch={setSearchValue}
                        style={{width: 200}}
                    />
                    <Input.Search
                        placeholder="Service ID"
                        enterButton="Add service"
                        onSearch={handleAddService}
                        style={{width: 300}}
                    />
                </Space>
            </div>
            <Table
                columns={columns}
                dataSource={dataSource}
                loading={loading}
                expandable={{
                    expandedRowRender,
                    onExpand: handleExpand,
                    expandedRowKeys,
                }}
            />
        </div>
    );
};
