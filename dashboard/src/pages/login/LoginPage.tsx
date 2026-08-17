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
import {ExternalLink, Eye, EyeOff, LockKeyhole, User} from 'lucide-react';
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
                <div style={{marginTop: 32, textAlign: 'center'}}>
                    <a
                        href="https://github.com/Ahoo-Wang/CoSky"
                        target="_blank"
                        rel="noopener noreferrer"
                        className="login-github-link"
                        aria-label="View CoSky on GitHub"
                    >
                        <ExternalLink/>
                    </a>
                </div>
                </CardContent>
            </Card>
        </div>
    );
};
