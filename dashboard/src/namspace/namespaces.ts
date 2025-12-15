export const SYSTEM_NAMESPACE = 'cosky-{system}';

export function isSystemNamespace(namespace: string): boolean {
    return namespace === SYSTEM_NAMESPACE;
}