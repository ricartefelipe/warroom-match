"use client";

import Link from "next/link";
import type { ReactNode } from "react";
import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { clearSession, loadSession, type UserRole } from "@/lib/session";

export function AppShell({ children }: { children: ReactNode }) {
  const router = useRouter();
  const [email, setEmail] = useState<string | null>(null);
  const [role, setRole] = useState<UserRole | null | undefined>(undefined);

  useEffect(() => {
    const session = loadSession();
    setEmail(session?.email ?? null);
    setRole(session?.role);
  }, []);

  function onLogout() {
    clearSession();
    router.push("/");
  }

  return (
    <div className="app-shell">
      <header className="topbar">
        <Link href="/app" className="brand">
          WarRoom <span>Match</span>
        </Link>
        <nav className="nav">
          {role === "company" ? <Link href="/app/company">Empresa</Link> : null}
          {role === "pro" ? <Link href="/app/pro">Profissional</Link> : null}
          {email ? (
            <button type="button" className="button-secondary" onClick={onLogout}>
              Sair ({email})
            </button>
          ) : null}
        </nav>
      </header>
      {children}
    </div>
  );
}
