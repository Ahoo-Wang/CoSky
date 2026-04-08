import type { SelectProps} from "antd";
import {Select} from "antd";
import {IMPORT_POLICY_OPTIONS} from "./ImportPolicySelectorOptions.ts";

export type ImportPolicySelectorProps = Omit<SelectProps, 'options'>

export function ImportPolicySelector(props: ImportPolicySelectorProps) {
    return (
        <Select defaultValue={'skip'} {...props} options={IMPORT_POLICY_OPTIONS}/>
    )
}