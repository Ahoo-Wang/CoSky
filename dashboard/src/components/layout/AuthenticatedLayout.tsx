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

import React, {useState} from 'react';
import {Layout, Menu, Dropdown, Watermark} from 'antd';
import {
    DashboardOutlined,
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
import {Outlet, useNavigate, useLocation} from 'react-router-dom';
import {CurrentNamespaceSelector} from './CurrentNamespaceSelector.tsx';
import {useSecurityContext} from "@ahoo-wang/fetcher-react";
import {useDrawer} from "../../contexts/DrawerContext.tsx";
import {ChangePwd} from "../security/ChangePwd.tsx";

const {Header, Sider, Content, Footer} = Layout;

export const AuthenticatedLayout: React.FC = () => {
    const [collapsed, setCollapsed] = useState(false);
    const {currentUser, signOut} = useSecurityContext();
    const navigate = useNavigate();
    const location = useLocation();
    const {openDrawer, closeDrawer} = useDrawer()
    const handleChangePwd = () => {
        openDrawer(
            <ChangePwd onSubmit={closeDrawer} onCancel={closeDrawer}/>,
            {
                title: 'Change Password',
                width: 500,
            }
        );
    }
    const menuItems = [
        {
            key: '/home',
            icon: <DashboardOutlined/>,
            label: 'Dashboard',
        },
        {
            key: '/config',
            icon: <FileOutlined/>,
            label: 'Configuration',
        },
        {
            key: '/service',
            icon: <CloudServerOutlined/>,
            label: 'Service',
        },
        {
            key: '/namespace',
            icon: <PartitionOutlined/>,
            label: 'Namespace',
        },
        {
            key: 'security',
            icon: <SecurityScanOutlined/>,
            label: 'Security',
            children: [
                {
                    key: '/user',
                    icon: <UserOutlined/>,
                    label: 'User',
                },
                {
                    key: '/role',
                    icon: <SecurityScanOutlined/>,
                    label: 'Role',
                },
                {
                    key: '/audit-log',
                    icon: <AuditOutlined/>,
                    label: 'Audit Log',
                },
            ],
        },
    ];

    const handleMenuClick = ({key}: { key: string }) => {
        if (key !== 'security') {
            navigate(key);
        }
    };

    const userMenuItems = [
        {
            key: 'changePwd',
            label: 'Change Password',
            onClick: () => handleChangePwd(),
        },
        {
            type: 'divider' as const,
        },
        {
            key: 'signOut',
            label: (
                <>
                    <LogoutOutlined/> Sign out
                </>
            ),
            danger: true,
            onClick: signOut,
        },
    ];

    return (
        <Layout style={{minHeight: '100vh'}}>
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
                        style={{color: 'white', textDecoration: 'none'}}
                    >
                        <h1 style={{margin: 0, fontSize: collapsed ? 18 : 24}}>
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
                    <div style={{display: 'flex', alignItems: 'center'}}>
                        {React.createElement(collapsed ? MenuUnfoldOutlined : MenuFoldOutlined, {
                            style: {fontSize: 18, cursor: 'pointer'},
                            onClick: () => setCollapsed(!collapsed),
                        })}
                        <CurrentNamespaceSelector/>
                    </div>
                    <Dropdown menu={{items: userMenuItems}}>
                        <a onClick={(e) => e.preventDefault()} style={{color: 'inherit'}}>
                            <UserOutlined/> {currentUser.sub} <DownOutlined/>
                        </a>
                    </Dropdown>
                </Header>
                <Content style={{margin: '16px', display: 'flex', flexDirection: 'column'}}>
                    <Watermark content={currentUser.sub}>
                        <div style={{
                            padding: 24,
                            minHeight: 360,
                            background: '#fff',
                            borderRadius: 8,
                            flex: 1,
                            display: 'flex',
                            flexDirection: 'column'
                        }}>
                            <Outlet/>
                        </div>
                    </Watermark>
                </Content>
                <Footer style={{textAlign: 'center'}}>
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
        </Layout>
    );
};
