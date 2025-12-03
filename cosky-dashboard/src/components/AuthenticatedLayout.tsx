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

import { useEffect } from 'react'
import { Outlet, useNavigate, useLocation } from 'react-router'
import { Layout, Menu, Select, Typography, Button } from 'antd'
import {
  HomeOutlined,
  SettingOutlined,
  CloudServerOutlined,
  AppstoreOutlined,
  UserOutlined,
  TeamOutlined,
  FileTextOutlined,
  ApartmentOutlined,
  LogoutOutlined,
} from '@ant-design/icons'
import { useAuth } from '../contexts/AuthContext'
import { useNamespace } from '../contexts/NamespaceContext'

const { Header, Sider, Content } = Layout
const { Title } = Typography

const menuItems = [
  { key: 'home', icon: <HomeOutlined />, label: 'Home' },
  { key: 'topology', icon: <ApartmentOutlined />, label: 'Topology' },
  { key: 'config', icon: <SettingOutlined />, label: 'Config' },
  { key: 'service', icon: <CloudServerOutlined />, label: 'Service' },
  { key: 'namespace', icon: <AppstoreOutlined />, label: 'Namespace' },
  { key: 'user', icon: <UserOutlined />, label: 'User' },
  { key: 'role', icon: <TeamOutlined />, label: 'Role' },
  { key: 'audit-log', icon: <FileTextOutlined />, label: 'Audit Log' },
]

export default function AuthenticatedLayout() {
  const navigate = useNavigate()
  const location = useLocation()
  const { isAuthenticated, logout } = useAuth()
  const { currentNamespace, setCurrentNamespace } = useNamespace()

  useEffect(() => {
    if (!isAuthenticated) {
      navigate('/login')
    }
  }, [isAuthenticated, navigate])

  const handleMenuClick = ({ key }: { key: string }) => {
    navigate(`/${key}`)
  }

  const handleLogout = () => {
    logout()
    navigate('/login')
  }

  const selectedKey = location.pathname.replace('/', '') || 'home'

  if (!isAuthenticated) {
    return null
  }

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Header style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '0 24px' }}>
        <div style={{ display: 'flex', alignItems: 'center' }}>
          <Title level={4} style={{ color: '#fff', margin: 0 }}>
            CoSky Dashboard
          </Title>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
          <Select
            value={currentNamespace}
            onChange={setCurrentNamespace}
            style={{ width: 150 }}
            options={[{ value: 'default', label: 'default' }]}
          />
          <Button icon={<LogoutOutlined />} onClick={handleLogout}>
            Logout
          </Button>
        </div>
      </Header>
      <Layout>
        <Sider width={200}>
          <Menu
            mode="inline"
            selectedKeys={[selectedKey]}
            style={{ height: '100%', borderRight: 0 }}
            items={menuItems}
            onClick={handleMenuClick}
          />
        </Sider>
        <Layout style={{ padding: '24px' }}>
          <Content
            style={{
              padding: 24,
              margin: 0,
              minHeight: 280,
              background: '#fff',
            }}
          >
            <Outlet />
          </Content>
        </Layout>
      </Layout>
    </Layout>
  )
}
