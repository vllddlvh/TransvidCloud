import { defineConfig, devices } from '@playwright/test'

export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: 0,
  reporter: 'list',
  use: { baseURL: 'http://localhost:5181', trace: 'retain-on-failure' },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: [
    { command: 'npm run dev -- --port 5181', url: 'http://localhost:5181', env: { VITE_API_MODE: 'mock', VITE_MOCK_PROCESS: 'auto', VITE_MOCK_UPLOAD_FAILURE_ONCE: 'false' } },
    { command: 'npm run dev -- --port 5182', url: 'http://localhost:5182', env: { VITE_API_MODE: 'real' } },
    { command: 'npm run dev -- --port 5183', url: 'http://localhost:5183', env: { VITE_API_MODE: 'mock', VITE_MOCK_UPLOAD_FAILURE_ONCE: 'true' } },
    { command: 'npm run dev -- --port 5184', url: 'http://localhost:5184', env: { VITE_API_MODE: 'mock', VITE_MOCK_PROCESS: 'fail' } },
  ],
})
