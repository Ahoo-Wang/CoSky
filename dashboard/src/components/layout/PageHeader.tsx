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

import {Space, type SpaceProps} from 'antd';

interface PageHeaderProps {
    title: string;
    actions?: React.ReactNode;
    spaceProps?: SpaceProps;
}

export function PageHeader({title, actions, spaceProps}: PageHeaderProps) {
    return (
        <div style={{
            marginBottom: 24,
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
        }}>
            <h2 style={{
                margin: 0,
                fontSize: 28,
                fontWeight: 600,
                color: '#262626',
                letterSpacing: '-0.5px',
            }}>
                {title}
            </h2>
            {actions && (
                <Space size="middle" {...spaceProps}>
                    {actions}
                </Space>
            )}
        </div>
    );
}
