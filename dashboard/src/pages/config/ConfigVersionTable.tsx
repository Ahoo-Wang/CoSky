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

import {Button, Table} from 'antd';
import {useQuery} from "@ahoo-wang/fetcher-react";
import {configApiClient} from "../../services/clients.ts";
import {ConfigVersion} from "../../generated";
import {ColumnsType} from "antd/es/table/interface";
import {useDrawer} from "../../contexts/DrawerContext.tsx";
import {ConfigVersionDiffer} from "./ConfigVersionDiffer.tsx";

interface ConfigVersionTableProps {
    namespace: string;
    configId: string;
}

export function ConfigVersionTable({namespace, configId}: ConfigVersionTableProps) {
    const {loading, result: versions, execute: loadVersions} = useQuery<string, ConfigVersion[]>({
        query: configId,
        execute: (query, attributes, abortController) => {
            return configApiClient.getConfigVersions(namespace, query, attributes, abortController);
        }
    })
    const {openDrawer, closeDrawer} = useDrawer();
    const handleDiffVersion = (record: ConfigVersion) => {
        openDrawer(<ConfigVersionDiffer namespace={namespace} configId={configId} version={record.version}
                                        onSuccess={() => {
                                            closeDrawer();
                                            loadVersions()
                                        }}/>,
            {
                title: 'Config Version Differ',
                width: '80vw',
            }
        )
    }
    const columns: ColumnsType<ConfigVersion> = [
        {title: 'Version', dataIndex: 'version', key: 'version'},
        {
            title: 'Action', key: 'action', render: (_, record) => (
                <Button type={'link'} onClick={() => {
                    handleDiffVersion(record)
                }}>Diff</Button>
            )
        }
    ];

    return (
        <Table
            dataSource={versions}
            rowKey="configId"
            columns={columns}
            loading={loading}
        />
    );
}
