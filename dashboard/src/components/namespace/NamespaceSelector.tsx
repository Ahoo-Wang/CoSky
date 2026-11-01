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

import {useMe} from "@/contexts/MeContext";
import {useNamespacesContext} from "@/contexts/namespace/NamespacesContext";
import type {MeResponse} from "@/generated";
import {OptionsSelect} from "@/components/ui/options-select";
import type {OptionsSelectProps} from "@/components/ui/options-select";

/**
 * A subject bound to some namespaces may not list them, since they live in `_system`, but its rules name them.
 */
const namespacesInRules = (rules: Partial<MeResponse>['rules'] = []) =>
    [...new Set(rules.flatMap(rule => rule?.namespaces ?? []))]
        .filter(namespace => namespace !== '*' && namespace !== '_system')
        .sort();

export type NamespaceSelectorProps = Omit<OptionsSelectProps, 'options'>;

export function NamespaceSelector(props: NamespaceSelectorProps) {
    const {namespaces, loading} = useNamespacesContext();
    const me = useMe();
    const names = namespaces.length > 0 ? namespaces.map(namespace => namespace.name) : namespacesInRules(me.rules);
    const availableNamespaces = props.value && !names.includes(props.value)
        ? [props.value, ...names]
        : names;
    const options = availableNamespaces.map(namespace => ({
        label: namespace,
        value: namespace,
    }))
    return (
        <OptionsSelect
            disabled={loading || props.disabled}
            options={options}
            placeholder="Select Namespace"
            {...props}
        />
    );
}
