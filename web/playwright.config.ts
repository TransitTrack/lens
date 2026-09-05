import { defineConfig } from '@playwright/test'

export default defineConfig({
  testDir: 'tests/e2e',
  webServer: {
    // --host 0.0.0.0: some environments only resolve the IPv6 loopback
    // ([::1]) for plain `nuxt dev`, which the Playwright's own readiness
    // probe (and browsers there) can't reach over 127.0.0.1.
    command: 'npx nuxt dev --host 0.0.0.0',
    url: 'http://127.0.0.1:3000',
    reuseExistingServer: !process.env.CI,
  },
  use: {
    baseURL: 'http://127.0.0.1:3000',
  },
})
