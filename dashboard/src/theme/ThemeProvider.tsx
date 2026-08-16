/*
 * Copyright [2021-present] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *      http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import {createContext, useContext, useEffect, useState, type ReactNode} from 'react';

export type Theme = 'light' | 'dark' | 'system';
export type ResolvedTheme = 'light' | 'dark';

const THEME_STORAGE_KEY = 'cosky:theme';

export interface ThemeContextType {
    theme: Theme;
    resolvedTheme: ResolvedTheme;
    setTheme: (theme: Theme) => void;
}

const ThemeContext = createContext<ThemeContextType | undefined>(undefined);

function getSystemTheme(): ResolvedTheme {
    return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
}

export function ThemeProvider({children}: { children: ReactNode }) {
    const [theme, setTheme] = useState<Theme>(
        () => (localStorage.getItem(THEME_STORAGE_KEY) as Theme | null) ?? 'system',
    );
    const [systemTheme, setSystemTheme] = useState<ResolvedTheme>(getSystemTheme);

    useEffect(() => {
        const media = window.matchMedia('(prefers-color-scheme: dark)');
        const listener = (e: MediaQueryListEvent) => setSystemTheme(e.matches ? 'dark' : 'light');
        media.addEventListener('change', listener);
        return () => media.removeEventListener('change', listener);
    }, []);

    const resolvedTheme: ResolvedTheme = theme === 'system' ? systemTheme : theme;

    useEffect(() => {
        document.documentElement.classList.toggle('dark', resolvedTheme === 'dark');
        localStorage.setItem(THEME_STORAGE_KEY, theme);
    }, [theme, resolvedTheme]);

    return (
        <ThemeContext.Provider value={{theme, resolvedTheme, setTheme}}>
            {children}
        </ThemeContext.Provider>
    );
}

// eslint-disable-next-line react-refresh/only-export-components
export function useTheme(): ThemeContextType {
    const context = useContext(ThemeContext);
    if (!context) {
        throw new Error('useTheme must be used within ThemeProvider');
    }
    return context;
}
