import js from '@eslint/js'
import pluginVue from 'eslint-plugin-vue'
import tseslint from 'typescript-eslint'

export default tseslint.config(
  {
    ignores: ['**/dist/**', '**/node_modules/**', '**/*.d.ts'],
  },
  js.configs.recommended,
  ...tseslint.configs.recommended,
  ...pluginVue.configs['flat/recommended'],
  {
    files: ['**/*.vue'],
    languageOptions: {
      parserOptions: {
        parser: tseslint.parser,
      },
    },
    rules: {
      // 由 TypeScript 负责未定义标识符检查，避免对 DOM 类型误报
      'no-undef': 'off',
    },
  },
  {
    rules: {
      'vue/multi-word-component-names': 'off',
      'vue/require-default-prop': 'off',
    },
  },
)
