import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { App } from './app';

describe('E-biessa Angular application', () => {
  beforeEach(async () => {
    localStorage.clear();
    window.scrollTo = () => undefined;
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideHttpClient(), provideHttpClientTesting()]
    }).compileComponents();
  });

  it('creates the marketplace shell', () => {
    const fixture = TestBed.createComponent(App);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('renders the E-biessa value proposition', () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('The right part.');
    expect(fixture.nativeElement.textContent).toContain('Marketplace');
    expect(fixture.nativeElement.textContent).toContain('E-biessa');
  });

  it('persists the selected color theme', () => {
    const fixture = TestBed.createComponent(App);
    fixture.componentInstance.toggleTheme();
    fixture.detectChanges();
    expect(fixture.componentInstance.darkMode()).toBe(true);
    expect(localStorage.getItem('e_bieca_theme')).toBe('dark');
    expect(document.documentElement.dataset['theme']).toBe('dark');
    expect(fixture.nativeElement.querySelector('.app-shell.dark-theme')).toBeTruthy();
  });

  it('shows the complete vehicle filter set', () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    const http = TestBed.inject(HttpTestingController);
    http.expectOne('http://localhost:8080/api/categories').flush([]);
    http.expectOne('http://localhost:8080/api/vehicles').flush([
      { id: 1, brand: 'Volkswagen', model: 'Golf', year: 2018 }
    ]);
    http.expectOne('http://localhost:8080/api/vehicles/catalog/years').flush([2020, 2019, 2018]);
    http.expectOne('http://localhost:8080/api/vehicles/catalog/makes').flush([{ id: 'volkswagen', name: 'Volkswagen' }]);
    http.expectOne(req => req.url.endsWith('/parts')).flush({ content: [], totalElements: 0, totalPages: 0 });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Make');
    expect(fixture.nativeElement.textContent).toContain('Model');
    expect(fixture.nativeElement.textContent).toContain('Year');
  });

  it('keeps sorting, pagination, and filters in the URL', () => {
    window.history.replaceState({}, '', '/');
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    const http = TestBed.inject(HttpTestingController);
    http.expectOne('http://localhost:8080/api/auth/providers').flush({ google: false, facebook: false, microsoft: false });
    http.expectOne('http://localhost:8080/api/categories').flush([]);
    http.expectOne('http://localhost:8080/api/vehicles').flush([]);
    http.expectOne('http://localhost:8080/api/vehicles/catalog/years').flush([2026]);
    http.expectOne('http://localhost:8080/api/vehicles/catalog/makes').flush([]);
    http.expectOne(req => req.url.endsWith('/parts')).flush({ content: [], totalElements: 0, totalPages: 0, number: 0 });

    fixture.componentInstance.filters.brand = 'Volkswagen';
    fixture.componentInstance.filters.partPosition = 'FRONT_LEFT';
    fixture.componentInstance.filters.negotiable = 'true';
    fixture.componentInstance.sort = 'price,asc';
    fixture.componentInstance.currentPage.set(2);
    fixture.componentInstance.loadParts();
    const request = http.expectOne(req => req.url.endsWith('/parts') && req.params.get('page') === '2');
    expect(request.request.params.get('sort')).toBe('price,asc');
    request.flush({ content: [], totalElements: 30, totalPages: 3, number: 2 });
    expect(window.location.search).toContain('brand=Volkswagen');
    expect(window.location.search).toContain('partPosition=FRONT_LEFT');
    expect(window.location.search).toContain('negotiable=true');
    expect(window.location.search).toContain('sort=price%2Casc');
    expect(window.location.search).toContain('page=2');
    window.history.replaceState({}, '', '/');
  });

  it('renders compatibility-aware AI results with explanations and preserves the query', () => {
    window.history.replaceState({}, '', '/');
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    const http = TestBed.inject(HttpTestingController);
    http.expectOne('http://localhost:8080/api/auth/providers').flush({ google: false, facebook: false, microsoft: false });
    http.expectOne('http://localhost:8080/api/categories').flush([{ id: 2, name: 'Brake Systems', subCategories: [] }]);
    http.expectOne('http://localhost:8080/api/vehicles').flush([]);
    http.expectOne('http://localhost:8080/api/vehicles/catalog/years').flush([2018]);
    http.expectOne('http://localhost:8080/api/vehicles/catalog/makes').flush([]);
    http.expectOne(req => req.url.endsWith('/parts')).flush({ content: [], totalElements: 0, totalPages: 0, number: 0 });

    fixture.componentInstance.setSearchMode('ai');
    fixture.componentInstance.aiQuery = 'front brake part for a diesel Golf';
    fixture.componentInstance.applyFilters();
    const request = http.expectOne(req => req.url.endsWith('/ai/search'));
    expect(request.request.params.get('query')).toBe('front brake part for a diesel Golf');
    expect(request.request.params.get('minimumScore')).toBe('0.5');
    request.flush({
      query: 'front brake part for a diesel Golf', tookMs: 14,
      extractedConstraints: { model: 'Golf', fuelType: 'Diesel', position: 'FRONT', category: 'Brake Systems' },
      answer: { summary: 'Listing #7 is the strongest current match.', recommendedListingIds: [7], cautions: ['Confirm exact fitment.'], generatedBy: 'deterministic' },
      results: [{
        score: .91, matchReasons: ['Golf compatible', 'Diesel', 'Brake system'],
        part: { id: 7, title: 'Volkswagen Golf front brake caliper', price: 320, sellerName: 'Garage Tunis', sellerId: 2, status: 'ACTIVE', views: 3, createdAt: '2026-08-01T10:00:00', imageUrls: [], images: [], favoriteCount: 0, favorited: false }
      }]
    });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('AI-ranked matches');
    expect(fixture.nativeElement.textContent).toContain('Golf compatible');
    expect(fixture.nativeElement.textContent).toContain('Diesel');
    expect(fixture.nativeElement.textContent).toContain('Brake system');
    expect(fixture.nativeElement.textContent).toContain('91% match');
    expect(fixture.nativeElement.textContent).toContain('E-biessa AI answer');
    expect(fixture.nativeElement.textContent).toContain('Listing #7 is the strongest current match.');
    expect(fixture.nativeElement.textContent).toContain('Confirm exact fitment.');
    expect(window.location.search).toContain('mode=ai');
    expect(window.location.search).toContain('q=front+brake+part+for+a+diesel+Golf');
    window.history.replaceState({}, '', '/');
  });

  it('previews a rich listing with multiple compatible vehicles before publishing', () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    const http = TestBed.inject(HttpTestingController);
    http.expectOne('http://localhost:8080/api/auth/providers').flush({ google: false, facebook: false, microsoft: false });
    http.expectOne('http://localhost:8080/api/categories').flush([]);
    http.expectOne('http://localhost:8080/api/vehicles').flush([
      { id: 4, brand: 'Volkswagen', model: 'Golf', year: 2018, engine: '1.6 TDI', fuelType: 'Diesel' },
      { id: 8, brand: 'Seat', model: 'Leon', year: 2019, engine: '1.6 TDI', fuelType: 'Diesel' }
    ]);
    http.expectOne('http://localhost:8080/api/vehicles/catalog/years').flush([2019, 2018]);
    http.expectOne('http://localhost:8080/api/vehicles/catalog/makes').flush([{ id: 'volkswagen', name: 'Volkswagen' }, { id: 'seat', name: 'Seat' }]);
    http.expectOne(req => req.url.endsWith('/parts')).flush({ content: [], totalElements: 0, totalPages: 0, number: 0 });
    fixture.componentInstance.user.set({ id: 3, userId: 3, email: 'seller@example.com', fullName: 'Seller', role: 'SELLER' });
    fixture.componentInstance.openEditor();
    fixture.componentInstance.draft.title = 'Interchangeable front caliper';
    fixture.componentInstance.draft.price = 280;
    fixture.componentInstance.draft.manufacturer = 'ATE';
    fixture.componentInstance.draft.partBrand = 'ATE Original';
    fixture.componentInstance.draft.negotiable = true;
    fixture.componentInstance.alternativeOemText = '1K0615123-A\n5Q0615123';
    fixture.componentInstance.toggleVehicleCompatibility(4, true);
    fixture.componentInstance.toggleVehicleCompatibility(8, true);
    fixture.componentInstance.listingPreview.set(true);
    fixture.detectChanges();

    expect(fixture.componentInstance.selectedDraftVehicles()).toHaveLength(2);
    expect(fixture.componentInstance.parseAlternativeOems()).toEqual(['1K0615123-A', '5Q0615123']);
    expect(fixture.nativeElement.textContent).toContain('Preview your listing');
    expect(fixture.nativeElement.textContent).toContain('Volkswagen Golf 2018');
    expect(fixture.nativeElement.textContent).toContain('Seat Leon 2019');
    expect(fixture.nativeElement.textContent).toContain('Negotiable');
  });

  it('reveals call, WhatsApp, and email actions to an authenticated buyer', () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    const http = TestBed.inject(HttpTestingController);
    http.expectOne('http://localhost:8080/api/categories').flush([]);
    http.expectOne('http://localhost:8080/api/vehicles').flush([]);
    http.expectOne('http://localhost:8080/api/vehicles/catalog/years').flush([2026]);
    http.expectOne('http://localhost:8080/api/vehicles/catalog/makes').flush([]);
    http.expectOne(req => req.url.endsWith('/parts')).flush({ content: [], totalElements: 0, totalPages: 0 });

    const part = {
      id: 5, title: 'Toyota Corolla alternator 2018', price: 420, sellerName: 'Parts Seller', sellerId: 12,
      status: 'ACTIVE' as const, views: 4, createdAt: '2026-01-01', imageUrls: [], images: [], favoriteCount: 0, favorited: false
    };
    fixture.componentInstance.user.set({ id: 2, userId: 2, email: 'buyer@example.com', fullName: 'Buyer', role: 'BUYER' });
    fixture.componentInstance.openPart(part);
    http.expectOne('http://localhost:8080/api/parts/5').flush({ ...part, views: 5 });
    fixture.componentInstance.revealSellerContact(part);
    http.expectOne('http://localhost:8080/api/sellers/12/contact').flush({
      sellerId: 12, sellerName: 'Parts Seller', phone: '+216 20 123 456', email: 'seller@example.com'
    });
    fixture.detectChanges();

    const whatsapp = fixture.nativeElement.querySelector('.contact-whatsapp') as HTMLAnchorElement;
    expect(fixture.nativeElement.textContent).toContain('+216 20 123 456');
    expect(fixture.nativeElement.querySelector('.contact-call')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('.contact-email')).toBeTruthy();
    expect(decodeURIComponent(whatsapp.href)).toContain('Toyota Corolla alternator 2018');
    expect(fixture.nativeElement.querySelector('.listing-page')).toBeTruthy();
    expect(window.location.pathname).toBe('/parts/5');
    fixture.componentInstance.closeListing();
  });

  it('offers social authentication and an email-or-phone recovery flow', () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    const http = TestBed.inject(HttpTestingController);
    http.expectOne('http://localhost:8080/api/auth/providers').flush({ google: true, facebook: true, microsoft: true });
    http.expectOne('http://localhost:8080/api/categories').flush([]);
    http.expectOne('http://localhost:8080/api/vehicles').flush([]);
    http.expectOne('http://localhost:8080/api/vehicles/catalog/years').flush([2026]);
    http.expectOne('http://localhost:8080/api/vehicles/catalog/makes').flush([]);
    http.expectOne(req => req.url.endsWith('/parts')).flush({ content: [], totalElements: 0, totalPages: 0 });
    fixture.componentInstance.openAuth(false);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Continue with Google');
    expect(fixture.nativeElement.textContent).toContain('Continue with Facebook');
    expect(fixture.nativeElement.textContent).toContain('Continue with Microsoft');
    fixture.componentInstance.authStep.set('recovery-request');
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Find your account');
    expect(fixture.nativeElement.textContent).toContain('Email or phone');
  });

  it('renders a listing-linked buyer and seller message thread', () => {
    const fixture = TestBed.createComponent(App);
    fixture.componentInstance.user.set({ id: 2, userId: 2, email: 'buyer@example.com', fullName: 'Buyer', role: 'BUYER' });
    const conversation = {
      id: 3, partId: 14, partTitle: 'Golf alternator', buyerId: 2, buyerName: 'Buyer', sellerId: 7,
      sellerName: 'Garage Seven', otherPartyName: 'Garage Seven', lastMessage: 'Yes, it is available.', unreadCount: 1
    };
    fixture.componentInstance.conversations.set([conversation]);
    fixture.componentInstance.activeConversation.set({ conversation, messages: [
      { id: 8, senderId: 2, senderName: 'Buyer', body: 'Is this still available?', createdAt: '2026-08-17T12:00:00' },
      { id: 9, senderId: 7, senderName: 'Garage Seven', body: 'Yes, it is available.', createdAt: '2026-08-17T12:05:00',
        attachments: [{ id: 2, url: '/uploads/connector.jpg', originalName: 'connector.jpg', contentType: 'image/jpeg', sizeBytes: 1200 }] }
    ] });
    fixture.componentInstance.view.set('inbox');
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Your inbox');
    expect(fixture.nativeElement.textContent).toContain('Golf alternator');
    expect(fixture.nativeElement.textContent).toContain('Is this still available?');
    expect(fixture.nativeElement.textContent).toContain('Yes, it is available.');
    expect(fixture.nativeElement.querySelector('.message-photos img')?.getAttribute('src')).toContain('/uploads/connector.jpg');
    expect(fixture.nativeElement.textContent).toContain('Add photos');

    fixture.componentInstance.realtimeConnected.set(true);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Live');
  });

  it('opens the newest conversation and orders its messages chronologically', () => {
    const fixture = TestBed.createComponent(App);
    fixture.componentInstance.user.set({ id: 2, userId: 2, email: 'buyer@example.com', fullName: 'Buyer', role: 'BUYER' });
    fixture.componentInstance.setView('inbox');
    const http = TestBed.inject(HttpTestingController);
    http.expectOne('http://localhost:8080/api/messages/conversations').flush([
      { id: 1, partId: 10, partTitle: 'Older listing', buyerId: 2, buyerName: 'Buyer', sellerId: 6, sellerName: 'Seller One', otherPartyName: 'Seller One', lastMessage: 'Older', lastMessageAt: '2026-08-17T10:00:00', unreadCount: 0 },
      { id: 2, partId: 11, partTitle: 'Newest listing', buyerId: 2, buyerName: 'Buyer', sellerId: 7, sellerName: 'Seller Two', otherPartyName: 'Seller Two', lastMessage: 'Newest', lastMessageAt: '2026-08-17T12:00:00', unreadCount: 1 }
    ]);
    http.expectOne('http://localhost:8080/api/messages/conversations/2').flush({
      conversation: { id: 2, partId: 11, partTitle: 'Newest listing', buyerId: 2, buyerName: 'Buyer', sellerId: 7, sellerName: 'Seller Two', otherPartyName: 'Seller Two', lastMessage: 'Newest', lastMessageAt: '2026-08-17T12:00:00', unreadCount: 1 },
      messages: [
        { id: 12, senderId: 7, senderName: 'Seller Two', body: 'Second message', createdAt: '2026-08-17T12:00:00' },
        { id: 11, senderId: 2, senderName: 'Buyer', body: 'First message', createdAt: '2026-08-17T11:00:00' }
      ]
    });
    http.expectOne('http://localhost:8080/api/messages/conversations').flush([]);
    fixture.detectChanges();

    expect(fixture.componentInstance.activeConversation()?.conversation.id).toBe(2);
    expect(fixture.componentInstance.activeConversation()?.messages.map(message => message.id)).toEqual([11, 12]);
    const messageText = [...fixture.nativeElement.querySelectorAll('.message-bubble p')].map((element: Element) => element.textContent);
    expect(messageText).toEqual(['First message', 'Second message']);
  });

  it('keeps older messages in place when the user has scrolled up', () => {
    const fixture = TestBed.createComponent(App);
    const component = fixture.componentInstance;
    const conversation = { id: 3, partId: 14, partTitle: 'Golf alternator', buyerId: 2, buyerName: 'Buyer', sellerId: 7, sellerName: 'Garage Seven', otherPartyName: 'Garage Seven', unreadCount: 0 };
    component.activeConversation.set({ conversation, messages: [] });
    let scrollCalls = 0;
    (component as any).scrollConversationToLatest = () => scrollCalls++;
    (component as any).isConversationNearLatest = () => false;

    (component as any).appendRealtimeMessage(3, { id: 1, senderId: 7, senderName: 'Garage Seven', body: 'New while reading', createdAt: '2026-08-17T12:00:00' });
    expect(scrollCalls).toBe(0);

    (component as any).isConversationNearLatest = () => true;
    (component as any).appendRealtimeMessage(3, { id: 2, senderId: 7, senderName: 'Garage Seven', body: 'New at bottom', createdAt: '2026-08-17T12:01:00' });
    expect(scrollCalls).toBe(1);
  });

  it('shows suspension state and administrator audit history', () => {
    const fixture = TestBed.createComponent(App);
    fixture.componentInstance.user.set({ id: 1, userId: 1, email: 'admin@example.com', fullName: 'Admin', role: 'ADMIN' });
    fixture.componentInstance.adminUsers.set([{ id: 7, userId: 7, email: 'seller@example.com', fullName: 'Seller', role: 'SELLER', suspended: true }]);
    fixture.componentInstance.adminAuditLogs.set([{ id: 2, adminId: 1, adminEmail: 'admin@example.com', action: 'USER_SUSPENDED', targetType: 'USER', targetId: 7, targetLabel: 'seller@example.com', details: 'Repeated policy violations', createdAt: '2026-08-17T12:00:00' }]);
    fixture.componentInstance.view.set('admin');
    fixture.componentInstance.adminSection.set('users');
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Suspended');
    fixture.componentInstance.adminSection.set('audit');
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Administrator audit log');
    expect(fixture.nativeElement.textContent).toContain('User suspended');
    expect(fixture.nativeElement.textContent).toContain('Repeated policy violations');
  });

  it('lets an eligible buyer rate a seller with an optional comment', () => {
    window.history.replaceState({}, '', '/');
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    const component = fixture.componentInstance;
    component.user.set({ id: 2, userId: 2, email: 'buyer@example.com', fullName: 'Buyer', role: 'BUYER' });
    component.publicSeller.set({ id: 7, fullName: 'Seller Seven', businessName: 'Seven Garage', activeListingCount: 3, averageRating: 4, reviewCount: 1 });
    component.sellerReviews.set({
      averageRating: 4, reviewCount: 1, eligible: true,
      reviews: [{ id: 1, buyerId: 8, buyerName: 'Previous Buyer', rating: 4, comment: 'Good communication.', createdAt: '2026-08-17T10:00:00', updatedAt: '2026-08-17T10:00:00' }]
    });
    component.view.set('seller-profile');
    component.loading.set(false);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Seller reputation');
    expect(fixture.nativeElement.textContent).toContain('Good communication.');
    expect(fixture.nativeElement.querySelectorAll('.star-picker button').length).toBe(5);

    component.setReviewRating(5);
    component.reviewDraft.comment = '';
    component.submitSellerReview(component.publicSeller()!);
    const http = TestBed.inject(HttpTestingController);
    const request = http.expectOne('http://localhost:8080/api/sellers/7/reviews/me');
    expect(request.request.body).toEqual({ rating: 5, comment: '' });
    request.flush({ averageRating: 5, reviewCount: 1, eligible: true, reviews: [] });
    expect(component.publicSeller()?.averageRating).toBe(5);
  });

  it('shows pending seller applications to buyers and administrators', () => {
    window.history.replaceState({}, '', '/');
    const buyerFixture = TestBed.createComponent(App);
    buyerFixture.detectChanges();
    buyerFixture.componentInstance.user.set({ id: 4, userId: 4, email: 'buyer@example.com', fullName: 'Buyer', role: 'BUYER', sellerApplicationStatus: 'PENDING' });
    buyerFixture.componentInstance.view.set('profile');
    buyerFixture.detectChanges();
    expect(buyerFixture.nativeElement.textContent).toContain('Application pending');
    expect(buyerFixture.nativeElement.textContent).toContain('remain a buyer until approval');

    const adminFixture = TestBed.createComponent(App);
    adminFixture.detectChanges();
    adminFixture.componentInstance.user.set({ id: 1, userId: 1, email: 'admin@example.com', fullName: 'Admin', role: 'ADMIN' });
    adminFixture.componentInstance.sellerApplications.set([{ id: 4, userId: 4, email: 'buyer@example.com', fullName: 'Buyer', role: 'BUYER', businessName: 'Buyer Garage', phone: '+21620000000', location: 'Tunis', sellerApplicationStatus: 'PENDING', sellerApplicationSubmittedAt: '2026-08-23T10:00:00' }]);
    adminFixture.componentInstance.view.set('admin');
    adminFixture.componentInstance.adminSection.set('applications');
    adminFixture.detectChanges();
    expect(adminFixture.nativeElement.textContent).toContain('Pending seller applications');
    expect(adminFixture.nativeElement.textContent).toContain('Buyer Garage');
    expect(adminFixture.nativeElement.textContent).toContain('Approve seller');
  });

  it('lets buyers report sellers and renders the administrator seller-report queue', () => {
    window.history.replaceState({}, '', '/');
    const buyerFixture = TestBed.createComponent(App);
    buyerFixture.detectChanges();
    const buyer = buyerFixture.componentInstance;
    buyer.user.set({ id: 2, userId: 2, email: 'buyer@example.com', fullName: 'Buyer', role: 'BUYER' });
    const seller = { id: 12, fullName: 'Seller Twelve', businessName: 'Garage Twelve', activeListingCount: 4, averageRating: 4.5, reviewCount: 2 };
    buyer.publicSeller.set(seller);
    buyer.sellerReviews.set({ averageRating: 4.5, reviewCount: 2, eligible: false, reviews: [] });
    buyer.view.set('seller-profile');
    buyer.loading.set(false);
    buyerFixture.detectChanges();
    expect(buyerFixture.nativeElement.textContent).toContain('Report this seller');
    buyer.openSellerReport(seller);
    buyerFixture.detectChanges();
    expect(buyerFixture.nativeElement.textContent).toContain('Report Garage Twelve');
    expect(buyerFixture.nativeElement.textContent).toContain('Fraud or payment scam');

    const adminFixture = TestBed.createComponent(App);
    adminFixture.detectChanges();
    const admin = adminFixture.componentInstance;
    admin.user.set({ id: 1, userId: 1, email: 'admin@example.com', fullName: 'Admin', role: 'ADMIN' });
    admin.adminSellerReports.set([{ id: 9, sellerId: 12, sellerName: 'Seller Twelve', businessName: 'Garage Twelve', reporterId: 2, reporterName: 'Buyer', reason: 'FRAUD_OR_SCAM', details: 'Requested a suspicious external payment.', status: 'PENDING', createdAt: '2026-08-25T10:00:00' }]);
    admin.view.set('admin');
    admin.adminSection.set('seller-reports');
    adminFixture.detectChanges();
    expect(adminFixture.nativeElement.textContent).toContain('Seller reports');
    expect(adminFixture.nativeElement.textContent).toContain('Requested a suspicious external payment.');
    expect(adminFixture.nativeElement.textContent).toContain('Suspend seller');
  });

  it('renders a separate administrator dashboard with overview metrics and focused navigation', () => {
    const fixture = TestBed.createComponent(App);
    const component = fixture.componentInstance;
    component.user.set({ id: 1, userId: 1, email: 'admin@example.com', fullName: 'Platform Admin', role: 'ADMIN' });
    component.adminUsers.set([
      { id: 1, userId: 1, email: 'admin@example.com', fullName: 'Platform Admin', role: 'ADMIN' },
      { id: 2, userId: 2, email: 'seller@example.com', fullName: 'Active Seller', role: 'SELLER', suspended: false }
    ]);
    component.sellerApplications.set([{ id: 3, userId: 3, email: 'buyer@example.com', fullName: 'Future Seller', role: 'BUYER' }]);
    component.adminParts.set([{ id: 10, title: 'Brake caliper', description: '', price: 150, status: 'ACTIVE', sellerId: 2, sellerName: 'Active Seller', views: 2, createdAt: '2026-08-25T09:00:00', imageUrls: [], images: [], favoriteCount: 0, favorited: false }]);
    component.adminReports.set([{ id: 4, partId: 10, partTitle: 'Brake caliper', partStatus: 'ACTIVE', sellerId: 2, sellerName: 'Active Seller', reporterId: 3, reporterName: 'Buyer', reason: 'WRONG_INFORMATION', status: 'PENDING', createdAt: '2026-08-25T10:00:00' }]);
    component.view.set('admin');
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.admin-dashboard')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('.topbar')).toBeFalsy();
    expect(fixture.nativeElement.textContent).toContain('Marketplace at a glance');
    expect(fixture.nativeElement.textContent).toContain('Pending work');
    expect(component.pendingAdminWork()).toBe(2);

    component.setAdminSection('listings');
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Brake caliper');
    expect(fixture.nativeElement.textContent).toContain('Inventory moderation');
  });
});
