import {Select, SelectProps} from "antd";

export interface ImportPolicySelectorProps extends Omit<SelectProps, 'options'> {
}

export const IMPORT_POLICY_OPTIONS = [
    {
        label: 'Skip',
        value: 'skip'
    },
    {
        label: 'Overwrite',
        value: 'overwrite'
    }
]

export function ImportPolicySelector(props: ImportPolicySelectorProps) {
    return (
        <Select defaultValue={'skip'} {...props} options={IMPORT_POLICY_OPTIONS}/>
    )
}