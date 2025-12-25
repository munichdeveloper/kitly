// User Types
export interface UserResponse {
  id: string;
  username: string;
  email: string;
  firstName?: string;
  lastName?: string;
  roles: string[];
  createdAt: string;
  isActive: boolean;
}

// Auth Types
export interface AuthResponse {
  token: string;
  type: string;
  username: string;
  email: string;
}

export interface LoginData {
  email: string;
  password: string;
}

export interface SignupData {
  username: string;
  email: string;
  password: string;
  firstName?: string;
  lastName?: string;
}

// Tenant Types
export interface TenantResponse {
  id: string;
  name: string;
  slug: string;
  domain: string;
  status: string;
  ownerId: string;
  createdAt: string;
  updatedAt: string;
}

export interface TenantRequest {
  name: string;
  slug: string;
  domain?: string;
}

// Session Types
export interface SessionResponse {
  token: string;
  type: string;
  userId: string;
  tenantId: string;
  roles: string[];
  entitlementVersion: number;
  expiresIn: number; // milliseconds
}

export interface CurrentSessionResponse {
  userId: string;
  username: string;
  tenantId: string;
  tenantName: string;
  roles: string[];
  entitlementVersion: number;
  expiresAt: string;
}

export interface RefreshTokenResponse {
  token: string;
  type: string;
  expiresIn: number;
}

export interface SwitchTenantRequest {
  tenantId: string;
}

// Member Types
export interface MembershipResponse {
  id: string;
  userId: string;
  username: string;
  email: string;
  firstName?: string;
  lastName?: string;
  role: string;
  status: string;
  joinedAt: string;
}

export interface UpdateMemberRequest {
  role?: string;
  status?: string;
}

// Invite Types
export interface InvitationRequest {
  email: string;
  role: string;
  teamId?: string;
}

export interface InvitationResponse {
  id: string;
  tenantId: string;
  teamId?: string;
  email: string;
  role: string;
  status: string;
  invitedByUsername: string;
  invitedAt: string;
  expiresAt: string;
  acceptedAt?: string;
}

export interface CreateInviteResponse {
  invitationId: string;
  token: string;
  email: string;
  role: string;
  expiresAt: string;
}

export interface AcceptInviteRequest {
  token: string;
  username?: string;
  password?: string;
}

// Entitlement Types
export interface EntitlementResponse {
  tenantId: string;
  planCode: string;
  status: string;
  seatsQuantity: number;
  activeSeats: number;
  entitlementVersion: number;
  items: Array<{
    key: string;
    value: string;
    source: string;
  }>;
}

export interface PlanDefinition {
  name: string;
  displayName: string;
  description: string;
  features: Record<string, boolean>;
  limits: Record<string, number>;
  price?: number;
}

// Billing Types
export interface CheckoutRequest {
  tenantId: string;
  plan: 'STARTER' | 'BUSINESS' | 'ENTERPRISE';
}

export interface CheckoutResponse {
  url: string;
}

export interface SubscriptionResponse {
  id: string;
  tenantId: string;
  plan: string;
  status: string;
  billingCycle: string;
  amount: number;
  currency: string;
  startsAt: string;
  endsAt?: string;
  trialEndsAt?: string;
  cancelledAt?: string;
  maxSeats: number;
}

export interface Invoice {
  id: string;
  tenantId: string;
  stripeInvoiceId: string;
  amountPaid: number;
  currency: string;
  status: string;
  invoicePdf: string;
  hostedInvoiceUrl: string;
  createdAt: string;
}

// UI State Types
export interface Toast {
  id: string;
  type: 'success' | 'error' | 'info' | 'warning';
  message: string;
  duration?: number;
}

/** Member role types */
export type MemberRole = 'OWNER' | 'ADMIN' | 'MEMBER';

/** Member status - ACTIVE members count towards seat limit, INACTIVE members do not */
export type MemberStatus = 'ACTIVE' | 'INACTIVE';

/** Invitation status - PENDING invites can be accepted, ACCEPTED invites have been used, EXPIRED invites can no longer be accepted */
export type InviteStatus = 'PENDING' | 'ACCEPTED' | 'EXPIRED';

/** Tenant status - ACTIVE tenants are operational, SUSPENDED tenants have limited access, DELETED tenants are archived */
export type TenantStatus = 'ACTIVE' | 'SUSPENDED' | 'DELETED';

// Application Settings Types
export interface ApplicationSettingResponse {
  id: string;
  key: string;
  value: string;
  type: 'STRING' | 'NUMBER' | 'BOOLEAN' | 'JSON' | 'URL' | 'EMAIL';
  description?: string;
  isPublic: boolean;
  createdAt: string;
  updatedAt: string;
  updatedBy?: string;
}

export interface ApplicationSettingRequest {
  key: string;
  value: string;
  type: 'STRING' | 'NUMBER' | 'BOOLEAN' | 'JSON' | 'URL' | 'EMAIL';
  description?: string;
  isPublic?: boolean;
}

