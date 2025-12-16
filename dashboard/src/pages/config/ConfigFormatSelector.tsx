import {Select, SelectProps} from "antd";

export interface ConfigFormatSelectorProps extends Omit<SelectProps, 'options'> {

}

export const CONFIG_FORMAT_SELECTOR_OPTIONS = [
    {
        label: 'YAML',
        value: 'yaml',
    },
    {
        label: 'YML',
        value: 'yml',
    },
    {
        label: 'JSON',
        value: 'json',
    },
    {
        label: 'XML',
        value: 'xml',
    },
    {
        label: 'Properties',
        value: 'properties',
    },
    {
        label: 'Text',
        value: 'txt',
    },
]

export function ConfigFormatSelector(props: ConfigFormatSelectorProps) {
    return <Select {...props} options={CONFIG_FORMAT_SELECTOR_OPTIONS}/>
}