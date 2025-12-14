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

import React, {useEffect, useState} from 'react';
import {Form, Input, Button, Card, Typography, Checkbox, message} from 'antd';
import {UserOutlined, LockOutlined, CloudOutlined} from '@ant-design/icons';
import {useNavigate} from 'react-router-dom';
import {authenticateApiClient} from "../../client/clients.ts";
import {useSecurityContext} from "@ahoo-wang/fetcher-react";
import './LoginPage.css';

const {Title, Text} = Typography;

interface LoginFormValues {
    username: string;
    password: string;
    remember?: boolean;
}

const ICON_COLOR = '#999';

export const LoginPage: React.FC = () => {
    const {signIn, authenticated} = useSecurityContext();
    const navigate = useNavigate();
    const [form] = Form.useForm();
    const [loading, setLoading] = useState(false);

    useEffect(() => {
        if (authenticated) {
            navigate('/home');
        }
    }, [authenticated, navigate]);

    const handleSubmit = async (values: LoginFormValues) => {
        setLoading(true);
        try {
            await signIn(() => {
                return authenticateApiClient.login(values.username, {
                    body: {
                        password: values.password
                    }
                })
            });
            message.success('Login successful!');
        } catch (error) {
            console.error('Login failed:', error);
            message.error('Login failed. Please check your credentials and try again.');
        } finally {
            setLoading(false);
        }
    };

    return (
        <div style={{
            display: 'flex',
            justifyContent: 'center',
            alignItems: 'center',
            minHeight: '100vh',
            background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
            padding: '20px',
        }}>
            <Card 
                style={{
                    width: '100%',
                    maxWidth: 420,
                    boxShadow: '0 20px 60px rgba(0, 0, 0, 0.3)',
                    borderRadius: 16,
                    border: 'none',
                    overflow: 'hidden',
                    animation: 'fadeInUp 0.6s ease-out',
                }}
                bodyStyle={{
                    padding: '48px 40px',
                }}
            >
                <div style={{textAlign: 'center', marginBottom: 40}}>
                    <div style={{
                        display: 'inline-flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        width: 80,
                        height: 80,
                        borderRadius: '50%',
                        background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
                        marginBottom: 24,
                        boxShadow: '0 8px 24px rgba(102, 126, 234, 0.4)',
                    }}>
                        <CloudOutlined style={{fontSize: 40, color: '#fff'}} />
                    </div>
                    <Title level={2} style={{marginBottom: 8, fontWeight: 600}}>
                        Welcome to CoSky
                    </Title>
                    <Text type="secondary" style={{fontSize: 14}}>
                        Sign in to manage your services
                    </Text>
                </div>
                <Form
                    form={form}
                    name="login"
                    onFinish={handleSubmit}
                    autoComplete="off"
                    size="large"
                >
                    <Form.Item
                        name="username"
                        rules={[{required: true, message: 'Please input your username!'}]}
                    >
                        <Input
                            prefix={<UserOutlined style={{color: ICON_COLOR}} />}
                            placeholder="Username"
                            style={{
                                borderRadius: 8,
                                padding: '12px 16px',
                            }}
                        />
                    </Form.Item>

                    <Form.Item
                        name="password"
                        rules={[{required: true, message: 'Please input your password!'}]}
                    >
                        <Input.Password
                            prefix={<LockOutlined style={{color: ICON_COLOR}} />}
                            placeholder="Password"
                            style={{
                                borderRadius: 8,
                                padding: '12px 16px',
                            }}
                        />
                    </Form.Item>

                    <Form.Item>
                        <Form.Item name="remember" valuePropName="checked" noStyle>
                            <Checkbox>Remember me</Checkbox>
                        </Form.Item>
                    </Form.Item>

                    <Form.Item style={{marginBottom: 0}}>
                        <Button 
                            type="primary" 
                            htmlType="submit" 
                            block
                            loading={loading}
                            className="login-submit-button"
                            style={{
                                height: 48,
                                borderRadius: 8,
                                fontSize: 16,
                                fontWeight: 500,
                                background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
                                border: 'none',
                            }}
                        >
                            Sign In
                        </Button>
                    </Form.Item>
                </Form>
            </Card>
        </div>
    );
};
