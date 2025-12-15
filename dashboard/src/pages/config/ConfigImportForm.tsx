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

import React, { useState } from 'react';
import { Button, Space } from 'antd';
import Editor from '@monaco-editor/react';

interface ConfigImportFormProps {
  onImport: (data: string) => Promise<void>;
  onCancel: () => void;
}

export const ConfigImportForm: React.FC<ConfigImportFormProps> = ({ onImport, onCancel }) => {
  const [importData, setImportData] = useState('');

  const handleImport = async () => {
    await onImport(importData);
    setImportData('');
  };

  return (
    <div>
      <h3 style={{ marginBottom: 16 }}>Import Configs</h3>
      <Editor
        height="500px"
        defaultLanguage="json"
        value={importData}
        onChange={(value) => setImportData(value || '')}
        options={{
          minimap: { enabled: false },
        }}
      />
      <div style={{ marginTop: 16 }}>
        <Space>
          <Button type="primary" onClick={handleImport}>
            Import
          </Button>
          <Button onClick={onCancel}>
            Cancel
          </Button>
        </Space>
      </div>
    </div>
  );
};
