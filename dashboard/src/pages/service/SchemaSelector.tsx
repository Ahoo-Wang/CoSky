import {SCHEMA_SELECTOR_OPTIONS} from "./SchemaSelectorOptions.ts";
import {OptionsSelect} from "@/components/ui/options-select";
import type {OptionsSelectProps} from "@/components/ui/options-select";

export type SchemaSelectorProps = Omit<OptionsSelectProps, 'options'>;

export function SchemaSelector(props: SchemaSelectorProps) {
    return (
        <OptionsSelect {...props} options={SCHEMA_SELECTOR_OPTIONS}/>
    );
}
