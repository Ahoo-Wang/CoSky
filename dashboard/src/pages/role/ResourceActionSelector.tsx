import {Select, SelectProps} from "antd";

export const RESOURCE_ACTION_OPTIONS = [
    {
        label: 'Read only',
        value: 'r'
    },
    {
        label: 'Write only',
        value: 'w'
    },
    {
        label: 'Read and write',
        value: 'rw'
    }
]

export interface ResourceActionSelectorProps extends Omit<SelectProps, 'options'> {

}

export function ResourceActionSelector(props: ResourceActionSelectorProps) {
    return (
        <Select options={RESOURCE_ACTION_OPTIONS} placeholder={'Select Resource Action'} {...props}/>
    )
}