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

import { Card, Row, Col, Statistic, Typography } from 'antd'
import {
  CloudServerOutlined,
  SettingOutlined,
  AppstoreOutlined,
} from '@ant-design/icons'
import { useNamespace } from '../contexts/NamespaceContext'

const { Title } = Typography

export default function Home() {
  const { currentNamespace } = useNamespace()

  return (
    <div>
      <Title level={2}>Dashboard</Title>
      <p>Current namespace: {currentNamespace}</p>

      <Row gutter={16} style={{ marginTop: 24 }}>
        <Col span={8}>
          <Card>
            <Statistic
              title="Services"
              value={0}
              prefix={<CloudServerOutlined />}
            />
          </Card>
        </Col>
        <Col span={8}>
          <Card>
            <Statistic
              title="Configs"
              value={0}
              prefix={<SettingOutlined />}
            />
          </Card>
        </Col>
        <Col span={8}>
          <Card>
            <Statistic
              title="Namespaces"
              value={0}
              prefix={<AppstoreOutlined />}
            />
          </Card>
        </Col>
      </Row>
    </div>
  )
}
