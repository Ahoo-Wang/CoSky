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

import React, { useState, useEffect } from 'react';
import { Select } from 'antd';
import { useNamespace } from '../../contexts/NamespaceContext';
import { NamespaceApiClient } from '../../generated';

const namespaceApiClient = new NamespaceApiClient();

export const NamespaceSelector: React.FC = () => {
  const { currentNamespace, setCurrent } = useNamespace();
  const [namespaces, setNamespaces] = useState<string[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    loadNamespaces();
  }, []);

  const loadNamespaces = async () => {
    setLoading(true);
    try {
      const result = await namespaceApiClient.getNamespaces();
      setNamespaces(result || []);
    } catch (error) {
      console.error('Failed to load namespaces:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleChange = (value: string) => {
    setCurrent(value);
  };

  return (
    <Select
      style={{ width: 200, marginLeft: 16 }}
      value={currentNamespace}
      onChange={handleChange}
      loading={loading}
      placeholder="Select Namespace"
    >
      {namespaces.map((ns) => (
        <Select.Option key={ns} value={ns}>
          {ns}
        </Select.Option>
      ))}
    </Select>
  );
};
