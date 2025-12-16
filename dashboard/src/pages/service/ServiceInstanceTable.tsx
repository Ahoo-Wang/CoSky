import {useExecutePromise, useQuery} from "@ahoo-wang/fetcher-react";
import {serviceApiClient} from "../../services/clients.ts";
import {Button, message, Popconfirm, Table} from "antd";
import {ServiceInstance} from "../../generated";
import {DeleteOutlined, EditOutlined} from "@ant-design/icons";
import dayjs from "dayjs";
import {useDrawer} from "../../contexts/DrawerContext.tsx";
import {ServiceInstanceEditor} from "./ServiceInstanceEditor.tsx";

export interface ServiceInstanceTableProps {
    namespace: string
    serviceId: string
}

export function ServiceInstanceTable(props: ServiceInstanceTableProps) {
    const {result: instances = [], loading: loadingInstances, execute: loadInstances} = useQuery({
        query: props,
        execute: (namespacedServiceId, _, abortController) => {
            return serviceApiClient.getInstances(namespacedServiceId.namespace, namespacedServiceId.serviceId, {abortController});
        }
    })
    const {loading: loadingExecutePromise, execute} = useExecutePromise({
        onSuccess: async () => {
            message.success('Delete instance success!');
            await loadInstances();
        },
        onError: () => {
            message.error('Delete instance failed!');
        }
    })
    const {openDrawer, closeDrawer} = useDrawer();
    const handleEditInstance = (serviceInstance: ServiceInstance) => {
        openDrawer(
            <ServiceInstanceEditor
                namespace={props.namespace}
                serviceId={props.serviceId}
                initialValues={serviceInstance}
                onSuccess={closeDrawer}
                onCancel={closeDrawer}
            />,
            {
                title: 'Edit Instance',
            }
        );
    }
    const handleDeleteInstance = async (serviceId: string, instanceId: string) => {
        await execute(() => {
            return serviceApiClient.deregister(props.namespace, serviceId, instanceId)
        })
    }
    const columns = [
        {title: 'Schema', dataIndex: 'schema', key: 'schema'},
        {title: 'Host', dataIndex: 'host', key: 'host'},
        {title: 'Port', dataIndex: 'port', key: 'port'},
        {title: 'Weight', dataIndex: 'weight', key: 'weight'},
        {
            title: 'Ephemeral',
            dataIndex: 'isEphemeral',
            key: 'isEphemeral',
            render: (isEphemeral: boolean) => isEphemeral ? 'true' : 'false'
        },
        {
            title: 'TtlAt',
            dataIndex: 'ttlAt',
            key: 'ttlAt',
            render: (ttlAt: number) => dayjs(ttlAt * 1000).format('YYYY-MM-DD HH:mm:ss')
        },
        {
            title: 'Metadata',
            dataIndex: 'metadata',
            key: 'metadata',
            render: (metadata: Record<string, string>) => JSON.stringify(metadata)
        },
        {
            title: 'Action',
            key: 'action',
            render: (_: any, record: ServiceInstance) => (
                <>
                    <Button
                        type="link"
                        icon={<EditOutlined/>}
                        onClick={() => handleEditInstance(record)}
                    >
                        Edit
                    </Button>
                    <Popconfirm
                        title="Are you sure to delete this instance?"
                        onConfirm={() => handleDeleteInstance(record.serviceId, record.instanceId)}
                        okText="Yes"
                        cancelText="No"
                    >
                        <Button type="link" danger icon={<DeleteOutlined/>} loading={loadingExecutePromise}>
                            Delete
                        </Button>
                    </Popconfirm>
                </>
            ),
        },
    ];

    return (
        <Table
            loading={loadingInstances}
            dataSource={instances}
            columns={columns}
            rowKey={'instanceId'}
        />
    )
}