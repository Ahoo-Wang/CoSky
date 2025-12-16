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

import React, {useState, useEffect} from 'react';
import {Button, Space} from 'antd';
import Editor from '@monaco-editor/react';

interface ConfigEditFormProps {
    configId: string;
    isAdd: boolean;
    onSuccess: () => void;
    onCancel: () => void;
}

export const ConfigEditor: React.FC<ConfigEditFormProps> = ({configId, isAdd, onSuccess, onCancel}) => {
    useEffect(() => {
        setConfigData(config?.data || '');
    }, [config]);

    const handleSave = async () => {
        await onSave(configData);
    };

    return (
        <div>
            <h3 style={{marginBottom: 16}}>
                {config ? `Edit Config: ${config.configId}` : 'Add Config'}
            </h3>
            <Editor
                height="500px"
                defaultLanguage="yaml"
                value={configData}
                onChange={(value) => setConfigData(value || '')}
                options={{
                    minimap: {enabled: false},
                }}
            />
            <div style={{marginTop: 16}}>
                <Space>
                    <Button type="primary" onClick={handleSave}>
                        Save
                    </Button>
                    <Button onClick={onCancel}>
                        Cancel
                    </Button>
                </Space>
            </div>
        </div>
    );
};
