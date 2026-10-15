import Keycloak from "keycloak-js";
import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from "react";
import type { ReactNode } from "react";

export interface AuthConfig {
  url: string;
  realm: string;
  clientId: string;
}

export interface AuthContextValue {
  subject: string;
  displayName: string;
  getAccessToken: () => Promise<string>;
  logout: () => void;
  expireSession: () => void;
}

export const AuthContext = createContext<AuthContextValue | null>(null);

export function useAuth(): AuthContextValue {
  const auth = useContext(AuthContext);
  if (!auth) throw new Error("AuthContext is missing");
  return auth;
}

export class AuthenticationRequiredError extends Error {
  constructor() {
    super("Your sign-in has expired. Sign in again to continue.");
    this.name = "AuthenticationRequiredError";
  }
}

function readConfig(value: unknown): AuthConfig {
  if (!value || typeof value !== "object") throw new Error("Sign-in configuration is invalid.");
  const config = value as Record<string, unknown>;
  if (typeof config.url !== "string" || typeof config.realm !== "string" || typeof config.clientId !== "string") {
    throw new Error("Sign-in configuration is incomplete.");
  }
  let url: URL;
  try {
    url = new URL(config.url);
  } catch {
    throw new Error("Sign-in URL is invalid.");
  }
  const localHttp = url.protocol === "http:" && (url.hostname === "localhost" || url.hostname === "127.0.0.1");
  if ((url.protocol !== "https:" && !localHttp) || !/^[A-Za-z0-9._-]+$/.test(config.realm) || !/^[A-Za-z0-9._-]+$/.test(config.clientId)) {
    throw new Error("Sign-in configuration contains invalid values.");
  }
  return { url: url.origin + url.pathname.replace(/\/$/, ""), realm: config.realm, clientId: config.clientId };
}

async function loadConfig(): Promise<AuthConfig> {
  let response: Response;
  try {
    response = await fetch("/auth-config.json", { cache: "no-store" });
  } catch {
    throw new Error("Could not load sign-in configuration. Check your connection and retry.");
  }
  if (!response.ok) throw new Error("Could not load sign-in configuration. Retry to continue.");
  try {
    return readConfig(await response.json());
  } catch (error) {
    if (error instanceof Error && error.message.startsWith("Sign-in configuration")) throw error;
    throw new Error("Could not read sign-in configuration. Retry to continue.");
  }
}

class AuthSession {
  readonly client: Keycloak;
  private initialization?: Promise<boolean>;

  constructor(config: AuthConfig) {
    this.client = new Keycloak(config);
  }

  initialize(): Promise<boolean> {
    this.initialization ??= this.client.init({
      onLoad: "check-sso",
      flow: "standard",
      pkceMethod: "S256",
      silentCheckSsoRedirectUri: `${window.location.origin}/silent-check-sso.html`,
      silentCheckSsoFallback: false,
    });
    return this.initialization;
  }
}

type Phase = "loading" | "anonymous" | "authenticated" | "error";

function AuthScreen({ phase, error, onLogin, onRetry }: {
  phase: Phase;
  error: string;
  onLogin: () => void;
  onRetry: () => void;
}) {
  return (
    <main className="auth-screen">
      <div className="auth-card">
        <img className="brand-icon" src="/brand/glorfeed-icon.png" alt="" />
        <p className="section-kicker">GLORFEED</p>
        <h1>{phase === "loading" ? "Checking your sign-in…" : phase === "error" ? "Sign-in is unavailable" : "Welcome to Glorfeed"}</h1>
        {phase === "loading" && <p role="status">Securely connecting to your workspace.</p>}
        {phase === "error" && <><p role="alert">{error}</p><button className="secondary-button" type="button" onClick={onRetry}>Retry</button></>}
        {phase === "anonymous" && <><p>Sign in to manage your topics and read your workspace.</p><button className="primary-button" type="button" onClick={onLogin}>Sign in</button></>}
      </div>
    </main>
  );
}

export function AuthGate({ children }: { children: ReactNode }) {
  const [attempt, setAttempt] = useState(0);
  const [session, setSession] = useState<AuthSession | null>(null);
  const [phase, setPhase] = useState<Phase>("loading");
  const [error, setError] = useState("");
  const [subject, setSubject] = useState("");
  const [displayName, setDisplayName] = useState("");
  const identity = useRef<{ session: AuthSession | null; subject: string; generation: number }>({ session: null, subject: "", generation: 0 });

  useEffect(() => {
    let active = true;
    setPhase("loading");
    setError("");
    void loadConfig().then((config) => {
      if (!active) return;
      const next = new AuthSession(config);
      identity.current = { session: next, subject: "", generation: identity.current.generation + 1 };
      setSession(next);
      next.client.onAuthLogout = () => {
        if (!active) return;
        identity.current = { session: next, subject: "", generation: identity.current.generation + 1 };
        setSubject("");
        setDisplayName("");
        setPhase("anonymous");
      };
      return next.initialize().then((authenticated) => {
        if (!active) return;
        if (!authenticated || !next.client.authenticated) {
          identity.current = { session: next, subject: "", generation: identity.current.generation + 1 };
          setSubject("");
          setDisplayName("");
          setPhase("anonymous");
          return;
        }
        const nextSubject = next.client.subject ?? "";
        if (!nextSubject.trim()) {
          next.client.clearToken();
          setError("Could not identify the signed-in account. Retry to continue.");
          setPhase("error");
          return;
        }
        identity.current = { session: next, subject: nextSubject, generation: identity.current.generation + 1 };
        setSubject(nextSubject);
        setDisplayName(next.client.tokenParsed?.preferred_username ?? next.client.tokenParsed?.name ?? "Your account");
        setPhase("authenticated");
      });
    }).catch((cause: unknown) => {
      if (!active) return;
      setError(cause instanceof Error ? cause.message : "Could not connect to sign-in. Retry to continue.");
      setPhase("error");
    });
    return () => { active = false; };
  }, [attempt]);

  const expireSession = useCallback(() => {
    if (identity.current.session !== session || identity.current.subject !== subject) return;
    identity.current = { session, subject: "", generation: identity.current.generation + 1 };
    setSubject("");
    setDisplayName("");
    setPhase("anonymous");
    session?.client.clearToken();
  }, [session, subject]);

  const getAccessToken = useCallback(async () => {
    if (!session || phase !== "authenticated") throw new AuthenticationRequiredError();
    const expected = identity.current;
    if (expected.session !== session || expected.subject !== subject) throw new AuthenticationRequiredError();
    try {
      await session.client.updateToken(30);
      if (identity.current.session !== session || identity.current.generation !== expected.generation) throw new AuthenticationRequiredError();
      const refreshedSubject = session.client.subject ?? "";
      if (refreshedSubject !== subject) {
        identity.current = { session, subject: refreshedSubject, generation: expected.generation + 1 };
        setSubject(refreshedSubject);
        setDisplayName(session.client.tokenParsed?.preferred_username ?? session.client.tokenParsed?.name ?? "Your account");
        if (!refreshedSubject.trim()) {
          session.client.clearToken();
          setError("Could not identify the signed-in account. Retry to continue.");
          setPhase("error");
        }
        throw new AuthenticationRequiredError();
      }
      if (!session.client.token) throw new AuthenticationRequiredError();
      return session.client.token;
    } catch {
      if (identity.current.session === session && identity.current.generation === expected.generation) expireSession();
      throw new AuthenticationRequiredError();
    }
  }, [expireSession, phase, session, subject]);

  const login = () => { void session?.client.login({ redirectUri: `${window.location.origin}/` }); };
  const logout = useCallback(() => {
    if (!session || identity.current.session !== session || identity.current.subject !== subject) return;
    const logoutUrl = session.client.createLogoutUrl({ redirectUri: `${window.location.origin}/` });
    expireSession();
    window.location.assign(logoutUrl);
  }, [expireSession, session, subject]);
  const contextValue = useMemo<AuthContextValue | null>(() => subject.trim() && session ? {
    subject,
    displayName,
    getAccessToken,
    logout,
    expireSession,
  } : null, [displayName, expireSession, getAccessToken, logout, session, subject]);

  if (phase !== "authenticated" || !contextValue) {
    return <AuthScreen phase={phase} error={error} onLogin={login} onRetry={() => setAttempt((value) => value + 1)} />;
  }

  return <AuthContext.Provider value={contextValue}><div key={subject} className="auth-app">{children}</div></AuthContext.Provider>;
}
