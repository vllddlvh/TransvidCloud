import { fileURLToPath, URL } from 'node:url'
import { loadEnv } from 'vite'
import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'

export default defineConfig(({ mode, command }) => {
  const mockBuild = mode === 'mock'
  const productionBuild = command === 'build' && !mockBuild
  const env = loadEnv(mode, process.cwd(), '')
  const configuredMode = process.env.VITE_API_MODE ?? env.VITE_API_MODE
  if (productionBuild && configuredMode !== 'real') {
    throw new Error('Production builds must set VITE_API_MODE=real. Use npm run build:mock for the demo build.')
  }
  return {
    plugins: [react()],
    resolve: {
      alias: {
        '@app/mock-runtime': fileURLToPath(new URL(
          productionBuild ? './src/runtime/mock.disabled.ts' : './src/runtime/mock.ts',
          import.meta.url,
        )),
      },
    },
    publicDir: productionBuild ? false : 'public',
    assetsInclude: ['**/src/mocks/media/segment-*.ts'],
    build: {
      outDir: mockBuild ? 'dist-mock' : 'dist',
      manifest: true,
    },
    test: {
      environment: 'jsdom',
      setupFiles: ['./src/test/setup.ts'],
      include: ['src/**/*.test.{ts,tsx}'],
      restoreMocks: true,
      environmentOptions: { jsdom: { url: 'http://localhost:5173' } },
    },
  }
})
