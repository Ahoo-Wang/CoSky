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

/** How many rows a list may show on a page. The first is what it shows until someone chooses another. */
export const PAGE_SIZES: readonly number[] = [10, 20, 50, 100];

export const DEFAULT_PAGE_SIZE = PAGE_SIZES[0];

const keyOf = (list: string) => `cosky:page-size:${list}`;

/** Reaching it throws where the browser keeps nothing for this page, as with storage turned off. */
const browserStorage = (): Storage => localStorage;

/**
 * The page size last chosen for [list] in this browser. Without storage, or with anything but a size offered stored
 * there, it is the default.
 */
export function storedPageSize(list: string, storage: () => Storage = browserStorage): number {
    try {
        const size = Number(storage().getItem(keyOf(list)));
        return PAGE_SIZES.includes(size) ? size : DEFAULT_PAGE_SIZE;
    } catch {
        return DEFAULT_PAGE_SIZE;
    }
}

/** Remembers [size] for [list] in this browser, where it lets the page keep anything. */
export function storePageSize(list: string, size: number, storage: () => Storage = browserStorage): void {
    try {
        storage().setItem(keyOf(list), String(size));
    } catch {
        // Storage turned off or full: the choice lasts as long as the page.
    }
}

/** The page that holds the first row of [page] once pages hold [to] rows instead of [from]. */
export function pageHolding(page: number, from: number, to: number): number {
    return Math.floor((page - 1) * from / to) + 1;
}
