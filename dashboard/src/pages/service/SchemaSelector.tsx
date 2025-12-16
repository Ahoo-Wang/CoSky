import {Select, SelectProps} from "antd";

export interface SchemaSelectorProps extends Omit<SelectProps, 'options'> {

}

export const SCHEMA_SELECTOR_OPTIONS = [
    {
        label: 'Http',
        value: 'http'
    },
    {
        label: 'Https',
        value: 'https'
    }
]

export function SchemaSelector(props: SchemaSelectorProps) {
    return (
        <Select {...props} options={SCHEMA_SELECTOR_OPTIONS}/>
    )
}