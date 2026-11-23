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
import {Copy} from 'lucide-react';
import {useQuery} from '@ahoo-wang/fetcher-react';
import dayjs from 'dayjs';
import {toast} from 'sonner';
import {serviceAccounts} from '@/services/resources';
import type {ServiceAccountResource} from '@/services/resources';
import {Button} from '@/components/ui/button';
import {Input} from '@/components/ui/input';
import {failureMessage} from '@/lib/actions';
import {copyWithFeedback} from '@/lib/clipboard';

/** The label of the account the server gives each namespace (design D45). */
const NAMESPACE_LABEL = 'cosky.namespace';
/** What an account may hold, so that one can be rotated in before the other is revoked. */
const MAX_TOKENS = 2;

/**
 * The account the server made for [namespace], if it did: one of that name someone made is not it.
 */
function ownAccountOf(accounts: ServiceAccountResource[], namespace: string): ServiceAccountResource | undefined {
    return accounts.find(account => account.name === namespace && account.labels[NAMESPACE_LABEL] === namespace);
}

function Snippet({label, text}: {label: string; text: string}) {
    return (
        <div className="space-y-1">
            <div className="flex items-center justify-between">
                <span className="text-sm font-medium">{label}</span>
                <Button
                    variant="ghost"
                    size="sm"
                    aria-label={`Copy ${label}`}
                    onClick={() => void copyWithFeedback(text, label)}
                >
                    <Copy/> Copy
                </Button>
            </div>
            <pre aria-label={label} className="overflow-x-auto rounded-md bg-muted p-3 text-xs"><code>{text}</code></pre>
        </div>
    );
}

/**
 * What an application of [namespace] needs: the settings, and the token its account's applications share. The token
 * is made only when asked for, since an account holds two at most.
 */
export function ConnectApplication({namespace, onClose}: {namespace: string; onClose: () => void}) {
    const endpoint = `${window.location.hostname}:9090`;
    const [token, setToken] = useState<string>();
    const [creating, setCreating] = useState(false);
    const {result: accounts, loading, execute: reload} = useQuery<null, ServiceAccountResource[]>({
        query: null,
        execute: (_, abortController) => serviceAccounts.list(abortController),
    });
    const account = accounts ? ownAccountOf(accounts, namespace) : undefined;
    const valid = account?.tokens.filter(it => !it.expiresAt || dayjs(it.expiresAt).isAfter(dayjs())).length ?? 0;

    const handleCreate = async () => {
        setCreating(true);
        try {
            setToken(await serviceAccounts.createToken(namespace));
            reload();
        } catch (failure) {
            toast.error(await failureMessage('Failed to create token', failure));
        } finally {
            setCreating(false);
        }
    };

    const settings = [
        'cosky:',
        `  endpoints: ${endpoint}`,
        `  namespace: ${namespace}`,
        '  token: ${COSKY_TOKEN}',
    ].join('\n');

    return (
        <div className="space-y-6">
            <section className="space-y-3">
                <p className="text-sm text-muted-foreground">
                    Add this to the application's <code>application.yaml</code>. Set <code>endpoints</code> to the
                    server's gRPC address if the application reaches it at another one.
                </p>
                <Snippet label="application.yaml" text={settings}/>
                <p className="text-sm text-muted-foreground">
                    Or set <code>token-file</code> to a file holding the token, such as a mounted secret: a token
                    rotated there needs no restart.
                </p>
            </section>
            <section className="space-y-3">
                <h3 className="text-sm font-medium">Token</h3>
                {loading && !accounts ? <p className="text-sm text-muted-foreground">Loading…</p> : null}
                {accounts && !account ? (
                    <p className="text-sm text-muted-foreground">
                        Namespace {namespace} has no account of its own. Give its applications a service account under
                        Security.
                    </p>
                ) : null}
                {account && !token ? (
                    <>
                        <p className="text-sm text-muted-foreground">
                            The applications of {namespace} share the token of the service account {namespace}. It
                            holds {valid} of at most {MAX_TOKENS} tokens: create one only if you have none to give.
                        </p>
                        <Button onClick={handleCreate} disabled={creating || valid >= MAX_TOKENS}>Create token</Button>
                        {valid >= MAX_TOKENS ? (
                            <p className="text-sm text-muted-foreground">Revoke one under Security first.</p>
                        ) : null}
                    </>
                ) : null}
                {token ? (
                    <>
                        <p className="text-sm text-muted-foreground">
                            Copy this token now. The server keeps only its hash, and cannot show it again.
                        </p>
                        <Input readOnly aria-label="New token" value={token} onFocus={event => event.target.select()}/>
                        <Snippet
                            label="Kubernetes secret"
                            text={`kubectl create secret generic cosky-${namespace} --from-literal=token=${token}`}
                        />
                    </>
                ) : null}
            </section>
            <Button variant="outline" onClick={onClose}>Done</Button>
        </div>
    );
}
