import { chromium } from '@playwright/test';
import { mkdir } from 'node:fs/promises';
import path from 'node:path';

const baseUrl = 'http://127.0.0.1:4200';
const outputDir = path.resolve('..', 'deliverables', 'E-biessa-Overleaf', 'images');
await mkdir(outputDir, { recursive: true });

const imageUrls = [
  'https://images.unsplash.com/photo-1486262715619-67b85e0b08d3?auto=format&fit=crop&w=1000&q=80',
  'https://images.unsplash.com/photo-1492144534655-ae79c964c9d7?auto=format&fit=crop&w=1000&q=80',
  'https://images.unsplash.com/photo-1503376780353-7e6692767b70?auto=format&fit=crop&w=1000&q=80',
  'https://images.unsplash.com/photo-1542362567-b07e54358753?auto=format&fit=crop&w=1000&q=80',
  'https://images.unsplash.com/photo-1553440569-bcc63803a83d?auto=format&fit=crop&w=1000&q=80',
];

const vehicles = [
  { id: 1, brand: 'Volkswagen', model: 'Golf', year: 2020, engine: '1.6 TDI', fuelType: 'Diesel' },
  { id: 2, brand: 'Peugeot', model: '208', year: 2021, engine: '1.2 PureTech', fuelType: 'Petrol' },
  { id: 3, brand: 'Renault', model: 'Clio', year: 2020, engine: '1.5 dCi', fuelType: 'Diesel' },
];

const definitions = [
  ['Volkswagen Golf front brake caliper 2020', 'Brake Systems', 'Tunis', 'ATE', 310, vehicles[0]],
  ['Peugeot 208 rear tail light 2021', 'Electrical Parts', 'Nabeul', 'Valeo', 273, vehicles[1]],
  ['Renault Clio LED front headlight 2020', 'Electrical Parts', 'Sfax', 'Renault OEM', 828, vehicles[2]],
  ['Volkswagen Golf diesel alternator', 'Engine Parts', 'Ariana', 'Bosch', 460, vehicles[0]],
  ['Peugeot 208 front shock absorber', 'Suspension', 'Sousse', 'Monroe', 335, vehicles[1]],
  ['Renault Clio ABS pump module', 'Brake Systems', 'Bizerte', 'Bosch', 590, vehicles[2]],
];

const listings = definitions.map((item, index) => {
  const [title, categoryName, location, partBrand, price, vehicle] = item;
  return {
    id: index + 1,
    title,
    description: 'Inspected original part in good working condition. Exact fitment and OEM reference are documented by the seller.',
    price,
    condition: index % 3 === 0 ? 'Like new' : 'Used - good',
    categoryName,
    categoryId: index % 2 + 1,
    sellerName: index % 2 ? 'Atlas Auto Parts' : 'Carthage Auto Parts',
    sellerId: index % 2 ? 3 : 2,
    brand: vehicle.brand,
    model: vehicle.model,
    year: vehicle.year,
    compatibleVehicles: [vehicle],
    manufacturer: partBrand,
    partBrand,
    oemNumber: `OEM-${vehicle.brand.slice(0, 3).toUpperCase()}-${20260 + index}`,
    alternativeOemNumbers: [`ALT-${8000 + index}`],
    partPosition: index % 2 ? 'REAR' : 'FRONT',
    engineCompatibility: vehicle.engine,
    fuelCompatibility: vehicle.fuelType,
    warrantyInformation: '30-day seller warranty',
    deliveryAvailable: true,
    collectionAvailable: true,
    negotiable: index % 2 === 0,
    location,
    status: 'ACTIVE',
    views: 64 + index * 17,
    createdAt: `2026-09-${String(12 - index).padStart(2, '0')}T10:00:00`,
    imageUrls: [imageUrls[index % imageUrls.length]],
    images: [{ id: index + 1, url: imageUrls[index % imageUrls.length], primary: true, sortOrder: 0 }],
    favoriteCount: 2 + index,
    favorited: false,
  };
});

const pageOfListings = {
  content: listings,
  totalElements: 60,
  totalPages: 10,
  number: 0,
  size: 6,
  first: true,
  last: false,
};

const conversations = [
  {
    id: 1,
    partId: 1,
    partTitle: listings[0].title,
    partImageUrl: listings[0].imageUrls[0],
    buyerId: 4,
    buyerName: 'Amine Ben Salem',
    sellerId: 2,
    sellerName: 'Carthage Auto Parts',
    otherPartyName: 'Carthage Auto Parts',
    lastMessage: 'Yes, I can send another close-up of the OEM number.',
    lastMessageAt: '2026-09-19T14:32:00',
    unreadCount: 1,
  },
  {
    id: 2,
    partId: 2,
    partTitle: listings[1].title,
    partImageUrl: listings[1].imageUrls[0],
    buyerId: 4,
    buyerName: 'Amine Ben Salem',
    sellerId: 3,
    sellerName: 'Atlas Auto Parts',
    otherPartyName: 'Atlas Auto Parts',
    lastMessage: 'Collection is possible tomorrow in Nabeul.',
    lastMessageAt: '2026-09-18T17:10:00',
    unreadCount: 0,
  },
];

const conversationDetail = {
  conversation: conversations[0],
  messages: [
    { id: 1, senderId: 4, senderName: 'Amine Ben Salem', body: 'Hello, is the caliper still available for a 2020 diesel Golf?', createdAt: '2026-09-19T14:20:00' },
    { id: 2, senderId: 2, senderName: 'Carthage Auto Parts', body: 'Yes, it is available. The reference matches the 1.6 TDI version.', createdAt: '2026-09-19T14:24:00' },
    { id: 3, senderId: 4, senderName: 'Amine Ben Salem', body: 'Could you send a closer photo of the OEM number?', createdAt: '2026-09-19T14:29:00' },
    { id: 4, senderId: 2, senderName: 'Carthage Auto Parts', body: 'Yes, I can send another close-up of the OEM number.', createdAt: '2026-09-19T14:32:00' },
  ],
};

const adminUsers = [
  { id: 1, userId: 1, email: 'admin@e-biessa.tn', fullName: 'E-biessa Administrator', role: 'ADMIN', location: 'Tunis', suspended: false },
  { id: 2, userId: 2, email: 'seller01@e-biessa.tn', fullName: 'Sami Trabelsi', businessName: 'Carthage Auto Parts', role: 'SELLER', location: 'Tunis', suspended: false },
  { id: 4, userId: 4, email: 'buyer01@e-biessa.tn', fullName: 'Amine Ben Salem', role: 'BUYER', location: 'Ariana', suspended: false },
];

const pendingSeller = {
  id: 8, userId: 8, email: 'future.seller@e-biessa.tn', fullName: 'Youssef Gharbi', role: 'BUYER',
  phone: '+216 22 345 678', location: 'Sousse', businessName: 'Gharbi Pièces Auto', website: 'https://example.test',
  bio: 'Independent automotive parts reseller.', sellerApplicationStatus: 'PENDING', sellerApplicationSubmittedAt: '2026-09-18T09:00:00',
};

const listingReport = {
  id: 1, partId: 2, partTitle: listings[1].title, partStatus: 'ACTIVE', sellerId: 3,
  sellerName: 'Atlas Auto Parts', reporterId: 4, reporterName: 'Amine Ben Salem', reason: 'WRONG_INFORMATION',
  details: 'The OEM number should be checked against the description.', status: 'PENDING', createdAt: '2026-09-19T11:00:00',
};

const sellerReport = {
  id: 2, sellerId: 3, sellerName: 'Saber Mansouri', businessName: 'Atlas Auto Parts', reporterId: 5,
  reporterName: 'Leila Ayari', reason: 'MISLEADING_PROFILE', details: 'Business location needs verification.',
  status: 'PENDING', createdAt: '2026-09-19T10:15:00',
};

const auditLogs = [
  { id: 1, adminId: 1, adminEmail: 'admin@e-biessa.tn', action: 'LISTING_STATUS_CHANGED', targetType: 'PART', targetId: 9, targetLabel: 'Brake disc pair', details: 'Status changed to INACTIVE after review.', createdAt: '2026-09-19T13:10:00' },
  { id: 2, adminId: 1, adminEmail: 'admin@e-biessa.tn', action: 'SELLER_APPLICATION_APPROVED', targetType: 'USER', targetId: 7, targetLabel: 'Tunisia Motors', details: 'Seller profile verified.', createdAt: '2026-09-19T09:45:00' },
];

function profileFor(role) {
  if (role === 'ADMIN') return { ...adminUsers[0], sellerApplicationStatus: 'APPROVED' };
  if (role === 'SELLER') return { ...adminUsers[1], sellerApplicationStatus: 'APPROVED' };
  return { ...adminUsers[2], sellerApplicationStatus: 'NONE' };
}

async function mockApi(page, role = 'BUYER') {
  const profile = profileFor(role);
  await page.route('http://localhost:8080/api/**', async route => {
    const request = route.request();
    const url = new URL(request.url());
    const pathname = url.pathname;
    let body = {};
    let status = 200;

    if (pathname === '/api/auth/providers') body = { google: false, facebook: false, microsoft: false };
    else if (pathname === '/api/auth/login') body = { ...profile, token: `report-${role.toLowerCase()}-token` };
    else if (pathname === '/api/users/me') body = profile;
    else if (pathname === '/api/categories') body = [{ id: 1, name: 'Brake Systems', subCategories: [] }, { id: 2, name: 'Electrical Parts', subCategories: [] }, { id: 3, name: 'Engine Parts', subCategories: [] }];
    else if (pathname === '/api/vehicles') body = vehicles;
    else if (pathname === '/api/vehicles/catalog/years') body = [2024, 2023, 2022, 2021, 2020, 2019];
    else if (pathname === '/api/vehicles/catalog/makes') body = [{ id: 'volkswagen', name: 'Volkswagen', country: 'Germany' }, { id: 'peugeot', name: 'Peugeot', country: 'France' }, { id: 'renault', name: 'Renault', country: 'France' }];
    else if (pathname === '/api/vehicles/catalog/models') body = ['Golf', '208', 'Clio'];
    else if (pathname === '/api/favorites') body = [];
    else if (pathname === '/api/recommendations/settings') body = { personalizationEnabled: true, vehicles: [vehicles[0]] };
    else if (pathname === '/api/recommendations') body = { mode: 'PERSONALIZED', results: listings.slice(0, 3).map((part, index) => ({ part, score: 10 - index, reasons: index === 0 ? ['Matches your Volkswagen Golf preference', 'Brake-system interest'] : ['Popular with similar buyers'] })), caution: 'Confirm exact fitment and OEM with the seller.' };
    else if (pathname === '/api/messages/conversations/realtime-ticket') { status = 503; body = { message: 'Realtime disabled for report capture.' }; }
    else if (pathname === '/api/messages/conversations') body = conversations;
    else if (/^\/api\/messages\/conversations\/\d+$/.test(pathname)) body = conversationDetail;
    else if (pathname === '/api/parts/my-listings') body = listings.slice(0, 4);
    else if (/^\/api\/parts\/\d+$/.test(pathname)) body = listings.find(item => item.id === Number(pathname.split('/').pop())) ?? listings[0];
    else if (pathname === '/api/parts') body = pageOfListings;
    else if (/^\/api\/sellers\/\d+\/contact$/.test(pathname)) body = { sellerId: listings[0].sellerId, sellerName: 'Sami Trabelsi', businessName: 'Carthage Auto Parts', phone: '+216 22 123 456', email: 'contact@carthage-auto.test' };
    else if (pathname === '/api/ai/search' || pathname === '/api/ai/assistant') body = {
      query: url.searchParams.get('query'), tookMs: 418,
      extractedConstraints: { brand: 'Volkswagen', model: 'Golf', year: 2020, fuelType: 'Diesel', position: 'FRONT', category: 'Brake Systems', partType: 'caliper' },
      results: [{ part: listings[0], score: 1, matchReasons: ['Golf compatible', 'Diesel', 'Front position', 'Brake system'] }],
      answer: { summary: `Found 1 active compatibility-checked listing. The strongest current match is Listing #1: ${listings[0].title}.`, recommendedListingIds: [1], cautions: ['Confirm the OEM or interchange number and exact fitment with the seller before buying.'], generatedBy: 'deterministic' },
    };
    else if (pathname === '/api/admin/users') body = adminUsers;
    else if (pathname === '/api/admin/seller-applications') body = [pendingSeller];
    else if (pathname === '/api/admin/reports') body = [listingReport];
    else if (pathname === '/api/admin/seller-reports') body = [sellerReport];
    else if (pathname === '/api/admin/audit-logs') body = auditLogs;
    else if (pathname === '/api/admin/parts') body = { ...pageOfListings, content: listings };

    await route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
  });
}

async function newPage(browser, role = 'BUYER') {
  const context = await browser.newContext({ viewport: { width: 1600, height: 1000 }, deviceScaleFactor: 1 });
  await context.addInitScript(() => localStorage.setItem('e_bieca_theme', 'light'));
  const page = await context.newPage();
  await mockApi(page, role);
  return { context, page };
}

async function signIn(page, role) {
  await page.goto(baseUrl, { waitUntil: 'networkidle' });
  await page.getByRole('button', { name: 'Log in', exact: true }).click();
  const form = page.locator('form.auth-modal');
  await form.getByLabel('Email').fill(`${role.toLowerCase()}@e-biessa.tn`);
  await form.getByLabel('Password').fill('AcademicDemo123!');
  await form.getByRole('button', { name: 'Log in', exact: true }).click();
  await page.getByText(profileFor(role).fullName, { exact: true }).waitFor();
}

async function save(page, name) {
  await page.evaluate(() => document.fonts.ready);
  await page.screenshot({ path: path.join(outputDir, name), animations: 'disabled' });
}

const browser = await chromium.launch({ headless: true });
try {
  {
    const { context, page } = await newPage(browser);
    await page.goto(baseUrl, { waitUntil: 'networkidle' });
    await page.getByRole('heading', { name: 'Parts worth a closer look' }).waitFor();
    await save(page, 'ui-marketplace.png');
    await context.close();
  }

  {
    const { context, page } = await newPage(browser, 'BUYER');
    await signIn(page, 'BUYER');
    await page.getByTestId('ai-search-mode').click();
    await page.getByTestId('market-search-input').fill('front brake caliper for a diesel Golf 2020');
    await page.getByTestId('market-search-submit').click();
    await page.getByTestId('ai-answer').waitFor();
    await page.locator('.market-shell').scrollIntoViewIfNeeded();
    await save(page, 'ui-ai-search.png');

    await page.getByRole('heading', { name: listings[0].title, exact: true }).click();
    await page.getByRole('heading', { name: listings[0].title, exact: true }).waitFor();
    await page.getByRole('button', { name: 'Show contact options' }).click();
    await page.getByText('+216 22 123 456', { exact: true }).waitFor();
    await page.locator('.listing-page').scrollIntoViewIfNeeded();
    await save(page, 'ui-listing-detail.png');

    await page.getByRole('button', { name: 'Inbox', exact: false }).click();
    await page.getByRole('heading', { name: 'Your inbox' }).waitFor();
    await page.getByText('Could you send a closer photo of the OEM number?', { exact: true }).waitFor();
    await save(page, 'ui-messaging.png');
    await context.close();
  }

  {
    const { context, page } = await newPage(browser, 'SELLER');
    await signIn(page, 'SELLER');
    await page.getByRole('button', { name: 'Seller studio', exact: true }).click();
    await page.getByRole('heading', { name: 'Your inventory', exact: true }).waitFor();
    await save(page, 'ui-seller-studio.png');
    await context.close();
  }

  {
    const { context, page } = await newPage(browser, 'ADMIN');
    await signIn(page, 'ADMIN');
    await page.getByRole('button', { name: 'Admin', exact: true }).click();
    await page.getByRole('heading', { name: 'Admin dashboard' }).waitFor();
    await page.getByText('Marketplace at a glance', { exact: true }).waitFor();
    await save(page, 'ui-admin-dashboard.png');
    await context.close();
  }
} finally {
  await browser.close();
}

console.log(`Saved report screenshots to ${outputDir}`);
