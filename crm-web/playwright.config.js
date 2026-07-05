import { defineConfig, devices } from '@playwright/test'

export default defineConfig({
  testDir: './tests/e2e',
  timeout: 30_000,
  workers: 1,
  expect: {
    timeout: 5_000
  },
  webServer: {
    command: 'npm run dev -- --host 127.0.0.1 --port 5174 --force',
    url: 'http://127.0.0.1:5174',
    reuseExistingServer: false,
    timeout: 60_000
  },
  use: {
    baseURL: 'http://127.0.0.1:5174',
    trace: 'retain-on-failure'
  },
  projects: [
    {
      name: 'desktop-chromium',
      use: { ...devices['Desktop Chrome'], channel: 'chrome' }
    },
    {
      name: 'mobile-chromium',
      use: { ...devices['Pixel 5'], channel: 'chrome' }
    }
  ]
})
