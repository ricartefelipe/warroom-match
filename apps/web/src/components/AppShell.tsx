"use client";

import Link from "next/link";
import type { ReactNode } from "react";
import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { getPublicConfig, type PublicConfig } from "@/lib/api";
import { clearSession, loadSession, type UserRole } from "@/lib/session";

export function AppShell({ children }: { children: ReactNode }) {
  const router = useRouter();
  const [email, setEmail] = useState<string | null>(null);
  const [role, setRole] = useState<UserRole | null | undefined>(undefined);
  const [config, setConfig] = useState<PublicConfig | null>(null);

  useEffect(() => {
    const session = loadSession();
    setEmail(session?.email ?? null);
    setRole(session?.role);
    getPublicConfig()
      .then(setConfig)
      .catch(() => setConfig(null));
  }, []);

  function onLogout() {
    clearSession();
    router.push("/");
  }

  return (
    <div className="app-shell">
      {config?.demo ? (
        <output className="demo-banner">
          MODO DEMONSTRAÇÃO: pagamentos, repasses e devoluções são simulados. Nenhum valor real circula.
        </output>
      ) : null}
      <header className="topbar">
        <Link href="/app" className="brand">
          WarRoom <span>Match</span>
        </Link>
        <nav className="nav">
          {role === "company" ? <Link href="/app/company">Empresa</Link> : null}
          {role === "pro" ? <Link href="/app/pro">Profissional</Link> : null}
          {role === "admin" ? <span className="muted">Suporte</span> : null}
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
