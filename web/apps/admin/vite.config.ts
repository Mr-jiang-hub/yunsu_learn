import { fileURLToPath, URL } from 'node:url'
import vue from '@vitejs/plugin-vue'
import { defineConfig, loadEnv } from 'vite'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  // 后端地址由 VITE_PROXY_TARGET 决定：默认文档约定的 8080；
  // 若本机 8080 落在 Windows 保留端口区间（8077-8176），改 .env.development 指向备用端口。
  const proxyTarget = env.VITE_PROXY_TARGET || 'http://127.0.0.1:8080'
  console.log(`[vite] /api 代理目标: ${proxyTarget}`)

  return {
    base: mode === 'production' ? '/admin/' : '/',
    plugins: [vue()],
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url)),
      },
    },
    server: {
      port: 5174,
      strictPort: true,
      proxy: {
        '/api': {
          target: proxyTarget,
          changeOrigin: true,
        },
      },
    },
  }
})
