import type { SelectProps} from "antd";
import {Select} from "antd";
import {SCHEMA_SELECTOR_OPTIONS} from "./SchemaSelectorOptions.ts";

export type SchemaSelectorProps = Omit<SelectProps, 'options'>

export function SchemaSelector(props: SchemaSelectorProps) {
    return (
        <Select {...props} options={SCHEMA_SELECTOR_OPTIONS}/>
    )
}