"use client";

import Link from "next/link";
import { FormEvent, useCallback, useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { AppShell } from "@/components/AppShell";
import {
  acceptApplication,
  applyToJob,
  checkoutJob,
  completeJob,
  formatBrl,
  getJob,
  listMessages,
  postMessage,
  startJob,
  statusLabel,
  submitReview,
  urgencyLabel,
  type JobDetail,
  type Message,
} from "@/lib/api";
import { loadSession, type UserRole } from "@/lib/session";

const POLL_MS = 4000;

export default function JobDetailPage() {
  const params = useParams();
  const router = useRouter();
  const jobId = params.id as string;

  const [job, setJob] = useState<JobDetail | null>(null);
  const [messages, setMessages] = useState<Message[]>([]);
  const [role, setRole] = useState<UserRole | null>(null);
  const [accountId, setAccountId] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [actionLoading, setActionLoading] = useState(false);

  const [pitch, setPitch] = useState("");
  const [messageBody, setMessageBody] = useState("");
  const [rating, setRating] = useState(5);
  const [comment, setComment] = useState("");
  const [reviewSent, setReviewSent] = useState(false);

  const refreshJob = useCallback(async () => {
    const session = loadSession();
    if (!session) {
      router.replace("/");
      return null;
    }
    setRole(session.role ?? null);
    setAccountId(session.accountId);
    const detail = await getJob(session.sessionToken, jobId);
    setJob(detail);
    return session;
  }, [jobId, router]);

  const refreshMessages = useCallback(async () => {
    const session = loadSession();
    if (!session) {
      return;
    }
    try {
      const msgs = await listMessages(session.sessionToken, jobId);
      setMessages(msgs);
    } catch {
      /* polling silencioso */
    }
  }, [jobId]);

  useEffect(() => {
    refreshJob()
      .then((session) => {
        if (session) {
          return refreshMessages();
        }
      })
      .catch((err) => setError(err instanceof Error ? err.message : "falha_job"))
      .finally(() => setLoading(false));
  }, [refreshJob, refreshMessages]);

  useEffect(() => {
    if (!job || job.status === "open" || job.status === "draft") {
      return;
    }
    const interval = setInterval(refreshMessages, POLL_MS);
    return () => clearInterval(interval);
  }, [job, refreshMessages]);

  async function runAction(fn: () => Promise<void>) {
    setActionLoading(true);
    setError(null);
    try {
      await fn();
      await refreshJob();
      await refreshMessages();
    } catch (err) {
      setError(err instanceof Error ? err.message : "falha_acao");
    } finally {
      setActionLoading(false);
    }
  }

  async function onApply(event: FormEvent) {
    event.preventDefault();
    const session = loadSession();
    if (!session) {
      return;
    }
    await runAction(async () => {
      await applyToJob(session.sessionToken, jobId, pitch.trim());
      setPitch("");
    });
  }

  async function onAccept(applicationId: string) {
    const session = loadSession();
    if (!session) {
      return;
    }
    await runAction(async () => {
      await acceptApplication(session.sessionToken, jobId, applicationId);
    });
  }

  async function onCheckout() {
    const session = loadSession();
    if (!session) {
      return;
    }
    await runAction(async () => {
      const result = await checkoutJob(session.sessionToken, jobId);
      if (result.url) {
        window.location.href = result.url;
      }
    });
  }

  async function onStart() {
    const session = loadSession();
    if (!session) {
      return;
    }
    await runAction(async () => {
      await startJob(session.sessionToken, jobId);
    });
  }

  async function onComplete() {
    const session = loadSession();
    if (!session) {
      return;
    }
    await runAction(async () => {
      await completeJob(session.sessionToken, jobId);
    });
  }

  async function onSendMessage(event: FormEvent) {
    event.preventDefault();
    const session = loadSession();
    if (!session || !messageBody.trim()) {
      return;
    }
    setActionLoading(true);
    setError(null);
    try {
      await postMessage(session.sessionToken, jobId, messageBody.trim());
      setMessageBody("");
      await refreshMessages();
    } catch (err) {
      setError(err instanceof Error ? err.message : "falha_mensagem");
    } finally {
      setActionLoading(false);
    }
  }

  async function onReview(event: FormEvent) {
    event.preventDefault();
    const session = loadSession();
    if (!session) {
      return;
    }
    await runAction(async () => {
      await submitReview(session.sessionToken, jobId, rating, comment.trim());
      setReviewSent(true);
    });
  }

  const isCompany = role === "company";
  const isPro = role === "pro";
  const isMatchedPro = isPro && job?.matchedProAccountId === accountId;
  const canMessage =
    job &&
    job.status !== "open" &&
    job.status !== "draft" &&
    job.status !== "cancelled" &&
    (isCompany || isMatchedPro || job.myApplicationStatus === "accepted");

  if (loading) {
    return (
      <AppShell>
        <section className="panel">
          <p className="muted">Carregando job...</p>
        </section>
      </AppShell>
    );
  }

  if (!job) {
    return (
      <AppShell>
        <section className="panel">
          <p className="error">{error ?? "job_nao_encontrado"}</p>
          <Link href={isCompany ? "/app/company" : "/app/pro"} className="button-secondary">
            Voltar
          </Link>
        </section>
      </AppShell>
    );
  }

  const pendingApplications = job.applications?.filter((a) => a.status === "pending") ?? [];

  return (
    <AppShell>
      <section className="panel">
        <div style={{ display: "flex", justifyContent: "space-between", gap: "1rem", flexWrap: "wrap" }}>
          <div>
            <Link
              href={isCompany ? "/app/company" : "/app/pro"}
              className="muted"
              style={{ fontSize: "0.85rem" }}
            >
              ← Voltar
            </Link>
            <h1 className="hero-title" style={{ marginTop: "0.5rem" }}>
              {job.title}
            </h1>
          </div>
          <div style={{ display: "flex", gap: "0.5rem", alignItems: "flex-start" }}>
            <span className={`badge ${job.urgency}`}>{urgencyLabel(job.urgency)}</span>
            <span className={`badge ${job.status}`}>{statusLabel(job.status)}</span>
          </div>
        </div>

        <div className="stat-row" style={{ marginTop: "1rem" }}>
          <div className="stat">
            <span className="muted">Budget</span>
            <strong>{formatBrl(job.budgetCents)}</strong>
          </div>
          {job.companyName ? (
            <div className="stat">
              <span className="muted">Empresa</span>
              <strong>{job.companyName}</strong>
            </div>
          ) : null}
          {job.matchedProName ? (
            <div className="stat">
              <span className="muted">Profissional</span>
              <strong>{job.matchedProName}</strong>
            </div>
          ) : null}
        </div>

        {job.description ? (
          <p style={{ marginTop: "1rem", lineHeight: 1.6 }}>{job.description}</p>
        ) : null}

        {job.skillsNeeded.length > 0 ? (
          <p className="mono muted" style={{ fontSize: "0.85rem" }}>
            Skills: {job.skillsNeeded.join(" · ")}
          </p>
        ) : null}

        <div className="actions">
          {isPro && job.status === "open" && !job.myApplicationStatus ? (
            <span className="badge open">Aberto para candidatura</span>
          ) : null}
          {isPro && job.myApplicationStatus === "pending" ? (
            <span className="badge matched">Candidatura enviada</span>
          ) : null}
          {isCompany && job.status === "matched" ? (
            <button type="button" className="button" disabled={actionLoading} onClick={onCheckout}>
              {actionLoading ? "Processando..." : "Fundar escrow"}
            </button>
          ) : null}
          {isPro && isMatchedPro && job.status === "funded" ? (
            <button type="button" className="button" disabled={actionLoading} onClick={onStart}>
              {actionLoading ? "Iniciando..." : "Iniciar trabalho"}
            </button>
          ) : null}
          {isCompany && job.status === "in_progress" ? (
            <button type="button" className="button" disabled={actionLoading} onClick={onComplete}>
              {actionLoading ? "Concluindo..." : "Confirmar conclusão"}
            </button>
          ) : null}
        </div>

        {error ? <p className="error">{error}</p> : null}
      </section>

      {isPro && job.status === "open" && !job.myApplicationStatus ? (
        <section className="panel">
          <h2 className="hero-title" style={{ fontSize: "1.15rem" }}>
            Candidatar-se
          </h2>
          <form onSubmit={onApply}>
            <div className="field">
              <label htmlFor="pitch">Pitch</label>
              <textarea
                id="pitch"
                required
                value={pitch}
                onChange={(e) => setPitch(e.target.value)}
                placeholder="Por que você é ideal para este plantão..."
              />
            </div>
            <button className="button" type="submit" disabled={actionLoading}>
              {actionLoading ? "Enviando..." : "Enviar candidatura"}
            </button>
          </form>
        </section>
      ) : null}

      {isCompany && pendingApplications.length > 0 ? (
        <section className="panel">
          <h2 className="hero-title" style={{ fontSize: "1.15rem" }}>
            Candidaturas pendentes
          </h2>
          <div className="stack">
            {pendingApplications.map((app) => (
              <div key={app.id} className="message">
                <div className="message-meta">{app.proName ?? app.proAccountId}</div>
                <p style={{ margin: "0 0 0.75rem" }}>{app.pitch}</p>
                <button
                  type="button"
                  className="button"
                  disabled={actionLoading}
                  onClick={() => onAccept(app.id)}
                >
                  Aceitar
                </button>
              </div>
            ))}
          </div>
        </section>
      ) : null}

      {canMessage ? (
        <section className="panel">
          <h2 className="hero-title" style={{ fontSize: "1.15rem" }}>
            Mensagens
          </h2>
          <div className="messages">
            {messages.length === 0 ? (
              <p className="muted">Nenhuma mensagem ainda.</p>
            ) : (
              messages.map((msg) => (
                <div key={msg.id} className="message">
                  <div className="message-meta">
                    {msg.senderName ?? msg.senderAccountId} ·{" "}
                    {new Date(msg.createdAt).toLocaleString("pt-BR")}
                  </div>
                  <p style={{ margin: 0 }}>{msg.body}</p>
                </div>
              ))
            )}
          </div>
          <form onSubmit={onSendMessage} style={{ marginTop: "1rem" }}>
            <div className="field">
              <label htmlFor="message">Nova mensagem</label>
              <input
                id="message"
                value={messageBody}
                onChange={(e) => setMessageBody(e.target.value)}
                placeholder="Escreva uma mensagem..."
              />
            </div>
            <button className="button-secondary" type="submit" disabled={actionLoading}>
              Enviar
            </button>
          </form>
        </section>
      ) : null}

      {job.status === "completed" && !reviewSent ? (
        <section className="panel">
          <h2 className="hero-title" style={{ fontSize: "1.15rem" }}>
            Avaliar
          </h2>
          <form onSubmit={onReview}>
            <div className="field">
              <label htmlFor="rating">Nota (1–5)</label>
              <select
                id="rating"
                value={rating}
                onChange={(e) => setRating(Number(e.target.value))}
              >
                {[5, 4, 3, 2, 1].map((n) => (
                  <option key={n} value={n}>
                    {n}
                  </option>
                ))}
              </select>
            </div>
            <div className="field">
              <label htmlFor="comment">Comentário</label>
              <textarea
                id="comment"
                value={comment}
                onChange={(e) => setComment(e.target.value)}
                placeholder="Como foi a experiência?"
              />
            </div>
            <button className="button" type="submit" disabled={actionLoading}>
              Enviar avaliação
            </button>
          </form>
        </section>
      ) : null}

      {reviewSent ? (
        <section className="panel">
          <p className="muted">Avaliação enviada. Obrigado!</p>
        </section>
      ) : null}
    </AppShell>
  );
}
