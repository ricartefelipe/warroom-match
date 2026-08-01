import type { AccountSession, UserRole } from "@/lib/session";

export function resolveApiBase(): string {
  const configured = process.env.NEXT_PUBLIC_API_BASE_URL;
  if (configured === "same-origin" || configured === "") {
    if (typeof window !== "undefined") {
      return window.location.origin;
    }
    return "";
  }
  if (configured == null) {
    return "http://localhost:8080";
  }
  return configured.replace(/\/$/, "");
}

export type JobUrgency = "p1" | "p2" | "p3";

export type JobStatus =
  | "draft"
  | "open"
  | "matched"
  | "funded"
  | "in_progress"
  | "completed"
  | "cancelled"
  | "disputed";

export type ApplicationStatus = "pending" | "accepted" | "rejected" | "cancelled";

export type Profile = {
  accountId: string;
  role?: UserRole | null;
  displayName?: string | null;
  bio?: string | null;
  skills?: string[];
  seniority?: string | null;
  availability?: "available" | "busy" | "offline";
  hourlyRateCents?: number | null;
};

export type Application = {
  id: string;
  jobId: string;
  proAccountId: string;
  proName?: string | null;
  pitch?: string | null;
  status: ApplicationStatus;
  createdAt: string;
};

export type JobSummary = {
  id: string;
  title: string;
  description?: string;
  urgency: JobUrgency;
  budgetCents: number;
  skillsNeeded: string[];
  status: JobStatus;
  companyAccountId?: string;
  companyName?: string | null;
  matchedProAccountId?: string | null;
  matchedProName?: string | null;
  myApplicationStatus?: ApplicationStatus | null;
  createdAt: string;
  updatedAt?: string;
};

export type JobDetail = JobSummary & {
  applications?: Application[];
};

export type Message = {
  id: string;
  jobId: string;
  senderAccountId: string;
  senderName?: string | null;
  body: string;
  createdAt: string;
};

export type Review = {
  id: string;
  jobId: string;
  reviewerAccountId: string;
  rating: number;
  comment?: string | null;
  createdAt: string;
};

function authHeaders(sessionToken: string): HeadersInit {
  return {
    "Content-Type": "application/json",
    Authorization: `Bearer ${sessionToken}`,
  };
}

async function parse<T>(response: Response): Promise<T> {
  const text = await response.text();
  const data = text ? JSON.parse(text) : {};
  if (!response.ok) {
    const error = typeof data.error === "string" ? data.error : "request_failed";
    throw new Error(error);
  }
  return data as T;
}

export async function requestMagicLink(
  email: string,
  name: string
): Promise<{ sent: boolean; email: string; magicLink?: string }> {
  const response = await fetch(`${resolveApiBase()}/v1/auth/magic-link`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email, name }),
  });
  return parse(response);
}

export async function loginWithPassword(email: string, password: string): Promise<AccountSession> {
  const response = await fetch(`${resolveApiBase()}/v1/auth/password`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email, password }),
  });
  return parse<AccountSession>(response);
}

export async function verifyMagicLink(token: string): Promise<AccountSession> {
  const response = await fetch(`${resolveApiBase()}/v1/auth/verify`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ token }),
  });
  return parse<AccountSession>(response);
}


export async function getMe(sessionToken: string): Promise<AccountSession> {
  const response = await fetch(`${resolveApiBase()}/v1/auth/me`, {
    headers: authHeaders(sessionToken),
    cache: "no-store",
  });
  return parse<AccountSession>(response);
}

export async function setRole(sessionToken: string, role: UserRole): Promise<AccountSession> {
  const response = await fetch(`${resolveApiBase()}/v1/me/role`, {
    method: "POST",
    headers: authHeaders(sessionToken),
    body: JSON.stringify({ role }),
  });
  return parse<AccountSession>(response);
}

export async function getProfile(sessionToken: string): Promise<Profile> {
  const response = await fetch(`${resolveApiBase()}/v1/me/profile`, {
    headers: authHeaders(sessionToken),
    cache: "no-store",
  });
  return parse<Profile>(response);
}

export async function updateProfile(
  sessionToken: string,
  profile: Partial<Profile>
): Promise<Profile> {
  const response = await fetch(`${resolveApiBase()}/v1/me/profile`, {
    method: "PUT",
    headers: authHeaders(sessionToken),
    body: JSON.stringify(profile),
  });
  return parse<Profile>(response);
}

export async function createJob(
  sessionToken: string,
  payload: {
    title: string;
    description: string;
    urgency: JobUrgency;
    budgetCents: number;
    skillsNeeded: string[];
  }
): Promise<JobSummary> {
  const response = await fetch(`${resolveApiBase()}/v1/jobs`, {
    method: "POST",
    headers: authHeaders(sessionToken),
    body: JSON.stringify(payload),
  });
  return parse<JobSummary>(response);
}

export async function listJobs(
  sessionToken: string,
  scope: "mine" | "open"
): Promise<JobSummary[]> {
  const response = await fetch(`${resolveApiBase()}/v1/jobs?scope=${scope}`, {
    headers: authHeaders(sessionToken),
    cache: "no-store",
  });
  return parse<JobSummary[]>(response);
}

export async function getJob(sessionToken: string, id: string): Promise<JobDetail> {
  const response = await fetch(`${resolveApiBase()}/v1/jobs/${id}`, {
    headers: authHeaders(sessionToken),
    cache: "no-store",
  });
  return parse<JobDetail>(response);
}

export async function applyToJob(
  sessionToken: string,
  jobId: string,
  pitch: string
): Promise<Application> {
  const response = await fetch(`${resolveApiBase()}/v1/jobs/${jobId}/apply`, {
    method: "POST",
    headers: authHeaders(sessionToken),
    body: JSON.stringify({ pitch }),
  });
  return parse<Application>(response);
}

export async function acceptApplication(
  sessionToken: string,
  jobId: string,
  applicationId: string
): Promise<JobDetail> {
  const response = await fetch(
    `${resolveApiBase()}/v1/jobs/${jobId}/accept/${applicationId}`,
    {
      method: "POST",
      headers: authHeaders(sessionToken),
    }
  );
  return parse<JobDetail>(response);
}

export async function checkoutJob(
  sessionToken: string,
  jobId: string
): Promise<{ url?: string; status?: string }> {
  const response = await fetch(`${resolveApiBase()}/v1/jobs/${jobId}/checkout`, {
    method: "POST",
    headers: authHeaders(sessionToken),
  });
  return parse<{ url?: string; status?: string }>(response);
}

export async function startJob(sessionToken: string, jobId: string): Promise<JobDetail> {
  const response = await fetch(`${resolveApiBase()}/v1/jobs/${jobId}/start`, {
    method: "POST",
    headers: authHeaders(sessionToken),
  });
  return parse<JobDetail>(response);
}

export async function completeJob(sessionToken: string, jobId: string): Promise<JobDetail> {
  const response = await fetch(`${resolveApiBase()}/v1/jobs/${jobId}/complete`, {
    method: "POST",
    headers: authHeaders(sessionToken),
  });
  return parse<JobDetail>(response);
}

export async function listMessages(sessionToken: string, jobId: string): Promise<Message[]> {
  const response = await fetch(`${resolveApiBase()}/v1/jobs/${jobId}/messages`, {
    headers: authHeaders(sessionToken),
    cache: "no-store",
  });
  return parse<Message[]>(response);
}

export async function postMessage(
  sessionToken: string,
  jobId: string,
  body: string
): Promise<Message> {
  const response = await fetch(`${resolveApiBase()}/v1/jobs/${jobId}/messages`, {
    method: "POST",
    headers: authHeaders(sessionToken),
    body: JSON.stringify({ body }),
  });
  return parse<Message>(response);
}

export async function submitReview(
  sessionToken: string,
  jobId: string,
  rating: number,
  comment: string
): Promise<Review> {
  const response = await fetch(`${resolveApiBase()}/v1/jobs/${jobId}/reviews`, {
    method: "POST",
    headers: authHeaders(sessionToken),
    body: JSON.stringify({ rating, comment }),
  });
  return parse<Review>(response);
}

export function formatBrl(cents: number): string {
  return new Intl.NumberFormat("pt-BR", {
    style: "currency",
    currency: "BRL",
  }).format(cents / 100);
}

export function urgencyLabel(urgency: JobUrgency): string {
  switch (urgency) {
    case "p1":
      return "P1 — crítico";
    case "p2":
      return "P2 — urgente";
    case "p3":
      return "P3 — normal";
    default: {
      const _exhaustive: never = urgency;
      return _exhaustive;
    }
  }
}

export function statusLabel(status: JobStatus): string {
  switch (status) {
    case "draft":
      return "rascunho";
    case "open":
      return "aberto";
    case "matched":
      return "match";
    case "funded":
      return "fundado";
    case "in_progress":
      return "em andamento";
    case "completed":
      return "concluído";
    case "cancelled":
      return "cancelado";
    case "disputed":
      return "disputa";
    default: {
      const _exhaustive: never = status;
      return _exhaustive;
    }
  }
}
