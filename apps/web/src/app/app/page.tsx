"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { AppShell } from "@/components/AppShell";
import { getMe, setRole } from "@/lib/api";
import { loadSession, saveSession, type UserRole } from "@/lib/session";

export default function AppHubPage() {
  const router = useRouter();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selecting, setSelecting] = useState(false);
  const [name, setName] = useState<string | null>(null);

  useEffect(() => {
    const session = loadSession();
    if (!session) {
      router.replace("/");
      return;
    }

    getMe(session.sessionToken)
      .then((me) => {
        saveSession({ ...session, ...me, sessionToken: session.sessionToken });
        setName(me.name ?? me.email);
        if (me.role === "company") {
          router.replace("/app/company");
          return;
        }
        if (me.role === "pro") {
          router.replace("/app/pro");
          return;
        }
        setLoading(false);
      })
      .catch((err) => {
        setError(err instanceof Error ? err.message : "falha_me");
        setLoading(false);
      });
  }, [router]);

  async function chooseRole(role: UserRole) {
    const session = loadSession();
    if (!session) {
      router.replace("/");
      return;
    }
    setSelecting(true);
    setError(null);
    try {
      const updated = await setRole(session.sessionToken, role);
      saveSession({ ...session, ...updated, sessionToken: session.sessionToken });
      router.replace(role === "company" ? "/app/company" : "/app/pro");
    } catch (err) {
      setError(err instanceof Error ? err.message : "falha_role");
      setSelecting(false);
    }
  }

  if (loading) {
    return (
      <AppShell>
        <section className="panel">
          <p className="muted">Carregando...</p>
        </section>
      </AppShell>
    );
  }

  return (
    <AppShell>
      <section className="panel">
        <h1 className="hero-title">Bem-vindo{name ? `, ${name}` : ""}</h1>
        <p className="muted">Como você vai usar o WarRoom Match?</p>
        <div className="role-picker" style={{ marginTop: "1.5rem" }}>
          <button
            type="button"
            className="role-option"
            disabled={selecting}
            onClick={() => chooseRole("company")}
          >
            <h3>Empresa</h3>
            <p className="muted" style={{ margin: 0 }}>
              Publicar jobs urgentes, aceitar profissionais e gerenciar pagamentos.
            </p>
          </button>
          <button
            type="button"
            className="role-option"
            disabled={selecting}
            onClick={() => chooseRole("pro")}
          >
            <h3>Profissional</h3>
            <p className="muted" style={{ margin: 0 }}>
              Ver plantões abertos, candidatar-se e executar jobs com escrow garantido.
            </p>
          </button>
        </div>
        {error ? <p className="error">{error}</p> : null}
      </section>
    </AppShell>
  );
}
