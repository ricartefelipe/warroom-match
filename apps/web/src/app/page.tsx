"use client";

import { FormEvent, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { loginWithPassword, requestMagicLink } from "@/lib/api";
import { loadSession, saveSession } from "@/lib/session";

export default function HomePage() {
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [name, setName] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [sent, setSent] = useState(false);
  const [devLink, setDevLink] = useState<string | null>(null);

  useEffect(() => {
    if (loadSession()) {
      router.replace("/app");
    }
  }, [router]);

  async function onMagicLink(event: FormEvent) {
    event.preventDefault();
    setLoading(true);
    setError(null);
    setSent(false);
    setDevLink(null);
    try {
      const result = await requestMagicLink(email.trim(), name.trim() || email.trim());
      setSent(true);
      if (result.magicLink) {
        setDevLink(result.magicLink);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "falha_ao_enviar");
    } finally {
      setLoading(false);
    }
  }

  async function onPassword(event: FormEvent) {
    event.preventDefault();
    setLoading(true);
    setError(null);
    try {
      saveSession(await loginWithPassword(email.trim(), password));
      router.replace("/app");
    } catch (err) {
      setError(err instanceof Error ? err.message : "invalid_credentials");
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="app-shell">
      <div className="login-layout">
        <section>
          <h1 className="brand-hero">
            WarRoom <span>Match</span>
          </h1>
          <p className="tagline muted">
            Incidente ou prazo crítico? Conecte sua empresa a profissionais vetted agora — pagamento
            seguro em escrow, taxa só quando o job acontece.
          </p>
          <div className="role-hint">
            <div className="role-card">
              <strong>Empresa</strong>
              <p className="muted" style={{ margin: "0.35rem 0 0" }}>
                Publique jobs urgentes, aceite candidatos e libere pagamento após conclusão.
              </p>
            </div>
            <div className="role-card">
              <strong>Profissional</strong>
              <p className="muted" style={{ margin: "0.35rem 0 0" }}>
                Veja o feed de plantões abertos, candidate-se e receba com escrow garantido.
              </p>
            </div>
          </div>
        </section>

        <section className="panel">
          <h2 className="hero-title">Entrar</h2>
          <p className="muted">
            Receba um link mágico no e-mail. O papel (empresa ou profissional) é escolhido após o
            login.
          </p>
          <form onSubmit={onMagicLink} style={{ marginTop: "1.25rem" }}>
            <div className="field">
              <label htmlFor="email">E-mail</label>
              <input
                id="email"
                type="email"
                required
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="voce@empresa.com"
              />
            </div>
            <div className="field">
              <label htmlFor="name">Nome</label>
              <input
                id="name"
                value={name}
                onChange={(e) => setName(e.target.value)}
                placeholder="Para magic link"
              />
            </div>
            <button className="button" type="submit" disabled={loading}>
              {loading ? "Enviando..." : "Enviar link de acesso"}
            </button>
            {sent ? (
              <p className="muted" style={{ marginTop: "0.9rem" }}>
                Link enviado. Confira sua caixa de entrada.
              </p>
            ) : null}
            {devLink ? (
              <div className="highlight-key mono">
                Dev: <a href={devLink}>{devLink}</a>
              </div>
            ) : null}
            {error ? <p className="error">{error}</p> : null}
          </form>
          <form onSubmit={onPassword} style={{ marginTop: "1.25rem" }}>
            <div className="field">
              <label htmlFor="password">Senha</label>
              <input
                id="password"
                type="password"
                required
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                autoComplete="current-password"
              />
            </div>
            <button className="button" type="submit" disabled={loading}>
              {loading ? "Entrando..." : "Entrar com senha"}
            </button>
          </form>
        </section>
      </div>
    </div>
  );
}
