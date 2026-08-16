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
import {useForm} from 'react-hook-form';
import {zodResolver} from '@hookform/resolvers/zod';
import {z} from 'zod';
import {toast} from 'sonner';
import {useNavigate} from 'react-router-dom';
import {Lock, User} from 'lucide-react';
import {authenticateApiHooks} from '@/services/clients';
import {useSecurityContext} from '@ahoo-wang/fetcher-react';
import CoskyLogo from '@/assets/cosky-logo-constellation.svg';
import type {ErrorResponse} from '@/generated';
import type {ExchangeError} from '@ahoo-wang/fetcher';
import {Button} from '@/components/ui/button';
import {Card, CardContent} from '@/components/ui/card';
import {Input} from '@/components/ui/input';
import {Form, FormControl, FormField, FormItem, FormMessage} from '@/components/ui/form';
import {Spinner} from '@/components/feedback/Spinner';
import {ParticleBackground} from './ParticleBackground';

const schema = z.object({
    username: z.string().min(1, 'Please input your username!'),
    password: z.string().min(1, 'Please input your password!'),
});
type LoginFormValues = z.infer<typeof schema>;

function GithubMark({className}: { className?: string }) {
    return (
        <svg viewBox="0 0 24 24" fill="currentColor" className={className} aria-hidden>
            <path d="M12 .297c-6.63 0-12 5.373-12 12 0 5.303 3.438 9.8 8.205 11.385.6.113.82-.258.82-.577 0-.285-.01-1.04-.015-2.04-3.338.724-4.042-1.61-4.042-1.61C4.422 18.07 3.633 17.7 3.633 17.7c-1.087-.744.084-.729.084-.729 1.205.084 1.838 1.236 1.838 1.236 1.07 1.835 2.809 1.305 3.495.998.108-.776.417-1.305.76-1.605-2.665-.3-5.466-1.332-5.466-5.93 0-1.31.465-2.38 1.235-3.22-.135-.303-.54-1.523.105-3.176 0 0 1.005-.322 3.3 1.23.96-.267 1.98-.399 3-.405 1.02.006 2.04.138 3 .405 2.28-1.552 3.285-1.23 3.285-1.23.645 1.653.24 2.873.12 3.176.765.84 1.23 1.91 1.23 3.22 0 4.61-2.805 5.625-5.475 5.92.42.36.81 1.096.81 2.22 0 1.606-.015 2.896-.015 3.286 0 .315.21.69.825.57C20.565 22.092 24 17.592 24 12.297c0-6.627-5.373-12-12-12"/>
        </svg>
    );
}

export function LoginPage() {
    const {signIn, authenticated} = useSecurityContext();
    const navigate = useNavigate();
    const form = useForm<LoginFormValues>({
        resolver: zodResolver(schema),
        defaultValues: {username: '', password: ''},
    });
    const {loading, execute: login} = authenticateApiHooks.useLogin({
        onBeforeExecute: (abortController, args) => {
            args[1].abortController = abortController
        },
        onSuccess: (result) => {
            signIn(result)
        }, onError: async (error: ExchangeError) => {
            const errorResponse = await error.exchange.requiredResponse.json<ErrorResponse>()
            toast.error(`Login failed. ${errorResponse.msg}
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
        <div className="relative flex min-h-screen items-center justify-center overflow-hidden bg-gradient-to-br from-brand-from to-brand-to">
            <ParticleBackground/>
            <Card className="relative z-10 w-full max-w-md border-white/20 bg-white/10 text-white shadow-2xl backdrop-blur-md dark:bg-black/20">
                <CardContent className="flex flex-col gap-6">
                    <div className="flex flex-col items-center gap-2 text-center">
                        <div className="flex size-20 items-center justify-center rounded-full bg-white/10 ring-1 ring-white/30">
                            <img src={CoskyLogo} alt="CoSky Logo" className="size-16 object-contain"/>
                        </div>
                        <h1 className="text-2xl font-semibold">CoSky</h1>
                        <p className="text-sm text-white/60">Microservice Governance Platform</p>
                    </div>

                    <Form {...form}>
                        <form onSubmit={form.handleSubmit(handleSubmit)} autoComplete="off" className="flex flex-col gap-4">
                            <FormField
                                control={form.control}
                                name="username"
                                render={({field}) => (
                                    <FormItem>
                                        <FormControl>
                                            <div className="relative">
                                                <User className="absolute top-1/2 left-3 size-4 -translate-y-1/2 text-white/50"/>
                                                <Input
                                                    placeholder="Username"
                                                    autoComplete="username"
                                                    className="h-10 border-white/20 bg-white/10 pl-9 text-white placeholder:text-white/50 focus-visible:border-white/40 focus-visible:ring-white/30"
                                                    {...field}
                                                />
                                            </div>
                                        </FormControl>
                                        <FormMessage className="text-red-200"/>
                                    </FormItem>
                                )}
                            />
                            <FormField
                                control={form.control}
                                name="password"
                                render={({field}) => (
                                    <FormItem>
                                        <FormControl>
                                            <div className="relative">
                                                <Lock className="absolute top-1/2 left-3 size-4 -translate-y-1/2 text-white/50"/>
                                                <Input
                                                    type="password"
                                                    placeholder="Password"
                                                    autoComplete="current-password"
                                                    className="h-10 border-white/20 bg-white/10 pl-9 text-white placeholder:text-white/50 focus-visible:border-white/40 focus-visible:ring-white/30"
                                                    {...field}
                                                />
                                            </div>
                                        </FormControl>
                                        <FormMessage className="text-red-200"/>
                                    </FormItem>
                                )}
                            />
                            <Button type="submit" size="lg" className="w-full" disabled={loading}>
                                {loading && <Spinner className="size-4 text-primary-foreground"/>}
                                Sign In
                            </Button>
                        </form>
                    </Form>

                    <div className="flex justify-center">
                        <a
                            href="https://github.com/Ahoo-Wang/CoSky"
                            target="_blank"
                            rel="noopener noreferrer"
                            className="text-white/60 transition-colors hover:text-white"
                        >
                            <GithubMark className="size-5"/>
                        </a>
                    </div>
                </CardContent>
            </Card>
        </div>
    );
};
