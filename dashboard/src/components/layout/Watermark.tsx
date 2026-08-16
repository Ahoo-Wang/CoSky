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

import type {ReactNode} from 'react';
import {useTheme} from '@/theme/ThemeProvider';

const escapeXml = (s: string) => s.replace(/[&<>"']/g, (c) => ({'&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&apos;'}[c] as string));

export function Watermark({content, children}: { content: string; children: ReactNode }) {
    const {resolvedTheme} = useTheme();
    const fill = resolvedTheme === 'dark' ? 'rgba(255,255,255,0.05)' : 'rgba(0,0,0,0.06)';
    const svg = `<svg xmlns='http://www.w3.org/2000/svg' width='240' height='160'><text x='20' y='80' transform='rotate(-20 120 80)' fill='${fill}' font-size='16'>${escapeXml(content)}</text></svg>`;
    const backgroundImage = `url("data:image/svg+xml,${encodeURIComponent(svg)}")`;
    return (
        <div className="relative flex-1">
            {children}
            <div aria-hidden className="pointer-events-none absolute inset-0 z-10" style={{backgroundImage, backgroundRepeat: 'repeat'}}/>
        </div>
    );
}
