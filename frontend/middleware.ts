import { NextResponse } from 'next/server';
import type { NextRequest } from 'next/server';

export function middleware(request: NextRequest) {
  const token = request.cookies.get('token')?.value || '';
  
  // Check if trying to access protected routes
  const isProtectedRoute = 
    request.nextUrl.pathname.startsWith('/workspaces') ||
    request.nextUrl.pathname.startsWith('/dashboard') ||
    request.nextUrl.pathname.startsWith('/subscriptions');

  // If no token and trying to access protected route, redirect to login
  if (isProtectedRoute && !token) {
    // Try to get token from localStorage (we'll use a different approach)
    // For now, redirect to login
    return NextResponse.redirect(new URL('/auth/login', request.url));
  }

  return NextResponse.next();
}

export const config = {
  matcher: ['/workspaces/:path*', '/dashboard/:path*', '/subscriptions/:path*'],
};
