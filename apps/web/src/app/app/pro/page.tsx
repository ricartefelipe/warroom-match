"use client";

import Link from "next/link";
import { FormEvent, useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { AppShell } from "@/components/AppShell";
import {
  formatBrl,
  getProfile,
  listJobs,
  statusLabel,
  submitVetting,
  urgencyLabel,
  vettingLabel,
  type JobSummary,
  type Profile,
} from "@/lib/api";
import { loadSession } from "@/lib/session";

export default function ProPage() {
  const router = useRouter();
  const [openJobs, setOpenJobs] = useState<JobSummary[]>([]);
  const [myJobs, setMyJobs] = useState<JobSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [profile, setProfile] = useState<Profile | null>(null);
  const [evidence, setEvidence] = useState("");
  const [sending, setSending] = useState(false);

  const refresh = useCallback(async () => {
    const session = loadSession();
    if (!session) {
      router.replace("/");
      return;
    }
    if (session.role !== "pro") {
      router.replace("/app");
      return;
    }
    try {
      const [open, mine, me] = await Promise.all([
        listJobs(session.sessionToken, "open"),
        listJobs(session.sessionToken, "mine"),
        getProfile(session.sessionToken),
      ]);
      setProfile(me);
      setOpenJobs(open);
      setMyJobs(mine.filter((j) => j.myApplicationStatus != null));
    } catch (err) {
      setError(err instanceof Error ? err.message : "falha_lista");
    } finally {
      setLoading(false);
    }
  }, [router]);

  useEffect(() => {
    refresh();
  }, [refresh]);

  async function onSubmitVetting(event: FormEvent) {
    event.preventDefault();
    const session = loadSession();
    if (!session || !evidence.trim()) {
      return;
    }
    setSending(true);
    setError(null);
    try {
      await submitVetting(session.sessionToken, evidence.trim());
      setEvidence("");
      await refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : "falha_vetting");
    } finally {
      setSending(false);
    }
  }

  const approved = profile?.vettingStatus === "approved";

  function applicationLabel(status: JobSummary["myApplicationStatus"]): string {
    switch (status) {
      case "pending":
        return "candidatura pendente";
      case "accepted":
        return "aceito";
      case "rejected":
        return "recusado";
      case "cancelled":
        return "cancelado";
      case null:
      case undefined:
        return "";
      default: {
        const _exhaustive: never = status;
        return _exhaustive;
      }
    }
  }

  return (
    <AppShell>
      <VettingPanel
        profile={profile}
        evidence={evidence}
        sending={sending}
        onEvidenceChange={setEvidence}
        onSubmit={onSubmitVetting}
      />

      <section className="panel">
        <h1 className="hero-title">Feed de plantões</h1>
        <p className="muted">
          {approved ? "Jobs abertos disponíveis para candidatura." : "Você verá os jobs, mas só poderá se candidatar após a aprovação."}
        </p>
        {loading ? (
          <p className="muted" style={{ marginTop: "1rem" }}>
            Carregando...
          </p>
        ) : error ? (
          <p className="error">{error}</p>
        ) : openJobs.length === 0 ? (
          <div className="empty-state">Nenhum job aberto no momento.</div>
        ) : (
          <div className="stack" style={{ marginTop: "1rem" }}>
            {openJobs.map((job) => (
              <Link key={job.id} href={`/app/jobs/${job.id}`} className="job-row">
                <div>
                  <h3>{job.title}</h3>
                  <p className="muted" style={{ margin: 0 }}>
                    {formatBrl(job.budgetCents)} · {urgencyLabel(job.urgency)}
                    {job.companyName ? ` · ${job.companyName}` : ""}
                  </p>
                  {job.skillsNeeded.length > 0 ? (
                    <p className="mono muted" style={{ margin: "0.35rem 0 0", fontSize: "0.8rem" }}>
                      {job.skillsNeeded.join(" · ")}
                    </p>
                  ) : null}
                </div>
                <span className={`badge ${job.urgency}`}>{job.urgency.toUpperCase()}</span>
              </Link>
            ))}
          </div>
        )}
      </section>

      <section className="panel">
        <h2 className="hero-title" style={{ fontSize: "1.25rem" }}>
          Minhas candidaturas
        </h2>
        {loading ? (
          <p className="muted">Carregando...</p>
        ) : myJobs.length === 0 ? (
          <div className="empty-state">Você ainda não se candidatou a nenhum job.</div>
        ) : (
          <div className="stack">
            {myJobs.map((job) => (
              <Link key={job.id} href={`/app/jobs/${job.id}`} className="job-row">
                <div>
                  <h3>{job.title}</h3>
                  <p className="muted" style={{ margin: 0 }}>
                    {formatBrl(job.budgetCents)} · {statusLabel(job.status)}
                  </p>
                </div>
                <span className="badge open">
                  {applicationLabel(job.myApplicationStatus)}
                </span>
              </Link>
            ))}
          </div>
        )}
      </section>
    </AppShell>
  );
}

type VettingPanelProps = Readonly<{
  profile: Profile | null;
  evidence: string;
  sending: boolean;
  onEvidenceChange: (value: string) => void;
  onSubmit: (event: FormEvent) => void;
}>;

function VettingPanel({ profile, evidence, sending, onEvidenceChange, onSubmit }: VettingPanelProps) {
  const status = profile?.vettingStatus;
  const approved = status === "approved";
  const canSubmit = !approved && status !== "suspended";
  let submitLabel = "Enviar para análise";
  if (sending) {
    submitLabel = "Enviando...";
  } else if (status === "submitted") {
    submitLabel = "Reenviar evidências";
  }

  return (
    <section className="panel">
      <h1 className="hero-title">Verificação profissional</h1>
      <p className="muted">
        Status: <strong>{vettingLabel(status)}</strong>
        {profile?.vettingNote ? ` · ${profile.vettingNote}` : ""}
      </p>
      {approved ? null : (
        <p className="muted">
          Só profissionais aprovados pelo suporte podem se candidatar. Envie links públicos, referências e
          um resumo da experiência. Não envie documentos de identidade por aqui.
        </p>
      )}
      {canSubmit ? (
        <form onSubmit={onSubmit}>
          <div className="field">
            <label htmlFor="evidence">Evidências</label>
            <textarea
              id="evidence"
              value={evidence}
              onChange={(e) => onEvidenceChange(e.target.value)}
              placeholder="github.com/..., linkedin.com/in/..., referências e resumo"
            />
          </div>
          <button className="button" type="submit" disabled={sending || !evidence.trim()}>
            {submitLabel}
          </button>
        </form>
      ) : null}
    </section>
  );
}
