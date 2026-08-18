import type {FormEvent} from "react";
import {useExecutePromise, useSecurityContext} from "@ahoo-wang/fetcher-react";
import type {ChangePwdRequest, ErrorResponse} from "../../generated";
import {userApiClient} from "../../services/clients.ts";
import type {ExchangeError} from "@ahoo-wang/fetcher";
import {toast} from "sonner";
import {Button} from "@/components/ui/button";
import {Input} from "@/components/ui/input";
import {Label} from "@/components/ui/label";

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
            try {
                const errorResponse = await error.exchange.requiredResponse.json<ErrorResponse>()
                toast.error(errorResponse.msg);
            } catch {
                toast.error('Change password failed.');
            }
        }
    })
    const handleChangePwd = async (values: ChangePwdRequest) => {
        try {
            await execute(() => {
                return userApiClient.changePwd(currentUser.sub, {
                    body: values
                })
            })
            onSubmit(values)
        } catch {
            // The hook already displayed the API error.
        }
    }
    const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        handleChangePwd(Object.fromEntries(new FormData(event.currentTarget)) as unknown as ChangePwdRequest);
    };

    return (
        <form className="space-y-5" onSubmit={handleSubmit}>
            <div className="space-y-2">
                <Label htmlFor="oldPassword">Old Password</Label>
                <Input id="oldPassword" name="oldPassword" type="password" autoComplete="current-password" required/>
            </div>
            <div className="space-y-2">
                <Label htmlFor="newPassword">New Password</Label>
                <Input id="newPassword" name="newPassword" type="password" autoComplete="new-password" required/>
            </div>
            <div className="flex gap-2 pt-2">
                <Button type="submit" loading={loading}>Submit</Button>
                <Button type="button" variant="outline" onClick={onCancel}>Cancel</Button>
            </div>
        </form>
    );
}
