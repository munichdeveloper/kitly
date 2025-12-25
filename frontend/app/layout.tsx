import type { Metadata } from "next";
import "./globals.css";
import { AuthProvider } from "@/lib/auth-context";
import { TenantProvider } from "@/lib/tenant-context";
import { ToastProvider } from "@/lib/toast-context";
import ToastContainer from "@/components/ToastContainer";
import PlatformAdminLink from "@/components/PlatformAdminLink";

export const metadata: Metadata = {
  title: "Kitly - B2B SaaS Platform",
  description: "A modern B2B SaaS platform with workspace management",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en">
      <body className="antialiased">
        <ToastProvider>
          <AuthProvider>
            <TenantProvider>
              {children}
              <PlatformAdminLink />
              <ToastContainer />
            </TenantProvider>
          </AuthProvider>
        </ToastProvider>
      </body>
    </html>
  );
}
