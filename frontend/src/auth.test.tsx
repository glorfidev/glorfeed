import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { StrictMode, useState } from "react";
import { AuthenticationRequiredError, AuthGate, useAuth } from "./auth";

const { instances, authState } = vi.hoisted(() => ({
  instances: [] as Array<Record<string, any>>,
  authState: { authenticated: false, subject: "user-1", token: "access-one", changedSubject: null as null | string, updateWait: null as null | Promise<void>, rejectUpdate: false },
}));

vi.mock("keycloak-js", () => ({
  default: class {
    authenticated = false;
    subject: string | undefined;
    token: string | undefined;
    tokenParsed: Record<string, string> | undefined;
    init = vi.fn(async (options: Record<string, unknown>) => {
      this.options = options;
      this.authenticated = authState.authenticated;
      this.subject = authState.subject;
      this.token = authState.token;
      this.tokenParsed = { preferred_username: this.subject };
      return this.authenticated;
    });
    updateToken = vi.fn(async () => {
      if (authState.updateWait) await authState.updateWait;
      if (authState.rejectUpdate) throw new Error("refresh failed");
      if (authState.changedSubject !== null) {
        this.subject = authState.changedSubject;
        this.token = "access-new-user";
        this.tokenParsed = { preferred_username: authState.changedSubject };
        authState.changedSubject = null;
      }
      return true;
    });
    login = vi.fn();
    logout = vi.fn();
    createLogoutUrl = vi.fn(({ redirectUri }: { redirectUri: string }) => `https://auth.example.test/logout?redirect_uri=${encodeURIComponent(redirectUri)}&id_token_hint=preserved`);
    clearToken = vi.fn();
    onAuthLogout?: () => void;
    options?: Record<string, unknown>;
    constructor(config: unknown) {
      this.config = config;
      instances.push(this);
    }
    config?: unknown;
  },
}));

function configResponse() {
  return { ok: true, json: async () => ({ url: "https://auth.example.test", realm: "glorfeed", clientId: "glorfeed-frontend" }) } as Response;
}

beforeEach(() => {
  instances.length = 0;
  authState.authenticated = false;
  authState.subject = "user-1";
  authState.token = "access-one";
  authState.changedSubject = null;
  authState.updateWait = null;
  authState.rejectUpdate = false;
  vi.stubGlobal("fetch", vi.fn().mockResolvedValue(configResponse()));
});

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});

describe("authentication gate", () => {
  it("initializes once in StrictMode with code flow and PKCE before rendering app", async () => {
    render(<StrictMode><AuthGate><p>Private workspace</p></AuthGate></StrictMode>);
    expect(await screen.findByRole("button", { name: "Sign in" })).toBeInTheDocument();
    expect(screen.queryByText("Private workspace")).not.toBeInTheDocument();
    expect(instances).toHaveLength(1);
    expect(instances[0].init).toHaveBeenCalledOnce();
    expect(instances[0].options).toMatchObject({ flow: "standard", pkceMethod: "S256", onLoad: "check-sso" });
  });

  it("shows a safe retryable error when public runtime config cannot be loaded", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue({ ok: false, status: 503 }));
    render(<AuthGate><p>Private workspace</p></AuthGate>);
    expect(await screen.findByRole("alert")).toHaveTextContent("Could not load sign-in configuration");
    expect(screen.getByRole("button", { name: "Retry" })).toBeInTheDocument();
    expect(screen.queryByText("Private workspace")).not.toBeInTheDocument();
  });

  it("does not mount the topic app or issue topic requests while signed out", async () => {
    const fetchMock = vi.fn().mockResolvedValue(configResponse());
    vi.stubGlobal("fetch", fetchMock);
    render(<AuthGate><p>Private topics</p></AuthGate>);
    expect(await screen.findByRole("button", { name: "Sign in" })).toBeInTheDocument();
    expect(screen.queryByText("Private topics")).not.toBeInTheDocument();
    expect(fetchMock.mock.calls.map(([url]) => url)).toEqual(["/auth-config.json"]);
  });

  it("refreshes on demand and discards a refreshed token when the account changes", async () => {
    authState.authenticated = true;
    let result = "";
    function SessionProbe() {
      const { subject, getAccessToken } = requireAuth();
      return <><span>{subject}</span><button type="button" onClick={() => void getAccessToken().then((token) => { result = `token:${token}`; }).catch(() => { result = "refresh rejected"; })}>Refresh token</button></>;
    }
    render(<AuthGate><SessionProbe /></AuthGate>);
    expect(await screen.findByText("user-1")).toBeInTheDocument();
    authState.changedSubject = "user-2";
    screen.getByRole("button", { name: "Refresh token" }).click();
    expect(await screen.findByText("user-2")).toBeInTheDocument();
    await vi.waitFor(() => expect(result).toBe("refresh rejected"));
    expect(result).not.toBe("token:access-new-user");
    expect(instances[0].updateToken).toHaveBeenCalledWith(30);
  });

  it("discards an in-flight refresh after the session logs out", async () => {
    authState.authenticated = true;
    let resolveUpdate!: () => void;
    authState.updateWait = new Promise<void>((resolve) => { resolveUpdate = resolve; });
    let refreshPromise: Promise<string> | undefined;
    function SessionProbe() {
      const { subject, getAccessToken } = requireAuth();
      const [result, setResult] = useState("");
      return <><span>{subject}</span><span>{result}</span><button type="button" onClick={() => { refreshPromise = getAccessToken(); void refreshPromise.then((token) => setResult(`token:${token}`)).catch(() => setResult("refresh rejected")); }}>Refresh token</button></>;
    }
    render(<AuthGate><SessionProbe /></AuthGate>);
    expect(await screen.findByText("user-1")).toBeInTheDocument();
    screen.getByRole("button", { name: "Refresh token" }).click();
    instances[0].onAuthLogout();
    resolveUpdate();
    expect(await screen.findByRole("button", { name: "Sign in" })).toBeInTheDocument();
    await expect(refreshPromise).rejects.toBeInstanceOf(AuthenticationRequiredError);
    expect(screen.queryByText("token:access-one")).not.toBeInTheDocument();
  });

  it("returns to the sign-in gate when Keycloak cannot refresh the session", async () => {
    authState.authenticated = true;
    authState.rejectUpdate = true;
    function SessionProbe() {
      const { getAccessToken } = requireAuth();
      return <button type="button" onClick={() => void getAccessToken().catch(() => undefined)}>Load topics</button>;
    }
    render(<AuthGate><SessionProbe /></AuthGate>);
    fireEvent.click(await screen.findByRole("button", { name: "Load topics" }));
    expect(await screen.findByRole("button", { name: "Sign in" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Load topics" })).not.toBeInTheDocument();
  });

  it("offers recovery when Keycloak authenticates without an account subject", async () => {
    authState.authenticated = true;
    authState.subject = "   ";
    render(<AuthGate><p>Private workspace</p></AuthGate>);
    expect(await screen.findByRole("alert")).toHaveTextContent("Could not identify the signed-in account");
    expect(screen.getByRole("button", { name: "Retry" })).toBeInTheDocument();
    expect(screen.queryByText("Private workspace")).not.toBeInTheDocument();
    expect(instances[0].clearToken).toHaveBeenCalledOnce();
  });

  it("clears adapter tokens and offers recovery if a refresh has no subject", async () => {
    authState.authenticated = true;
    authState.changedSubject = "   ";
    function SessionProbe() {
      const { getAccessToken } = requireAuth();
      return <button type="button" onClick={() => void getAccessToken().catch(() => undefined)}>Refresh token</button>;
    }
    render(<AuthGate><SessionProbe /></AuthGate>);
    fireEvent.click(await screen.findByRole("button", { name: "Refresh token" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Could not identify the signed-in account");
    expect(instances[0].clearToken).toHaveBeenCalledOnce();
  });
});

function requireAuth() {
  // A local hook import keeps the mocked Keycloak module initialized before the provider is rendered.
  return useAuth();
}
