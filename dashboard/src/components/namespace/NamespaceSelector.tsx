import {useNamespaces} from "../../hooks/useNamespaces.ts";
import {Select, SelectProps} from "antd";

export type NamespaceSelectorProps = Omit<SelectProps<string>, 'loading' | 'options'>

export function NamespaceSelector(props: NamespaceSelectorProps) {
    const {namespaces, loading} = useNamespaces();
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