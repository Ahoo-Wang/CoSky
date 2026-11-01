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

import {useState} from 'react';
import {KeyRound, Plus, Trash2} from 'lucide-react';
import {useQuery} from '@ahoo-wang/fetcher-react';
import dayjs from 'dayjs';
import {toast} from 'sonner';
import {useDrawer} from '@/contexts/DrawerContext';
import {serviceAccounts} from '@/services/resources';
import type {ServiceAccountResource, TokenInfo} from '@/services/resources';
import {DataTableWrapper} from '@/components/layout/DataTableWrapper';
import {PageHeader} from '@/components/layout/PageHeader';
import {Button} from '@/components/ui/button';
import {ConfirmButton} from '@/components/ui/confirm-button';
import {DataTable} from '@/components/ui/data-table';
import type {DataTableColumn} from '@/components/ui/data-table';
import {Input} from '@/components/ui/input';
import {failureMessage, runAction} from '@/lib/actions';

/**
 * The only time anyone sees the plaintext of a token.
 */
function NewToken({token, onClose}: {token: string; onClose: () => void}) {
    return (
        <div className="space-y-4">
            <p className="text-sm text-muted-foreground">Copy this token now. The server keeps only its hash, and cannot show it again.</p>
            <Input readOnly aria-label="New token" value={token} onFocus={event => event.target.select()}/>
            <Button onClick={onClose}>Done</Button>
        </div>
    );
}

export function ServiceAccountPage() {
    const {openDrawer, closeDrawer} = useDrawer();
    const [name, setName] = useState('');
    const [description, setDescription] = useState('');
    const [creating, setCreating] = useState(false);
    const {result, loading, error, execute: loadAccounts} = useQuery<null, ServiceAccountResource[]>({
        initialQuery: null,
        execute: (_, __, abortController) => serviceAccounts.list(abortController),
    });

    const handleCreate = async () => {
        setCreating(true);
        const created = await runAction(
            () => serviceAccounts.create(name.trim(), description),
            {success: 'Service account added', failure: 'Failed to add service account'},
        );
        setCreating(false);
        if (created) {
            setName('');
            setDescription('');
            loadAccounts();
        }
    };

    const handleDelete = async (accountName: string) => {
        const deleted = await runAction(
            () => serviceAccounts.delete(accountName),
            {success: 'Service account deleted', failure: 'Failed to delete service account'},
        );
        if (deleted) {
            loadAccounts();
        }
    };

    const handleCreateToken = async (accountName: string) => {
        try {
            const token = await serviceAccounts.createToken(accountName);
            openDrawer(<NewToken token={token} onClose={closeDrawer}/>, {title: `New token of ${accountName}`});
            loadAccounts();
        } catch (failure) {
            toast.error(await failureMessage('Failed to create token', failure));
        }
    };

    const handleRevoke = async (accountName: string, tokenId: string) => {
        const revoked = await runAction(
            () => serviceAccounts.revokeToken(accountName, tokenId),
            {success: 'Token revoked', failure: 'Failed to revoke token'},
        );
        if (revoked) {
            loadAccounts();
        }
    };

    const tokenColumns = (accountName: string): DataTableColumn<TokenInfo>[] => [
        {header: 'Token', accessor: 'id', key: 'id'},
        {
            header: 'Expires',
            key: 'expiresAt',
            cell: token => token.expiresAt ? dayjs(token.expiresAt).format('YYYY-MM-DD HH:mm:ss') : 'Never',
        },
        {
            header: 'Action',
            key: 'action',
            className: 'w-36 text-right',
            cell: token => (
                <ConfirmButton
                    title="Are you sure to revoke this token?"
                    description={`Whoever holds token “${token.id}” can no longer sign in.`}
                    onConfirm={() => handleRevoke(accountName, token.id)}
                    variant="ghost"
                    size="sm"
                    className="text-destructive"
                >
                    <Trash2/> Revoke
                </ConfirmButton>
            ),
        },
    ];

    const columns: DataTableColumn<ServiceAccountResource>[] = [
        {
            header: 'Name',
            accessor: 'name',
            key: 'name',
            sort: (left, right) => left.name.localeCompare(right.name),
        },
        {header: 'Description', accessor: 'description', key: 'description'},
        {header: 'Tokens', key: 'tokens', cell: account => account.tokens.length},
        {
            header: 'Action',
            key: 'action',
            className: 'w-64 text-right',
            cell: account => (
                <div className="flex justify-end gap-1">
                    <Button variant="outline" size="sm" onClick={() => handleCreateToken(account.name)}>
                        <KeyRound/> New token
                    </Button>
                    <ConfirmButton
                        title="Are you sure to delete this service account?"
                        description={`Service account “${account.name}” and its tokens will be deleted.`}
                        onConfirm={() => handleDelete(account.name)}
                        variant="ghost"
                        size="sm"
                        className="text-destructive"
                    >
                        <Trash2/> Delete
                    </ConfirmButton>
                </div>
            ),
        },
    ];

    return (
        <div>
            <PageHeader
                title="Service Account"
                description="Programs sign in with the tokens of a service account. A role binding says what they may do."
                actions={
                    <form className="flex flex-wrap items-center gap-2" onSubmit={event => {
                        event.preventDefault();
                        void handleCreate();
                    }}>
                        <Input value={name} onChange={event => setName(event.target.value)} placeholder="Enter name"
                               required aria-label="Service account name" className="w-48"/>
                        <Input value={description} onChange={event => setDescription(event.target.value)}
                               placeholder="Description" aria-label="Service account description" className="w-56"/>
                        <Button type="submit" loading={creating}><Plus/>Add Service Account</Button>
                    </form>
                }
            />
            <DataTableWrapper>
                <DataTable
                    columns={columns}
                    data={result}
                    loading={loading}
                    error={error}
                    onRetry={loadAccounts}
                    getRowKey={account => account.name}
                    expandable={{
                        render: account => (
                            <DataTable
                                columns={tokenColumns(account.name)}
                                data={account.tokens}
                                getRowKey={token => token.id}
                                pagination={false}
                                emptyMessage="No tokens yet."
                            />
                        ),
                    }}
                    search={{placeholder: 'Search service accounts...', getValue: account => account.name}}
                    emptyMessage="No service accounts yet."
                />
            </DataTableWrapper>
        </div>
    );
}
