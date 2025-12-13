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
import { Card, Row, Col, Statistic } from 'antd';
import { 
  PartitionOutlined, 
  FileOutlined, 
  CloudServerOutlined, 
  ClusterOutlined 
} from '@ant-design/icons';
import { useNamespace } from '../../contexts/NamespaceContext';
import { StatApiClient } from '../../generated';

const statApiClient = new StatApiClient();

export const DashboardPage: React.FC = () => {
  const { currentNamespace } = useNamespace();
  const [stat, setStat] = useState({
    namespaces: 0,
    configs: 0,
    services: { total: 0, health: 0 },
    instances: 0,
  });

  useEffect(() => {
    loadStat();
  }, [currentNamespace]);

  const loadStat = async () => {
    try {
      const result = await statApiClient.getStat(currentNamespace);
      setStat(result);
    } catch (error) {
      console.error('Failed to load statistics:', error);
    }
  };

  return (
    <div>
      <h2 style={{ marginBottom: 24 }}>Dashboard</h2>
      <Row gutter={[16, 16]}>
        <Col xs={24} sm={12} lg={6}>
          <Card>
            <Statistic
              title="Namespace Count"
              value={stat.namespaces}
              prefix={<PartitionOutlined />}
              valueStyle={{ color: '#3f8600' }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card>
            <Statistic
              title="Config Count"
              value={stat.configs}
              prefix={<FileOutlined />}
              valueStyle={{ color: '#1890ff' }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card>
            <Statistic
              title="Service Count"
              value={stat.services.total}
              prefix={<CloudServerOutlined />}
              valueStyle={{ color: '#cf1322' }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card>
            <Statistic
              title="Instance Count"
              value={stat.instances}
              prefix={<ClusterOutlined />}
              valueStyle={{ color: '#722ed1' }}
            />
          </Card>
        </Col>
      </Row>
    </div>
  );
};
