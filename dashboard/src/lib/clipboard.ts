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

/**
 * Copies [text], and says whether it did.
 *
 * The Clipboard API exists only in a secure context, HTTPS or localhost. A Dashboard reached over plain HTTP, as
 * through a cluster's address, has no `navigator.clipboard` at all, so the text is copied from a hidden field instead.
 */
export async function copyText(text: string): Promise<boolean> {
    if (navigator.clipboard?.writeText) {
        try {
            await navigator.clipboard.writeText(text);
            return true;
        } catch {
            // Refused, such as without focus: the field below may still work.
        }
    }
    const field = document.createElement('textarea');
    field.value = text;
    field.setAttribute('readonly', '');
    field.style.position = 'fixed';
    field.style.opacity = '0';
    document.body.appendChild(field);
    field.focus();
    field.select();
    try {
        return document.execCommand('copy');
    } catch {
        return false;
    } finally {
        field.remove();
    }
}

/**
 * Copies [text] and says so. When the browser allows no copying, it says to copy by hand: the text is on screen.
 */
export async function copyWithFeedback(text: string, what: string): Promise<void> {
    if (await copyText(text)) {
        toast.success(`Copied ${what}`);
    } else {
        toast.error(`Could not copy ${what}`, {description: 'Select it and copy it yourself.'});
    }
}
