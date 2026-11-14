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

import {useLayoutEffect, useState} from 'react';
import {DiffEditor} from '@monaco-editor/react';
import type {MonacoDiffEditor} from '@monaco-editor/react';
import {useQuery} from '@ahoo-wang/fetcher-react';
import dayjs from 'dayjs';
import {configs} from '@/services/resources';
import type {ConfigResource} from '@/services/resources';
import {editorLanguage} from '@/pages/config/formats';
import {Button} from '@/components/ui/button';
import {ConfirmButton} from '@/components/ui/confirm-button';
import {DefinitionList} from '@/components/ui/definition-list';
import {Separator} from '@/components/ui/separator';
import {Skeleton} from '@/components/ui/skeleton';
import {errorReason, runAction} from '@/lib/actions';

export interface ConfigVersionDifferProps {
    namespace: string;
    name: string;
    version: ConfigResource;
    onSuccess: () => void;
}

export function ConfigVersionDiffer({namespace, name, version, onSuccess}: ConfigVersionDifferProps) {
    const [reason, setReason] = useState<string>();
    const {result: current, error, execute: reload} = useQuery<string, ConfigResource>({
        query: name,
        execute: (query, abortController) => configs.get(namespace, query, abortController),
        onError: async failure => setReason(await errorReason(failure)),
    });
    const [diffEditor, setDiffEditor] = useState<MonacoDiffEditor>();

    // Monaco holds on to every model until it is disposed. Left to the DiffEditor, its two would be disposed while the
    // diff still shows them, which Monaco reports as a bug; so it keeps them, and they are disposed here once the diff
    // has let go of them. The cleanup of a layout effect runs as the drawer closes, before the DiffEditor's own.
    useLayoutEffect(() => {
        if (!diffEditor) return;
        // onMount hands the editor over with the two models it compares.
        const {original, modified} = diffEditor.getModel()!;
        return () => {
            diffEditor.setModel(null);
            original.dispose();
            modified.dispose();
        };
    }, [diffEditor]);

    const handleRollback = async () => {
        const rolledBack = await runAction(
            () => configs.rollback(namespace, name, version.revision, current?.revision),
            {success: 'Rolled back', failure: 'Failed to roll back'},
        );
        if (rolledBack) {
            onSuccess();
        }
    };

    if (error) {
        return (
            <div role="alert" className="space-y-4 rounded-xl border border-destructive/30 bg-destructive/5 p-6 text-sm">
                <p>Failed to load the current version{reason ? `: ${reason}` : '.'}</p>
                <p>Nothing can be compared or rolled back until it loads.</p>
                <Button variant="outline" onClick={() => reload()}>Retry</Button>
            </div>
        );
    }
    if (!current) {
        return <Skeleton className="h-96 w-full"/>;
    }
    return (
        <div className="space-y-5">
            <DefinitionList items={[
                {label: 'Name', value: name},
                {label: 'This revision', value: version.revision},
                {label: 'Current revision', value: current.revision},
                {label: 'Updated', value: dayjs(version.updatedAt).format('YYYY-MM-DD HH:mm:ss')},
                {label: 'Updated by', value: version.updatedBy},
                {label: 'SHA-256', value: version.sha256},
            ]}/>
            <Separator/>
            <DiffEditor
                key={`diff-${namespace}-${name}-${version.revision}`}
                height="60vh"
                theme="vs-dark"
                language={editorLanguage(current.format)}
                original={version.content}
                modified={current.content}
                keepCurrentOriginalModel
                keepCurrentModifiedModel
                onMount={setDiffEditor}
                options={{readOnly: true, minimap: {enabled: false}}}
            />
            <Separator/>
            <ConfirmButton
                title="Roll back to this revision?"
                description={`Configuration “${name}” will get the content of revision ${version.revision}, as a new revision.`}
                onConfirm={handleRollback}
                disabled={version.revision === current.revision}
                className="w-full"
            >
                Roll back to revision {version.revision}
            </ConfirmButton>
        </div>
    );
}
