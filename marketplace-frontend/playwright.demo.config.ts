import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './e2e-demo',
  fullyParallel: false,
  workers: 1,
  retries: 0,
  timeout: 60_000,
  use: {
    baseURL: 'http://localhost:4280',
    ...devices['Desktop Chrome'],
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
});
