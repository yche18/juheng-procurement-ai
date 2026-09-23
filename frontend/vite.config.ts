import react from '@vitejs/plugin-react'
import { loadEnv } from 'vite'
import { defineConfig } from 'vitest/config'

export default defineConfig(({ mode }) => {
  const environment = loadEnv(mode, '.', '')
  const proxyTarget =
    environment.JUHENG_FRONTEND_PROXY_TARGET ?? 'http://localhost:8080'
  const usePolling = environment.JUHENG_FRONTEND_USE_POLLING === 'true'

  return {
    plugins: [react()],
    server: {
      port: 5173,
      strictPort: true,
      watch: usePolling ? { usePolling: true } : undefined,
      proxy: {
        '/api': {
          target: proxyTarget,
          changeOrigin: true,
        },
      },
    },
    test: {
      environment: 'jsdom',
      environmentOptions: {
        jsdom: {
          url: 'http://localhost:3000/',
        },
      },
      setupFiles: ['./src/test/setup.ts'],
      clearMocks: true,
      restoreMocks: true,
      css: true,
    },
  }
})
