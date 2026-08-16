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

import type { NodeProps } from '@xyflow/react';
import { Handle, Position } from '@xyflow/react';
import {useTheme} from '@/theme/ThemeProvider';
import type {ServiceNodeData} from './topologies.ts';
import {flowThemes} from './topologies.ts';

export function ServiceNode({ data, selected }: NodeProps) {
    const nodeData = data as unknown as ServiceNodeData;
    const { label, nodeType, inDegree, outDegree } = nodeData;
    const {resolvedTheme} = useTheme();
    const flowTheme = flowThemes[resolvedTheme];

    // Get node icon based on type
    const getIcon = () => {
        switch (nodeType) {
            case 'source':
                return '▶';
            case 'target':
                return '⏸';
            case 'intermediate':
                return '⚡';
            default:
                return '●';
        }
    };

    return (
        <div
            style={{
                padding: '8px 12px',
                borderRadius: '6px',
                background: flowTheme.nodeBg,
                color: flowTheme.nodeText,
                border: selected
                    ? `2px solid ${flowTheme.nodeBorder}`
                    : '2px solid transparent',
                boxShadow: selected
                    ? '0 4px 12px rgba(0,0,0,0.3)'
                    : '0 2px 8px rgba(0,0,0,0.15)',
                minWidth: '150px',
                transition: 'all 0.2s ease',
                cursor: 'pointer',
            }}
        >
            {/* Input handle for incoming edges */}
            <Handle
                type="target"
                position={Position.Top}
                style={{
                    background: flowTheme.nodeBorder,
                    width: '8px',
                    height: '8px',
                    border: '2px solid transparent',
                }}
            />

            <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                {/* Icon indicator */}
                <span style={{ fontSize: '14px', opacity: 0.9 }}>
                    {getIcon()}
                </span>

                {/* Label with word wrap support */}
                <div style={{
                    flex: 1,
                    fontSize: '13px',
                    fontWeight: 500,
                    wordWrap: 'break-word',
                    lineHeight: '1.4'
                }}>
                    {label}
                </div>
            </div>

            {/* Degree information */}
            <div style={{
                marginTop: '4px',
                fontSize: '11px',
                opacity: 0.85,
                display: 'flex',
                justifyContent: 'space-between',
            }}>
                <span>In: {inDegree}</span>
                <span>Out: {outDegree}</span>
            </div>

            {/* Output handle for outgoing edges */}
            <Handle
                type="source"
                position={Position.Bottom}
                style={{
                    background: flowTheme.nodeBorder,
                    width: '8px',
                    height: '8px',
                    border: '2px solid transparent',
                }}
            />
        </div>
    );
}
