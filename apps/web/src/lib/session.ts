export type UserRole = "company" | "pro";

export type AccountSession = {
  accountId: string;
  email: string;
  name?: string;
  role?: UserRole | null;
  sessionToken: string;
};

const KEY = "warroom.session";

export function loadSession(): AccountSession | null {
  if (typeof window === "undefined") {
    return null;
  }
  const raw = window.localStorage.getItem(KEY);
  if (!raw) {
    return null;
  }
  try {
    const parsed = JSON.parse(raw) as AccountSession;
    if (!parsed.sessionToken) {
      window.localStorage.removeItem(KEY);
      return null;
    }
    return parsed;
  } catch {
    return null;
  }
}

export function saveSession(session: AccountSession): void {
  window.localStorage.setItem(KEY, JSON.stringify(session));
}

export function clearSession(): void {
  window.localStorage.removeItem(KEY);
}

export function requireSession(): AccountSession {
  const session = loadSession();
  if (!session) {
    throw new Error("session_required");
  }
  return session;
}
