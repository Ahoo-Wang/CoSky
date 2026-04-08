import type { SelectProps} from "antd";
import {Select} from "antd";
import {useNamespacesContext} from "../../contexts/namespace/useNamespacesContext.ts";

export type NamespaceSelectorProps = Omit<SelectProps<string>, 'loading' | 'options'>

export function NamespaceSelector(props: NamespaceSelectorProps) {
    const {namespaces, loading} = useNamespacesContext();
    const options = namespaces.map(namespace => ({
        label: namespace,
        value: namespace,
    }))
    return (
        <Select
            loading={loading}
            options={options}
            placeholder="Select Namespace"
            {...props}
        />
    );
}