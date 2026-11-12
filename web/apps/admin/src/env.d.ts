/// <reference types="vite/client" />

declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<Record<string, unknown>, Record<string, unknown>, unknown>
  export default component
}

interface ImportMetaEnv {
  readonly VITE_API_BASE_URL: string
  readonly VITE_AUTH_GUARD_ENABLED: string
  readonly VITE_LOGIN_PATH: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
