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

import React from 'react';
import { Table, Button } from 'antd';

interface ConfigVersionsViewProps {
  configId: string;
  versions: any[];
  onClose: () => void;
}

export const ConfigVersionsView: React.FC<ConfigVersionsViewProps> = ({ configId, versions, onClose }) => {
  const columns = [
    { title: 'Version', dataIndex: 'version', key: 'version' },
    { title: 'Create Time', dataIndex: 'createTime', key: 'createTime' },
  ];

  return (
    <div>
      <h3 style={{ marginBottom: 16 }}>Config Versions: {configId}</h3>
      <Table
        dataSource={versions.map((v: any, i: number) => ({ ...v, key: i }))}
        columns={columns}
        pagination={false}
      />
      <div style={{ marginTop: 16 }}>
        <Button onClick={onClose}>
          Close
        </Button>
      </div>
    </div>
  );
};
