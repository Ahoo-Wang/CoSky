import type { SelectProps} from "antd";
import {Select} from "antd";
import {CONFIG_FORMAT_SELECTOR_OPTIONS} from "./ConfigFormatSelectorOptions.ts";

export type ConfigFormatSelectorProps = Omit<SelectProps, 'options'>

export function ConfigFormatSelector(props: ConfigFormatSelectorProps) {
    return <Select {...props} options={CONFIG_FORMAT_SELECTOR_OPTIONS}/>
}