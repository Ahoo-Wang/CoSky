/*
 * Copyright [2021-present] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)]
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

import {useEffect, useState, type RefObject} from 'react';
import {Maximize2, Minimize2} from 'lucide-react';
import {Button} from '@/components/ui/button';

export function FullscreenButton({targetRef}: { targetRef: RefObject<HTMLElement | null> }) {
    const [isFullscreen, setIsFullscreen] = useState(false);
    useEffect(() => {
        const onChange = () => setIsFullscreen(Boolean(document.fullscreenElement));
        document.addEventListener('fullscreenchange', onChange);
        return () => document.removeEventListener('fullscreenchange', onChange);
    }, []);
    const toggle = () => {
        if (document.fullscreenElement) {
            void document.exitFullscreen();
        } else {
            void targetRef.current?.requestFullscreen();
        }
    };
    return (
        <Button variant="outline" size="icon" onClick={toggle} aria-label="Toggle fullscreen">
            {isFullscreen ? <Minimize2 className="h-4 w-4"/> : <Maximize2 className="h-4 w-4"/>}
        </Button>
    );
}
