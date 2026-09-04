import { expect, Page, test } from '@playwright/test';

const emptyPage = { content: [], totalElements: 0, totalPages: 0, number: 0, size: 12, first: true, last: true };
const listing = {
  id: 7, title: 'Volkswagen Golf front brake caliper', description: 'Inspected OEM caliper', price: 310,
  condition: 'Used - good', categoryName: 'Brake Systems', sellerName: 'Carthage Auto Parts', sellerId: 2,
  compatibleVehicles: [{ id: 3, brand: 'Volkswagen', model: 'Golf', year: 2020, engine: '1.6 TDI', fuelType: 'Diesel' }],
  partPosition: 'FRONT', location: 'Tunis', status: 'ACTIVE', views: 20, createdAt: '2026-01-01T12:00:00',
  imageUrls: [], images: [], favoriteCount: 0, favorited: false,
};

async function mockApi(page: Page, role: 'BUYER' | 'SELLER' | 'ADMIN' = 'BUYER') {
  const profile = {
    id: role === 'ADMIN' ? 1 : role === 'SELLER' ? 2 : 3,
    userId: role === 'ADMIN' ? 1 : role === 'SELLER' ? 2 : 3,
    email: `${role.toLowerCase()}@example.test`, fullName: `${role} Test User`, role,
    sellerApplicationStatus: role === 'BUYER' ? 'NONE' : 'APPROVED',
  };
  await page.route('http://localhost:8080/api/**', async route => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname;
    let body: unknown = {};
    let status = 200;
    if (path === '/api/auth/providers') body = { google: false, facebook: false, microsoft: false };
    else if (path === '/api/auth/login') body = { ...profile, token: 'e2e-token' };
    else if (path === '/api/users/me') body = profile;
    else if (path === '/api/categories' || path === '/api/vehicles' || path === '/api/favorites' || path === '/api/messages/conversations' || path === '/api/parts/my-listings') body = [];
    else if (path === '/api/vehicles/catalog/years' || path === '/api/vehicles/catalog/makes' || path === '/api/vehicles/catalog/models') body = [];
    else if (path === '/api/parts') body = emptyPage;
    else if (path === '/api/ai/search') body = {
      query: url.searchParams.get('query'), tookMs: 42,
      extractedConstraints: { brand: 'Volkswagen', model: 'Golf', year: 2020, fuelType: 'Diesel', position: 'FRONT', category: 'Brake Systems', partType: 'caliper' },
      results: [{ part: listing, score: 1, matchReasons: ['Golf compatible', 'Diesel', 'Front position', 'Brake system'] }],
      answer: {
        summary: 'Found 1 active compatibility-checked listing. The strongest current match is Listing #7: Volkswagen Golf front brake caliper.',
        recommendedListingIds: [7],
        cautions: ['Confirm the OEM or interchange number and exact fitment with the seller before buying.'],
        generatedBy: 'deterministic',
      },
    };
    else if (path === '/api/messages/conversations/realtime-ticket') { status = 503; body = { message: 'disabled in browser fixture' }; }
    else if (path === '/api/admin/users') body = [profile];
    else if (path === '/api/admin/seller-applications' || path === '/api/admin/reports' || path === '/api/admin/seller-reports' || path === '/api/admin/audit-logs') body = [];
    else if (path === '/api/admin/parts') body = emptyPage;
    await route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
  });
}

async function signIn(page: Page, role: 'BUYER' | 'SELLER' | 'ADMIN') {
  await mockApi(page, role);
  await page.goto('/');
  await page.getByRole('button', { name: 'Log in', exact: true }).click();
  const form = page.locator('form.auth-modal');
  await form.getByLabel('Email').fill(`${role.toLowerCase()}@example.test`);
  await form.getByLabel('Password').fill('password123');
  await form.getByRole('button', { name: 'Log in', exact: true }).click();
  await expect(page.getByText(`${role} Test User`, { exact: true })).toBeVisible();
}

test('AI search renders only the deterministic, active-listing answer', async ({ page }) => {
  await mockApi(page);
  await page.goto('/');
  await page.getByTestId('ai-search-mode').click();
  await page.getByTestId('market-search-input').fill('front brake caliper for a diesel Golf 2020');
  await page.getByTestId('market-search-submit').click();

  const answer = page.getByTestId('ai-answer');
  await expect(answer).toContainText('Verified from active listings');
  await expect(answer).toContainText('Listing #7');
  await expect(page.getByText('Volkswagen Golf front brake caliper', { exact: true })).toBeVisible();
  await expect(page.getByText('Golf compatible', { exact: false })).toBeVisible();
});

for (const role of ['BUYER', 'SELLER', 'ADMIN'] as const) {
  test(`${role.toLowerCase()} receives the correct workspace`, async ({ page }) => {
    await signIn(page, role);
    if (role === 'BUYER') {
      await expect(page.getByRole('button', { name: 'Seller studio' })).toHaveCount(0);
      await expect(page.getByRole('button', { name: 'Admin', exact: true })).toHaveCount(0);
      await page.getByRole('button', { name: 'Inbox', exact: true }).click();
      await expect(page.getByRole('heading', { name: 'Your inbox' })).toBeVisible();
    } else if (role === 'SELLER') {
      await expect(page.getByRole('button', { name: 'Admin', exact: true })).toHaveCount(0);
      await page.getByRole('button', { name: 'Seller studio', exact: true }).click();
      await expect(page.getByRole('heading', { name: 'Your inventory', exact: true })).toBeVisible();
    } else {
      await page.getByRole('button', { name: 'Admin', exact: true }).click();
      await expect(page.getByRole('heading', { name: 'Admin dashboard' })).toBeVisible();
      await expect(page.getByText('Marketplace at a glance')).toBeVisible();
    }
  });
}
