import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ApiService } from './api.service';
import { vi } from 'vitest';

describe('ApiService', () => {
  let api: ApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(ApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('bounds an unresponsive AI request without retrying it', () => {
    vi.useFakeTimers();
    try {
      let errorName = '';
      api.semanticSearch('brake', {}).subscribe({ error: error => errorName = error.name });
      const request = http.expectOne(req => req.url.endsWith('/ai/search'));
      vi.advanceTimersByTime(120_000);
      expect(errorName).toBe('TimeoutError');
      expect(request.cancelled).toBe(true);
      http.expectNone(req => req.url.endsWith('/ai/search'));
    } finally {
      vi.useRealTimers();
    }
  });

  it('sends all marketplace vehicle filters', () => {
    api.browse({ search: 'lamp', brand: 'Volkswagen', model: 'Golf', year: '2018', categoryId: '', condition: '', minPrice: '100', maxPrice: '500', partBrand: 'Bosch', partPosition: 'FRONT', engine: '1.6 TDI', fuelType: 'Diesel', warranty: '90 days', deliveryAvailable: 'true', collectionAvailable: 'true', negotiable: 'true' }).subscribe();
    const request = http.expectOne(req => req.url.endsWith('/parts'));
    expect(request.request.params.get('brand')).toBe('Volkswagen');
    expect(request.request.params.get('model')).toBe('Golf');
    expect(request.request.params.get('year')).toBe('2018');
    expect(request.request.params.get('minPrice')).toBe('100');
    expect(request.request.params.get('maxPrice')).toBe('500');
    expect(request.request.params.get('partBrand')).toBe('Bosch');
    expect(request.request.params.get('partPosition')).toBe('FRONT');
    expect(request.request.params.get('engine')).toBe('1.6 TDI');
    expect(request.request.params.get('fuelType')).toBe('Diesel');
    expect(request.request.params.get('warranty')).toBe('90 days');
    expect(request.request.params.get('deliveryAvailable')).toBe('true');
    expect(request.request.params.get('collectionAvailable')).toBe('true');
    expect(request.request.params.get('negotiable')).toBe('true');
    expect(request.request.params.has('categoryId')).toBe(false);
    request.flush({ content: [], totalElements: 0, totalPages: 0 });
  });

  it('sends compatibility filters and a relevance threshold to AI search', () => {
    api.semanticSearch('front brake part for a diesel Golf', {
      model: 'Golf', fuelType: 'Diesel', partPosition: 'FRONT', category: 'Brake Systems'
    }).subscribe();
    const request = http.expectOne(req => req.url.endsWith('/ai/search'));
    expect(request.request.method).toBe('GET');
    expect(request.request.params.get('query')).toBe('front brake part for a diesel Golf');
    expect(request.request.params.get('minimumScore')).toBe('0.5');
    expect(request.request.params.get('model')).toBe('Golf');
    expect(request.request.params.get('fuelType')).toBe('Diesel');
    expect(request.request.params.get('partPosition')).toBe('FRONT');
    expect(request.request.params.get('category')).toBe('Brake Systems');
    request.flush({ query: 'front brake part for a diesel Golf', results: [], tookMs: 8, extractedConstraints: {} });
  });

  it('calls the authenticated marketplace assistant with safe filters', () => {
    api.marketplaceAssistant('recommend a Golf brake for me', { model: 'Golf', fuelType: 'Diesel' }).subscribe();
    const request = http.expectOne(req => req.url.endsWith('/ai/assistant'));
    expect(request.request.method).toBe('GET');
    expect(request.request.params.get('query')).toBe('recommend a Golf brake for me');
    expect(request.request.params.get('model')).toBe('Golf');
    expect(request.request.params.get('fuelType')).toBe('Diesel');
    request.flush({ query: 'recommend a Golf brake for me', intent: 'RECOMMENDATION', results: [], tookMs: 4,
      extractedConstraints: {}, toolsUsed: ['search_agent', 'recommendation_agent'],
      answer: { summary: 'No match', recommendedListingIds: [], cautions: [], generatedBy: 'deterministic' } });
  });

  it('uploads a part photo for authenticated visual identification', () => {
    const photo = new File(['image-bytes'], 'unknown-part.jpg', { type: 'image/jpeg' });
    api.identifyPartImage(photo).subscribe();
    const request = http.expectOne('http://localhost:8080/api/ai/identify');
    expect(request.request.method).toBe('POST');
    expect(request.request.body instanceof FormData).toBe(true);
    expect((request.request.body as FormData).get('file')).toBe(photo);
    request.flush({ candidates: [{ partType: 'brake caliper', category: 'Brake Systems', confidence: .74 }],
      model: 'openai/clip-vit-base-patch32', caution: 'Confirm fitment.' });
  });

  it('loads dependent CarQuery vehicle catalog options through the backend', () => {
    api.vehicleCatalogYears().subscribe();
    http.expectOne('http://localhost:8080/api/vehicles/catalog/years').flush([2026, 2025]);

    api.vehicleCatalogMakes('2020').subscribe();
    const makes = http.expectOne(req => req.url.endsWith('/vehicles/catalog/makes'));
    expect(makes.request.params.get('year')).toBe('2020');
    makes.flush([{ id: 'volkswagen', name: 'Volkswagen', country: 'Germany' }]);

    api.vehicleCatalogModels('volkswagen', '2020').subscribe();
    const models = http.expectOne(req => req.url.endsWith('/vehicles/catalog/models'));
    expect(models.request.params.get('makeId')).toBe('volkswagen');
    expect(models.request.params.get('year')).toBe('2020');
    models.flush(['Golf', 'Passat']);
  });

  it('decodes a VIN through the authenticated backend route', () => {
    api.decodeVin('WVWZZZ1KZAW000001').subscribe();
    const request = http.expectOne('http://localhost:8080/api/vehicles/vin/WVWZZZ1KZAW000001');
    expect(request.request.method).toBe('GET');
    request.flush({ vin: 'WVWZZZ1KZAW000001', make: 'Volkswagen', model: 'Golf', year: 2020,
      matchedVehicleIds: [3], warnings: [] });
  });

  it('publishes all rich listing and compatibility fields', () => {
    api.createPart({
      title: 'Front caliper', description: 'Bench tested', price: 280, condition: 'Used - good', categoryId: 2,
      vehicleIds: [4, 8], manufacturer: 'ATE', partBrand: 'ATE Original', oemNumber: '1K0615123',
      alternativeOemNumbers: ['1K0615124'], partPosition: 'FRONT_LEFT', engineCompatibility: '1.6 TDI',
      fuelCompatibility: 'Diesel', warrantyInformation: '90 days', deliveryAvailable: true,
      collectionAvailable: true, negotiable: true, location: 'Tunis'
    }).subscribe();
    const request = http.expectOne('http://localhost:8080/api/parts');
    expect(request.request.method).toBe('POST');
    expect(request.request.body.vehicleIds).toEqual([4, 8]);
    expect(request.request.body.alternativeOemNumbers).toEqual(['1K0615124']);
    expect(request.request.body.negotiable).toBe(true);
    request.flush({ id: 1 });
  });

  it('requests a grounded price estimate using listing identity fields', () => {
    const listing = {
      title: 'Golf front caliper', description: '', price: 1, condition: 'Used - good', categoryId: 2,
      vehicleIds: [7], manufacturer: '', partBrand: 'ATE', oemNumber: '1K0615123',
      alternativeOemNumbers: [], partPosition: 'FRONT', engineCompatibility: '', fuelCompatibility: '',
      warrantyInformation: '', deliveryAvailable: false, collectionAvailable: true, negotiable: false, location: ''
    };
    api.estimatePrice(listing, 14).subscribe();
    const request = http.expectOne('http://localhost:8080/api/parts/price-estimate');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ excludeListingId: 14, title: 'Golf front caliper', categoryId: 2,
      vehicleIds: [7], condition: 'Used - good', partBrand: 'ATE', oemNumber: '1K0615123', partPosition: 'FRONT' });
    request.flush({ suggestedPrice: 300, lowerRange: 250, upperRange: 400, comparableCount: 5,
     confidence: 'MEDIUM', comparableListingIds: [1, 2], reasons: ['2 comparable listings'], caution: 'Estimate only' });
  });

  it('loads a shareable listing detail', () => {
    const photo = new File(['part'], 'caliper.png', { type: 'image/png' });
    api.identifyPartImage(photo).subscribe();
    const identification = http.expectOne('http://localhost:8080/api/ai/identify');
    expect(identification.request.method).toBe('POST');
    expect(identification.request.body instanceof FormData).toBe(true);
    expect((identification.request.body as FormData).get('file')).toBe(photo);
    identification.flush({ candidates: [], model: 'clip', caution: 'Confirm fitment.' });

    api.part(42).subscribe();
    const request = http.expectOne('http://localhost:8080/api/parts/42');
    expect(request.request.method).toBe('GET');
    request.flush({
      id: 42, title: 'Golf headlight', price: 450, sellerId: 7, sellerName: 'Garage Seven',
      status: 'ACTIVE', views: 12, createdAt: '2026-08-01T12:00:00', imageUrls: [], images: [],
      favoriteCount: 0, favorited: false
    });
  });

  it('updates the authenticated profile', () => {
    api.updateProfile({
      fullName: 'Updated Buyer', phone: '+216 22 333 444', location: 'Tunis',
      businessName: '', website: '', bio: 'Looking for reliable parts.'
    }).subscribe();
    const request = http.expectOne('http://localhost:8080/api/users/me');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body.fullName).toBe('Updated Buyer');
    request.flush({ id: 1, userId: 1, email: 'buyer@example.com', fullName: 'Updated Buyer', phone: '+216 22 333 444', role: 'BUYER' });
  });

  it('loads and updates a profile through administrator endpoints', () => {
    api.adminUser(7).subscribe();
    const details = http.expectOne('http://localhost:8080/api/admin/users/7');
    expect(details.request.method).toBe('GET');
    details.flush({ id: 7, userId: 7, email: 'seller@example.com', fullName: 'Seller', role: 'SELLER' });

    api.adminUpdateProfile(7, { fullName: 'Seller', phone: '', location: 'Sousse', businessName: 'Garage 7', website: '', bio: '' }).subscribe();
    const update = http.expectOne('http://localhost:8080/api/admin/users/7/profile');
    expect(update.request.method).toBe('PUT');
    expect(update.request.body.location).toBe('Sousse');
    update.flush({ id: 7, userId: 7, email: 'seller@example.com', fullName: 'Seller', location: 'Sousse', role: 'SELLER' });
  });

  it('supports primary selection and image deletion', () => {
    api.makePrimaryImage(9, 4).subscribe();
    const primary = http.expectOne('http://localhost:8080/api/parts/9/images/4/primary');
    expect(primary.request.method).toBe('PUT');
    primary.flush([]);

    api.deleteImage(9, 4).subscribe();
    const deletion = http.expectOne('http://localhost:8080/api/parts/9/images/4');
    expect(deletion.request.method).toBe('DELETE');
    deletion.flush(null);
  });

  it('persists image ordering', () => {
    api.reorderImages(9, [5, 3, 4]).subscribe();
    const request = http.expectOne('http://localhost:8080/api/parts/9/images/order');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({ imageIds: [5, 3, 4] });
    request.flush([]);
  });

  it('submits and moderates listing reports', () => {
    api.reportListing(14, 'WRONG_INFORMATION', 'Incorrect OEM number').subscribe();
    const report = http.expectOne('http://localhost:8080/api/parts/14/reports');
    expect(report.request.method).toBe('POST');
    expect(report.request.body.reason).toBe('WRONG_INFORMATION');
    report.flush({ id: 2, partId: 14, status: 'PENDING' });

    api.adminReports('PENDING').subscribe();
    const queue = http.expectOne(req => req.url.endsWith('/admin/reports') && req.params.get('status') === 'PENDING');
    expect(queue.request.method).toBe('GET');
    queue.flush([]);

    api.resolveReport(2, 'ACTIONED', 'Listing hidden').subscribe();
    const resolution = http.expectOne('http://localhost:8080/api/admin/reports/2');
    expect(resolution.request.body).toEqual({ status: 'ACTIONED', adminNote: 'Listing hidden' });
    resolution.flush({ id: 2, partId: 14, status: 'ACTIONED' });
  });

  it('supports private listing conversations and replies', () => {
    api.startConversation(14, 'Is this still available?').subscribe();
    const start = http.expectOne('http://localhost:8080/api/messages/conversations');
    expect(start.request.method).toBe('POST');
    expect(start.request.body).toEqual({ partId: 14, message: 'Is this still available?' });
    start.flush({ conversation: { id: 3 }, messages: [] });

    api.conversations().subscribe();
    http.expectOne('http://localhost:8080/api/messages/conversations').flush([]);
    api.conversation(3).subscribe();
    http.expectOne('http://localhost:8080/api/messages/conversations/3').flush({ conversation: { id: 3 }, messages: [] });
    api.sendMessage(3, 'Yes, it is available.').subscribe();
    const reply = http.expectOne('http://localhost:8080/api/messages/conversations/3/messages');
    expect(reply.request.body.message).toBe('Yes, it is available.');
    reply.flush({ id: 9, body: 'Yes, it is available.' });

    const photo = new File(['image'], 'connector.jpg', { type: 'image/jpeg' });
    api.sendMessageWithImages(3, 'Connector close-up', [photo]).subscribe();
    const imageReply = http.expectOne('http://localhost:8080/api/messages/conversations/3/messages');
    expect(imageReply.request.method).toBe('POST');
    expect(imageReply.request.body instanceof FormData).toBe(true);
    expect((imageReply.request.body as FormData).get('message')).toBe('Connector close-up');
    expect((imageReply.request.body as FormData).getAll('files')).toHaveLength(1);
    imageReply.flush({ id: 10, body: 'Connector close-up', attachments: [{ id: 1, url: '/uploads/photo.jpg' }] });
  });

  it('creates a short-lived realtime ticket and WebSocket URL', () => {
    api.realtimeTicket().subscribe();
    const request = http.expectOne('http://localhost:8080/api/messages/conversations/realtime-ticket');
    expect(request.request.method).toBe('POST');
    request.flush({ ticket: 'one-time-ticket', expiresInSeconds: 30 });
    expect(api.realtimeSocketUrl('ticket with spaces')).toBe('ws://localhost:8080/ws/messages?ticket=ticket%20with%20spaces');
  });

  it('manages suspension and loads immutable administrator audit history', () => {
    api.updateSuspension(7, true, 'Repeated policy violations').subscribe();
    const suspension = http.expectOne('http://localhost:8080/api/admin/users/7/suspension');
    expect(suspension.request.method).toBe('PATCH');
    expect(suspension.request.body).toEqual({ suspended: true, reason: 'Repeated policy violations' });
    suspension.flush({ id: 7, suspended: true });

    api.adminAuditLogs().subscribe();
    const logs = http.expectOne('http://localhost:8080/api/admin/audit-logs');
    expect(logs.request.method).toBe('GET');
    logs.flush([]);
  });

  it('submits and reviews seller applications', () => {
    api.becomeSeller().subscribe();
    const application = http.expectOne('http://localhost:8080/api/users/me/become-seller');
    expect(application.request.method).toBe('POST');
    application.flush({ id: 4, role: 'BUYER', sellerApplicationStatus: 'PENDING' });

    api.sellerApplications().subscribe();
    const queue = http.expectOne('http://localhost:8080/api/admin/seller-applications');
    expect(queue.request.method).toBe('GET');
    queue.flush([]);

    api.decideSellerApplication(4, false, 'Business details could not be verified').subscribe();
    const decision = http.expectOne('http://localhost:8080/api/admin/seller-applications/4');
    expect(decision.request.method).toBe('PATCH');
    expect(decision.request.body).toEqual({ approved: false, reason: 'Business details could not be verified' });
    decision.flush({ id: 4, role: 'BUYER', sellerApplicationStatus: 'REJECTED' });
  });

  it('submits and moderates seller reports', () => {
    api.reportSeller(12, 'FRAUD_OR_SCAM', 'Requested a suspicious external payment.').subscribe();
    const report = http.expectOne('http://localhost:8080/api/sellers/12/reports');
    expect(report.request.method).toBe('POST');
    expect(report.request.body).toEqual({ reason: 'FRAUD_OR_SCAM', details: 'Requested a suspicious external payment.' });
    report.flush({ id: 9, sellerId: 12, reason: 'FRAUD_OR_SCAM', status: 'PENDING' });

    api.adminSellerReports('PENDING').subscribe();
    const queue = http.expectOne(request => request.url === 'http://localhost:8080/api/admin/seller-reports');
    expect(queue.request.params.get('status')).toBe('PENDING');
    queue.flush([]);

    api.resolveSellerReport(9, 'ACTIONED', 'Confirmed fraud').subscribe();
    const resolution = http.expectOne('http://localhost:8080/api/admin/seller-reports/9');
    expect(resolution.request.method).toBe('PATCH');
    expect(resolution.request.body).toEqual({ status: 'ACTIONED', adminNote: 'Confirmed fraud' });
    resolution.flush({ id: 9, status: 'ACTIONED' });
  });

  it('marks a seller listing as sold', () => {
    api.updatePartStatus(21, 'SOLD').subscribe();
    const request = http.expectOne('http://localhost:8080/api/parts/21/status');
    expect(request.request.method).toBe('PATCH');
    expect(request.request.body).toEqual({ status: 'SOLD' });
    request.flush({
      id: 21, title: 'Alternator', price: 300, sellerId: 4, sellerName: 'Seller', status: 'SOLD',
      soldAt: '2026-08-14T12:00:00', views: 0,
      createdAt: '2026-08-01T12:00:00', imageUrls: [], images: [], favoriteCount: 0, favorited: false
    });
  });

  it('separates public seller data from buyer-only contact data', () => {
    api.sellerProfile(12).subscribe();
    const profile = http.expectOne('http://localhost:8080/api/sellers/12');
    expect(profile.request.method).toBe('GET');
    profile.flush({ id: 12, fullName: 'Seller', businessName: 'Garage 12', activeListingCount: 4 });

    api.sellerListings(12).subscribe();
    const listings = http.expectOne('http://localhost:8080/api/sellers/12/listings');
    expect(listings.request.method).toBe('GET');
    listings.flush([]);

    api.sellerContact(12).subscribe();
    const contact = http.expectOne('http://localhost:8080/api/sellers/12/contact');
    expect(contact.request.method).toBe('GET');
    contact.flush({ sellerId: 12, sellerName: 'Seller', phone: '+21620123456', email: 'seller@example.com' });

    api.sellerReviews(12).subscribe();
    const reviews = http.expectOne('http://localhost:8080/api/sellers/12/reviews');
    expect(reviews.request.method).toBe('GET');
    reviews.flush({ averageRating: 4.5, reviewCount: 2, eligible: false, reviews: [] });

    api.saveSellerReview(12, 5, 'Fast and helpful').subscribe();
    const saveReview = http.expectOne('http://localhost:8080/api/sellers/12/reviews/me');
    expect(saveReview.request.method).toBe('PUT');
    expect(saveReview.request.body).toEqual({ rating: 5, comment: 'Fast and helpful' });
    saveReview.flush({ averageRating: 5, reviewCount: 1, eligible: true, reviews: [] });
  });

  it('supports provider discovery, OAuth exchange, and password recovery', () => {
    api.authProviders().subscribe();
    http.expectOne('http://localhost:8080/api/auth/providers')
      .flush({ google: true, facebook: false, microsoft: true });

    api.exchangeOAuth('exchange-code').subscribe();
    const exchange = http.expectOne('http://localhost:8080/api/auth/oauth/exchange');
    expect(exchange.request.body.code).toBe('exchange-code');
    exchange.flush({ token: 'jwt', userId: 1, email: 'buyer@example.com', fullName: 'Buyer', role: 'BUYER' });

    api.requestRecovery('buyer@example.com').subscribe();
    const start = http.expectOne('http://localhost:8080/api/auth/recovery/request');
    expect(start.request.body.identifier).toBe('buyer@example.com');
    start.flush({ message: 'If an account matches, a recovery code has been sent.', developmentCode: '123456' });

    api.completeRecovery({ identifier: 'buyer@example.com', code: '123456', newPassword: 'NewPassword123!' }).subscribe();
    const complete = http.expectOne('http://localhost:8080/api/auth/recovery/complete');
    expect(complete.request.body.code).toBe('123456');
    complete.flush(null);
  });
});
