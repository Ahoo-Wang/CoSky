import {useNamespacesContext} from "../../contexts/namespace/NamespacesContext.tsx";
import {OptionsSelect} from "@/components/ui/options-select";
import type {OptionsSelectProps} from "@/components/ui/options-select";

export type NamespaceSelectorProps = Omit<OptionsSelectProps, 'options'>;

export function NamespaceSelector(props: NamespaceSelectorProps) {
    const {namespaces, loading} = useNamespacesContext();
    const availableNamespaces = props.value && !namespaces.includes(props.value)
        ? [props.value, ...namespaces]
        : namespaces;
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
