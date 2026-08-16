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

import {useForm} from 'react-hook-form';
import {zodResolver} from '@hookform/resolvers/zod';
import {z} from 'zod';
import {toast} from 'sonner';
import {useExecutePromise, useSecurityContext} from '@ahoo-wang/fetcher-react';
import {Button} from '@/components/ui/button';
import {Input} from '@/components/ui/input';
import {Form, FormControl, FormField, FormItem, FormLabel, FormMessage} from '@/components/ui/form';
import type {ChangePwdRequest, ErrorResponse} from '../../generated';
import {userApiClient} from '../../services/clients.ts';
import type {ExchangeError} from '@ahoo-wang/fetcher';

const schema = z.object({
    oldPassword: z.string().min(1, 'Please input old password!'),
    newPassword: z.string().min(1, 'Please input new password!'),
});
type Values = z.infer<typeof schema>;

export interface ChangePwdProps {
    onSubmit: (values: ChangePwdRequest) => void;
    onCancel: () => void;
}

export function ChangePwd({onSubmit, onCancel}: ChangePwdProps) {
    const {currentUser} = useSecurityContext()
    const {loading, execute} = useExecutePromise<boolean, ExchangeError>({
        propagateError: true,
        onSuccess: () => {
            toast.success('Change password success!');
        },
        onError: async (error) => {
            const errorResponse = await error.exchange.requiredResponse.json<ErrorResponse>()
            toast.error(`${errorResponse.msg}`);
        }
    })
    const form = useForm<Values>({
        resolver: zodResolver(schema),
        defaultValues: {oldPassword: '', newPassword: ''},
    });
    const handleSubmit = async (values: Values) => {
        await execute(() => {
            return userApiClient.changePwd(currentUser.sub, {
                body: values
            })
        })
        onSubmit(values)
    }
    return (
        <Form {...form}>
            <form onSubmit={form.handleSubmit(handleSubmit)} className="space-y-4">
                <FormField
                    control={form.control}
                    name="oldPassword"
                    render={({field}) => (
                        <FormItem>
                            <FormLabel>Old Password</FormLabel>
                            <FormControl>
                                <Input type="password" {...field} />
                            </FormControl>
                            <FormMessage/>
                        </FormItem>
                    )}
                />
                <FormField
                    control={form.control}
                    name="newPassword"
                    render={({field}) => (
                        <FormItem>
                            <FormLabel>New Password</FormLabel>
                            <FormControl>
                                <Input type="password" {...field} />
                            </FormControl>
                            <FormMessage/>
                        </FormItem>
                    )}
                />
                <div className="flex gap-2">
                    <Button type="submit" disabled={loading || form.formState.isSubmitting}>
                        Submit
                    </Button>
                    <Button type="button" variant="outline" onClick={onCancel}>
                        Cancel
                    </Button>
                </div>
            </form>
        </Form>
    )
}
