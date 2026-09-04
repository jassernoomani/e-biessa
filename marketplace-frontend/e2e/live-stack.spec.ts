import { expect, test } from '@playwright/test';

test.describe('live Spring + FastAPI + Angular journeys', () => {
  test.skip(process.env.E2E_LIVE !== 'true', 'Run with npm run test:e2e:live after starting MySQL, Ollama, FastAPI, and Spring Boot.');

  const accounts = [
    { role: 'BUYER', email: 'buyer01@e-biessa.tn', password: 'Buyer123!', destination: 'Inbox', heading: 'Your inbox' },
    { role: 'SELLER', email: 'seller01@e-biessa.tn', password: 'Seller123!', destination: 'Seller studio', heading: 'Your inventory' },
    { role: 'ADMIN', email: 'admin@e-biessa.tn', password: 'Admin123!', destination: 'Admin', heading: 'Admin dashboard' },
  ] as const;

  for (const account of accounts) {
    test(`${account.role.toLowerCase()} can authenticate and open the authorized workspace`, async ({ page }) => {
      await page.goto('/');
      await page.getByRole('button', { name: 'Log in', exact: true }).click();
      const form = page.locator('form.auth-modal');
      await form.getByLabel('Email').fill(account.email);
      await form.getByLabel('Password').fill(account.password);
      await form.getByRole('button', { name: 'Log in', exact: true }).click();
      await page.getByRole('button', { name: account.destination, exact: true }).click();
      await expect(page.getByRole('heading', { name: account.heading, exact: true })).toBeVisible();
    });
  }

  test('trained AI path returns a verified listing answer', async ({ page }) => {
    test.setTimeout(180_000);
    await page.goto('/');
    await page.getByTestId('ai-search-mode').click();
    await page.getByTestId('market-search-input').fill('front brake caliper for a diesel Golf 2020');
    await page.getByTestId('market-search-submit').click();
    await expect(page.getByTestId('ai-answer')).toContainText('Verified from active listings', { timeout: 150_000 });
    await expect(page.locator('.part-card')).toHaveCount(1);
  });
});
