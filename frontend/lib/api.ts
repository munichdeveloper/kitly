const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api';

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

export interface AuthResponse {
  token: string;
  type: string;
  username: string;
  email: string;
}

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

export interface TenantResponse {
  id: string;
  name: string;
  slug: string;
  createdAt: string;
  updatedAt: string;
}

export interface SubscriptionResponse {
  id: string;
  tenantId: string;
  plan: string;
  status: string;
  billingCycle?: string;
  amount?: number;
  currency?: string;
  startsAt: string;
  endsAt?: string;
  maxSeats?: number;
}

export interface EntitlementResponse {
  tenantId: string;
  planCode: string;
  status: string;
  seatsQuantity: number;
  activeSeats: number;
  entitlementVersion: number;
  items: EntitlementItem[];
}

export interface EntitlementItem {
  key: string;
  value: string;
  source: string;
}

export interface MembershipResponse {
  id: string;
  userId: string;
  tenantId: string;
  role: string;
  status: string;
  createdAt: string;
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

  static async login(data: LoginData): Promise<AuthResponse> {
    const response = await fetch(`${API_BASE_URL}/auth/login`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(data),
    });

    if (!response.ok) {
      throw new Error('Login failed');
    }

    return response.json();
  }

  static async signup(data: SignupData): Promise<AuthResponse> {
    const response = await fetch(`${API_BASE_URL}/auth/signup`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(data),
    });

    if (!response.ok) {
      throw new Error('Signup failed');
    }

    return response.json();
  }

  static async getCurrentUser(): Promise<UserResponse> {
    const response = await fetch(`${API_BASE_URL}/users/me`, {
      method: 'GET',
      headers: this.getAuthHeader(),
    });

    if (!response.ok) {
      throw new Error('Failed to fetch user');
    }

    return response.json();
  }

  static async checkHealth(): Promise<{ status: string; application: string }> {
    const response = await fetch(`${API_BASE_URL}/health`);
    if (!response.ok) {
      throw new Error('Health check failed');
    }
    return response.json();
  }

  // Tenant endpoints
  static async getTenants(): Promise<TenantResponse[]> {
    const response = await fetch(`${API_BASE_URL}/tenants`, {
      method: 'GET',
      headers: this.getAuthHeader(),
    });
    if (!response.ok) {
      throw new Error('Failed to fetch tenants');
    }
    return response.json();
  }

  static async createTenant(name: string, slug: string): Promise<TenantResponse> {
    const response = await fetch(`${API_BASE_URL}/tenants`, {
      method: 'POST',
      headers: this.getAuthHeader(),
      body: JSON.stringify({ name, slug }),
    });
    if (!response.ok) {
      throw new Error('Failed to create tenant');
    }
    return response.json();
  }

  // Subscription endpoints
  static async getActiveSubscription(tenantId: string): Promise<SubscriptionResponse> {
    const response = await fetch(`${API_BASE_URL}/tenants/${tenantId}/subscription`, {
      method: 'GET',
      headers: this.getAuthHeader(),
    });
    if (!response.ok) {
      throw new Error('Failed to fetch subscription');
    }
    return response.json();
  }

  // Entitlement endpoints
  static async getEntitlements(): Promise<EntitlementResponse> {
    const response = await fetch(`${API_BASE_URL}/entitlements/me`, {
      method: 'GET',
      headers: this.getAuthHeader(),
    });
    if (!response.ok) {
      throw new Error('Failed to fetch entitlements');
    }
    return response.json();
  }

  static async getPlans(): Promise<any> {
    const response = await fetch(`${API_BASE_URL}/plans`, {
      method: 'GET',
    });
    if (!response.ok) {
      throw new Error('Failed to fetch plans');
    }
    return response.json();
  }

  // Membership endpoints
  static async getMemberships(tenantId: string): Promise<MembershipResponse[]> {
    const response = await fetch(`${API_BASE_URL}/tenants/${tenantId}/memberships`, {
      method: 'GET',
      headers: this.getAuthHeader(),
    });
    if (!response.ok) {
      throw new Error('Failed to fetch memberships');
    }
    return response.json();
  }
}
