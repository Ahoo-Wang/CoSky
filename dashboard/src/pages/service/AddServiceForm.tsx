import {useExecutePromise} from "@ahoo-wang/fetcher-react";
import {App, Button, Form, Input} from "antd";
import {PlusOutlined} from "@ant-design/icons";
import {serviceApiClient} from "../../services/clients.ts";

export interface AddServiceFormProps {
    namespace: string;
    onSuccess: () => void;
}

export function AddServiceForm({namespace, onSuccess}: AddServiceFormProps) {
    const {message} = App.useApp()
    const [form] = Form.useForm();
    const {loading, execute} = useExecutePromise({
        onSuccess: () => {
            message.success(`Add service success!`);
            onSuccess();
            form.resetFields();
        },
        onError: () => {
            message.error('Add service failed!');
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