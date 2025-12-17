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
    GithubOutlined,
} from '@ant-design/icons';
import {Outlet, useNavigate, useLocation, NavLink} from 'react-router-dom';
import {CurrentNamespaceSelector} from './CurrentNamespaceSelector.tsx';
import {useSecurityContext} from "@ahoo-wang/fetcher-react";
import {useDrawer} from "../../contexts/DrawerContext.tsx";
import {ChangePwd} from "../security/ChangePwd.tsx";
import {ErrorBoundary} from "../error/ErrorBoundary.tsx";

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
                defaultSize: '20vw',
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
            <Sider
                collapsible
                collapsed={collapsed}
                onCollapse={setCollapsed}
                breakpoint="md"
                style={{
                    boxShadow: '2px 0 8px rgba(0, 0, 0, 0.15)',
                }}
            >
                <div style={{
                    height: 64,
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    color: 'white',
                    background: 'rgba(255, 255, 255, 0.05)',
                    borderBottom: '1px solid rgba(255, 255, 255, 0.1)',
                }}>
                    <NavLink to="/" style={{
                        color: 'white',
                        textDecoration: 'none',
                        transition: 'opacity 0.3s ease',
                    }}
                             onMouseEnter={(e) => e.currentTarget.style.opacity = '0.8'}
                             onMouseLeave={(e) => e.currentTarget.style.opacity = '1'}>
                        <h1 style={{
                            margin: 0,
                            fontSize: collapsed ? 18 : 24,
                            fontWeight: 600,
                            letterSpacing: '0.5px',
                        }}>
                            {collapsed ? 'CS' : 'CoSky'}
                        </h1>
                    </NavLink>

                </div>
                <Menu
                    theme="dark"
                    mode="inline"
                    selectedKeys={[location.pathname]}
                    items={menuItems}
                    onClick={handleMenuClick}
                    style={{
                        borderRight: 'none',
                    }}
                />
            </Sider>
            <Layout>
                <Header style={{
                    padding: '0 24px',
                    background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    boxShadow: '0 2px 8px rgba(0, 0, 0, 0.1)',
                    color: 'white',
                }}>
                    <div style={{display: 'flex', alignItems: 'center', gap: '16px'}}>
                        {React.createElement(collapsed ? MenuUnfoldOutlined : MenuFoldOutlined, {
                            style: {
                                fontSize: 18,
                                cursor: 'pointer',
                                color: 'white',
                                transition: 'transform 0.3s ease',
                            },
                            onClick: () => setCollapsed(!collapsed),
                            onMouseEnter: (e: React.MouseEvent<HTMLElement>) => {
                                (e.currentTarget as HTMLElement).style.transform = 'scale(1.1)';
                            },
                            onMouseLeave: (e: React.MouseEvent<HTMLElement>) => {
                                (e.currentTarget as HTMLElement).style.transform = 'scale(1)';
                            },
                        })}
                        <CurrentNamespaceSelector/>
                    </div>
                    <div style={{display: 'flex', alignItems: 'center', gap: '16px'}}>
                        <Dropdown menu={{items: userMenuItems}}>
                            <a
                                onClick={(e) => e.preventDefault()}
                                style={{
                                    color: 'white',
                                    padding: '8px 12px',
                                    borderRadius: '6px',
                                    transition: 'background 0.3s ease',
                                    display: 'flex',
                                    alignItems: 'center',
                                    gap: '8px',
                                }}
                                onMouseEnter={(e) => {
                                    e.currentTarget.style.background = 'rgba(255, 255, 255, 0.15)';
                                }}
                                onMouseLeave={(e) => {
                                    e.currentTarget.style.background = 'transparent';
                                }}
                            >
                                <UserOutlined/> {currentUser.sub} <DownOutlined/>
                            </a>
                        </Dropdown>
                        <a
                            href="https://github.com/Ahoo-Wang/CoSky"
                            target="_blank"
                            rel="noopener noreferrer"
                            style={{
                                color: 'white',
                                fontSize: 22,
                                display: 'flex',
                                alignItems: 'center',
                                transition: 'transform 0.3s ease, opacity 0.3s ease',
                            }}
                            onMouseEnter={(e) => {
                                e.currentTarget.style.transform = 'scale(1.1)';
                                e.currentTarget.style.opacity = '0.8';
                            }}
                            onMouseLeave={(e) => {
                                e.currentTarget.style.transform = 'scale(1)';
                                e.currentTarget.style.opacity = '1';
                            }}
                        >
                            <GithubOutlined/>
                        </a>
                    </div>
                </Header>
                <Content style={{
                    margin: '24px',
                    display: 'flex',
                    flexDirection: 'column',
                }}>
                    <Watermark content={currentUser.sub}>
                        <div style={{
                            padding: 32,
                            minHeight: 360,
                            background: '#fff',
                            borderRadius: 12,
                            flex: 1,
                            display: 'flex',
                            flexDirection: 'column',
                            boxShadow: '0 1px 2px rgba(0, 0, 0, 0.03), 0 2px 4px rgba(0, 0, 0, 0.03), 0 4px 8px rgba(0, 0, 0, 0.03)',
                        }}>
                            <ErrorBoundary>
                                <Outlet/>
                            </ErrorBoundary>
                        </div>
                    </Watermark>
                </Content>
                <Footer style={{
                    textAlign: 'center',
                    background: 'transparent',
                    color: '#666',
                    fontSize: '14px',
                }}>
                    <a
                        href="https://github.com/Ahoo-Wang/CoSky"
                        target="_blank"
                        rel="noopener noreferrer"
                        title="High-performance, low-cost microservice governance platform. Service Discovery and Configuration Service."
                        style={{
                            color: '#667eea',
                            fontWeight: 500,
                            transition: 'color 0.3s ease',
                        }}
                        onMouseEnter={(e) => {
                            e.currentTarget.style.color = '#764ba2';
                        }}
                        onMouseLeave={(e) => {
                            e.currentTarget.style.color = '#667eea';
                        }}
                    >
                        CoSky
                    </a>
                    {' © 2021-present'}
                </Footer>
            </Layout>
        </Layout>
    );
};
