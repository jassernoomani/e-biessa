import { expect, Page, test } from '@playwright/test';

const emptyPage = { content: [], totalElements: 0, totalPages: 0, number: 0, size: 12, first: true, last: true };
// A valid blank PNG makes the upload-preview tests reproducible without external photos.
const photoBuffer = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAGQAAABkCAIAAAD/gAIDAAAA5klEQVR4nO3SsREAIAwDMWD/ncMK+V6qXf35zsxh5y13iNV4ViBWIFYgViBWIFYgViBWIFYgViBWIFYgViBWIFYgViBWIFYgViBWIFYgViBWIFYgViBWIFYgViBWIFYgViBWIFYgViBWIFYgViBWIFYgViBWIFYgViBWIFYgViBWIFYgViBWIFYgViBWIFYgViBWIFYgViBWIFYgViBWIFYgViBWIFYgViBWIFYgViBWIFYgViBWIFYgViBWIFYgViBWIFYgViBWIFYgViBWIFYgViBWIFYgViBWIFYgViBWIFYgViBWIFYgViBWIFYgViBWIFYgViBWIFYgViBWIFYg1tn7z/EDxbGcl84AAAAASUVORK5CYII=', 'base64');
const listing = {
  id: 7, title: 'Volkswagen Golf front brake caliper', description: 'Inspected OEM caliper', price: 310,
  condition: 'Used - good', categoryName: 'Brake Systems', sellerName: 'Carthage Auto Parts', sellerId: 2,
  compatibleVehicles: [{ id: 3, brand: 'Volkswagen', model: 'Golf', year: 2020, engine: '1.6 TDI', fuelType: 'Diesel' }],
  partPosition: 'FRONT', location: 'Tunis', status: 'ACTIVE', views: 20, createdAt: '2026-01-01T12:00:00',
  imageUrls: [], images: [], favoriteCount: 0, favorited: false,
};

test('dialogs animate out and reduced motion disables navigation effects', async ({ page }) => {
  await mockApi(page);
  await page.goto('/');
  await page.getByRole('button', { name: 'Log in', exact: true }).click();
  await expect(page.locator('.auth-modal')).toBeVisible();
  await page.locator('.auth-modal .close').click();
  await expect(page.locator('.modal-backdrop')).toHaveCount(0);
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await page.getByRole('button', { name: 'Log in', exact: true }).click();
  expect(await page.locator('.modal-backdrop').evaluate(element => getComputedStyle(element).animationName)).toBe('none');
  await page.locator('.auth-modal .close').click();
  await expect(page.locator('.modal-backdrop')).toHaveCount(0);
  await signIn(page, 'BUYER');
  await page.getByRole('button', { name: 'Saved', exact: true }).click();
  expect(await page.locator('main > .page').evaluate(element => getComputedStyle(element).animationName)).toBe('none');
});

test('workshop catalogue keeps theme geometry and supports mobile category filters', async ({ page }, testInfo) => {
  await page.addInitScript(() => localStorage.setItem('e_bieca_theme', 'light'));
  await mockApi(page);
  await page.route('**/api/categories', route => route.fulfill({ json: [{ id: 1, name: 'Brake Systems' }, { id: 2, name: 'Electrical Parts' }] }));
  await page.route('**/api/parts?*', route => route.fulfill({ json: { ...emptyPage, content: [listing, { ...listing, id: 8, title: 'Bosch alternator', categoryName: 'Electrical Parts' }], totalElements: 2, totalPages: 1 } }));
  await page.setViewportSize({ width: 1440, height: 1000 });
  await page.goto('/');
  await expect(page.locator('.part-card').first()).toBeVisible();
  await expect(page.getByRole('complementary', { name: 'Vehicle and part filters' })).toBeVisible();
  const contrast = await page.locator('.topbar .primary').evaluate(element => {
    const style = getComputedStyle(element);
    const luminance = (color: string) => {
      const channels = color.match(/\d+/g)!.slice(0, 3).map(value => {
        const channel = Number(value) / 255;
        return channel <= 0.04045 ? channel / 12.92 : ((channel + 0.055) / 1.055) ** 2.4;
      });
      return channels[0] * 0.2126 + channels[1] * 0.7152 + channels[2] * 0.0722;
    };
    const foreground = luminance(style.color);
    const background = luminance(style.backgroundColor);
    return (Math.max(foreground, background) + 0.05) / (Math.min(foreground, background) + 0.05);
  });
  expect(contrast).toBeGreaterThanOrEqual(4.5);
  await page.locator('.hero').evaluate(async element => { await Promise.all(element.getAnimations().map(animation => animation.finished)); });
  const light = await page.locator('.hero').boundingBox();
  await page.screenshot({ path: testInfo.outputPath('workshop-light.png'), fullPage: true });
  await page.getByRole('button', { name: 'Switch to dark mode' }).click();
  const dark = await page.locator('.hero').boundingBox();
  expect(dark).toEqual(light);
  await page.screenshot({ path: testInfo.outputPath('workshop-dark.png'), fullPage: true });
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await page.setViewportSize({ width: 390, height: 844 });
  await expect(page.getByRole('button', { name: 'Marketplace', exact: true })).toBeInViewport();
  await expect(page.locator('.mobile-filter-toggle')).toBeVisible();
  await page.locator('.mobile-filter-toggle').click();
  await expect(page.locator('#market-filters')).toBeVisible();
  await page.getByRole('region', { name: 'Browse part categories' }).getByRole('button', { name: 'Brake Systems' }).click();
  await expect(page.locator('#market-filters select').first()).toHaveValue('1');
  await page.locator('.mobile-filter-toggle').click();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy();
  await page.screenshot({ path: testInfo.outputPath('workshop-mobile.png'), fullPage: true });
});

test('catalogue sections stay full bleed and share one grid in both themes', async ({ page }, testInfo) => {
  await page.addInitScript(() => localStorage.setItem('e_bieca_theme', 'light'));
  await mockApi(page);
  await page.route('**/api/categories', route => route.fulfill({ json: ['Engine Parts', 'Brake Systems', 'Electrical Parts', 'Suspension', 'Body & Exterior', 'Interior', 'Transmission', 'Cooling & Heating'].map((name, index) => ({ id: index + 1, name })) }));
  await page.route('**/api/parts?*', route => route.fulfill({ json: { ...emptyPage, content: [listing, { ...listing, id: 8, title: 'Bosch alternator' }, { ...listing, id: 9, title: 'Renault Clio headlight' }], totalElements: 3, totalPages: 1 } }));
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await page.setViewportSize({ width: 1985, height: 1080 });
  await page.goto('/');
  await page.evaluate(() => document.fonts.ready);
  for (const theme of ['light', 'dark']) {
    if (theme === 'dark') await page.getByRole('button', { name: 'Switch to dark mode' }).click();
    // Compare the available page width, excluding the reserved scrollbar gutter.
    const viewport = await page.locator('#workshop-app').evaluate(element => element.getBoundingClientRect().width);
    for (const selector of ['.topbar', '.hero', '.category-strip', '.market-shell', 'footer']) {
      const box = await page.locator(selector).boundingBox();
      expect(box!.x, selector).toBe(0);
      expect(box!.width, selector).toBe(viewport);
    }
    const starts = await Promise.all(['.topbar .brand', '.hero-copy', '.category-strip-title'].map(async selector => (await page.locator(selector).boundingBox())!.x));
    for (const start of starts) expect(Math.abs(start - starts[0])).toBeLessThanOrEqual(1);
    await expect(page.locator('.market-shell')).toHaveCSS('border-top-width', '0px');
    await expect(page.locator('.filters')).toHaveCSS('position', 'sticky');
    expect((await page.locator('.filters').boundingBox())!.x).toBe(24);
    await assertPageReflows(page, `${theme} full-bleed catalogue`);
    await page.screenshot({ path: testInfo.outputPath(`polished-${theme}.png`), fullPage: true });
  }
});

async function assertPageReflows(page: Page, surface: string) {
  const overflow = await page.evaluate(() => {
    const width = document.documentElement.clientWidth;
    return {
      viewport: width,
      document: document.documentElement.scrollWidth,
      offenders: [...document.querySelectorAll('main *, header *')]
        .filter(element => {
          const box = element.getBoundingClientRect();
          if (box.width === 0 || box.right <= width + 1) return false;
          // Tables and navigation have deliberately owned horizontal scrolling.
          return !element.closest('.table-wrap, .category-shortcuts, .topbar nav, .admin-sidebar nav');
        }).slice(0, 6).map(element => element.className),
    };
  });
  expect.soft(overflow.document, `${surface}: ${JSON.stringify(overflow)}`).toBeLessThanOrEqual(overflow.viewport + 1);
}

test('catalogue rail stays at the window edge and columns adapt to zoom-equivalent widths', async ({ page }, testInfo) => {
  await mockApi(page);
  const parts = Array.from({ length: 12 }, (_, index) => ({ ...listing, id: index + 10 }));
  await page.route('**/api/parts?*', route => route.fulfill({ json: { ...emptyPage, content: parts, totalElements: 60, totalPages: 5, last: false } }));
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await page.goto('/');
  const counts: number[] = [];
  for (const width of [960, 1280, 1920, 2560]) {
    await page.setViewportSize({ width, height: 1000 });
    await expect(page.locator('.part-card')).toHaveCount(12);
    const rail = page.locator('#market-filters');
    expect((await rail.boundingBox())!.x).toBe(width <= 1100 ? 16 : 24);
    await page.locator('.market-shell').evaluate(element => window.scrollTo(0, window.scrollY + element.getBoundingClientRect().top + 40));
    const railBox = (await rail.boundingBox())!;
    expect(railBox.y).toBe(width <= 1100 ? 16 : 88);
    expect(railBox.y + railBox.height).toBeLessThanOrEqual(1000);
    counts.push(await page.locator('.parts-grid').evaluate(element => getComputedStyle(element).gridTemplateColumns.split(' ').length));
    await assertPageReflows(page, `adaptive catalogue ${width}`);
    if (width === 960 || width === 1920) await page.screenshot({ path: testInfo.outputPath(`adaptive-${width}.png`) });
  }
  expect(counts[0]).toBe(2);
  expect(counts[2]).toBeGreaterThan(counts[1]);
  expect(counts[3]).toBeGreaterThan(counts[2]);
  await page.setViewportSize({ width: 390, height: 844 });
  await page.locator('.mobile-filter-toggle').click();
  await expect(page.locator('#market-filters')).toBeVisible();
  await expect(page.locator('#market-filters')).toHaveCSS('position', 'static');
  await expect(page.locator('.topbar')).toHaveCSS('grid-template-rows', /\S+ \S+/);
  await page.screenshot({ path: testInfo.outputPath('adaptive-phone-filters.png'), fullPage: true });
});

for (const role of ['BUYER', 'SELLER', 'ADMIN'] as const) {
  test(`${role.toLowerCase()} layouts reflow from small phone to wide desktop`, async ({ page }, testInfo) => {
    test.setTimeout(90_000);
    await page.emulateMedia({ reducedMotion: 'reduce' });
    await page.setViewportSize({ width: 320, height: 800 });
    await signIn(page, role);
    const populatedPage = { ...emptyPage, content: [listing], totalElements: 1, totalPages: 1 };
    await page.route('**/api/parts?*', route => route.fulfill({ json: populatedPage }));
    await page.route('**/api/parts/my-listings', route => route.fulfill({ json: [listing] }));
    await page.route('**/api/admin/parts?*', route => route.fulfill({ json: populatedPage }));
    const conversation = { id: 5, partId: 7, partTitle: listing.title, buyerId: 3, buyerName: 'BUYER Test User', sellerId: 2, sellerName: listing.sellerName, otherPartyName: listing.sellerName, lastMessage: 'Is this available?', unreadCount: 0 };
    await page.route('**/api/messages/conversations', route => route.fulfill({ json: [conversation] }));
    await page.route('**/api/messages/conversations/5', route => route.fulfill({ json: { conversation, messages: [{ id: 1, senderId: 2, senderName: listing.sellerName, body: 'Yes. Please confirm the OEM reference before ordering.', createdAt: '2026-08-18T12:00:00' }] } }));
    await page.getByTestId('market-search-submit').click();
    for (const width of [320, 390, 768, 1024, 1366, 1920, 2560]) {
      await page.setViewportSize({ width, height: width < 760 ? 844 : 1000 });
      await page.getByRole('button', { name: 'Marketplace', exact: true }).click();
      await expect(page.locator('.part-card').first()).toBeVisible();
      await assertPageReflows(page, `${role} catalogue ${width}`);
      await page.getByRole('button', { name: listing.title, exact: true }).first().click();
      await expect(page.locator('.listing-contact-panel')).toBeVisible();
      await assertPageReflows(page, `${role} listing ${width}`);
      await page.getByRole('button', { name: 'Inbox', exact: true }).click();
      const backToConversations = page.getByRole('button', { name: 'Conversations', exact: true });
      await expect(page.locator('.conversation-list > button:visible, .thread-back:visible').first()).toBeVisible();
      if (width < 761 && await backToConversations.isVisible()) await backToConversations.click();
      await expect(page.locator('.conversation-list > button').first()).toBeVisible();
      await page.locator('.conversation-list > button').first().click();
      await expect(page.locator('.composer-row')).toBeVisible();
      await assertPageReflows(page, `${role} inbox ${width}`);
      if (width === 390 || width === 768) await page.screenshot({ path: testInfo.outputPath(`inbox-${width}.png`), fullPage: true });
      if (role === 'BUYER' && width === 390) {
        await page.getByRole('button', { name: 'Switch to dark mode' }).click();
        await assertPageReflows(page, 'dark phone inbox');
        await page.screenshot({ path: testInfo.outputPath('inbox-phone-dark.png'), fullPage: true });
        await page.getByRole('button', { name: 'Switch to light mode' }).click();
      }
      await page.getByRole('button', { name: 'Open profile page' }).click();
      await expect(page.locator('.profile-layout')).toBeVisible();
      await assertPageReflows(page, `${role} profile ${width}`);
      if (role !== 'BUYER') {
        await page.getByRole('button', { name: 'Seller studio', exact: true }).click();
        await expect(page.locator('.table-wrap')).toBeVisible();
        await assertPageReflows(page, `${role} inventory ${width}`);
        if (width === 390 || width === 768) await page.screenshot({ path: testInfo.outputPath(`inventory-${width}.png`), fullPage: true });
        await page.getByRole('button', { name: '+ New listing', exact: true }).click();
        await expect(page.locator('.editor-modal')).toBeVisible();
        const modal = await page.locator('.editor-modal').boundingBox();
        expect(modal!.width).toBeLessThanOrEqual(width);
        expect(await page.locator('.editor-modal').evaluate(element => element.scrollWidth <= element.clientWidth + 1)).toBeTruthy();
        await page.locator('.editor-modal .close').click();
      }
      if (role === 'ADMIN') {
        await page.getByRole('button', { name: 'Admin', exact: true }).click();
        await expect(page.getByRole('heading', { name: 'Admin dashboard' })).toBeVisible();
        await assertPageReflows(page, `admin dashboard ${width}`);
        if (width <= 760) {
          const sections = page.getByRole('navigation', { name: 'Administration sections' });
          for (const button of await sections.getByRole('button').all()) await expect(button).toBeInViewport();
        }
        if (width === 390 || width === 768) await page.screenshot({ path: testInfo.outputPath(`admin-${width}.png`), fullPage: true });
        await page.getByRole('button', { name: '← Back to marketplace', exact: true }).click();
      }
      if (width === 768) await page.getByRole('button', { name: /Switch to (dark|light) mode/ }).click();
    }
  });
}

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
  await expect(page.getByText(`${role} Test User`, { exact: true })).toBeAttached();
  await expect(page.getByRole('button', { name: 'Open profile page' })).toBeVisible();
}

test('AI search renders only the deterministic, active-listing answer', async ({ page }, testInfo) => {
  await mockApi(page);
  await page.goto('/');
  await page.getByTestId('ai-search-mode').click();
  await page.getByTestId('market-search-input').fill('front brake caliper for a diesel Golf 2020');
  await page.getByTestId('market-search-submit').click();

  const answer = page.getByTestId('ai-answer');
  await expect(answer).toContainText('Verified from active listings');
  await expect(answer).toContainText('Listing #7');
  await expect(answer).toContainText('Search summary');
  await expect(answer.locator('.ai-answer-icon')).toHaveCount(0);
  await page.locator('.results').screenshot({ path: testInfo.outputPath('search-summary-light.png') });
  await page.getByRole('button', { name: 'Switch to dark mode' }).click();
  await page.locator('.results').screenshot({ path: testInfo.outputPath('search-summary-dark.png') });
  await expect(page.getByText('Volkswagen Golf front brake caliper', { exact: true })).toBeVisible();
  await expect(page.getByText('Golf compatible', { exact: false })).toBeVisible();
});

test('signed-in buyer identifies a part photo and uses the suggestion in search', async ({ page }) => {
  await signIn(page, 'BUYER');
  await page.getByTestId('ai-search-mode').click();
  await page.locator('.vision-upload input').setInputFiles({
    name: 'caliper.png', mimeType: 'image/png', buffer: photoBuffer,
  });
  await expect(page.getByText('Possible match', { exact: true })).toBeVisible();
  await expect(page.getByText('brake caliper', { exact: true })).toBeVisible();
  const previewBox = await page.getByAltText('Uploaded part preview').boundingBox();
  const suggestionsBox = await page.locator('.vision-suggestions').boundingBox();
  expect(previewBox).not.toBeNull();
  expect(suggestionsBox).not.toBeNull();
  expect(previewBox!.x + previewBox!.width).toBeLessThanOrEqual(suggestionsBox!.x);
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

test('uncertain photo identification offers guidance without a false part suggestion', async ({ page }, testInfo) => {
  await signIn(page, 'BUYER');
  await page.route('**/api/ai/identify', route => route.fulfill({ json: {
    candidates: [], status: 'UNCERTAIN', model: 'test-vision',
    caution: 'Several part types look similar. Try a closer photo or search by name.'
  } }));
  await page.getByTestId('ai-search-mode').click();
  await page.locator('.vision-upload input').setInputFiles({
    name: 'unclear-part.png', mimeType: 'image/png', buffer: photoBuffer,
  });
  const result = page.locator('.vision-suggestions');
  await expect(result).toContainText('Part not confidently identified');
  await expect(result).toContainText('Try a closer photo');
  await expect(result.getByRole('button')).toHaveCount(0);
  await page.getByTestId('vision-search').screenshot({ path: testInfo.outputPath('photo-uncertain.png') });
});

for (const role of ['BUYER', 'SELLER', 'ADMIN'] as const) {
  test(`${role.toLowerCase()} receives the correct workspace`, async ({ page }, testInfo) => {
    await page.emulateMedia({ reducedMotion: 'reduce' });
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
    await page.screenshot({ path: testInfo.outputPath(`${role.toLowerCase()}-workspace.png`), fullPage: true });
  });
}

test('brake illustration follows the theme with readable annotations and stable geometry', async ({ page }, testInfo) => {
  await page.addInitScript(() => localStorage.setItem('e_bieca_theme', 'light'));
  await mockApi(page);
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await page.setViewportSize({ width: 1440, height: 1000 });
  await page.goto('/');
  await page.evaluate(() => document.fonts.ready);
  const panel = page.locator('.assembly-visual');
  await expect(panel.getByRole('img', { name: 'Technical illustration of a ventilated brake disc and caliper' })).toBeVisible();
  const lightBox = await panel.boundingBox();
  for (const theme of ['light', 'dark']) {
    if (theme === 'dark') await page.getByRole('button', { name: 'Switch to dark mode' }).click();
    await expect(panel).toHaveCSS('background-color', theme === 'light' ? 'rgb(255, 255, 255)' : 'rgb(34, 42, 49)');
    const contrasts = await panel.evaluate(element => {
      const luminance = (color: string) => {
        const values = color.match(/[\d.]+/g)!.slice(0, 3).map(value => {
          const channel = Number(value) / 255;
          return channel <= .04045 ? channel / 12.92 : ((channel + .055) / 1.055) ** 2.4;
        });
        return values[0] * .2126 + values[1] * .7152 + values[2] * .0722;
      };
      const background = luminance(getComputedStyle(element).backgroundColor);
      return [...element.querySelectorAll('text, .assembly-heading, .assembly-stamp, figcaption strong')].map(label => {
        const style = getComputedStyle(label);
        const foreground = luminance(label.tagName === 'text' ? style.fill : style.color);
        return (Math.max(foreground, background) + .05) / (Math.min(foreground, background) + .05);
      });
    });
    for (const contrast of contrasts) expect(contrast).toBeGreaterThanOrEqual(4.5);
    expect(await panel.boundingBox()).toEqual(lightBox);
    await panel.screenshot({ path: testInfo.outputPath(`assembly-${theme}.png`) });
  }
});

test('refreshed catalogue supports keyboard listing access and native filter selection', async ({ page }) => {
  await mockApi(page);
  await page.route('**/api/categories', route => route.fulfill({ json: [{ id: 1, name: 'Brake Systems' }] }));
  await page.route('**/api/parts?*', route => route.fulfill({ json: { ...emptyPage, content: [listing], totalElements: 1, totalPages: 1 } }));
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await page.goto('/');
  const select = page.locator('#market-filters select').first();
  await select.focus();
  await select.press('ArrowDown');
  await select.press('Enter');
  await expect(select).toHaveValue('1');
  const open = page.getByRole('button', { name: listing.title, exact: true });
  await open.focus();
  await expect(open).toBeFocused();
  await open.press('Enter');
  await expect(page.getByRole('heading', { name: listing.title, exact: true })).toBeVisible();
  for (const theme of ['light', 'dark']) {
    const desiredToggle = theme === 'dark' ? 'Switch to dark mode' : 'Switch to light mode';
    const toggle = page.getByRole('button', { name: desiredToggle });
    if (await toggle.count()) await toggle.click();
    await expect(page.locator('.listing-information')).toHaveCSS('background-color', theme === 'dark' ? 'rgb(34, 42, 49)' : 'rgb(255, 255, 255)');
  }
  await page.setViewportSize({ width: 390, height: 844 });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy();
});
