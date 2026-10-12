import {CONFIG_FORMAT_SELECTOR_OPTIONS} from "@/pages/config/ConfigFormatSelectorOptions";
import {OptionsSelect} from "@/components/ui/options-select";
import type {OptionsSelectProps} from "@/components/ui/options-select";

export type ConfigFormatSelectorProps = Omit<OptionsSelectProps, 'options'>;

export function ConfigFormatSelector(props: ConfigFormatSelectorProps) {
    return <OptionsSelect {...props} options={CONFIG_FORMAT_SELECTOR_OPTIONS}/>;
}
