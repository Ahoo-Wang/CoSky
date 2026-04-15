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

import {useEffect} from 'react';
import {Form, Input, Button, Card, Typography, App} from 'antd';
import {UserOutlined, LockOutlined, GithubOutlined} from '@ant-design/icons';
import {useNavigate} from 'react-router-dom';
import {authenticateApiHooks} from "../../services/clients.ts";
import {useSecurityContext} from "@ahoo-wang/fetcher-react";
import './LoginPage.css';
import CoskyLogo from "../../assets/cosky-logo-constellation.svg";
import type {ErrorResponse} from "../../generated";
import type {ExchangeError} from "@ahoo-wang/fetcher";

const {Title, Text} = Typography;

interface LoginFormValues {
    username: string;
    password: string;
}

export function LoginPage() {
    const {message} = App.useApp()
    const {signIn, authenticated} = useSecurityContext();
    const navigate = useNavigate();
    const [form] = Form.useForm();
    const {loading, execute: login} = authenticateApiHooks.useLogin({
        onBeforeExecute: (abortController, args) => {
            args[1].abortController = abortController
        },
        onSuccess: (result) => {
            signIn(result)
        }, onError: async (error: ExchangeError) => {
            const errorResponse = await error.exchange.requiredResponse.json<ErrorResponse>()
            message.error(`Login failed. ${errorResponse.msg}
            `);
        }
    })

    useEffect(() => {
        if (authenticated) {
            navigate('/home');
        }
    }, [authenticated, navigate]);

    const handleSubmit = async (values: LoginFormValues) => {
        await login(values.username, {
            body: {
                password: values.password
            }
        })
    };

    return (
        <div className="login-container">
            {/* Circuit lines */}
            <div className="login-circuit"/>

            {/* Floating particles */}
            <div className="login-particles">
                <div className="login-particle"/>
                <div className="login-particle"/>
                <div className="login-particle"/>
                <div className="login-particle"/>
                <div className="login-particle"/>
            </div>

            <Card className="login-card">
                {/* Status indicator */}
                <div className="login-status">
                    <span className="login-status-dot"/>
                    <span>Secure</span>
                </div>

                {/* Corner decorators */}
                <div className="login-decorator login-decorator-top-left"/>
                <div className="login-decorator login-decorator-top-right"/>
                <div className="login-decorator login-decorator-bottom-left"/>
                <div className="login-decorator login-decorator-bottom-right"/>

                {/* Logo Section */}
                <div style={{textAlign: 'center', marginBottom: 40, position: 'relative'}}>
                    <div className="login-logo-glow"/>
                    <div className="login-logo-ring"/>
                    <div className="login-logo-container" style={{
                        display: 'inline-flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        width: 100,
                        height: 100,
                        borderRadius: '50%',
                        background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
                        boxShadow: '0 8px 32px rgba(102, 126, 234, 0.5)',
                        position: 'relative',
                        zIndex: 1,
                    }}>
                        <img
                            src={CoskyLogo}
                            alt="CoSky Logo"
                            style={{
                                width: 65,
                                height: 65,
                                objectFit: 'contain',
                            }}
                        />
                    </div>
                    <Title level={2} className="login-title" style={{marginBottom: 8, marginTop: 24, fontWeight: 600}}>
                        CoSky
                    </Title>
                    <Text style={{color: 'rgba(255, 255, 255, 0.6)', fontSize: 14}}>
                        Microservice Governance Platform
                    </Text>
                </div>

                {/* Login Form */}
                <Form
                    form={form}
                    name="login"
                    onFinish={handleSubmit}
                    autoComplete="off"
                    size="large"
                    className="login-form"
                >
                    <Form.Item
                        name="username"
                        rules={[{required: true, message: 'Please input your username!'}]}
                        style={{marginBottom: 24}}
                    >
                        <Input
                            prefix={<UserOutlined/>}
                            placeholder="Username"
                        />
                    </Form.Item>

                    <Form.Item
                        name="password"
                        rules={[{required: true, message: 'Please input your password!'}]}
                        style={{marginBottom: 32}}
                    >
                        <Input.Password
                            prefix={<LockOutlined/>}
                            placeholder="Password"
                        />
                    </Form.Item>

                    <Form.Item style={{marginBottom: 0}}>
                        <Button
                            type="primary"
                            htmlType="submit"
                            block
                            loading={loading}
                            className="login-submit-button"
                        >
                            Sign In
                        </Button>
                    </Form.Item>
                </Form>

                {/* Footer */}
                <div style={{marginTop: 32, textAlign: 'center'}}>
                    <a
                        href="https://github.com/Ahoo-Wang/CoSky"
                        target="_blank"
                        rel="noopener noreferrer"
                        className="login-github-link"
                    >
                        <GithubOutlined/>
                    </a>
                </div>
            </Card>
        </div>
    );
};
