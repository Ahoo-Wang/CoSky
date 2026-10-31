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

import {DiffEditor} from '@monaco-editor/react';
import {useQuery} from '@ahoo-wang/fetcher-react';
import dayjs from 'dayjs';
import {configs} from '@/services/resources';
import type {ConfigResource} from '@/services/resources';
import {editorLanguage} from '@/pages/config/formats';
import {ConfirmButton} from '@/components/ui/confirm-button';
import {DefinitionList} from '@/components/ui/definition-list';
import {Separator} from '@/components/ui/separator';
import {Skeleton} from '@/components/ui/skeleton';
import {runAction} from '@/lib/actions';

export interface ConfigVersionDifferProps {
    namespace: string;
    name: string;
    version: ConfigResource;
    onSuccess: () => void;
}

export function ConfigVersionDiffer({namespace, name, version, onSuccess}: ConfigVersionDifferProps) {
    const {result: current} = useQuery<string, ConfigResource>({
        query: name,
        execute: (query, _, abortController) => configs.get(namespace, query, abortController),
    });

    const handleRollback = async () => {
        const rolledBack = await runAction(
            () => configs.rollback(namespace, name, version.revision),
            {success: 'Rolled back', failure: 'Failed to roll back'},
        );
        if (rolledBack) {
            onSuccess();
        }
    };

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
