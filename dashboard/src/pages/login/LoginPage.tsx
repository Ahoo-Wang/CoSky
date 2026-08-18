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

import {useEffect, useState} from 'react';
import type {FormEvent} from 'react';
import {Eye, EyeOff, LockKeyhole, User} from 'lucide-react';
import {useNavigate} from 'react-router-dom';
import {authenticateApiHooks} from "../../services/clients.ts";
import {useSecurityContext} from "@ahoo-wang/fetcher-react";
import './LoginPage.css';
import CoskyLogo from "../../assets/cosky-logo-constellation.svg";
import type {ErrorResponse} from "../../generated";
import type {ExchangeError} from "@ahoo-wang/fetcher";
import {toast} from 'sonner';
import {Button} from '@/components/ui/button';
import {Card, CardContent} from '@/components/ui/card';
import {Input} from '@/components/ui/input';
import {Label} from '@/components/ui/label';

interface LoginFormValues {
    username: string;
    password: string;
}

function GitHubIcon() {
    return <svg data-brand="github" aria-hidden="true" focusable="false" viewBox="0 0 24 24">
        <path fill="currentColor" d="M12 .297c-6.63 0-12 5.373-12 12 0 5.303 3.438 9.8 8.205 11.385.6.113.82-.258.82-.577 0-.285-.01-1.04-.015-2.04-3.338.724-4.042-1.61-4.042-1.61C4.422 18.07 3.633 17.7 3.633 17.7c-1.087-.744.084-.729.084-.729 1.205.084 1.838 1.236 1.838 1.236 1.07 1.835 2.809 1.305 3.495.998.108-.776.417-1.305.76-1.605-2.665-.3-5.466-1.332-5.466-5.93 0-1.31.465-2.38 1.235-3.22-.135-.303-.54-1.523.105-3.176 0 0 1.005-.322 3.3 1.23.96-.267 1.98-.399 3-.405 1.02.006 2.04.138 3 .405 2.28-1.552 3.285-1.23 3.285-1.23.645 1.653.24 2.873.12 3.176.765.84 1.23 1.91 1.23 3.22 0 4.61-2.805 5.625-5.475 5.92.42.36.81 1.096.81 2.22 0 1.606-.015 2.896-.015 3.286 0 .315.21.69.825.57C20.565 22.092 24 17.592 24 12.297c0-6.627-5.373-12-12-12"/>
    </svg>;
}

function GiteeIcon() {
    return <svg aria-hidden="true" focusable="false" viewBox="0 0 24 24">
        <path fill="currentColor" d="M11.984 0A12 12 0 0 0 0 12a12 12 0 0 0 12 12 12 12 0 0 0 12-12A12 12 0 0 0 12 0a12 12 0 0 0-.016 0zm6.09 5.333c.328 0 .593.266.592.593v1.482a.594.594 0 0 1-.593.592H9.777c-.982 0-1.778.796-1.778 1.778v5.63c0 .327.266.592.593.592h5.63c.982 0 1.778-.796 1.778-1.778v-.296a.593.593 0 0 0-.592-.593h-4.15a.592.592 0 0 1-.592-.592v-1.482a.593.593 0 0 1 .593-.592h6.815c.327 0 .593.265.593.592v3.408a4 4 0 0 1-4 4H5.926a.593.593 0 0 1-.593-.593V9.778a4.444 4.444 0 0 1 4.445-4.444h8.296Z"/>
    </svg>;
}

export function LoginPage() {
    const [showPassword, setShowPassword] = useState(false);
    const {signIn, authenticated} = useSecurityContext();
    const navigate = useNavigate();
    const {loading, execute: login} = authenticateApiHooks.useLogin({
        onBeforeExecute: (abortController, args) => {
            args[1].abortController = abortController
        },
        onSuccess: (result) => {
            signIn(result)
        }, onError: async (error: ExchangeError) => {
            const errorResponse = await error.exchange.requiredResponse.json<ErrorResponse>()
            toast.error(`Login failed. ${errorResponse.msg}`);
        }
    })

    useEffect(() => {
        if (authenticated) {
            navigate('/home');
        }
    }, [authenticated, navigate]);

    const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        const values = Object.fromEntries(new FormData(event.currentTarget)) as unknown as LoginFormValues;
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
                <CardContent className="login-card-content">
                <div className="login-logo-section">
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
                    <h1 className="login-title">CoSky</h1>
                    <p className="login-subtitle">Microservice Governance Platform</p>
                </div>

                <form
                    onSubmit={handleSubmit}
                    autoComplete="off"
                    className="login-form"
                >
                    <div className="login-field">
                        <Label htmlFor="username" className="sr-only">Username</Label>
                        <User/>
                        <Input id="username" name="username" placeholder="Username" autoComplete="username" required/>
                    </div>

                    <div className="login-field">
                        <Label htmlFor="password" className="sr-only">Password</Label>
                        <LockKeyhole/>
                        <Input
                            id="password"
                            name="password"
                            type={showPassword ? 'text' : 'password'}
                            placeholder="Password"
                            autoComplete="current-password"
                            required
                        />
                        <Button
                            type="button"
                            variant="ghost"
                            size="icon-sm"
                            onClick={() => setShowPassword(value => !value)}
                            className="login-password-toggle"
                            aria-label={showPassword ? 'Hide password' : 'Show password'}
                        >
                            {showPassword ? <EyeOff/> : <Eye/>}
                        </Button>
                    </div>

                    <Button type="submit" loading={loading} className="login-submit-button">
                        Sign In
                    </Button>
                </form>

                {/* Footer */}
                <div className="login-repository-links">
                    <a
                        href="https://github.com/Ahoo-Wang/CoSky"
                        target="_blank"
                        rel="noopener noreferrer"
                        className="login-repository-link login-repository-link--github"
                        aria-label="View CoSky on GitHub"
                    >
                        <GitHubIcon/>
                    </a>
                    <a
                        href="https://gitee.com/AhooWang/CoSky"
                        target="_blank"
                        rel="noopener noreferrer"
                        className="login-repository-link login-repository-link--gitee"
                        aria-label="View CoSky on Gitee"
                    >
                        <GiteeIcon/>
                    </a>
                </div>
                </CardContent>
            </Card>
        </div>
    );
};
