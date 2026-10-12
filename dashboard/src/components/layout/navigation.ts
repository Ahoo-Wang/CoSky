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

import {Cloud, FileText, History, LayoutDashboard, Network, ShieldCheck, User} from 'lucide-react';
import type {LucideIcon} from 'lucide-react';

export interface NavigationItem {
    to: string;
    label: string;
    description: string;
    icon: LucideIcon;
}

export const primaryItems: NavigationItem[] = [
    {to: '/home', label: 'Dashboard', description: 'System health and topology', icon: LayoutDashboard},
    {to: '/config', label: 'Configuration', description: 'Manage configuration files', icon: FileText},
    {to: '/service', label: 'Service', description: 'Services and instances', icon: Cloud},
    {to: '/namespace', label: 'Namespace', description: 'Isolation boundaries', icon: Network},
];

export const securityItems: NavigationItem[] = [
    {to: '/user', label: 'User', description: 'Accounts and role assignments', icon: User},
    {to: '/role', label: 'Role', description: 'Access policies', icon: ShieldCheck},
    {to: '/audit-log', label: 'Audit Log', description: 'Security and operation history', icon: History},
];

export const navigationItems: NavigationItem[] = [...primaryItems, ...securityItems];
