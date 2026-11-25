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
import {toast} from 'sonner';
import {copyWithFeedback} from '@/lib/clipboard';
import {session} from '@/security/session';

/**
 * Offers a new token that arrived when something else was on screen: it is shown only once, so it is offered rather
 * than lost. The offer lasts until it is closed or whoever is signed in changes, since the toasts outlive the pages and
 * signing out need not unload this one: the next person at this computer must find no token to copy.
 *
 * @param signIns `session.signIns()` when the token was asked for: one that arrives after its asker signed out is not
 * offered at all.
 */
export function offerToken(title: string, token: string, signIns: number): void {
    if (session.signIns() !== signIns) {
        return;
    }
    let stop = () => {};
    const id = toast.success(title, {
        description: 'It is shown only once: copy it now.',
        duration: Infinity,
        closeButton: true,
        action: {
            label: 'Copy token',
            onClick: () => {
                stop();
                void copyWithFeedback(token, 'the token');
            },
        },
        onDismiss: () => stop(),
    });
    const unsubscribe = session.subscribe(() => {
        if (session.signIns() !== signIns) {
            unsubscribe();
            toast.dismiss(id);
        }
    });
    stop = unsubscribe;
}
