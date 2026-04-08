import type { SelectProps} from "antd";
import {Select} from "antd";
import {RESOURCE_ACTION_OPTIONS} from "./ResourceActionSelectorOptions.ts";

export type ResourceActionSelectorProps = Omit<SelectProps, 'options'>

export function ResourceActionSelector(props: ResourceActionSelectorProps) {
    return (
        <Select options={RESOURCE_ACTION_OPTIONS} placeholder={'Select Resource Action'} {...props}/>
    )
}