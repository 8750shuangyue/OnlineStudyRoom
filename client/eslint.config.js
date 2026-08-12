import js from '@eslint/js'
import globals from 'globals'
import reactHooks from 'eslint-plugin-react-hooks'
import reactRefresh from 'eslint-plugin-react-refresh'
import prettier from 'eslint-config-prettier'

export default [
  { ignores: ['dist', 'node_modules', 'coverage'] },
  js.configs.recommended,
  {
    files: ['**/*.{js,jsx}'],
    languageOptions: {
      ecmaVersion: 2022,
      globals: { ...globals.browser, ...globals.es2022 },
      parserOptions: { ecmaFeatures: { jsx: true } }
    },
    plugins: {
      'react-hooks': reactHooks,
      'react-refresh': reactRefresh
    },
    rules: {
      ...reactHooks.configs.recommended.rules,
      // 数据加载模式：useEffect(() => { load() }, [load]) 是项目统一写法
      'react-hooks/set-state-in-effect': 'off',
      // hook / 辅助函数与组件同文件导出是项目惯例，关闭 Fast Refresh 提示
      'react-refresh/only-export-components': 'off',
      'no-unused-vars': ['warn', { argsIgnorePattern: '^_' }]
    }
  },
  {
    files: ['playwright.config.js', 'e2e/**/*.js'],
    languageOptions: {
      globals: { ...globals.node }
    }
  },
  prettier
]
