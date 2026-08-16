import {useExecutePromise} from "@ahoo-wang/fetcher-react";
import {Button, Form, Input} from "antd";
import {toast} from 'sonner';
import {PlusOutlined} from "@ant-design/icons";
import {serviceApiClient} from "../../services/clients.ts";

export interface AddServiceFormProps {
    namespace: string;
    onSuccess: () => void;
}

export function AddServiceForm({namespace, onSuccess}: AddServiceFormProps) {
    const [form] = Form.useForm();
    const {loading, execute} = useExecutePromise({
        onSuccess: () => {
            toast.success(`Add service success!`);
            onSuccess();
            form.resetFields();
        },
        onError: () => {
            toast.error('Add service failed!');
        }
    });
    const handleFinish = async (values: { serviceId: string }) => {
        await execute(() => {
            return serviceApiClient.setService(namespace, values.serviceId);
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