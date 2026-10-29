import { defineConfig } from '@playwright/test';
export default defineConfig({
  testDir: './e2e', timeout: 45000, workers: 1,
  use: { baseURL: process.env['POC_BASE_URL'] ?? 'http://127.0.0.1:8080', headless: true,
    launchOptions: { executablePath: process.env['CHROMIUM_EXECUTABLE'], args: ['--no-sandbox'] } },
  reporter: [['list'], ['json', { outputFile: 'test-results/playwright-results.json' }]],
});
