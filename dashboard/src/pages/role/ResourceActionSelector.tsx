import {RESOURCE_ACTION_OPTIONS} from "./ResourceActionSelectorOptions.ts";
import {OptionsSelect} from "@/components/ui/options-select";
import type {OptionsSelectProps} from "@/components/ui/options-select";

export type ResourceActionSelectorProps = Omit<OptionsSelectProps, 'options'>;

export function ResourceActionSelector(props: ResourceActionSelectorProps) {
    return (
        <OptionsSelect options={RESOURCE_ACTION_OPTIONS} placeholder="Select Resource Action" {...props}/>
    );
}
