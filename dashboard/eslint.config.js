import js from '@eslint/js'
import globals from 'globals'
import reactHooks from 'eslint-plugin-react-hooks'
import reactRefresh from 'eslint-plugin-react-refresh'
import reactCompiler from "eslint-plugin-react-compiler";
import tseslint from 'typescript-eslint'
import {defineConfig, globalIgnores} from 'eslint/config'

export default defineConfig([
    globalIgnores(['dist']),
    {
        ignores: ['src/generated/**'],
        files: ['**/*.{ts,tsx}'],
        extends: [
            js.configs.recommended,
            tseslint.configs.recommended,
            reactHooks.configs.flat.recommended,
            reactRefresh.configs.vite,
            reactCompiler.configs.recommended
        ],
        languageOptions: {
            ecmaVersion: 2020,
            globals: globals.browser,
        },
        rules: {
            '@typescript-eslint/no-explicit-any': 'warn',
            "@typescript-eslint/consistent-type-imports": [
                "error",
                {
                    "prefer": "type-imports"
                }
            ]
        }
    },
])
