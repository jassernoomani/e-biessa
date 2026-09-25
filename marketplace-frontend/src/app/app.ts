import { CommonModule } from '@angular/common';
import { Component, ElementRef, HostBinding, HostListener, NgZone, OnDestroy, OnInit, ViewChild, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { forkJoin, switchMap } from 'rxjs';
import { ApiService } from './api.service';
import { ThemeService } from './core/theme.service';
import { AdminAuditLog, Category, Conversation, ConversationDetail, ImageAsset, ListingReport, MarketplaceMessage, Part, PartPayload, PartStatus, PriceEstimate, ProfilePayload, ReportReason, ReportStatus, SellerContact, SellerProfile, SellerReport, SellerReportReason, SellerReviewSummary, SemanticPartResult, SemanticSearchResponse, User, UserRole, Vehicle, VehicleCatalogMake, VisionCandidate, VisionIdentification } from './models';
import { BrandLogoComponent } from './shared/brand-logo.component';
import { RecommendationsComponent } from './recommendations/recommendations.component';

type View = 'market' | 'favorites' | 'inbox' | 'studio' | 'admin' | 'profile' | 'admin-profile' | 'seller-profile' | 'listing-detail';
type AdminSection = 'overview' | 'applications' | 'seller-reports' | 'listing-reports' | 'users' | 'listings' | 'audit';
type SpeechEvent = Event & { results: { length: number; [index: number]: { [index: number]: { transcript: string } } } };
interface BrowserSpeechRecognition {
  lang: string;
  continuous: boolean;
  interimResults: boolean;
  onresult: ((event: SpeechEvent) => void) | null;
  onerror: ((event: Event & { error?: string }) => void) | null;
  onnomatch: (() => void) | null;
  onend: (() => void) | null;
  start(): void;
  stop(): void;
}
type BrowserSpeechRecognitionConstructor = new () => BrowserSpeechRecognition;

@Component({
  selector: 'app-root',
  imports: [CommonModule, FormsModule, BrandLogoComponent, RecommendationsComponent],
  templateUrl: './app.html',
  styleUrls: ['./app.css', './features.css', './theme.css']
})
export class App implements OnInit, OnDestroy {
  private readonly api = inject(ApiService);
  private readonly theme = inject(ThemeService);
  private readonly zone = inject(NgZone);
  readonly view = signal<View>('market');
  readonly user = signal<User | null>(null);
  readonly parts = signal<Part[]>([]);
  readonly favorites = signal<Part[]>([]);
  readonly listings = signal<Part[]>([]);
  readonly categories = signal<Category[]>([]);
  readonly vehicles = signal<Vehicle[]>([]);
  readonly catalogYears = signal<number[]>([]);
  readonly catalogMakes = signal<VehicleCatalogMake[]>([]);
  readonly catalogModels = signal<string[]>([]);
  readonly adminUsers = signal<User[]>([]);
  readonly sellerApplications = signal<User[]>([]);
  readonly adminParts = signal<Part[]>([]);
  readonly adminReports = signal<ListingReport[]>([]);
  readonly adminSellerReports = signal<SellerReport[]>([]);
  readonly adminAuditLogs = signal<AdminAuditLog[]>([]);
  readonly adminSection = signal<AdminSection>('overview');
  readonly adminUserSearch = signal('');
  readonly adminListingSearch = signal('');
  readonly conversations = signal<Conversation[]>([]);
  readonly activeConversation = signal<ConversationDetail | null>(null);
  readonly inboxLoading = signal(false);
  readonly selectedAdminUser = signal<User | null>(null);
  readonly selected = signal<Part | null>(null);
  readonly selectedImageUrl = signal('');
  readonly listingLoading = signal(false);
  readonly publicSeller = signal<SellerProfile | null>(null);
  readonly publicSellerListings = signal<Part[]>([]);
  readonly sellerReviews = signal<SellerReviewSummary | null>(null);
  readonly reviewSubmitting = signal(false);
  readonly sellerContact = signal<SellerContact | null>(null);
  readonly contactLoading = signal(false);
  readonly loading = signal(true);
  readonly message = signal('');
  readonly authOpen = signal(false);
  readonly registerMode = signal(false);
  readonly authStep = signal<'credentials' | 'recovery-request' | 'recovery-complete'>('credentials');
  readonly oauthProviders = signal({ google: false, facebook: false, microsoft: false });
  readonly recoveryDevelopmentCode = signal('');
  readonly editorOpen = signal(false);
  readonly listingPreview = signal(false);
  readonly priceEstimating = signal(false);
  readonly priceEstimate = signal<PriceEstimate | null>(null);
  readonly editingId = signal<number | null>(null);
  readonly editingPart = signal<Part | null>(null);
  readonly filePreviews = signal<{ name: string; url: string }[]>([]);
  readonly submitting = signal(false);
  readonly darkMode = this.theme.darkMode;
  readonly total = signal(0);
  readonly currentPage = signal(0);
  readonly totalPages = signal(0);
  readonly searchMode = signal<'keyword' | 'ai'>('keyword');
  readonly aiSearchResponse = signal<SemanticSearchResponse | null>(null);
  readonly aiMatches = signal<Record<number, SemanticPartResult>>({});
  readonly aiError = signal('');
  readonly visionLoading = signal(false);
  readonly visionResult = signal<VisionIdentification | null>(null);
  readonly visionPreview = signal('');
  readonly visionError = signal('');
  readonly voiceListening = signal(false);
  readonly voiceError = signal('');
  readonly reportOpen = signal(false);
  readonly reportingPart = signal<Part | null>(null);
  readonly sellerReportOpen = signal(false);
  readonly reportingSeller = signal<SellerProfile | null>(null);
  readonly draggedImageId = signal<number | null>(null);
  readonly replyImagePreviews = signal<{ name: string; url: string }[]>([]);
  readonly realtimeConnected = signal(false);
  readonly favoriteIds = computed(() => new Set(this.favorites().map(part => part.id)));
  readonly unreadMessages = computed(() => this.conversations().reduce((total, conversation) => total + conversation.unreadCount, 0));
  readonly filteredConversations = computed(() => {
    const query = this.conversationSearch().trim().toLowerCase();
    if (!query) return this.conversations();
    return this.conversations().filter(conversation =>
      [conversation.otherPartyName, conversation.partTitle, conversation.lastMessage]
        .some(value => value?.toLowerCase().includes(query))
    );
  });
  readonly engines = computed(() => [...new Set(this.vehicles().map(vehicle => vehicle.engine).filter((value): value is string => !!value))].sort());
  readonly fuelTypes = computed(() => [...new Set(this.vehicles().map(vehicle => vehicle.fuelType).filter((value): value is string => !!value))].sort());
  readonly filteredAdminUsers = computed(() => {
    const query = this.adminUserSearch().trim().toLowerCase();
    if (!query) return this.adminUsers();
    return this.adminUsers().filter(person =>
      [person.fullName, person.email, person.location, person.businessName, person.role]
        .some(value => value?.toLowerCase().includes(query))
    );
  });
  readonly filteredAdminParts = computed(() => {
    const query = this.adminListingSearch().trim().toLowerCase();
    if (!query) return this.adminParts();
    return this.adminParts().filter(part =>
      [part.title, part.sellerName, part.oemNumber, part.brand, part.model, part.status]
        .some(value => value?.toString().toLowerCase().includes(query))
    );
  });
  readonly activeSellerCount = computed(() => this.adminUsers().filter(person => person.role === 'SELLER' && !person.suspended).length);
  readonly suspendedUserCount = computed(() => this.adminUsers().filter(person => person.suspended).length);
  readonly activeAdminListingCount = computed(() => this.adminParts().filter(part => part.status === 'ACTIVE').length);
  readonly pendingAdminWork = computed(() => this.sellerApplications().length +
    this.adminReports().filter(report => report.status === 'PENDING').length +
    this.adminSellerReports().filter(report => report.status === 'PENDING').length);

  filters = { search: '', categoryId: '', brand: '', model: '', year: '', condition: '', minPrice: '', maxPrice: '', manufacturer: '', partBrand: '', partPosition: '', engine: '', fuelType: '', warranty: '', deliveryAvailable: '', collectionAvailable: '', negotiable: '' };
  aiQuery = '';
  voiceLanguage = 'fr-TN';
  sort = 'createdAt,desc';
  adminReportFilter: ReportStatus | '' = 'PENDING';
  adminSellerReportFilter: ReportStatus | '' = 'PENDING';
  reportDraft: { reason: ReportReason; details: string } = { reason: 'WRONG_INFORMATION', details: '' };
  sellerReportDraft: { reason: SellerReportReason; details: string } = { reason: 'FRAUD_OR_SCAM', details: '' };
  credentials = { email: '', password: '', fullName: '', phone: '' };
  recovery = { identifier: '', code: '', newPassword: '' };
  profileDraft: ProfilePayload = this.emptyProfile();
  draft: PartPayload = this.emptyDraft();
  alternativeOemText = '';
  listingMessageDraft = '';
  replyDraft = '';
  replyImages: File[] = [];
  reviewDraft = { rating: 0, comment: '' };
  readonly conversationSearch = signal('');
  @ViewChild('messageStream') private messageStream?: ElementRef<HTMLElement>;
  private messageSocket?: WebSocket;
  private voiceRecognition?: BrowserSpeechRecognition;
  private socketReconnectTimer?: ReturnType<typeof setTimeout>;
  private socketGeneration = 0;
  suspensionReasonDraft = '';
  sellerDecisionNotes: Record<number, string> = {};
  sellerReportAdminNotes: Record<number, string> = {};
  uploadFiles: File[] = [];

  @HostBinding('class.dark-theme') get darkThemeClass(): boolean { return this.darkMode(); }

  ngOnInit(): void {
    window.history.scrollRestoration = 'manual';
    if (window.location.pathname === '/') this.restoreBrowseStateFromUrl();
    this.api.authProviders().subscribe({ next: providers => this.oauthProviders.set(providers) });
    forkJoin({
      categories: this.api.categories(), vehicles: this.api.vehicles(),
      catalogYears: this.api.vehicleCatalogYears(), catalogMakes: this.api.vehicleCatalogMakes(this.filters.year)
    }).subscribe({
      next: ({ categories, vehicles, catalogYears, catalogMakes }) => {
        this.categories.set(categories);
        this.vehicles.set(vehicles);
        this.catalogYears.set(catalogYears);
        this.catalogMakes.set(this.withSelectedMake(catalogMakes));
        if (this.filters.brand) this.loadCatalogModels();
      },
      error: () => this.notice('The API is offline. Start Spring Boot on port 8080.')
    });
    if (this.searchMode() === 'ai' && this.aiQuery.trim()) this.runAiSearch(false);
    else this.loadParts(false);
    if (this.api.token) this.restoreSession();
    if (!this.loadSellerFromLocation()) this.loadListingFromLocation();
    this.handleOAuthCallback();
  }

  ngOnDestroy(): void {
    this.voiceRecognition?.stop();
    this.disconnectRealtimeMessaging();
  }

  @HostListener('window:popstate') onPopState(): void {
    if (this.loadSellerFromLocation() || this.loadListingFromLocation()) return;
    this.selected.set(null);
    this.sellerContact.set(null);
    this.view.set('market');
    this.restoreBrowseStateFromUrl();
    if (this.searchMode() === 'ai' && this.aiQuery.trim()) this.runAiSearch(false);
    else this.loadParts(false);
  }

  loadParts(syncUrl = true): void {
    this.aiSearchResponse.set(null);
    this.aiMatches.set({});
    this.aiError.set('');
    this.loading.set(true);
    const query = { ...this.filters, page: String(this.currentPage()), sort: this.sort };
    if (syncUrl && window.location.pathname === '/') this.syncBrowseUrl();
    this.api.browse(query).subscribe({
      next: page => {
        this.parts.set(page.content);
        this.total.set(page.totalElements);
        this.totalPages.set(page.totalPages);
        this.currentPage.set(page.number ?? this.currentPage());
        this.loading.set(false);
      },
      error: error => { this.loading.set(false); this.handleError(error); }
    });
  }

  applyFilters(): void {
    this.currentPage.set(0);
    if (this.searchMode() === 'ai') this.runAiSearch();
    else this.loadParts();
  }

  searchInput(): string { return this.searchMode() === 'ai' ? this.aiQuery : this.filters.search; }

  updateSearchInput(value: string): void {
    if (this.searchMode() === 'ai') this.aiQuery = value;
    else this.filters.search = value;
  }

  setSearchMode(mode: 'keyword' | 'ai'): void {
    if (this.searchMode() === mode) return;
    this.searchMode.set(mode);
    this.currentPage.set(0);
    if (mode === 'ai') {
      this.aiQuery = this.aiQuery || this.filters.search;
      this.aiSearchResponse.set(null);
      this.aiMatches.set({});
      this.parts.set([]);
      this.total.set(0);
      this.totalPages.set(0);
      this.loading.set(false);
      this.syncBrowseUrl();
    } else {
      this.filters.search = this.filters.search || this.aiQuery;
      this.loadParts();
    }
  }

  runAiSearch(syncUrl = true): void {
    const query = this.aiQuery.trim();
    if (query.length < 2) {
      this.aiError.set('Describe the part you need using at least 2 characters.');
      this.parts.set([]);
      this.total.set(0);
      if (syncUrl) this.syncBrowseUrl();
      return;
    }
    this.loading.set(true);
    this.aiError.set('');
    if (syncUrl && window.location.pathname === '/') this.syncBrowseUrl();
    const request = this.user()?.role === 'BUYER'
      ? this.api.marketplaceAssistant(query, this.semanticFilterParams())
      : this.api.semanticSearch(query, this.semanticFilterParams());
    request.subscribe({
      next: response => {
        this.aiSearchResponse.set(response);
        this.aiMatches.set(Object.fromEntries(response.results.map(result => [result.part.id, result])));
        this.parts.set(response.results.map(result => result.part));
        this.total.set(response.results.length);
        this.totalPages.set(0);
        this.loading.set(false);
      },
      error: error => {
        this.loading.set(false);
        this.parts.set([]);
        this.total.set(0);
        this.aiError.set(error?.error?.message || 'AI search is temporarily unavailable. Try keyword search instead.');
      }
    });
  }

  identifyPartPhoto(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) return;
    if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type)) {
      this.visionError.set('Choose a JPEG, PNG, or WebP image.');
      return;
    }
    if (file.size > 5 * 1024 * 1024) {
      this.visionError.set('The image must be 5 MB or smaller.');
      return;
    }
    const reader = new FileReader();
    reader.onload = () => this.visionPreview.set(String(reader.result ?? ''));
    reader.readAsDataURL(file);
    this.visionLoading.set(true);
    this.visionError.set('');
    this.visionResult.set(null);
    this.api.identifyPartImage(file).subscribe({
      next: result => {
        this.visionResult.set(result);
        this.visionLoading.set(false);
      },
      error: error => {
        this.visionLoading.set(false);
        this.visionError.set(error?.error?.message || 'The photo could not be identified. Check that the vision model is enabled.');
      }
    });
  }

  toggleVoiceSearch(): void {
    if (this.voiceListening()) {
      this.voiceRecognition?.stop();
      return;
    }
    const browserWindow = window as typeof window & {
      SpeechRecognition?: BrowserSpeechRecognitionConstructor;
      webkitSpeechRecognition?: BrowserSpeechRecognitionConstructor;
    };
    const Recognition = browserWindow.SpeechRecognition ?? browserWindow.webkitSpeechRecognition;
    if (!Recognition) {
      this.voiceError.set('Voice search is not supported by this browser. Use Chrome or Edge on localhost.');
      return;
    }
    const recognition = new Recognition();
    recognition.lang = this.voiceLanguage;
    recognition.continuous = false;
    recognition.interimResults = false;
    recognition.onresult = event => {
      const transcript = event.results[event.results.length - 1]?.[0]?.transcript?.trim();
      if (transcript) this.zone.run(() => this.updateSearchInput(transcript));
      this.voiceError.set('');
    };
    recognition.onnomatch = () => this.zone.run(() => this.voiceError.set(
      'No matching speech was recognized. Select the language you are speaking, move closer to the microphone, and try again.'
    ));
    recognition.onerror = event => this.zone.run(() => this.voiceError.set(this.voiceRecognitionError(event.error)));
    recognition.onend = () => this.zone.run(() => this.voiceListening.set(false));
    this.voiceRecognition = recognition;
    this.voiceError.set('');
    this.voiceListening.set(true);
    recognition.start();
  }

  private voiceRecognitionError(error?: string): string {
    switch (error) {
      case 'aborted':
        return '';
      case 'not-allowed':
      case 'service-not-allowed':
        return 'Microphone access or the browser speech service was blocked. Allow microphone access for localhost, then try again.';
      case 'audio-capture':
        return 'No working microphone was found. Check the selected Windows input device and its microphone level.';
      case 'no-speech':
        return 'No speech was detected. Select FR, AR, or EN to match your language, then speak after the microphone activates.';
      case 'language-not-supported':
        return 'This browser does not support the selected recognition language. Try French or English.';
      case 'network':
        return 'The browser speech service could not be reached. Brave may block this service; try Chrome or Edge, or type your search.';
      default:
        return 'Speech recognition failed in the browser. Check microphone permission and try Chrome or Edge.';
    }
  }

  searchVisionCandidate(candidate: VisionCandidate): void {
    const current = this.aiQuery.trim();
    this.aiQuery = current.toLowerCase().includes(candidate.partType.toLowerCase())
      ? current : `${candidate.partType} ${current}`.trim();
    this.runAiSearch();
  }

  aiMatch(partId: number): SemanticPartResult | undefined { return this.aiMatches()[partId]; }

  aiToolLabels(): string[] {
    const labels: Record<string, string> = {
      search_agent: 'Inventory search', compatibility_agent: 'Compatibility check',
      recommendation_agent: 'Personalized ranking'
    };
    const tools = (this.aiSearchResponse() as SemanticSearchResponse & { toolsUsed?: string[] } | null)?.toolsUsed ?? [];
    return tools.map(tool => labels[tool]).filter((label): label is string => !!label);
  }

  aiConstraintLabels(): string[] {
    const constraints = this.aiSearchResponse()?.extractedConstraints ?? {};
    const labels: string[] = [];
    const push = (key: string, label?: string) => {
      const value = constraints[key];
      if (value !== null && value !== undefined && `${value}`.trim()) labels.push(label ? `${label}: ${value}` : `${value}`);
    };
    push('brand', 'Make'); push('model', 'Model'); push('year', 'Year'); push('fuelType', 'Fuel');
    push('position', 'Position'); push('category', 'System'); push('partType', 'Part');
    return labels;
  }

  changeSort(): void { this.currentPage.set(0); this.loadParts(); }

  goToPage(page: number): void {
    if (page < 0 || page >= this.totalPages() || page === this.currentPage()) return;
    this.currentPage.set(page);
    this.loadParts();
    requestAnimationFrame(() => document.querySelector('.results')?.scrollIntoView({ behavior: 'smooth' }));
  }

  pageNumbers(): number[] {
    const start = Math.max(0, Math.min(this.currentPage() - 2, this.totalPages() - 5));
    return Array.from({ length: Math.min(5, this.totalPages()) }, (_, index) => start + index);
  }

  setView(next: View): void {
    const publicViews: View[] = ['market', 'seller-profile', 'listing-detail'];
    if (!publicViews.includes(next) && !this.user()) { this.authOpen.set(true); return; }
    if (!['seller-profile', 'listing-detail'].includes(next) && ['seller-profile', 'listing-detail'].includes(this.view())) {
      window.history.pushState({}, '', next === 'market' ? this.marketStateUrl() : '/');
      this.selected.set(null);
      this.sellerContact.set(null);
    }
    this.view.set(next);
    if (next === 'favorites') this.loadFavorites();
    if (next === 'inbox') this.loadConversations();
    if (next === 'studio') this.loadListings();
    if (next === 'admin') { this.adminSection.set('overview'); this.loadAdmin(); }
    if (next === 'profile') this.loadOwnProfile();
  }

  submitAuth(): void {
    this.submitting.set(true);
    const request = this.registerMode()
      ? this.api.register(this.credentials)
      : this.api.login({ email: this.credentials.email, password: this.credentials.password });
    request.subscribe({
      next: result => {
        this.completeSignIn(result);
        this.submitting.set(false);
        this.credentials = { email: '', password: '', fullName: '', phone: '' };
      },
      error: error => { this.submitting.set(false); this.handleError(error); }
    });
  }

  openAuth(register = false): void {
    this.registerMode.set(register);
    this.authStep.set('credentials');
    this.authOpen.set(true);
  }

  startOAuth(provider: 'google' | 'facebook' | 'microsoft'): void {
    if (!this.oauthProviders()[provider]) {
      this.notice(`${provider[0].toUpperCase() + provider.slice(1)} sign-in has not been configured yet.`);
      return;
    }
    window.location.assign(this.api.oauthStartUrl(provider));
  }

  requestRecovery(): void {
    if (!this.recovery.identifier.trim()) { this.notice('Enter your registered email or phone number.'); return; }
    this.submitting.set(true);
    this.api.requestRecovery(this.recovery.identifier.trim()).subscribe({
      next: response => {
        this.submitting.set(false);
        this.recoveryDevelopmentCode.set(response.developmentCode ?? '');
        if (response.developmentCode) this.recovery.code = response.developmentCode;
        this.authStep.set('recovery-complete');
        this.notice(response.message);
      },
      error: error => { this.submitting.set(false); this.handleError(error); }
    });
  }

  completeRecovery(): void {
    if (!/^\d{6}$/.test(this.recovery.code) || this.recovery.newPassword.length < 8) {
      this.notice('Enter the six-digit code and a password of at least 8 characters.'); return;
    }
    this.submitting.set(true);
    this.api.completeRecovery({
      identifier: this.recovery.identifier.trim(), code: this.recovery.code,
      newPassword: this.recovery.newPassword
    }).subscribe({
      next: () => {
        this.submitting.set(false);
        this.credentials.email = this.recovery.identifier.includes('@') ? this.recovery.identifier : '';
        this.credentials.password = '';
        this.recovery = { identifier: '', code: '', newPassword: '' };
        this.recoveryDevelopmentCode.set('');
        this.authStep.set('credentials');
        this.registerMode.set(false);
        this.notice('Password updated. You can now log in.');
      },
      error: error => { this.submitting.set(false); this.handleError(error); }
    });
  }

  restoreSession(): void {
    this.api.me().subscribe({
      next: profile => {
        this.user.set({ ...profile, userId: profile.id });
        this.loadFavorites();
        this.loadConversations();
        this.connectRealtimeMessaging();
      },
      error: () => this.logout()
    });
  }

  logout(): void {
    this.disconnectRealtimeMessaging();
    if (this.view() === 'seller-profile' || this.view() === 'listing-detail') {
      window.history.pushState({}, '', this.marketStateUrl());
    }
    this.api.token = null;
    this.user.set(null);
    this.favorites.set([]);
    this.conversations.set([]);
    this.activeConversation.set(null);
    this.sellerContact.set(null);
    this.view.set('market');
    this.loadParts();
  }

  toggleTheme(): void {
    this.theme.toggle();
  }

  toggleFavorite(part: Part, event?: Event): void {
    event?.stopPropagation();
    if (!this.user()) { this.authOpen.set(true); return; }
    const favored = this.favoriteIds().has(part.id) || part.favorited;
    const action = favored ? this.api.unfavorite(part.id) : this.api.favorite(part.id);
    action.subscribe({
      next: () => { this.loadFavorites(); this.loadParts(); },
      error: error => this.handleError(error)
    });
  }

  becomeSeller(): void {
    if (!this.profileDraft.phone.trim() || !this.profileDraft.location.trim() || !this.profileDraft.businessName.trim()) {
      this.notice('Add your phone number, location, and business name before applying.');
      this.openProfile();
      return;
    }
    this.submitting.set(true);
    this.api.updateProfile(this.profileDraft).pipe(switchMap(() => this.api.becomeSeller())).subscribe({
      next: profile => {
        this.submitting.set(false);
        this.user.update(current => current ? { ...current, ...profile, userId: profile.id } : current);
        this.notice('Your seller application was submitted for administrator review.');
        this.setView('profile');
      },
      error: error => { this.submitting.set(false); this.handleError(error); }
    });
  }

  openProfile(): void {
    if (!this.user()) { this.authOpen.set(true); return; }
    this.setView('profile');
  }

  openPart(part: Part): void {
    this.selected.set(part);
    this.selectedImageUrl.set(this.partGallery(part)[0] ?? this.imageUrl(part));
    if (this.sellerContact()?.sellerId !== part.sellerId) this.sellerContact.set(null);
    this.view.set('listing-detail');
    this.listingLoading.set(true);
    window.history.pushState({}, '', `/parts/${part.id}`);
    this.scrollToPageTop();
    this.api.part(part.id).subscribe({
      next: details => {
        this.selected.set(details);
        this.selectedImageUrl.set(this.partGallery(details)[0] ?? this.imageUrl(details));
        this.listingLoading.set(false);
        this.scrollToPageTop();
      },
      error: error => {
        this.listingLoading.set(false);
        this.selected.set(null);
        this.view.set('market');
        window.history.replaceState({}, '', this.marketStateUrl());
        this.handleError(error);
      }
    });
  }

  openListingFromConversation(partId: number): void {
    window.history.pushState({}, '', `/parts/${partId}`);
    this.loadListingFromLocation();
  }

  openSellerProfile(part: Part, event?: Event): void {
    event?.stopPropagation();
    this.selected.set(null);
    this.loadSellerProfile(part.sellerId, true);
  }

  closeListing(): void {
    this.selected.set(null);
    this.sellerContact.set(null);
    this.view.set('market');
    window.history.pushState({}, '', this.marketStateUrl());
    this.scrollToPageTop();
  }

  partGallery(part: Part): string[] {
    const urls = part.images?.length ? part.images.map(image => image.url) : part.imageUrls ?? [];
    return [...new Set(urls)].map(url => this.assetUrl(url));
  }

  selectListingImage(url: string): void { this.selectedImageUrl.set(url); }

  async shareListing(part: Part): Promise<void> {
    const url = `${window.location.origin}/parts/${part.id}`;
    try {
      if (navigator.share) await navigator.share({ title: part.title, text: `View ${part.title} on E-biessa`, url });
      else {
        await navigator.clipboard.writeText(url);
        this.notice('Listing link copied.');
      }
    } catch (error) {
      if ((error as DOMException)?.name !== 'AbortError') this.notice('Could not share the listing. Copy the page address instead.');
    }
  }

  revealSellerContact(part: Part): void {
    if (part.status === 'SOLD') {
      this.notice('This part has been sold and is no longer available.');
      return;
    }
    if (!this.user()) {
      this.registerMode.set(false);
      this.authOpen.set(true);
      return;
    }
    if (this.user()?.role !== 'BUYER') {
      this.notice('Seller contact details are available to buyer accounts.');
      return;
    }
    this.contactLoading.set(true);
    this.api.sellerContact(part.sellerId).subscribe({
      next: contact => { this.sellerContact.set(contact); this.contactLoading.set(false); },
      error: error => { this.contactLoading.set(false); this.handleError(error); }
    });
  }

  openReport(part: Part): void {
    if (!this.user()) { this.openAuth(false); return; }
    if (this.user()?.role !== 'BUYER') { this.notice('Only buyer accounts can report marketplace listings.'); return; }
    this.reportingPart.set(part);
    this.reportDraft = { reason: 'WRONG_INFORMATION', details: '' };
    this.reportOpen.set(true);
  }

  submitReport(): void {
    const part = this.reportingPart();
    if (!part) return;
    this.submitting.set(true);
    this.api.reportListing(part.id, this.reportDraft.reason, this.reportDraft.details.trim()).subscribe({
      next: () => {
        this.submitting.set(false);
        this.reportOpen.set(false);
        this.reportingPart.set(null);
        this.notice('Report submitted. An administrator will review it.');
      },
      error: error => { this.submitting.set(false); this.handleError(error); }
    });
  }

  openSellerReport(seller: SellerProfile): void {
    if (!this.user()) { this.openAuth(false); return; }
    if (this.user()?.role !== 'BUYER') { this.notice('Only buyer accounts can report sellers.'); return; }
    this.reportingSeller.set(seller);
    this.sellerReportDraft = { reason: 'FRAUD_OR_SCAM', details: '' };
    this.sellerReportOpen.set(true);
  }

  submitSellerReport(): void {
    const seller = this.reportingSeller();
    const details = this.sellerReportDraft.details.trim();
    if (!seller || details.length < 10) { this.notice('Describe what happened using at least 10 characters.'); return; }
    this.submitting.set(true);
    this.api.reportSeller(seller.id, this.sellerReportDraft.reason, details).subscribe({
      next: () => {
        this.submitting.set(false);
        this.sellerReportOpen.set(false);
        this.reportingSeller.set(null);
        this.notice('Seller report submitted privately for administrator review.');
      },
      error: error => { this.submitting.set(false); this.handleError(error); }
    });
  }

  phoneUrl(phone: string): string { return `tel:${phone.replace(/[^+\d]/g, '')}`; }
  whatsappUrl(phone: string, part: Part): string {
    const digits = phone.replace(/\D/g, '');
    const message = `Hello, I found your “${part.title}” listing on E-biessa. Is it still available?`;
    return `https://wa.me/${digits}?text=${encodeURIComponent(message)}`;
  }
  emailUrl(email: string, part: Part): string {
    const subject = `E-biessa enquiry: ${part.title}`;
    const body = `Hello, I found your “${part.title}” listing on E-biessa. Is it still available?`;
    return `mailto:${email}?subject=${encodeURIComponent(subject)}&body=${encodeURIComponent(body)}`;
  }
  websiteUrl(website: string): string {
    const value = website.trim();
    return /^https?:\/\//i.test(value) ? value : `https://${value}`;
  }
  sellerInitials(): string {
    const seller = this.publicSeller();
    return seller?.fullName.split(' ').map(word => word[0]).slice(0, 2).join('').toUpperCase() ?? '';
  }

  saveProfile(): void {
    if (!this.profileDraft.fullName.trim()) { this.notice('Full name is required.'); return; }
    this.submitting.set(true);
    const payload = this.normalizedProfile();
    const adminTarget = this.view() === 'admin-profile' ? this.selectedAdminUser() : null;
    const request = adminTarget ? this.api.adminUpdateProfile(adminTarget.id, payload) : this.api.updateProfile(payload);
    request.subscribe({
      next: profile => {
        if (adminTarget) {
          this.selectedAdminUser.set({ ...profile, userId: profile.id });
          this.adminUsers.update(users => users.map(user => user.id === profile.id ? { ...profile, userId: profile.id } : user));
        } else {
          this.user.set({ ...profile, userId: profile.id });
        }
        this.submitting.set(false);
        this.notice('Profile updated.');
      },
      error: error => { this.submitting.set(false); this.handleError(error); }
    });
  }

  openAdminProfile(person: User): void {
    this.selectedAdminUser.set(person);
    this.fillProfileDraft(person);
    this.suspensionReasonDraft = person.suspensionReason ?? '';
    this.view.set('admin-profile');
    this.api.adminUser(person.id).subscribe({
      next: profile => { this.selectedAdminUser.set({ ...profile, userId: profile.id }); this.fillProfileDraft(profile); this.suspensionReasonDraft = profile.suspensionReason ?? ''; },
      error: error => this.handleError(error)
    });
  }

  profileSubject(): User | null { return this.view() === 'admin-profile' ? this.selectedAdminUser() : this.user(); }
  profileInitials(): string {
    return this.profileSubject()?.fullName.split(' ').map(word => word[0]).slice(0, 2).join('').toUpperCase() ?? '';
  }
  profileCompletion(): number {
    const profile = this.profileSubject();
    if (!profile) return 0;
    const values = [profile.fullName, profile.email, profile.phone, profile.location, profile.bio,
      profile.role === 'SELLER' ? profile.businessName : 'not-required'];
    return Math.round(values.filter(Boolean).length / values.length * 100);
  }

  resetFilters(): void {
    this.filters = { search: '', categoryId: '', brand: '', model: '', year: '', condition: '', minPrice: '', maxPrice: '', manufacturer: '', partBrand: '', partPosition: '', engine: '', fuelType: '', warranty: '', deliveryAvailable: '', collectionAvailable: '', negotiable: '' };
    this.sort = 'createdAt,desc';
    this.catalogModels.set([]);
    this.currentPage.set(0);
    this.loadCatalogMakes();
    if (this.searchMode() === 'ai') {
      this.aiQuery = '';
      this.aiSearchResponse.set(null);
      this.aiMatches.set({});
      this.aiError.set('');
      this.parts.set([]);
      this.total.set(0);
      this.loading.set(false);
      this.syncBrowseUrl();
    } else this.loadParts();
  }

  yearChanged(): void {
    this.filters.model = '';
    this.catalogModels.set([]);
    this.loadCatalogMakes();
    this.applyFilters();
  }

  brandChanged(): void {
    this.filters.model = '';
    this.catalogModels.set([]);
    this.loadCatalogModels();
    this.applyFilters();
  }

  modelChanged(): void { this.applyFilters(); }

  advancedFilterCount(): number {
    const keys: Array<keyof typeof this.filters> = [
      'manufacturer', 'partBrand', 'partPosition', 'engine', 'fuelType', 'warranty',
      'deliveryAvailable', 'collectionAvailable', 'negotiable', 'condition', 'minPrice', 'maxPrice'
    ];
    return keys.filter(key => Boolean(this.filters[key])).length;
  }

  private loadCatalogMakes(): void {
    this.api.vehicleCatalogMakes(this.filters.year).subscribe({
      next: makes => {
        this.catalogMakes.set(this.withSelectedMake(makes));
        if (this.filters.brand) this.loadCatalogModels();
      },
      error: () => this.notice('Vehicle makes could not be refreshed. Existing filters remain available.')
    });
  }

  private loadCatalogModels(): void {
    if (!this.filters.brand) return;
    const makeId = this.catalogMakes().find(make => make.name === this.filters.brand)?.id ?? this.catalogSlug(this.filters.brand);
    this.api.vehicleCatalogModels(makeId, this.filters.year).subscribe({
      next: models => this.catalogModels.set(this.filters.model && !models.includes(this.filters.model)
        ? [this.filters.model, ...models]
        : models),
      error: () => this.notice('Vehicle models could not be refreshed. You can still use the current filter.')
    });
  }

  private withSelectedMake(makes: VehicleCatalogMake[]): VehicleCatalogMake[] {
    if (!this.filters.brand || makes.some(make => make.name === this.filters.brand)) return makes;
    return [{ id: this.catalogSlug(this.filters.brand), name: this.filters.brand }, ...makes];
  }

  private catalogSlug(value: string): string {
    return value.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '');
  }

  openEditor(part?: Part): void {
    this.editingId.set(part?.id ?? null);
    this.editingPart.set(part ?? null);
    this.draft = part ? {
      title: part.title, description: part.description ?? '', price: part.price,
      condition: part.condition ?? 'Used - good', categoryId: part.categoryId ?? null,
      vehicleIds: part.compatibleVehicles?.map(vehicle => vehicle.id) ?? (this.vehicleFor(part)?.id ? [this.vehicleFor(part)!.id] : []),
      manufacturer: part.manufacturer ?? '', partBrand: part.partBrand ?? '', oemNumber: part.oemNumber ?? '',
      alternativeOemNumbers: part.alternativeOemNumbers ?? [], partPosition: part.partPosition ?? '',
      engineCompatibility: part.engineCompatibility ?? '', fuelCompatibility: part.fuelCompatibility ?? '',
      warrantyInformation: part.warrantyInformation ?? '', deliveryAvailable: part.deliveryAvailable ?? false,
      collectionAvailable: part.collectionAvailable ?? true, negotiable: part.negotiable ?? false, location: part.location ?? ''
    } : this.emptyDraft();
    this.alternativeOemText = this.draft.alternativeOemNumbers.join('\n');
    this.listingPreview.set(false);
    this.priceEstimate.set(null);
    this.uploadFiles = [];
    this.filePreviews.set([]);
    this.editorOpen.set(true);
  }

  estimateListingPrice(): void {
    if (!this.draft.title.trim() && !this.draft.categoryId && !this.draft.vehicleIds.length && !this.draft.oemNumber.trim()) {
      this.notice('Add a title, category, vehicle, or OEM number before estimating a price.');
      return;
    }
    this.priceEstimating.set(true);
    this.priceEstimate.set(null);
    this.api.estimatePrice({ ...this.draft, alternativeOemNumbers: this.parseAlternativeOems() }, this.editingId() ?? undefined).subscribe({
      next: estimate => {
        this.priceEstimate.set(estimate);
        this.priceEstimating.set(false);
      },
      error: error => {
        this.priceEstimating.set(false);
        this.handleError(error);
      }
    });
  }

  useSuggestedPrice(): void {
    const suggested = this.priceEstimate()?.suggestedPrice;
    if (suggested != null) this.draft.price = suggested;
  }

  async selectImages(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const files = input.files;
    if (!files?.length) { this.uploadFiles = []; this.filePreviews.set([]); return; }
    const existingCount = this.editingPart()?.images?.length ?? 0;
    const invalid = Array.from(files).find(file => !['image/jpeg', 'image/png', 'image/webp'].includes(file.type) || file.size > 5 * 1024 * 1024);
    if (invalid) {
      input.value = '';
      this.uploadFiles = [];
      this.filePreviews.set([]);
      this.notice('Photos must be JPEG, PNG, or WebP and no larger than 5 MB each.');
      return;
    }
    if (existingCount + files.length > 6) {
      input.value = '';
      this.notice(`A listing can have at most 6 photos. You can add ${6 - existingCount} more.`);
      return;
    }
    const originals = Array.from(files);
    const compressed = await Promise.all(originals.map(file => this.compressImage(file)));
    this.uploadFiles = compressed;
    const bytesSaved = originals.reduce((sum, file, index) => sum + Math.max(0, file.size - compressed[index].size), 0);
    const previews = await Promise.all(compressed.map(file => new Promise<{ name: string; url: string }>(resolve => {
      const reader = new FileReader();
      reader.onload = () => resolve({ name: file.name, url: String(reader.result) });
      reader.readAsDataURL(file);
    })));
    this.filePreviews.set(previews);
    if (bytesSaved > 50_000) this.notice(`Photos compressed by ${Math.round(bytesSaved / 1024)} KB before upload.`);
  }

  saveListing(): void {
    if (!this.draft.title.trim()) { this.notice('Add a clear listing title.'); return; }
    if (!this.draft.price || this.draft.price <= 0) { this.notice('Price must be greater than zero.'); return; }
    this.submitting.set(true);
    const payload = { ...this.draft, alternativeOemNumbers: this.parseAlternativeOems() };
    const id = this.editingId();
    const action = id ? this.api.updatePart(id, payload) : this.api.createPart(payload);
    action.subscribe({
      next: part => {
        if (this.uploadFiles?.length) {
          this.api.uploadImages(part.id, this.uploadFiles).subscribe({
            next: () => this.finishSave(), error: error => { this.submitting.set(false); this.handleError(error); }
          });
        } else this.finishSave();
      },
      error: error => { this.submitting.set(false); this.handleError(error); }
    });
  }

  makePrimary(image: ImageAsset): void {
    const part = this.editingPart();
    if (!part) return;
    this.api.makePrimaryImage(part.id, image.id).subscribe({
      next: images => { this.applyImages(images); this.notice('Primary photo updated.'); },
      error: error => this.handleError(error)
    });
  }

  startImageDrag(image: ImageAsset): void { this.draggedImageId.set(image.id); }

  dropImage(target: ImageAsset, event: DragEvent): void {
    event.preventDefault();
    const part = this.editingPart();
    const sourceId = this.draggedImageId();
    this.draggedImageId.set(null);
    if (!part || sourceId == null || sourceId === target.id) return;
    const images = [...(part.images ?? [])].sort((a, b) => a.sortOrder - b.sortOrder);
    const from = images.findIndex(image => image.id === sourceId);
    const to = images.findIndex(image => image.id === target.id);
    if (from < 0 || to < 0) return;
    const [moved] = images.splice(from, 1);
    images.splice(to, 0, moved);
    this.api.reorderImages(part.id, images.map(image => image.id)).subscribe({
      next: ordered => { this.applyImages(ordered); this.notice('Photo order updated.'); },
      error: error => this.handleError(error)
    });
  }

  deleteListingImage(image: ImageAsset): void {
    const part = this.editingPart();
    if (!part || !confirm('Delete this photo?')) return;
    this.api.deleteImage(part.id, image.id).subscribe({
      next: () => {
        const remaining = (part.images ?? []).filter(item => item.id !== image.id);
        if (image.primary && remaining.length) remaining[0] = { ...remaining[0], primary: true };
        this.applyImages(remaining);
        this.notice('Photo deleted.');
      },
      error: error => this.handleError(error)
    });
  }

  updateStatus(part: Part, status: PartStatus): void {
    if (status === 'SOLD') { this.markSold(part); return; }
    this.api.updatePartStatus(part.id, status).subscribe({
      next: () => { this.loadListings(); this.loadParts(); }, error: e => this.handleError(e)
    });
  }

  markSold(part: Part): void {
    if (part.status === 'SOLD') return;
    if (!confirm(`Mark “${part.title}” as sold? It will disappear from the marketplace immediately.`)) return;
    this.api.updatePartStatus(part.id, 'SOLD').subscribe({
      next: updated => {
        this.listings.update(parts => parts.map(item => item.id === updated.id ? updated : item));
        this.loadParts();
        this.notice('Part marked as sold and removed from the marketplace.');
      },
      error: e => this.handleError(e)
    });
  }

  removePart(part: Part): void {
    if (!confirm(`Delete “${part.title}”? This cannot be undone.`)) return;
    this.api.deletePart(part.id).subscribe({ next: () => this.loadListings(), error: e => this.handleError(e) });
  }

  changeRole(user: User, role: UserRole): void {
    this.api.updateRole(user.id, role).subscribe({
      next: updated => {
        this.adminUsers.update(users => users.map(person => person.id === updated.id ? { ...updated, userId: updated.id } : person));
        if (this.selectedAdminUser()?.id === updated.id) this.selectedAdminUser.set({ ...updated, userId: updated.id });
        this.notice('User role updated.');
      },
      error: e => this.handleError(e)
    });
  }

  decideSellerApplication(person: User, approved: boolean): void {
    const reason = (this.sellerDecisionNotes[person.id] || '').trim();
    if (!approved && !reason) { this.notice('Enter a reason before rejecting the application.'); return; }
    if (!confirm(`${approved ? 'Approve' : 'Reject'} the seller application from ${person.fullName}?`)) return;
    this.api.decideSellerApplication(person.id, approved, reason).subscribe({
      next: updated => {
        this.sellerApplications.update(applications => applications.filter(application => application.id !== person.id));
        this.adminUsers.update(users => users.map(user => user.id === updated.id ? { ...updated, userId: updated.id } : user));
        delete this.sellerDecisionNotes[person.id];
        this.loadAdminAuditLogs();
        this.notice(approved ? 'Seller application approved.' : 'Seller application rejected.');
      },
      error: error => this.handleError(error)
    });
  }

  updateSuspension(person: User, suspended: boolean): void {
    const reason = suspended ? this.suspensionReasonDraft.trim() : '';
    if (suspended && !reason) { this.notice('Enter a clear suspension reason.'); return; }
    const action = suspended ? 'suspend this account and hide its active listings' : 'restore account access';
    if (!confirm(`Are you sure you want to ${action}?`)) return;
    this.api.updateSuspension(person.id, suspended, reason).subscribe({
      next: updated => {
        const normalized = { ...updated, userId: updated.id };
        this.selectedAdminUser.set(normalized);
        this.adminUsers.update(users => users.map(user => user.id === updated.id ? normalized : user));
        this.suspensionReasonDraft = updated.suspensionReason ?? '';
        this.notice(suspended ? 'Account suspended and active listings hidden.' : 'Account access restored.');
      },
      error: error => this.handleError(error)
    });
  }

  startConversation(part: Part): void {
    if (!this.user()) { this.openAuth(false); return; }
    if (this.user()?.role !== 'BUYER') { this.notice('Only buyer accounts can start a conversation.'); return; }
    const body = this.listingMessageDraft.trim();
    if (!body) { this.notice('Write a message for the seller.'); return; }
    this.submitting.set(true);
    this.api.startConversation(part.id, body).subscribe({
      next: conversation => {
        this.submitting.set(false); this.listingMessageDraft = ''; this.activeConversation.set(this.withChronologicalMessages(conversation));
        this.view.set('inbox'); this.scrollConversationToLatest(); this.loadConversations(); this.notice('Message sent to the seller.');
      },
      error: error => { this.submitting.set(false); this.handleError(error); }
    });
  }

  openConversation(conversation: Conversation): void {
    this.clearReplyImages();
    this.replyDraft = '';
    this.api.conversation(conversation.id).subscribe({
      next: detail => {
        this.activeConversation.set(this.withChronologicalMessages(detail));
        this.scrollConversationToLatest();
        this.loadConversations();
      },
      error: error => this.handleError(error)
    });
  }

  sendConversationMessage(): void {
    const active = this.activeConversation();
    const body = this.replyDraft.trim();
    if (!active || (!body && !this.replyImages.length)) return;
    this.submitting.set(true);
    const action = this.replyImages.length
      ? this.api.sendMessageWithImages(active.conversation.id, body, this.replyImages)
      : this.api.sendMessage(active.conversation.id, body);
    action.subscribe({
      next: sent => {
        this.replyDraft = ''; this.clearReplyImages(); this.submitting.set(false);
        this.appendRealtimeMessage(active.conversation.id, sent, true);
        this.loadConversations();
      },
      error: error => { this.submitting.set(false); this.handleError(error); }
    });
  }

  async selectReplyImages(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const selected = Array.from(input.files ?? []);
    if (!selected.length) return;
    if (selected.length > 4) {
      this.notice('A message can contain at most 4 photos.');
      input.value = '';
      return;
    }
    const invalid = selected.find(file => !['image/jpeg', 'image/png', 'image/webp'].includes(file.type) || file.size > 5 * 1024 * 1024);
    if (invalid) {
      this.notice('Message photos must be JPEG, PNG, or WebP and no larger than 5 MB each.');
      input.value = '';
      return;
    }
    const compressed = await Promise.all(selected.map(file => this.compressImage(file)));
    this.replyImages = compressed;
    this.replyImagePreviews.set(await Promise.all(compressed.map(file => new Promise<{ name: string; url: string }>(resolve => {
      const reader = new FileReader();
      reader.onload = () => resolve({ name: file.name, url: String(reader.result) });
      reader.readAsDataURL(file);
    }))));
  }

  removeReplyImage(index: number): void {
    this.replyImages = this.replyImages.filter((_, itemIndex) => itemIndex !== index);
    this.replyImagePreviews.update(previews => previews.filter((_, itemIndex) => itemIndex !== index));
  }

  private clearReplyImages(): void {
    this.replyImages = [];
    this.replyImagePreviews.set([]);
  }

  auditActionLabel(action: string): string { return action.toLowerCase().replaceAll('_', ' ').replace(/^./, letter => letter.toUpperCase()); }

  setAdminSection(section: AdminSection): void { this.adminSection.set(section); }

  moderate(part: Part, status: PartStatus): void {
    this.api.moderatePart(part.id, status).subscribe({ next: () => this.loadAdmin(), error: e => this.handleError(e) });
  }

  changeReportFilter(): void { this.loadAdminReports(); }

  changeSellerReportFilter(): void { this.loadAdminSellerReports(); }

  resolveAdminReport(report: ListingReport, status: 'DISMISSED' | 'ACTIONED'): void {
    const verb = status === 'ACTIONED' ? 'hide the listing and close' : 'dismiss';
    if (!confirm(`Are you sure you want to ${verb} this report?`)) return;
    const note = status === 'ACTIONED' ? 'Listing hidden by administrator after report review.' : 'Report reviewed and dismissed.';
    this.api.resolveReport(report.id, status, note).subscribe({
      next: () => { this.loadAdmin(); this.notice(status === 'ACTIONED' ? 'Listing hidden and report actioned.' : 'Report dismissed.'); },
      error: error => this.handleError(error)
    });
  }

  resolveAdminSellerReport(report: SellerReport, status: 'DISMISSED' | 'ACTIONED'): void {
    const note = (this.sellerReportAdminNotes[report.id] || '').trim();
    if (status === 'ACTIONED' && !note) { this.notice('Add an administrator note before suspending the seller.'); return; }
    const action = status === 'ACTIONED' ? 'suspend this seller and hide all active listings' : 'dismiss this seller report';
    if (!confirm(`Are you sure you want to ${action}?`)) return;
    this.api.resolveSellerReport(report.id, status, note).subscribe({
      next: () => {
        delete this.sellerReportAdminNotes[report.id];
        this.loadAdmin();
        this.notice(status === 'ACTIONED' ? 'Seller suspended and active listings hidden.' : 'Seller report dismissed.');
      },
      error: error => this.handleError(error)
    });
  }

  reportReasonLabel(reason: ReportReason): string {
    return reason.toLowerCase().replaceAll('_', ' ').replace(/^./, letter => letter.toUpperCase());
  }
  sellerReportReasonLabel(reason: SellerReportReason): string {
    return reason.toLowerCase().replaceAll('_', ' ').replace(/^./, letter => letter.toUpperCase());
  }

  imageUrl(part: Part): string {
    const url = part.imageUrls?.[0];
    if (!url) return 'https://images.unsplash.com/photo-1486262715619-67b85e0b08d3?auto=format&fit=crop&w=900&q=75';
    return url.startsWith('http') ? url : `http://localhost:8080${url}`;
  }

  assetUrl(url: string): string { return url.startsWith('http') || url.startsWith('data:') ? url : `http://localhost:8080${url}`; }

  money(value: number): string { return new Intl.NumberFormat('fr-TN').format(value) + ' TND'; }
  initials(): string { return this.user()?.fullName.split(' ').map(word => word[0]).slice(0, 2).join('').toUpperCase() ?? ''; }
  vehicleFor(part: Part): Vehicle | undefined { return this.vehicles().find(v => v.brand === part.brand && v.model === part.model && v.year === part.year); }
  isVehicleSelected(vehicleId: number): boolean { return this.draft.vehicleIds.includes(vehicleId); }
  toggleVehicleCompatibility(vehicleId: number, checked: boolean): void {
    this.draft.vehicleIds = checked
      ? [...new Set([...this.draft.vehicleIds, vehicleId])]
      : this.draft.vehicleIds.filter(id => id !== vehicleId);
  }
  selectedDraftVehicles(): Vehicle[] { return this.vehicles().filter(vehicle => this.draft.vehicleIds.includes(vehicle.id)); }
  positionLabel(value?: string): string { return value ? value.toLowerCase().replaceAll('_', ' ').replace(/^./, letter => letter.toUpperCase()) : 'Not specified'; }
  deliverySummary(part: Part | PartPayload): string {
    const options = [part.deliveryAvailable ? 'Delivery' : '', part.collectionAvailable ? 'Collection' : ''].filter(Boolean);
    return options.length ? options.join(' or ') : 'Contact seller';
  }
  trackPart(_: number, part: Part): number { return part.id; }

  private loadFavorites(): void {
    if (!this.user()) return;
    this.api.favorites().subscribe({ next: data => this.favorites.set(data), error: e => this.handleError(e) });
  }
  private loadOwnProfile(): void {
    this.api.me().subscribe({
      next: profile => {
        const current = { ...profile, userId: profile.id };
        this.user.set(current);
        this.fillProfileDraft(current);
        this.loadFavorites();
        if (current.role === 'SELLER' || current.role === 'ADMIN') this.loadListings();
      },
      error: error => this.handleError(error)
    });
  }
  private loadListings(): void {
    this.api.myListings().subscribe({ next: data => this.listings.set(data), error: e => this.handleError(e) });
  }
  loadAdmin(): void {
    if (this.user()?.role !== 'ADMIN') { this.view.set('market'); return; }
    forkJoin({ users: this.api.adminUsers(), applications: this.api.sellerApplications(), parts: this.api.adminParts(), reports: this.api.adminReports(this.adminReportFilter || undefined), sellerReports: this.api.adminSellerReports(this.adminSellerReportFilter || undefined), auditLogs: this.api.adminAuditLogs() }).subscribe({
      next: ({ users, applications, parts, reports, sellerReports, auditLogs }) => {
        this.adminUsers.set(users); this.sellerApplications.set(applications); this.adminParts.set(parts.content); this.adminReports.set(reports); this.adminSellerReports.set(sellerReports); this.adminAuditLogs.set(auditLogs);
      },
      error: e => this.handleError(e)
    });
  }
  private loadAdminAuditLogs(): void {
    this.api.adminAuditLogs().subscribe({ next: logs => this.adminAuditLogs.set(logs), error: error => this.handleError(error) });
  }
  private loadAdminSellerReports(): void {
    this.api.adminSellerReports(this.adminSellerReportFilter || undefined).subscribe({
      next: reports => this.adminSellerReports.set(reports), error: error => this.handleError(error)
    });
  }
  private loadConversations(): void {
    if (!this.user()) return;
    this.inboxLoading.set(true);
    this.api.conversations().subscribe({
      next: conversations => {
        const ordered = [...conversations].sort((a, b) =>
          new Date(b.lastMessageAt || 0).getTime() - new Date(a.lastMessageAt || 0).getTime()
        );
        this.conversations.set(ordered);
        this.inboxLoading.set(false);
        if (this.view() === 'inbox' && !this.activeConversation() && ordered.length) this.openConversation(ordered[0]);
      },
      error: error => { this.inboxLoading.set(false); this.handleError(error); }
    });
  }

  private connectRealtimeMessaging(): void {
    if (!this.user() || !this.api.token) return;
    this.disconnectRealtimeMessaging(false);
    const generation = ++this.socketGeneration;
    this.api.realtimeTicket().subscribe({
      next: response => {
        if (generation !== this.socketGeneration || !this.user()) return;
        const socket = new WebSocket(this.api.realtimeSocketUrl(response.ticket));
        this.messageSocket = socket;
        socket.onopen = () => {
          if (generation === this.socketGeneration) this.realtimeConnected.set(true);
        };
        socket.onmessage = event => {
          if (generation !== this.socketGeneration) return;
          this.receiveRealtimeMessage(event.data);
        };
        socket.onerror = () => socket.close();
        socket.onclose = () => {
          if (generation !== this.socketGeneration) return;
          this.realtimeConnected.set(false);
          this.scheduleRealtimeReconnect(generation);
        };
      },
      error: () => {
        if (generation === this.socketGeneration) this.scheduleRealtimeReconnect(generation);
      }
    });
  }

  private receiveRealtimeMessage(raw: string): void {
    try {
      const event = JSON.parse(raw) as { type: string; conversationId: number; message: MarketplaceMessage };
      if (event.type !== 'MESSAGE_CREATED' || !event.message?.id) return;
      const isReceived = event.message.senderId !== this.user()?.id;
      const isOpenThread = this.activeConversation()?.conversation.id === event.conversationId;
      const wasNearLatest = isOpenThread && this.isConversationNearLatest();
      const previousScrollTop = this.messageStream?.nativeElement.scrollTop ?? 0;
      this.appendRealtimeMessage(event.conversationId, event.message);
      if (isReceived && isOpenThread) {
        this.api.conversation(event.conversationId).subscribe({
          next: detail => {
            this.activeConversation.set(this.withChronologicalMessages(detail));
            if (wasNearLatest) this.scrollConversationToLatest(true);
            else this.restoreConversationScroll(previousScrollTop);
            this.loadConversations();
          }
        });
      } else {
        this.loadConversations();
      }
    } catch {
      // Ignore malformed socket frames; persisted HTTP messaging remains authoritative.
    }
  }

  private appendRealtimeMessage(conversationId: number, message: MarketplaceMessage, forceScroll = false): void {
    const shouldScroll = forceScroll || this.isConversationNearLatest();
    let appended = false;
    this.activeConversation.update(detail => {
      if (!detail || detail.conversation.id !== conversationId || detail.messages.some(item => item.id === message.id)) return detail;
      appended = true;
      return this.withChronologicalMessages({ ...detail, messages: [...detail.messages, message] });
    });
    if (appended && shouldScroll) this.scrollConversationToLatest(true);
  }

  private withChronologicalMessages(detail: ConversationDetail): ConversationDetail {
    return {
      ...detail,
      messages: [...detail.messages].sort((a, b) =>
        new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime()
      )
    };
  }

  private scrollConversationToLatest(smooth = false): void {
    window.setTimeout(() => {
      const stream = this.messageStream?.nativeElement;
      if (!stream) return;
      if (typeof stream.scrollTo === 'function') {
        stream.scrollTo({ top: stream.scrollHeight, behavior: smooth ? 'smooth' : 'auto' });
      } else {
        stream.scrollTop = stream.scrollHeight;
      }
    });
  }

  private isConversationNearLatest(): boolean {
    const stream = this.messageStream?.nativeElement;
    if (!stream) return true;
    return stream.scrollHeight - stream.scrollTop - stream.clientHeight < 96;
  }

  private restoreConversationScroll(scrollTop: number): void {
    window.setTimeout(() => {
      const stream = this.messageStream?.nativeElement;
      if (stream) stream.scrollTop = scrollTop;
    });
  }

  private scheduleRealtimeReconnect(generation: number): void {
    if (!this.user() || generation !== this.socketGeneration) return;
    clearTimeout(this.socketReconnectTimer);
    this.socketReconnectTimer = setTimeout(() => {
      if (generation === this.socketGeneration && this.user()) this.connectRealtimeMessaging();
    }, 3000);
  }

  private disconnectRealtimeMessaging(invalidate = true): void {
    if (invalidate) this.socketGeneration++;
    clearTimeout(this.socketReconnectTimer);
    this.socketReconnectTimer = undefined;
    const socket = this.messageSocket;
    this.messageSocket = undefined;
    if (socket && socket.readyState < WebSocket.CLOSING) socket.close(1000, 'Session ended');
    this.realtimeConnected.set(false);
  }
  private loadAdminReports(): void {
    this.api.adminReports(this.adminReportFilter || undefined).subscribe({
      next: reports => this.adminReports.set(reports), error: error => this.handleError(error)
    });
  }
  private loadSellerProfile(sellerId: number, updateUrl: boolean): void {
    this.loading.set(true);
    this.view.set('seller-profile');
    this.publicSeller.set(null);
    this.publicSellerListings.set([]);
    this.sellerReviews.set(null);
    this.reviewDraft = { rating: 0, comment: '' };
    if (updateUrl) window.history.pushState({}, '', `/sellers/${sellerId}`);
    forkJoin({ profile: this.api.sellerProfile(sellerId), listings: this.api.sellerListings(sellerId), reviews: this.api.sellerReviews(sellerId) }).subscribe({
      next: ({ profile, listings, reviews }) => {
        this.publicSeller.set(profile);
        this.publicSellerListings.set(listings);
        this.sellerReviews.set(reviews);
        if (reviews.myReview) this.reviewDraft = { rating: reviews.myReview.rating, comment: reviews.myReview.comment || '' };
        this.loading.set(false);
      },
      error: error => {
        this.loading.set(false);
        this.view.set('market');
        window.history.replaceState({}, '', '/');
        this.handleError(error);
      }
    });
  }

  setReviewRating(rating: number): void {
    this.reviewDraft.rating = rating;
  }

  reviewStars(rating: number): string {
    const rounded = Math.max(0, Math.min(5, Math.round(rating)));
    return `${'★'.repeat(rounded)}${'☆'.repeat(5 - rounded)}`;
  }

  submitSellerReview(seller: SellerProfile): void {
    if (this.reviewDraft.rating < 1 || this.reviewDraft.rating > 5) {
      this.notice('Choose a rating from 1 to 5 stars.');
      return;
    }
    this.reviewSubmitting.set(true);
    this.api.saveSellerReview(seller.id, this.reviewDraft.rating, this.reviewDraft.comment).subscribe({
      next: reviews => {
        this.sellerReviews.set(reviews);
        this.publicSeller.update(profile => profile ? { ...profile, averageRating: reviews.averageRating, reviewCount: reviews.reviewCount } : profile);
        if (reviews.myReview) this.reviewDraft = { rating: reviews.myReview.rating, comment: reviews.myReview.comment || '' };
        this.reviewSubmitting.set(false);
        this.notice('Your seller review has been saved.');
      },
      error: error => {
        this.reviewSubmitting.set(false);
        this.handleError(error);
      }
    });
  }
  private handleOAuthCallback(): void {
    if (window.location.pathname !== '/auth/callback') return;
    const params = new URLSearchParams(window.location.search);
    const code = params.get('code');
    const error = params.get('error');
    window.history.replaceState({}, '', '/');
    if (error) { this.notice(error); return; }
    if (!code) return;
    this.submitting.set(true);
    this.api.exchangeOAuth(code).subscribe({
      next: result => { this.submitting.set(false); this.completeSignIn(result); },
      error: failure => { this.submitting.set(false); this.handleError(failure); }
    });
  }
  private completeSignIn(result: { token: string; userId: number; email: string; fullName: string; role: UserRole }): void {
    this.api.token = result.token;
    this.user.set({ ...result, id: result.userId });
    this.authOpen.set(false);
    this.notice(`Welcome, ${result.fullName}.`);
    this.loadFavorites();
    this.loadParts();
    this.restoreSession();
  }
  private loadSellerFromLocation(): boolean {
    const match = window.location.pathname.match(/^\/sellers\/(\d+)\/?$/);
    if (!match) return false;
    this.loadSellerProfile(Number(match[1]), false);
    return true;
  }
  private loadListingFromLocation(): boolean {
    const match = window.location.pathname.match(/^\/parts\/(\d+)\/?$/);
    if (!match) return false;
    this.view.set('listing-detail');
    this.listingLoading.set(true);
    this.scrollToPageTop();
    this.selected.set(null);
    this.sellerContact.set(null);
    this.api.part(Number(match[1])).subscribe({
      next: details => {
        this.selected.set(details);
        this.selectedImageUrl.set(this.partGallery(details)[0] ?? this.imageUrl(details));
        this.listingLoading.set(false);
        this.scrollToPageTop();
      },
      error: error => {
        this.listingLoading.set(false);
        this.view.set('market');
        window.history.replaceState({}, '', '/');
        this.handleError(error);
      }
    });
    return true;
  }
  private restoreBrowseStateFromUrl(): void {
    const params = new URLSearchParams(window.location.search);
    this.searchMode.set(params.get('mode') === 'ai' ? 'ai' : 'keyword');
    this.aiQuery = params.get('q') ?? '';
    const keys: Array<keyof typeof this.filters> = [
      'search', 'categoryId', 'brand', 'model', 'year', 'condition', 'minPrice', 'maxPrice',
      'manufacturer', 'partBrand', 'partPosition', 'engine', 'fuelType', 'warranty', 'deliveryAvailable', 'collectionAvailable', 'negotiable'
    ];
    keys.forEach(key => this.filters[key] = params.get(key) ?? '');
    const allowedSorts = new Set(['createdAt,desc', 'price,asc', 'price,desc', 'views,desc', 'title,asc']);
    const requestedSort = params.get('sort') ?? 'createdAt,desc';
    this.sort = allowedSorts.has(requestedSort) ? requestedSort : 'createdAt,desc';
    const requestedPage = Number(params.get('page') ?? 0);
    this.currentPage.set(Number.isInteger(requestedPage) && requestedPage >= 0 ? requestedPage : 0);
  }
  private syncBrowseUrl(): void {
    window.history.replaceState({}, '', this.marketStateUrl());
  }
  private marketStateUrl(): string {
    const params = new URLSearchParams();
    Object.entries(this.filters).forEach(([key, value]) => {
      if (value && (this.searchMode() !== 'ai' || key !== 'search')) params.set(key, value);
    });
    if (this.searchMode() === 'ai') {
      params.set('mode', 'ai');
      if (this.aiQuery.trim()) params.set('q', this.aiQuery.trim());
    } else {
      if (this.sort !== 'createdAt,desc') params.set('sort', this.sort);
      if (this.currentPage() > 0) params.set('page', String(this.currentPage()));
    }
    const query = params.toString();
    return query ? `/?${query}` : '/';
  }
  private semanticFilterParams(): Record<string, string> {
    const category = this.categories().find(item => String(item.id) === String(this.filters.categoryId))?.name ?? '';
    return {
      brand: this.filters.brand,
      model: this.filters.model,
      year: this.filters.year,
      condition: this.filters.condition,
      category,
      fuelType: this.filters.fuelType,
      partPosition: this.filters.partPosition,
      minPrice: this.filters.minPrice,
      maxPrice: this.filters.maxPrice
    };
  }
  private applyImages(images: ImageAsset[]): void {
    const ordered = [...images].sort((left, right) => left.sortOrder - right.sortOrder);
    this.editingPart.update(part => part ? { ...part, images: ordered, imageUrls: ordered.map(image => image.url) } : part);
    this.listings.update(parts => parts.map(part => part.id === this.editingPart()?.id ? { ...part, images: ordered, imageUrls: ordered.map(image => image.url) } : part));
  }
  private scrollToPageTop(): void {
    requestAnimationFrame(() => window.scrollTo({ top: 0, behavior: 'auto' }));
  }
  private async compressImage(file: File): Promise<File> {
    if (typeof createImageBitmap !== 'function') return file;
    const bitmap = await createImageBitmap(file);
    const maxDimension = 1920;
    const scale = Math.min(1, maxDimension / Math.max(bitmap.width, bitmap.height));
    if (scale === 1 && file.size < 900_000) { bitmap.close(); return file; }
    const canvas = document.createElement('canvas');
    canvas.width = Math.max(1, Math.round(bitmap.width * scale));
    canvas.height = Math.max(1, Math.round(bitmap.height * scale));
    canvas.getContext('2d')?.drawImage(bitmap, 0, 0, canvas.width, canvas.height);
    bitmap.close();
    const blob = await new Promise<Blob | null>(resolve => canvas.toBlob(resolve, 'image/webp', .82));
    if (!blob || blob.size >= file.size) return file;
    return new File([blob], file.name.replace(/\.[^.]+$/, '') + '.webp', {
      type: 'image/webp', lastModified: file.lastModified
    });
  }
  private finishSave(): void { this.submitting.set(false); this.listingPreview.set(false); this.editorOpen.set(false); this.notice('Listing saved.'); this.loadListings(); this.loadParts(); }
  private emptyDraft(): PartPayload {
    return { title: '', description: '', price: 0, condition: 'Used - good', categoryId: null, vehicleIds: [],
      manufacturer: '', partBrand: '', oemNumber: '', alternativeOemNumbers: [], partPosition: '',
      engineCompatibility: '', fuelCompatibility: '', warrantyInformation: '', deliveryAvailable: false,
      collectionAvailable: true, negotiable: false, location: '' };
  }
  parseAlternativeOems(): string[] {
    return [...new Set(this.alternativeOemText.split(/[\n,]/).map(value => value.trim()).filter(Boolean))];
  }
  private emptyProfile(): ProfilePayload {
    return { fullName: '', phone: '', location: '', businessName: '', website: '', bio: '' };
  }
  private fillProfileDraft(profile: User): void {
    this.profileDraft = {
      fullName: profile.fullName ?? '', phone: profile.phone ?? '', location: profile.location ?? '',
      businessName: profile.businessName ?? '', website: profile.website ?? '', bio: profile.bio ?? ''
    };
  }
  private normalizedProfile(): ProfilePayload {
    return {
      fullName: this.profileDraft.fullName.trim(), phone: this.profileDraft.phone.trim(),
      location: this.profileDraft.location.trim(), businessName: this.profileDraft.businessName.trim(),
      website: this.profileDraft.website.trim(), bio: this.profileDraft.bio.trim()
    };
  }
  private notice(text: string): void { this.message.set(text); setTimeout(() => this.message.set(''), 3500); }
  private handleError(error: any): void {
    const fieldErrors = error?.error?.errors ? Object.values(error.error.errors).join(' · ') : '';
    const fallback = error?.status === 0 ? 'Cannot reach the API. Check that Spring Boot is running on port 8080.'
      : error?.status === 401 ? 'Your session expired. Please log in again.'
      : error?.status === 403 ? 'Your account does not have permission for that action.'
      : 'Something went wrong. Please try again.';
    this.notice(fieldErrors || error?.error?.message || fallback);
  }
}
