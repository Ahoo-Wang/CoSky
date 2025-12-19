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
import {Navigate} from 'react-router-dom';
import {RefreshableRouteGuard} from "@ahoo-wang/fetcher-react";
import {coSecConfigurer} from "../../services/fetcher.ts";
import {Skeleton} from "antd";

interface ProtectedRouteProps {
    children: React.ReactNode;
}

export const ProtectedRoute: React.FC<ProtectedRouteProps> = ({children}) => {
    return (
        <RefreshableRouteGuard
            tokenManager={coSecConfigurer.tokenManager!}
            fallback={<Navigate to="/login" replace/>}
            refreshing={<Skeleton/>}>
            {children}
        </RefreshableRouteGuard>)

};
