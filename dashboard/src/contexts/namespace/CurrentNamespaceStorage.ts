import {KeyStorage, typedIdentitySerializer} from "@ahoo-wang/fetcher-storage";

const NAMESPACE_KEY = 'cosky:ns:current';

export const currentNamespaceStorage = new KeyStorage<string>({
    key: NAMESPACE_KEY,
    serializer: typedIdentitySerializer()
})