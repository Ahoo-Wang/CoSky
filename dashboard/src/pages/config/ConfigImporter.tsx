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
import {UploadCloud} from 'lucide-react';
import {toast} from 'sonner';
import {configs} from '@/services/resources';
import {Button} from '@/components/ui/button';
import {Label} from '@/components/ui/label';
import {runAction} from '@/lib/actions';

interface ConfigImporterProps {
    namespace: string;
    onSuccess: () => void;
    onCancel: () => void;
}

export function ConfigImporter({namespace, onSuccess, onCancel}: ConfigImporterProps) {
    const [file, setFile] = useState<File>();
    const [importing, setImporting] = useState(false);

    const handleSubmit = async () => {
        if (!file) {
            toast.error('Please select a ZIP file.');
            return;
        }
        setImporting(true);
        const imported = await runAction(
            () => configs.importArchive(namespace, file),
            {success: 'Configs imported', failure: 'Failed to import configs'},
        );
        setImporting(false);
        if (imported) {
            onSuccess();
        }
    };

    return (
        <form className="space-y-5" onSubmit={event => {
            event.preventDefault();
            void handleSubmit();
        }}>
            <Label className="flex min-h-48 cursor-pointer flex-col items-center justify-center rounded-xl border border-dashed text-center">
                <UploadCloud className="mb-3 size-9 text-primary"/>
                <span className="font-medium">{file?.name ?? 'Choose a ZIP file'}</span>
                <span className="mt-1 text-xs font-normal text-muted-foreground">
                    Each entry path is a Config name, and replaces the Config of that name.
                </span>
                <input type="file" accept=".zip,application/zip" className="sr-only"
                       onChange={event => setFile(event.target.files?.[0])}/>
            </Label>
            <div className="flex gap-2">
                <Button type="submit" loading={importing}>Submit</Button>
                <Button type="button" variant="outline" onClick={onCancel}>Cancel</Button>
            </div>
        </form>
    );
}
