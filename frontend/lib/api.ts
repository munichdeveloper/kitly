import {
  LoginData,
  SignupData,
  AuthResponse,
  UserResponse,
  TenantResponse,
  TenantRequest,
  SessionResponse,
  CurrentSessionResponse,
  RefreshTokenResponse,
  SwitchTenantRequest,
  MembershipResponse,
  UpdateMemberRequest,
  InvitationRequest,
  InvitationResponse,
  CreateInviteResponse,
  AcceptInviteRequest,
  EntitlementResponse,
  PlanDefinition,
  CheckoutRequest,
  CheckoutResponse,
  SubscriptionResponse,
  Invoice,
} from './types';

// Re-export types for convenience
export type {
  LoginData,
  SignupData,
  AuthResponse,
  UserResponse,
  TenantResponse,
  TenantRequest,
  SessionResponse,
  CurrentSessionResponse,
  RefreshTokenResponse,
  SwitchTenantRequest,
  MembershipResponse,
  UpdateMemberRequest,
  InvitationRequest,
  InvitationResponse,
  CreateInviteResponse,
  AcceptInviteRequest,
  EntitlementResponse,
  PlanDefinition,
  CheckoutRequest,
  CheckoutResponse,
  SubscriptionResponse,
  Invoice,
};

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api';

export class ApiError extends Error {
  constructor(public status: number, message: string, public data?: unknown) {
    super(message);
    this.name = 'ApiError';
  }
}

export class ApiClient {
  private static getAuthHeader(): HeadersInit {
    if (typeof window === 'undefined') {
      return {
        'Content-Type': 'application/json',
      };
    }
    const token = localStorage.getItem('token');
    if (token) {
      return {
        'Authorization': `Bearer ${token}`,
        'Content-Type': 'application/json',
      };
    }
    return {
      'Content-Type': 'application/json',
    };
  }

  private static async handleResponse<T>(response: Response): Promise<T> {
    if (!response.ok) {
      let errorMessage = `Request failed with status ${response.status}`;
      let errorData;
      
      try {
        errorData = await response.json();
        errorMessage = errorData.message || errorData.error || errorMessage;
      } catch {
        // If response is not JSON, use status text
        errorMessage = response.statusText || errorMessage;
      }
      
      throw new ApiError(response.status, errorMessage, errorData);
    }
    
    // Handle 204 No Content
    if (response.status === 204) {
      return {} as T;
    }
    
    return response.json();
  }

  // ========== Auth APIs ==========
  static async login(data: LoginData): Promise<AuthResponse> {
    const response = await fetch(`${API_BASE_URL}/auth/login`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(data),
    });
    return this.handleResponse<AuthResponse>(response);
  }

  static async signup(data: SignupData): Promise<AuthResponse> {
    const response = await fetch(`${API_BASE_URL}/auth/signup`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(data),
    });
    return this.handleResponse<AuthResponse>(response);
  }

  static async getCurrentUser(): Promise<UserResponse> {
    const response = await fetch(`${API_BASE_URL}/users/me`, {
      method: 'GET',
      headers: this.getAuthHeader(),
    });
    return this.handleResponse<UserResponse>(response);
  }

  // ========== Tenant APIs ==========
  static async createTenant(data: TenantRequest): Promise<TenantResponse> {
    const response = await fetch(`${API_BASE_URL}/tenants`, {
      method: 'POST',
      headers: this.getAuthHeader(),
      body: JSON.stringify(data),
    });
    return this.handleResponse<TenantResponse>(response);
  }

  static async getTenant(tenantId: string): Promise<TenantResponse> {
    const response = await fetch(`${API_BASE_URL}/tenants/${tenantId}`, {
      method: 'GET',
      headers: this.getAuthHeader(),
    });
    return this.handleResponse<TenantResponse>(response);
  }

  static async getUserTenants(): Promise<TenantResponse[]> {
    const response = await fetch(`${API_BASE_URL}/me/tenants`, {
      method: 'GET',
      headers: this.getAuthHeader(),
    });
    return this.handleResponse<TenantResponse[]>(response);
  }

  // ========== Session APIs ==========
  static async switchTenant(data: SwitchTenantRequest): Promise<SessionResponse> {
    const response = await fetch(`${API_BASE_URL}/sessions/switch-tenant`, {
      method: 'POST',
      headers: this.getAuthHeader(),
      body: JSON.stringify(data),
    });
    return this.handleResponse<SessionResponse>(response);
  }

  static async refreshSession(): Promise<RefreshTokenResponse> {
    const response = await fetch(`${API_BASE_URL}/sessions/refresh`, {
      method: 'POST',
      headers: this.getAuthHeader(),
    });
    return this.handleResponse<RefreshTokenResponse>(response);
  }

  static async getCurrentSession(): Promise<CurrentSessionResponse> {
    const response = await fetch(`${API_BASE_URL}/sessions/current`, {
      method: 'GET',
      headers: this.getAuthHeader(),
    });
    return this.handleResponse<CurrentSessionResponse>(response);
  }

  // ========== Member APIs ==========
  static async getTenantMembers(tenantId: string): Promise<MembershipResponse[]> {
    const response = await fetch(`${API_BASE_URL}/tenants/${tenantId}/members`, {
      method: 'GET',
      headers: this.getAuthHeader(),
    });
    return this.handleResponse<MembershipResponse[]>(response);
  }

  static async updateMember(
    tenantId: string,
    userId: string,
    data: UpdateMemberRequest
  ): Promise<MembershipResponse> {
    const response = await fetch(`${API_BASE_URL}/tenants/${tenantId}/members/${userId}`, {
      method: 'PATCH',
      headers: this.getAuthHeader(),
      body: JSON.stringify(data),
    });
    return this.handleResponse<MembershipResponse>(response);
  }

  // ========== Invite APIs ==========
  static async createInvite(tenantId: string, data: InvitationRequest): Promise<CreateInviteResponse> {
    const response = await fetch(`${API_BASE_URL}/tenants/${tenantId}/invites`, {
      method: 'POST',
      headers: this.getAuthHeader(),
      body: JSON.stringify(data),
    });
    return this.handleResponse<CreateInviteResponse>(response);
  }

  static async getPendingInvites(tenantId: string): Promise<InvitationResponse[]> {
    const response = await fetch(`${API_BASE_URL}/tenants/${tenantId}/invites`, {
      method: 'GET',
      headers: this.getAuthHeader(),
    });
    return this.handleResponse<InvitationResponse[]>(response);
  }

  static async acceptInvite(data: AcceptInviteRequest): Promise<void> {
    const response = await fetch(`${API_BASE_URL}/invites/accept`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(data),
    });
    return this.handleResponse<void>(response);
  }

  // ========== Entitlement APIs ==========
  static async getTenantEntitlements(tenantId: string): Promise<EntitlementResponse> {
    const response = await fetch(`${API_BASE_URL}/tenants/${tenantId}/entitlements`, {
      method: 'GET',
      headers: this.getAuthHeader(),
    });
    return this.handleResponse<EntitlementResponse>(response);
  }

  static async getMyEntitlements(): Promise<EntitlementResponse> {
    const response = await fetch(`${API_BASE_URL}/entitlements/me`, {
      method: 'GET',
      headers: this.getAuthHeader(),
    });
    return this.handleResponse<EntitlementResponse>(response);
  }

  static async getPlanCatalog(): Promise<Record<string, PlanDefinition>> {
    const response = await fetch(`${API_BASE_URL}/plans`, {
      method: 'GET',
      headers: {
        'Content-Type': 'application/json',
      },
    });
    return this.handleResponse<Record<string, PlanDefinition>>(response);
  }

  // ========== Billing APIs ==========
  static async createCheckoutSession(data: CheckoutRequest): Promise<CheckoutResponse> {
    const response = await fetch(`${API_BASE_URL}/billing/checkout`, {
      method: 'POST',
      headers: this.getAuthHeader(),
      body: JSON.stringify(data),
    });
    return this.handleResponse<CheckoutResponse>(response);
  }

  static async getSubscription(tenantId: string): Promise<SubscriptionResponse> {
    const response = await fetch(`${API_BASE_URL}/billing/subscription/${tenantId}`, {
      method: 'GET',
      headers: this.getAuthHeader(),
    });
    return this.handleResponse<SubscriptionResponse>(response);
  }

  static async getInvoices(tenantId: string): Promise<Invoice[]> {
    const response = await fetch(`${API_BASE_URL}/billing/invoices/${tenantId}`, {
      method: 'GET',
      headers: this.getAuthHeader(),
    });
    return this.handleResponse<Invoice[]>(response);
  }

  // ========== Health Check ==========
  static async checkHealth(): Promise<{ status: string; application: string }> {
    const response = await fetch(`${API_BASE_URL}/health`);
    return this.handleResponse<{ status: string; application: string }>(response);
  }
}
