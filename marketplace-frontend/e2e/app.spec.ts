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
  let recommendationSettings = { personalizationEnabled: true, vehicles: [] as typeof listing.compatibleVehicles };
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
    else if (path === '/api/recommendations/settings') {
      if (request.method() === 'PUT') {
        const payload = request.postDataJSON();
        recommendationSettings = { personalizationEnabled: payload.personalizationEnabled,
          vehicles: payload.vehicleIds.length ? listing.compatibleVehicles : [] };
      }
      body = recommendationSettings;
    }
    else if (path === '/api/recommendations/history') { status = 204; }
    else if (path === '/api/recommendations') body = { mode: 'DISCOVER', results: [{ part: listing, score: 5,
      reasons: ['Recent listing to explore'] }], caution: 'Confirm exact fitment and OEM with the seller.' };
    else if (path === '/api/parts/7') body = listing;
    else if (path === '/api/vehicles') body = listing.compatibleVehicles;
    else if (path === '/api/categories' || path === '/api/vehicles' || path === '/api/favorites' || path === '/api/messages/conversations' || path === '/api/parts/my-listings') body = [];
    else if (path === '/api/vehicles/catalog/years' || path === '/api/vehicles/catalog/makes' || path === '/api/vehicles/catalog/models') body = [];
    else if (path === '/api/parts') body = emptyPage;
    else if (path === '/api/ai/search' || path === '/api/ai/assistant') body = {
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
    else if (path === '/api/ai/identify') body = {
      candidates: [{ partType: 'brake caliper', category: 'Brake Systems', confidence: 0.82 }],
      model: 'openai/clip-vit-base-patch32',
      caution: 'Confirm the OEM number and exact fitment with the seller.',
    };
    else if (path === '/api/messages/conversations/realtime-ticket') { status = 503; body = { message: 'disabled in browser fixture' }; }
    else if (path === '/api/admin/users') body = [profile];
    else if (path === '/api/admin/seller-applications' || path === '/api/admin/reports' || path === '/api/admin/seller-reports' || path === '/api/admin/audit-logs') body = [];
    else if (path === '/api/admin/parts') body = emptyPage;
    await route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
  });
}

test('buyer manages recommendation preferences and opens a suggested listing', async ({ page }, testInfo) => {
  await page.addInitScript(() => localStorage.setItem('e_bieca_theme', 'light'));
  await signIn(page, 'BUYER');
  const recommendations = page.getByRole('region', { name: 'Buyer recommendations' });
  await expect(recommendations.getByRole('heading', { name: 'Recommended for you' })).toBeVisible();
  await expect(recommendations.getByText('Recent listing to explore')).toBeVisible();
  await recommendations.getByRole('button', { name: 'My preferences' }).click();
  await recommendations.getByLabel('Volkswagen · Golf · 2020 · 1.6 TDI · Diesel', { exact: true }).check();
  await recommendations.getByRole('button', { name: 'Save preferences' }).click();
  await expect(recommendations.getByRole('status').filter({ hasText: 'Preferences saved' })).toBeVisible();
  await expect(recommendations.getByRole('button', { name: 'Refresh picks' })).toBeEnabled();
  const lightBox = await recommendations.boundingBox();
  await recommendations.screenshot({ path: testInfo.outputPath('recommendations-light.png'), style: '.topbar,.toast { visibility: hidden !important; }' });
  await page.getByRole('button', { name: 'Switch to dark mode' }).click();
  const darkBox = await recommendations.boundingBox();
  expect(darkBox?.width).toBe(lightBox?.width);
  expect(darkBox?.height).toBe(lightBox?.height);
  await recommendations.screenshot({ path: testInfo.outputPath('recommendations-dark.png'), style: '.topbar,.toast { visibility: hidden !important; }' });
  await recommendations.getByRole('button', { name: 'Clear recommendation history', exact: true }).click();
  await recommendations.getByRole('button', { name: 'Confirm clear history', exact: true }).click();
  await expect(recommendations.getByRole('status').filter({ hasText: 'history cleared' })).toBeVisible();
  await recommendations.getByRole('button', { name: /Volkswagen Golf front brake caliper/ }).click();
  await expect(page.getByRole('heading', { name: 'Volkswagen Golf front brake caliper', exact: true })).toBeVisible();
});

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

test('signed-in buyer identifies a part photo and uses the suggestion in search', async ({ page }) => {
  await signIn(page, 'BUYER');
  await page.getByTestId('ai-search-mode').click();
  await page.locator('.vision-upload input').setInputFiles({
    name: 'caliper.png', mimeType: 'image/png', buffer: Buffer.from('part-photo'),
  });
  await expect(page.getByText('Possible matches')).toBeVisible();
  await expect(page.getByText('brake caliper', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: /brake caliper.*Brake Systems/ }).click();
  await expect(page.getByTestId('market-search-input')).toHaveValue('brake caliper');
  await expect(page.getByText('Volkswagen Golf front brake caliper', { exact: true })).toBeVisible();
});

test('voice search transcribes locally into the editable query field', async ({ page }) => {
  await page.addInitScript(() => {
    class FakeSpeechRecognition {
      onresult?: (event: unknown) => void;
      onend?: () => void;
      start() {
        this.onresult?.({ results: [[{ transcript: 'Golf diesel brake' }]] });
        this.onend?.();
      }
      stop() { this.onend?.(); }
    }
    Object.defineProperty(window, 'SpeechRecognition', { value: FakeSpeechRecognition });
    Object.defineProperty(window, 'webkitSpeechRecognition', { value: FakeSpeechRecognition });
  });
  await mockApi(page);
  await page.goto('/');
  await page.getByTestId('ai-search-mode').click();
  await page.getByRole('button', { name: 'Start voice search' }).click();
  await expect(page.getByTestId('market-search-input')).toHaveValue('Golf diesel brake');
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
