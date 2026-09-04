import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { AdminAuditLog, AuthUser, Category, Conversation, ConversationDetail, ImageAsset, ListingReport, MarketplaceMessage, OAuthProviders, Page, Part, PartPayload, PartStatus, ProfilePayload, RecoveryStartResponse, ReportReason, ReportStatus, SellerContact, SellerProfile, SellerReport, SellerReportReason, SellerReviewSummary, SemanticSearchResponse, User, UserRole, Vehicle, VehicleCatalogMake } from './models';

interface AuthResponse extends AuthUser { token: string; }

@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly baseUrl = 'http://localhost:8080/api';

  constructor(private readonly http: HttpClient) {}

  get token(): string | null { return localStorage.getItem('e_bieca_token') || localStorage.getItem('partly_token'); }
  set token(value: string | null) {
    if (value) localStorage.setItem('e_bieca_token', value);
    else localStorage.removeItem('e_bieca_token');
    localStorage.removeItem('partly_token');
  }

  browse(filters: Record<string, string>): Observable<Page<Part>> {
    let params = new HttpParams().set('size', '12');
    Object.entries(filters).forEach(([key, value]) => { if (value) params = params.set(key, value); });
    return this.http.get<Page<Part>>(`${this.baseUrl}/parts`, { params });
  }
  semanticSearch(query: string, filters: Record<string, string>, minimumScore = 0.5): Observable<SemanticSearchResponse> {
    let params = new HttpParams()
      .set('query', query)
      .set('limit', '12')
      .set('minimumScore', String(minimumScore));
    Object.entries(filters).forEach(([key, value]) => { if (value) params = params.set(key, value); });
    return this.http.get<SemanticSearchResponse>(`${this.baseUrl}/ai/search`, { params });
  }
  part(id: number): Observable<Part> { return this.http.get<Part>(`${this.baseUrl}/parts/${id}`); }
  categories(): Observable<Category[]> { return this.http.get<Category[]>(`${this.baseUrl}/categories`); }
  vehicles(): Observable<Vehicle[]> { return this.http.get<Vehicle[]>(`${this.baseUrl}/vehicles`); }
  vehicleCatalogYears(): Observable<number[]> { return this.http.get<number[]>(`${this.baseUrl}/vehicles/catalog/years`); }
  vehicleCatalogMakes(year?: string): Observable<VehicleCatalogMake[]> {
    const options = year ? { params: new HttpParams().set('year', year) } : {};
    return this.http.get<VehicleCatalogMake[]>(`${this.baseUrl}/vehicles/catalog/makes`, options);
  }
  vehicleCatalogModels(makeId: string, year?: string): Observable<string[]> {
    let params = new HttpParams().set('makeId', makeId);
    if (year) params = params.set('year', year);
    return this.http.get<string[]>(`${this.baseUrl}/vehicles/catalog/models`, { params });
  }
  login(body: {email: string; password: string}): Observable<AuthResponse> { return this.http.post<AuthResponse>(`${this.baseUrl}/auth/login`, body); }
  register(body: {email: string; password: string; fullName: string; phone: string}): Observable<AuthResponse> { return this.http.post<AuthResponse>(`${this.baseUrl}/auth/register`, body); }
  authProviders(): Observable<OAuthProviders> { return this.http.get<OAuthProviders>(`${this.baseUrl}/auth/providers`); }
  exchangeOAuth(code: string): Observable<AuthResponse> { return this.http.post<AuthResponse>(`${this.baseUrl}/auth/oauth/exchange`, { code }); }
  requestRecovery(identifier: string): Observable<RecoveryStartResponse> { return this.http.post<RecoveryStartResponse>(`${this.baseUrl}/auth/recovery/request`, { identifier }); }
  completeRecovery(body: {identifier: string; code: string; newPassword: string}): Observable<void> { return this.http.post<void>(`${this.baseUrl}/auth/recovery/complete`, body); }
  oauthStartUrl(provider: 'google' | 'facebook' | 'microsoft'): string { return `${this.baseUrl.replace(/\/api$/, '')}/oauth2/authorization/${provider}`; }
  me(): Observable<User> { return this.http.get<User>(`${this.baseUrl}/users/me`); }
  updateProfile(body: ProfilePayload): Observable<User> { return this.http.put<User>(`${this.baseUrl}/users/me`, body); }
  becomeSeller(): Observable<User> { return this.http.post<User>(`${this.baseUrl}/users/me/become-seller`, {}); }
  sellerProfile(id: number): Observable<SellerProfile> { return this.http.get<SellerProfile>(`${this.baseUrl}/sellers/${id}`); }
  sellerListings(id: number): Observable<Part[]> { return this.http.get<Part[]>(`${this.baseUrl}/sellers/${id}/listings`); }
  sellerContact(id: number): Observable<SellerContact> { return this.http.get<SellerContact>(`${this.baseUrl}/sellers/${id}/contact`); }
  sellerReviews(id: number): Observable<SellerReviewSummary> { return this.http.get<SellerReviewSummary>(`${this.baseUrl}/sellers/${id}/reviews`); }
  saveSellerReview(id: number, rating: number, comment: string): Observable<SellerReviewSummary> {
    return this.http.put<SellerReviewSummary>(`${this.baseUrl}/sellers/${id}/reviews/me`, { rating, comment });
  }
  conversations(): Observable<Conversation[]> { return this.http.get<Conversation[]>(`${this.baseUrl}/messages/conversations`); }
  realtimeTicket(): Observable<{ ticket: string; expiresInSeconds: number }> {
    return this.http.post<{ ticket: string; expiresInSeconds: number }>(`${this.baseUrl}/messages/conversations/realtime-ticket`, {});
  }
  realtimeSocketUrl(ticket: string): string {
    const server = this.baseUrl.replace(/\/api$/, '').replace(/^http:/, 'ws:').replace(/^https:/, 'wss:');
    return `${server}/ws/messages?ticket=${encodeURIComponent(ticket)}`;
  }
  conversation(id: number): Observable<ConversationDetail> { return this.http.get<ConversationDetail>(`${this.baseUrl}/messages/conversations/${id}`); }
  startConversation(partId: number, message: string): Observable<ConversationDetail> { return this.http.post<ConversationDetail>(`${this.baseUrl}/messages/conversations`, { partId, message }); }
  sendMessage(conversationId: number, message: string): Observable<MarketplaceMessage> { return this.http.post<MarketplaceMessage>(`${this.baseUrl}/messages/conversations/${conversationId}/messages`, { message }); }
  sendMessageWithImages(conversationId: number, message: string, files: File[]): Observable<MarketplaceMessage> {
    const body = new FormData();
    body.append('message', message);
    files.forEach(file => body.append('files', file));
    return this.http.post<MarketplaceMessage>(`${this.baseUrl}/messages/conversations/${conversationId}/messages`, body);
  }
  favorites(): Observable<Part[]> { return this.http.get<Part[]>(`${this.baseUrl}/favorites`); }
  favorite(id: number): Observable<unknown> { return this.http.post(`${this.baseUrl}/favorites/${id}`, {}); }
  unfavorite(id: number): Observable<unknown> { return this.http.delete(`${this.baseUrl}/favorites/${id}`); }
  myListings(): Observable<Part[]> { return this.http.get<Part[]>(`${this.baseUrl}/parts/my-listings`); }
  createPart(body: PartPayload): Observable<Part> { return this.http.post<Part>(`${this.baseUrl}/parts`, body); }
  updatePart(id: number, body: PartPayload): Observable<Part> { return this.http.put<Part>(`${this.baseUrl}/parts/${id}`, body); }
  updatePartStatus(id: number, status: PartStatus): Observable<Part> { return this.http.patch<Part>(`${this.baseUrl}/parts/${id}/status`, { status }); }
  deletePart(id: number): Observable<void> { return this.http.delete<void>(`${this.baseUrl}/parts/${id}`); }
  uploadImages(id: number, files: FileList | File[]): Observable<ImageAsset[]> {
    const body = new FormData();
    Array.from(files).forEach(file => body.append('files', file));
    return this.http.post<ImageAsset[]>(`${this.baseUrl}/parts/${id}/images`, body);
  }
  makePrimaryImage(partId: number, imageId: number): Observable<ImageAsset[]> { return this.http.put<ImageAsset[]>(`${this.baseUrl}/parts/${partId}/images/${imageId}/primary`, {}); }
  reorderImages(partId: number, imageIds: number[]): Observable<ImageAsset[]> { return this.http.put<ImageAsset[]>(`${this.baseUrl}/parts/${partId}/images/order`, { imageIds }); }
  deleteImage(partId: number, imageId: number): Observable<void> { return this.http.delete<void>(`${this.baseUrl}/parts/${partId}/images/${imageId}`); }
  reportListing(partId: number, reason: ReportReason, details: string): Observable<ListingReport> { return this.http.post<ListingReport>(`${this.baseUrl}/parts/${partId}/reports`, { reason, details }); }
  reportSeller(sellerId: number, reason: SellerReportReason, details: string): Observable<SellerReport> {
    return this.http.post<SellerReport>(`${this.baseUrl}/sellers/${sellerId}/reports`, { reason, details });
  }
  adminUsers(): Observable<User[]> { return this.http.get<User[]>(`${this.baseUrl}/admin/users`); }
  sellerApplications(): Observable<User[]> { return this.http.get<User[]>(`${this.baseUrl}/admin/seller-applications`); }
  decideSellerApplication(id: number, approved: boolean, reason: string): Observable<User> {
    return this.http.patch<User>(`${this.baseUrl}/admin/seller-applications/${id}`, { approved, reason });
  }
  adminUser(id: number): Observable<User> { return this.http.get<User>(`${this.baseUrl}/admin/users/${id}`); }
  adminUpdateProfile(id: number, body: ProfilePayload): Observable<User> { return this.http.put<User>(`${this.baseUrl}/admin/users/${id}/profile`, body); }
  adminParts(): Observable<Page<Part>> { return this.http.get<Page<Part>>(`${this.baseUrl}/admin/parts?size=100`); }
  updateRole(id: number, role: UserRole): Observable<User> { return this.http.patch<User>(`${this.baseUrl}/admin/users/${id}/role`, { role }); }
  updateSuspension(id: number, suspended: boolean, reason: string): Observable<User> { return this.http.patch<User>(`${this.baseUrl}/admin/users/${id}/suspension`, { suspended, reason }); }
  adminAuditLogs(): Observable<AdminAuditLog[]> { return this.http.get<AdminAuditLog[]>(`${this.baseUrl}/admin/audit-logs`); }
  moderatePart(id: number, status: PartStatus): Observable<Part> { return this.http.patch<Part>(`${this.baseUrl}/admin/parts/${id}/status`, { status }); }
  adminReports(status?: ReportStatus): Observable<ListingReport[]> {
    const options = status ? { params: new HttpParams().set('status', status) } : {};
    return this.http.get<ListingReport[]>(`${this.baseUrl}/admin/reports`, options);
  }
  resolveReport(id: number, status: 'DISMISSED' | 'ACTIONED', adminNote: string): Observable<ListingReport> {
    return this.http.patch<ListingReport>(`${this.baseUrl}/admin/reports/${id}`, { status, adminNote });
  }
  adminSellerReports(status?: ReportStatus): Observable<SellerReport[]> {
    const options = status ? { params: new HttpParams().set('status', status) } : {};
    return this.http.get<SellerReport[]>(`${this.baseUrl}/admin/seller-reports`, options);
  }
  resolveSellerReport(id: number, status: 'DISMISSED' | 'ACTIONED', adminNote: string): Observable<SellerReport> {
    return this.http.patch<SellerReport>(`${this.baseUrl}/admin/seller-reports/${id}`, { status, adminNote });
  }
}
