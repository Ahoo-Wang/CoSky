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
import { Layout, Menu, Dropdown, Watermark, Modal, Form, Input, message } from 'antd';
import {
  DashboardOutlined,
  RadarChartOutlined,
  FileOutlined,
  CloudServerOutlined,
  PartitionOutlined,
  SecurityScanOutlined,
  UserOutlined,
  AuditOutlined,
  LogoutOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  DownOutlined,
} from '@ant-design/icons';
import { Outlet, useNavigate, useLocation } from 'react-router-dom';
import { NamespaceSelector } from '../common/NamespaceSelector';
import { userApiClient } from '../../client/clients';
import {useSecurityContext} from "@ahoo-wang/fetcher-react";

const { Header, Sider, Content, Footer } = Layout;

export const AuthenticatedLayout: React.FC = () => {
  const [collapsed, setCollapsed] = useState(false);
  const [changePwdVisible, setChangePwdVisible] = useState(false);
  const { currentUser, signOut } = useSecurityContext();
  const navigate = useNavigate();
  const location = useLocation();
  const [form] = Form.useForm();

  const menuItems = [
    {
      key: '/home',
      icon: <DashboardOutlined />,
      label: 'Dashboard',
    },
    {
      key: '/topology',
      icon: <RadarChartOutlined />,
      label: 'Topology',
    },
    {
      key: '/config',
      icon: <FileOutlined />,
      label: 'Configuration',
    },
    {
      key: '/service',
      icon: <CloudServerOutlined />,
      label: 'Service',
    },
    {
      key: '/namespace',
      icon: <PartitionOutlined />,
      label: 'Namespace',
    },
    {
      key: 'security',
      icon: <SecurityScanOutlined />,
      label: 'Security',
      children: [
        {
          key: '/user',
          icon: <UserOutlined />,
          label: 'User',
        },
        {
          key: '/role',
          icon: <SecurityScanOutlined />,
          label: 'Role',
        },
        {
          key: '/audit-log',
          icon: <AuditOutlined />,
          label: 'Audit Log',
        },
      ],
    },
  ];

  const handleMenuClick = ({ key }: { key: string }) => {
    if (key !== 'security') {
      navigate(key);
    }
  };

  const handleChangePwd = async (values: { oldPassword: string; newPassword: string }) => {
    try {
      await userApiClient.changePwd(currentUser.sub, {
        body: {
          oldPassword: values.oldPassword,
          newPassword: values.newPassword,
        },
      });
      message.success('Password reset complete!');
      setChangePwdVisible(false);
      form.resetFields();
    } catch (error) {
      console.error('Failed to change password:', error);
      message.error('Failed to change password');
    }
  };

  const userMenuItems = [
    {
      key: 'changePwd',
      label: 'Change Password',
      onClick: () => setChangePwdVisible(true),
    },
    {
      type: 'divider' as const,
    },
    {
      key: 'signOut',
      label: (
        <>
          <LogoutOutlined /> Sign out
        </>
      ),
      danger: true,
      onClick: signOut,
    },
  ];

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sider collapsible collapsed={collapsed} onCollapse={setCollapsed} breakpoint="md">
        <div style={{ 
          height: 64, 
          display: 'flex', 
          alignItems: 'center', 
          justifyContent: 'center',
          color: 'white'
        }}>
          <a 
            href="https://github.com/Ahoo-Wang/CoSky" 
            target="_blank" 
            rel="noopener noreferrer"
            style={{ color: 'white', textDecoration: 'none' }}
          >
            <h1 style={{ margin: 0, fontSize: collapsed ? 18 : 24 }}>
              {collapsed ? 'CS' : 'CoSky'}
            </h1>
          </a>
        </div>
        <Menu
          theme="dark"
          mode="inline"
          selectedKeys={[location.pathname]}
          items={menuItems}
          onClick={handleMenuClick}
        />
      </Sider>
      <Layout>
        <Header style={{ 
          padding: '0 16px', 
          background: '#fff', 
          display: 'flex', 
          alignItems: 'center',
          justifyContent: 'space-between'
        }}>
          <div style={{ display: 'flex', alignItems: 'center' }}>
            {React.createElement(collapsed ? MenuUnfoldOutlined : MenuFoldOutlined, {
              style: { fontSize: 18, cursor: 'pointer' },
              onClick: () => setCollapsed(!collapsed),
            })}
            <NamespaceSelector />
          </div>
          <Dropdown menu={{ items: userMenuItems }}>
            <a onClick={(e) => e.preventDefault()} style={{ color: 'inherit' }}>
              <UserOutlined /> {currentUser.sub} <DownOutlined />
            </a>
          </Dropdown>
        </Header>
        <Content style={{ margin: '16px' }}>
          <Watermark content={currentUser.sub}>
            <div style={{ 
              padding: 24, 
              minHeight: 360, 
              background: '#fff',
              borderRadius: 8 
            }}>
              <Outlet />
            </div>
          </Watermark>
        </Content>
        <Footer style={{ textAlign: 'center' }}>
          <a 
            href="https://github.com/Ahoo-Wang/CoSky" 
            target="_blank" 
            rel="noopener noreferrer"
            title="High-performance, low-cost microservice governance platform. Service Discovery and Configuration Service."
          >
            CoSky
          </a>
          {' © 2021-present'}
        </Footer>
      </Layout>

      <Modal
        title={`Change User:[${currentUser.sub}] Password`}
        open={changePwdVisible}
        onCancel={() => {
          setChangePwdVisible(false);
          form.resetFields();
        }}
        onOk={() => form.submit()}
        width={600}
      >
        <Form form={form} layout="vertical" onFinish={handleChangePwd}>
          <Form.Item
            name="oldPassword"
            label="Old Password"
            rules={[{ required: true, message: 'Please input old password!' }]}
          >
            <Input.Password />
          </Form.Item>
          <Form.Item
            name="newPassword"
            label="New Password"
            rules={[{ required: true, message: 'Please input new password!' }]}
          >
            <Input.Password />
          </Form.Item>
        </Form>
      </Modal>
    </Layout>
  );
};
