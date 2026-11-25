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

import {Button} from '@/components/ui/button';
import {Input} from '@/components/ui/input';

/**
 * The only time anyone sees the plaintext of a token.
 */
export function NewToken({token, onClose}: {token: string; onClose: () => void}) {
    return (
        <div className="space-y-4">
            <p className="text-sm text-muted-foreground">Copy this token now. The server keeps only its hash, and cannot show it again.</p>
            <Input readOnly aria-label="New token" value={token} onFocus={event => event.target.select()}/>
            <Button onClick={onClose}>Done</Button>
        </div>
    );
}
