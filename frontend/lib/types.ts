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
  username: string;
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
  planName: string;
  version: number;
  features: Record<string, boolean>;
  limits: Record<string, number>;
  computedAt: string;
}

export interface PlanDefinition {
  name: string;
  displayName: string;
  description: string;
  features: Record<string, boolean>;
  limits: Record<string, number>;
  price?: number;
}

// UI State Types
export interface Toast {
  id: string;
  type: 'success' | 'error' | 'info' | 'warning';
  message: string;
  duration?: number;
}

export type MemberRole = 'OWNER' | 'ADMIN' | 'MEMBER';
export type MemberStatus = 'ACTIVE' | 'INACTIVE';
export type InviteStatus = 'PENDING' | 'ACCEPTED' | 'EXPIRED';
export type TenantStatus = 'ACTIVE' | 'SUSPENDED' | 'DELETED';
