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
import {Eye, EyeOff, KeyRound} from 'lucide-react';
import {useNavigate} from 'react-router-dom';
import {useQuery} from '@ahoo-wang/fetcher-react';
import {toast} from 'sonner';
import CoskyLogo from "@/assets/cosky-logo-constellation.svg";
import type {AuthConfigResponse} from "@/generated";
import {GiteeIcon, GitHubIcon} from '@/components/icons/repository-icons';
import {Button} from '@/components/ui/button';
import {Card, CardContent} from '@/components/ui/card';
import {Input} from '@/components/ui/input';
import {Label} from '@/components/ui/label';
import {failureMessage} from '@/lib/actions';
import {cn} from '@/lib/utils';
import {beginSignIn, completeSignIn} from '@/security/oidc';
import {session} from '@/security/session';
import {identityClient} from '@/services/clients';

const particles = [
    {position: 'top-[20%] left-[10%]', delay: ''},
    {position: 'top-[60%] left-[85%]', delay: '[animation-delay:1s]'},
    {position: 'top-[80%] left-[20%]', delay: '[animation-delay:2s]'},
    {position: 'top-[30%] left-[75%]', delay: '[animation-delay:0.5s]'},
    {position: 'top-[70%] left-[50%]', delay: '[animation-delay:1.5s]'},
];

const corners = [
    {position: 'top-5 left-5', delay: ''},
    {position: 'top-5 right-5', delay: '[animation-delay:0.5s]'},
    {position: 'bottom-5 left-5', delay: '[animation-delay:1s]'},
    {position: 'right-5 bottom-5', delay: '[animation-delay:1.5s]'},
];

const fieldIconClass = 'absolute top-1/2 left-[14px] z-1 w-[17px] -translate-y-1/2 text-login-icon';

const fieldInputClass = cn(
    'h-11 rounded-[8px] border border-login-indigo/30 bg-white/5 px-[42px] text-white placeholder:text-white/40',
    'transition-all duration-300 ease-[ease] hover:border-login-indigo/60 hover:shadow-[0_0_15px_rgba(102,126,234,0.3)]',
    'focus-visible:border-login-indigo focus-visible:bg-login-indigo/10 focus-visible:shadow-[0_0_20px_rgba(102,126,234,0.5)] focus-visible:ring-0',
);

const submitClass = cn(
    'login-shimmer relative h-12 w-full overflow-hidden rounded-[8px] border-0 text-[16px] font-semibold tracking-[1px] text-white',
    'bg-[linear-gradient(135deg,var(--login-indigo)_0%,var(--login-violet)_100%)] shadow-[0_4px_20px_rgba(102,126,234,0.4)]',
    'transition-all duration-300 ease-[ease] hover:-translate-y-0.5 hover:shadow-[0_8px_30px_rgba(102,126,234,0.6)] active:translate-y-0',
    'disabled:opacity-70',
);

const repositoryLinkClass = 'inline-flex items-center justify-center text-white/50 transition-all duration-300 ease-[ease] hover:scale-120 [&_svg]:size-6';

export function LoginPage() {
    const [showToken, setShowToken] = useState(false);
    const [signingIn, setSigningIn] = useState(false);
    const navigate = useNavigate();
    const {result: config} = useQuery<null, AuthConfigResponse>({
        initialQuery: null,
        execute: (_, __, abortController) => identityClient.getAuthConfig({abortController}),
    });
    const oidc = config?.oidc;

    // The provider sends the browser back here.
    useEffect(() => {
        completeSignIn().then(idToken => {
            if (idToken) {
                session.signIn(idToken);
                navigate('/home');
            }
        }).catch(async error => {
            toast.error(await failureMessage('Sign-in failed', error));
        });
    }, [navigate]);

    const handleToken = async (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        const token = String(new FormData(event.currentTarget).get('token')).trim();
        setSigningIn(true);
        session.signIn(token);
        try {
            await identityClient.getMe();
            navigate('/home');
        } catch (error) {
            session.signOut();
            toast.error(await failureMessage('Sign-in failed', error));
        } finally {
            setSigningIn(false);
        }
    };

    const handleOidc = async () => {
        if (!oidc) return;
        try {
            await beginSignIn(oidc);
        } catch (error) {
            toast.error(await failureMessage('Cannot reach the identity provider', error));
        }
    };

    return (
        <div className="login-backdrop relative flex min-h-screen items-center justify-center overflow-hidden p-5">
            <div className="login-circuit pointer-events-none absolute inset-0 z-0 opacity-15"/>

            <div className="pointer-events-none absolute inset-0 z-0 overflow-hidden">
                {particles.map(({position, delay}) => (
                    <div
                        key={position}
                        className={cn(
                            'absolute size-1 rounded-full bg-login-indigo/60 shadow-[0_0_10px_rgba(102,126,234,0.8)] animate-login-float motion-reduce:animate-none',
                            position, delay,
                        )}
                    />
                ))}
            </div>

            <Card className={cn(
                'login-card-glow relative z-10 w-full max-w-[440px] animate-login-enter overflow-hidden rounded-[16px]',
                'border border-login-indigo/30 bg-[rgba(10,10,30,0.85)] ring-0 backdrop-blur-[20px]',
                'shadow-[0_0_40px_rgba(102,126,234,0.2),0_25px_50px_rgba(0,0,0,0.5),inset_0_1px_0_rgba(255,255,255,0.1)]',
            )}>
                <div className="absolute top-[15px] right-[15px] flex items-center gap-[6px] text-[10px] tracking-[1px] text-white/50 uppercase">
                    <span className="size-[6px] animate-login-blink-fast rounded-full bg-login-online shadow-[0_0_8px_var(--login-online)] motion-reduce:animate-none"/>
                    <span>Secure</span>
                </div>

                {corners.map(({position, delay}) => (
                    <div
                        key={position}
                        className={cn(
                            'absolute size-[6px] animate-login-blink rounded-full bg-login-indigo shadow-[0_0_10px_var(--login-indigo),0_0_20px_var(--login-indigo)] motion-reduce:animate-none',
                            position, delay,
                        )}
                    />
                ))}

                <CardContent className="px-6 pt-[38px] pb-6">
                    <div className="relative mb-10 text-center">
                        <div className="absolute top-1/2 left-1/2 z-0 size-[120px] -translate-1/2 animate-login-pulse rounded-full bg-[radial-gradient(circle,rgba(102,126,234,0.6)_0%,rgba(118,75,162,0.3)_40%,transparent_70%)] motion-reduce:animate-none"/>
                        <div className="absolute top-1/2 left-1/2 size-[140px] -translate-1/2 animate-login-pulse rounded-full border-2 border-login-indigo/30 [animation-direction:reverse] motion-reduce:animate-none"/>
                        <div className="relative z-1 inline-flex size-[100px] items-center justify-center rounded-full bg-[linear-gradient(135deg,var(--login-indigo)_0%,var(--login-violet)_100%)] shadow-[0_8px_32px_rgba(102,126,234,0.5)]">
                            <img src={CoskyLogo} alt="CoSky Logo" className="size-[65px] object-contain"/>
                        </div>
                        <h1 className="mt-6 mb-2 animate-login-glow text-[30px] font-[650] text-white motion-reduce:animate-none">CoSky</h1>
                        <p className="text-[14px] text-white/60">Microservice Governance Platform</p>
                    </div>

                    {oidc && (
                        <Button type="button" onClick={handleOidc} className={cn(submitClass, 'mb-6')}>
                            Sign in with {new URL(oidc.issuer).host}
                        </Button>
                    )}

                    <form onSubmit={handleToken} autoComplete="off" className="grid gap-6">
                        <div className="relative">
                            <Label htmlFor="token" className="sr-only">Token</Label>
                            <KeyRound className={fieldIconClass}/>
                            <Input
                                id="token"
                                name="token"
                                type={showToken ? 'text' : 'password'}
                                placeholder="Service account token"
                                autoComplete="off"
                                required
                                className={fieldInputClass}
                            />
                            <Button
                                type="button"
                                variant="ghost"
                                size="icon-sm"
                                onClick={() => setShowToken(value => !value)}
                                className="absolute top-1/2 right-[7px] z-2 -translate-y-1/2 text-[rgba(126,145,255,0.85)]"
                                aria-label={showToken ? 'Hide token' : 'Show token'}
                            >
                                {showToken ? <EyeOff/> : <Eye/>}
                            </Button>
                        </div>

                        <Button type="submit" loading={signingIn} className={submitClass}>
                            Sign In
                        </Button>
                    </form>

                    <div className="mt-8 flex justify-center gap-[18px]">
                        <a
                            href="https://github.com/Ahoo-Wang/CoSky"
                            target="_blank"
                            rel="noopener noreferrer"
                            className={cn(repositoryLinkClass, 'hover:text-white hover:[text-shadow:0_0_20px_rgba(102,126,234,0.8)]')}
                            aria-label="View CoSky on GitHub"
                        >
                            <GitHubIcon/>
                        </a>
                        <a
                            href="https://gitee.com/AhooWang/CoSky"
                            target="_blank"
                            rel="noopener noreferrer"
                            className={cn(repositoryLinkClass, 'hover:text-brand-gitee hover:drop-shadow-[0_0_10px_rgba(199,29,35,0.6)]')}
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
