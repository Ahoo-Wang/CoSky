import {useExecutePromise} from "@ahoo-wang/fetcher-react";
import {Button, Form, Input, message} from "antd";
import {PlusOutlined} from "@ant-design/icons";
import {serviceApiClient} from "../../services/clients.ts";

export interface AddServiceFormProps {
    namespace: string;
    onSubmit: (serviceId: string) => void;
}

export function AddServiceForm({namespace, onSubmit}: AddServiceFormProps) {
    const [form] = Form.useForm();
    const {loading, execute} = useExecutePromise<string>({
        onSuccess: (serviceId) => {
            message.success(`Add service [${serviceId}] success!`);
            onSubmit(serviceId);
            form.resetFields();
        },
        onError: () => {
            message.error('Add service failed!');
        }
    });
    const handleFinish = async (values: { serviceId: string }) => {
        await execute(async () => {
            await serviceApiClient.setService(namespace, values.serviceId);
            return values.serviceId;
        })
    };

    return (
        <Form form={form} layout="inline" onFinish={handleFinish}>
            <Form.Item
                name="serviceId"
                rules={[{required: true, message: 'Please input serviceId!'}]}
            >
                <Input placeholder="Enter serviceId"/>
            </Form.Item>
            <Form.Item>
                <Button type="primary" htmlType="submit" loading={loading} icon={<PlusOutlined/>}>
                    Add Service
                </Button>
            </Form.Item>
        </Form>
    )
}