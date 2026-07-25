"use client";

import Link from "next/link";
import { FormEvent, useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { AppShell } from "@/components/AppShell";
import {
  createJob,
  formatBrl,
  listJobs,
  statusLabel,
  urgencyLabel,
  type JobSummary,
  type JobUrgency,
} from "@/lib/api";
import { loadSession } from "@/lib/session";

export default function CompanyPage() {
  const router = useRouter();
  const [jobs, setJobs] = useState<JobSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [creating, setCreating] = useState(false);

  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [urgency, setUrgency] = useState<JobUrgency>("p2");
  const [budgetCents, setBudgetCents] = useState("");
  const [skillsNeeded, setSkillsNeeded] = useState("");

  const refresh = useCallback(async () => {
    const session = loadSession();
    if (!session) {
      router.replace("/");
      return;
    }
    if (session.role !== "company") {
      router.replace("/app");
      return;
    }
    try {
      const data = await listJobs(session.sessionToken, "mine");
      setJobs(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : "falha_lista");
    } finally {
      setLoading(false);
    }
  }, [router]);

  useEffect(() => {
    refresh();
  }, [refresh]);

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    const session = loadSession();
    if (!session) {
      return;
    }
    setCreating(true);
    setError(null);
    try {
      const budget = Math.round(parseFloat(budgetCents.replace(",", ".")) * 100);
      if (Number.isNaN(budget) || budget < 20000) {
        throw new Error("budget_minimo_200");
      }
      const skills = skillsNeeded
        .split(",")
        .map((s) => s.trim())
        .filter(Boolean);
      await createJob(session.sessionToken, {
        title: title.trim(),
        description: description.trim(),
        urgency,
        budgetCents: budget,
        skillsNeeded: skills,
      });
      setTitle("");
      setDescription("");
      setBudgetCents("");
      setSkillsNeeded("");
      await refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : "falha_criar");
    } finally {
      setCreating(false);
    }
  }

  return (
    <AppShell>
      <section className="panel">
        <h1 className="hero-title">Publicar job</h1>
        <p className="muted">Crie um plantão urgente para profissionais vetted.</p>
        <form onSubmit={onSubmit} style={{ marginTop: "1rem" }}>
          <div className="field">
            <label htmlFor="title">Título</label>
            <input
              id="title"
              required
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              placeholder="Ex: Backend sênior para incidente em produção"
            />
          </div>
          <div className="field">
            <label htmlFor="description">Descrição</label>
            <textarea
              id="description"
              required
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="Contexto, stack, prazo e expectativa..."
            />
          </div>
          <div className="grid-2">
            <div className="field">
              <label htmlFor="urgency">Urgência</label>
              <select
                id="urgency"
                value={urgency}
                onChange={(e) => setUrgency(e.target.value as JobUrgency)}
              >
                <option value="p1">P1 — crítico</option>
                <option value="p2">P2 — urgente</option>
                <option value="p3">P3 — normal</option>
              </select>
            </div>
            <div className="field">
              <label htmlFor="budget">Budget (R$)</label>
              <input
                id="budget"
                required
                value={budgetCents}
                onChange={(e) => setBudgetCents(e.target.value)}
                placeholder="500.00"
              />
            </div>
          </div>
          <div className="field">
            <label htmlFor="skills">Skills (separadas por vírgula)</label>
            <input
              id="skills"
              value={skillsNeeded}
              onChange={(e) => setSkillsNeeded(e.target.value)}
              placeholder="java, spring, postgres"
            />
          </div>
          <button className="button" type="submit" disabled={creating}>
            {creating ? "Publicando..." : "Publicar job"}
          </button>
          {error ? <p className="error">{error}</p> : null}
        </form>
      </section>

      <section className="panel">
        <h2 className="hero-title" style={{ fontSize: "1.25rem" }}>
          Meus jobs
        </h2>
        {loading ? (
          <p className="muted">Carregando...</p>
        ) : jobs.length === 0 ? (
          <div className="empty-state">Nenhum job publicado ainda.</div>
        ) : (
          <div className="stack">
            {jobs.map((job) => (
              <Link key={job.id} href={`/app/jobs/${job.id}`} className="job-row">
                <div>
                  <h3>{job.title}</h3>
                  <p className="muted" style={{ margin: 0 }}>
                    {formatBrl(job.budgetCents)} · {urgencyLabel(job.urgency)}
                  </p>
                </div>
                <span className={`badge ${job.status}`}>{statusLabel(job.status)}</span>
              </Link>
            ))}
          </div>
        )}
      </section>
    </AppShell>
  );
}
