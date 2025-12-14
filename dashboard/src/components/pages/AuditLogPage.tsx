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
import {Table} from 'antd';
import {QueryLogResponse} from '../../generated';
import dayjs from 'dayjs';
import {useQuery} from "@ahoo-wang/fetcher-react";
import {auditLogApiClient} from "../../client/clients.ts";

type Paging = {
    pageIndex: number;
    pageSize: number;
};

export const AuditLogPage: React.FC = () => {
    const {result, loading, setQuery} = useQuery<Paging, QueryLogResponse>({
        initialQuery: {
            pageIndex: 1,
            pageSize: 10,
        },
        execute: (query, _, abortController) => {
            return auditLogApiClient.queryLog(query.pageIndex, query.pageSize, {abortController});
        },
    })

    const columns = [
        {
            title: 'Timestamp',
            dataIndex: 'timestamp',
            key: 'timestamp',
            render: (timestamp: number) => dayjs(timestamp).format('YYYY-MM-DD HH:mm:ss'),
        },
        {
            title: 'Operator',
            dataIndex: 'operator',
            key: 'operator',
        },
        {
            title: 'ClientIP',
            dataIndex: 'ip',
            key: 'ip',
        },
        {
            title: 'Resource',
            dataIndex: 'resource',
            key: 'resource',
        },
        {
            title: 'Action',
            dataIndex: 'action',
            key: 'action',
        },
        {
            title: 'Status',
            dataIndex: 'status',
            key: 'status',
        },
        {
            title: 'Msg',
            dataIndex: 'msg',
            key: 'msg',
        },
    ];

    return (
        <div>
            <Table
                columns={columns}
                dataSource={result?.list}
                pagination={{
                    total: result?.total,
                    showTotal: (total) => `Total ${total} items`,
                    onChange: (pageIndex, pageSize) => {
                        setQuery({pageIndex, pageSize});
                    }
                }}
                loading={loading}
            />
        </div>
    );
};
