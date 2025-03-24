// @ts-check
import eslint from '@eslint/js';
import tseslint from 'typescript-eslint';

export default tseslint.config(
    {
        files: ["src/**/*.ts"],
        extends: [
            eslint.configs.recommended,
            tseslint.configs.recommended,
        ],
        rules: {
            "no-var": "off",
            'no-unused-vars': 'off',
            "@typescript-eslint/no-explicit-any": 'warn',
            "@typescript-eslint/no-unused-vars": 'warn'
        }
    }
)