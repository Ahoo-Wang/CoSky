import {Button, Form, Input, message, Space} from "antd";
import {useExecutePromise, useSecurityContext} from "@ahoo-wang/fetcher-react";
import {ChangePwdRequest, ErrorResponse} from "../../generated";
import {userApiClient} from "../../services/clients.ts";
import {ExchangeError} from "@ahoo-wang/fetcher";

export interface ChangePwdProps {
    onSubmit: (values: ChangePwdRequest) => void;
    onCancel: () => void;
}

export function ChangePwd({onSubmit, onCancel}: ChangePwdProps) {
    const {currentUser} = useSecurityContext()
    const {loading, execute} = useExecutePromise<boolean, ExchangeError>({
        propagateError: true,
        onSuccess: () => {
            message.success('Change password success!');
        },
        onError: async (error) => {
            const errorResponse = await error.exchange.requiredResponse.json<ErrorResponse>()
            message.error(`${errorResponse.msg}`);
        }
    })
    const handleChangePwd = async (values: ChangePwdRequest) => {
        await execute(() => {
            return userApiClient.changePwd(currentUser.sub, {
                body: values
            })
        })
        onSubmit(values)
    }
    const [form] = Form.useForm<ChangePwdRequest>();
    return (<Form form={form} layout="vertical" onFinish={handleChangePwd}>
        <Form.Item
            name="oldPassword"
            label="Old Password"
            rules={[{required: true, message: 'Please input old password!'}]}
        >
            <Input.Password/>
        </Form.Item>
        <Form.Item
            name="newPassword"
            label="New Password"
            rules={[{required: true, message: 'Please input new password!'}]}
        >
            <Input.Password/>
        </Form.Item>
        <Form.Item>
            <Space>
                <Button type="primary" htmlType="submit" loading={loading}>
                    Submit
                </Button>
                <Button onClick={onCancel}>
                    Cancel
                </Button>
            </Space>
        </Form.Item>
    </Form>)
}