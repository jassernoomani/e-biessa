export type UserRole = 'BUYER' | 'SELLER' | 'ADMIN';
export type SellerApplicationStatus = 'NONE' | 'PENDING' | 'APPROVED' | 'REJECTED';
export type PartStatus = 'ACTIVE' | 'SOLD' | 'INACTIVE';

export interface AuthUser {
  userId: number;
  email: string;
  fullName: string;
  role: UserRole;
}

export interface User extends AuthUser {
  id: number;
  phone?: string;
  location?: string;
  businessName?: string;
  website?: string;
  bio?: string;
  suspended?: boolean;
  suspensionReason?: string;
  suspendedAt?: string;
  createdAt?: string;
  sellerApplicationStatus?: SellerApplicationStatus;
  sellerApplicationSubmittedAt?: string;
  sellerApplicationReviewedAt?: string;
  sellerApplicationReviewNote?: string;
}

export interface SellerProfile {
  id: number;
  fullName: string;
  businessName?: string;
  location?: string;
  website?: string;
  bio?: string;
  memberSince?: string;
  activeListingCount: number;
  averageRating: number;
  reviewCount: number;
}

export interface SellerReview {
  id: number;
  buyerId: number;
  buyerName: string;
  rating: number;
  comment?: string;
  createdAt: string;
  updatedAt: string;
}

export interface SellerReviewSummary {
  averageRating: number;
  reviewCount: number;
  eligible: boolean;
  myReview?: SellerReview;
  reviews: SellerReview[];
}

export interface SellerContact {
  sellerId: number;
  sellerName: string;
  businessName?: string;
  phone?: string;
  email?: string;
}

export interface OAuthProviders {
  google: boolean;
  facebook: boolean;
  microsoft: boolean;
}

export interface RecoveryStartResponse {
  message: string;
  developmentCode?: string;
}

export interface ProfilePayload {
  fullName: string;
  phone: string;
  location: string;
  businessName: string;
  website: string;
  bio: string;
}

export interface ImageAsset {
  id: number;
  url: string;
  primary: boolean;
  sortOrder: number;
}

export interface Category {
  id: number;
  name: string;
  subCategories: Category[];
}

export interface Vehicle {
  id: number;
  brand: string;
  model: string;
  year: number;
  engine?: string;
  fuelType?: string;
}

export interface VehicleCatalogMake {
  id: string;
  name: string;
  country?: string;
}

export interface Part {
  id: number;
  title: string;
  description?: string;
  price: number;
  condition?: string;
  categoryName?: string;
  categoryId?: number;
  sellerName: string;
  sellerId: number;
  brand?: string;
  model?: string;
  year?: number;
  compatibleVehicles?: Vehicle[];
  manufacturer?: string;
  partBrand?: string;
  oemNumber?: string;
  alternativeOemNumbers?: string[];
  partPosition?: string;
  engineCompatibility?: string;
  fuelCompatibility?: string;
  warrantyInformation?: string;
  deliveryAvailable?: boolean;
  collectionAvailable?: boolean;
  negotiable?: boolean;
  location?: string;
  status: PartStatus;
  views: number;
  createdAt: string;
  soldAt?: string;
  imageUrls: string[];
  images: ImageAsset[];
  favoriteCount: number;
  favorited: boolean;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  first: boolean;
  last: boolean;
}

export interface SemanticPartResult {
  part: Part;
  score: number;
  matchReasons: string[];
}

export interface GroundedAiAnswer {
  summary: string;
  recommendedListingIds: number[];
  cautions: string[];
  generatedBy: 'deterministic';
}

export interface SemanticSearchResponse {
  query: string;
  results: SemanticPartResult[];
  tookMs: number;
  extractedConstraints: Record<string, string | number | null>;
  answer: GroundedAiAnswer;
}

export type ReportReason = 'SCAM' | 'WRONG_INFORMATION' | 'PROHIBITED_ITEM' | 'DUPLICATE' | 'ALREADY_SOLD' | 'OTHER';
export type ReportStatus = 'PENDING' | 'DISMISSED' | 'ACTIONED';
export type SellerReportReason = 'FRAUD_OR_SCAM' | 'HARASSMENT' | 'MISLEADING_PROFILE' | 'OFF_PLATFORM_ABUSE' | 'PROHIBITED_ACTIVITY' | 'OTHER';

export interface ListingReport {
  id: number;
  partId: number;
  partTitle: string;
  partStatus: PartStatus;
  sellerId: number;
  sellerName: string;
  reporterId: number;
  reporterName: string;
  reason: ReportReason;
  details?: string;
  status: ReportStatus;
  adminNote?: string;
  createdAt: string;
  resolvedAt?: string;
}

export interface SellerReport {
  id: number;
  sellerId: number;
  sellerName: string;
  businessName?: string;
  reporterId: number;
  reporterName: string;
  reason: SellerReportReason;
  details: string;
  status: ReportStatus;
  adminNote?: string;
  createdAt: string;
  resolvedAt?: string;
}

export interface AdminAuditLog {
  id: number;
  adminId: number;
  adminEmail: string;
  action: string;
  targetType: string;
  targetId?: number;
  targetLabel?: string;
  details?: string;
  createdAt: string;
}

export interface Conversation {
  id: number;
  partId: number;
  partTitle: string;
  partImageUrl?: string;
  buyerId: number;
  buyerName: string;
  sellerId: number;
  sellerName: string;
  otherPartyName: string;
  lastMessage?: string;
  lastMessageAt?: string;
  unreadCount: number;
}

export interface MarketplaceMessage {
  id: number;
  senderId: number;
  senderName: string;
  body: string;
  createdAt: string;
  readAt?: string;
  attachments?: MessageAttachment[];
}

export interface MessageAttachment {
  id: number;
  url: string;
  originalName: string;
  contentType: string;
  sizeBytes: number;
}

export interface ConversationDetail {
  conversation: Conversation;
  messages: MarketplaceMessage[];
}

export interface PartPayload {
  title: string;
  description: string;
  price: number;
  condition: string;
  categoryId: number | null;
  vehicleIds: number[];
  manufacturer: string;
  partBrand: string;
  oemNumber: string;
  alternativeOemNumbers: string[];
  partPosition: string;
  engineCompatibility: string;
  fuelCompatibility: string;
  warrantyInformation: string;
  deliveryAvailable: boolean;
  collectionAvailable: boolean;
  negotiable: boolean;
  location: string;
}
