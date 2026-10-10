import { expect, test } from '@playwright/test';

const accounts = [
  { email: 'buyer01@e-biessa.tn', password: 'Buyer123!', destination: 'Inbox', heading: 'Your inbox' },
  { email: 'seller01@e-biessa.tn', password: 'Seller123!', destination: 'Seller studio', heading: 'Your inventory' },
  { email: 'admin@e-biessa.tn', password: 'Admin123!', destination: 'Admin', heading: 'Admin dashboard' },
];

for (const account of accounts) {
  test(`isolated published app: ${account.destination}`, async ({ page }) => {
    const leakedRequests: string[] = [];
    await page.route('**/*', async route => {
      const url = new URL(route.request().url());
      if (['localhost', '127.0.0.1'].includes(url.hostname) && url.port === '8080') {
        leakedRequests.push(url.href);
        await route.abort();
      } else {
        await route.continue();
      }
    });
    await page.goto('/');
    await expect(page.locator('.part-card').first()).toBeVisible();
    await page.getByRole('button', { name: 'Log in', exact: true }).click();
    const form = page.locator('form.auth-modal');
    await form.getByLabel('Email').fill(account.email);
    await form.getByLabel('Password').fill(account.password);
    await form.getByRole('button', { name: 'Log in', exact: true }).click();
    await page.getByRole('button', { name: account.destination, exact: true }).click();
    await expect(page.getByRole('heading', { name: account.heading, exact: true })).toBeVisible();
    expect(leakedRequests).toEqual([]);
  });
}
