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

import {useRef, useState} from 'react';
import {UploadCloud} from 'lucide-react';
import {cn} from '@/lib/utils';

export interface FileDropzoneProps {
    accept: string;
    onFile: (file: File) => void;
    hint?: string;
}

export function FileDropzone({accept, onFile, hint = 'Click or drag file to upload'}: FileDropzoneProps) {
    const [dragging, setDragging] = useState(false);
    const inputRef = useRef<HTMLInputElement>(null);
    return (
        <div
            onClick={() => inputRef.current?.click()}
            onDragOver={(e) => { e.preventDefault(); setDragging(true); }}
            onDragLeave={() => setDragging(false)}
            onDrop={(e) => {
                e.preventDefault();
                setDragging(false);
                const file = e.dataTransfer.files?.[0];
                if (file) onFile(file);
            }}
            className={cn(
                'flex cursor-pointer flex-col items-center justify-center gap-2 rounded-lg border-2 border-dashed p-8 text-center transition-colors',
                dragging ? 'border-primary bg-primary/5' : 'border-border hover:border-primary/50',
            )}
        >
            <UploadCloud className="h-8 w-8 text-muted-foreground"/>
            <p className="text-sm text-muted-foreground">{hint}</p>
            <input
                ref={inputRef}
                type="file"
                accept={accept}
                className="hidden"
                onChange={(e) => {
                    const file = e.target.files?.[0];
                    if (file) onFile(file);
                    e.target.value = '';
                }}
            />
        </div>
    );
}
