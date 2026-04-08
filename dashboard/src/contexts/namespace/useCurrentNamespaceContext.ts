import {useContext} from 'react';
import {CurrentNamespaceContext, type CurrentNamespaceContextType} from './CurrentNamespaceContext.tsx';

export const useCurrentNamespaceContext = (): CurrentNamespaceContextType => {
    const context = useContext(CurrentNamespaceContext);
    if (!context) {
        throw new Error('useNamespace must be used within a NamespaceProvider');
    }
    return context;
};