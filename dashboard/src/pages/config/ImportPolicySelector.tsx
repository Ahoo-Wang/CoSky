import {IMPORT_POLICY_OPTIONS} from "./ImportPolicySelectorOptions.ts";
import {OptionsSelect} from "@/components/ui/options-select";
import type {OptionsSelectProps} from "@/components/ui/options-select";

export type ImportPolicySelectorProps = Omit<OptionsSelectProps, 'options'>;

export function ImportPolicySelector(props: ImportPolicySelectorProps) {
    return (
        <OptionsSelect defaultValue="skip" {...props} options={IMPORT_POLICY_OPTIONS}/>
    );
}
