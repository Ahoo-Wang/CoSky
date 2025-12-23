import {jsonSerializer, KeyStorage} from "@ahoo-wang/fetcher-storage";
import {useKeyStorage} from "@ahoo-wang/fetcher-react";

const collapsedStore = new KeyStorage({
    key: "layout:collapsed",
    defaultValue: false,
    serializer: jsonSerializer
})

export function useLayoutCollapsed() {
    return useKeyStorage(collapsedStore)
}