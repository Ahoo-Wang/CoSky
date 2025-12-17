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

import {BrowserRouter} from 'react-router-dom';
import {ConfigProvider, theme} from 'antd';
import './services/fetcher'
import ErrorBoundary from "antd/es/alert/ErrorBoundary";
import {AppRoutes} from "./AppRoutes.tsx";
import { App as AntdApp } from 'antd';

function App() {
    return (
        <ConfigProvider
            theme={{
                algorithm: theme.defaultAlgorithm,
                token: {
                    colorPrimary: '#667eea',
                    borderRadius: 8,
                    colorBgContainer: '#ffffff',
                },
                components: {
                    Layout: {
                        headerBg: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
                        siderBg: '#001529',
                    },
                    Card: {
                        borderRadiusLG: 12,
                    },
                },
            }}
        >
            <AntdApp>
                <ErrorBoundary>
                    <BrowserRouter>
                        <AppRoutes />
                    </BrowserRouter>
                </ErrorBoundary>
            </AntdApp>
        </ConfigProvider>
    );
}

export default App;
