import { AuthenticationRequiredError } from "./auth";

export interface Topic {
  id: string;
  name: string;
  description: string;
  createdAt: string;
}

export interface TopicInput {
  name: string;
  description: string;
}

export type AccessTokenProvider = () => Promise<string>;

async function request<T>(path: string, getAccessToken: AccessTokenProvider, init?: RequestInit): Promise<T> {
  let token: string;
  try {
    token = await getAccessToken();
  } catch {
    throw new AuthenticationRequiredError();
  }
  const headers = new Headers(init?.headers);
  headers.set("Authorization", `Bearer ${token}`);
  let response: Response;
  try {
    response = await fetch(path, { ...init, headers });
  } catch {
    throw new Error("Could not reach the topic service. Check your connection and try again.");
  }

  if (response.status === 401) throw new AuthenticationRequiredError();

  if (!response.ok) {
    const message = response.status === 400
      ? "Check that the topic name and description are valid."
      : response.status === 404
        ? "That topic no longer exists. Refresh the list and try again."
        : `The topic service returned an error (HTTP ${response.status}). Please try again.`;
    throw new Error(message);
  }

  if (response.status === 204) return undefined as T;

  try {
    return await response.json() as T;
  } catch {
    throw new Error("The topic service returned an unreadable response. Please try again.");
  }
}

export function getTopics(getAccessToken: AccessTokenProvider): Promise<Topic[]> {
  return request<Topic[]>("/api/topics", getAccessToken);
}

export function createTopic(input: TopicInput, getAccessToken: AccessTokenProvider): Promise<Topic> {
  return request<Topic>("/api/topics", getAccessToken, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(input),
  });
}

export function updateTopic(id: string, input: TopicInput, getAccessToken: AccessTokenProvider): Promise<Topic> {
  return request<Topic>(`/api/topics/${encodeURIComponent(id)}`, getAccessToken, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(input),
  });
}

export function deleteTopic(id: string, getAccessToken: AccessTokenProvider): Promise<void> {
  return request<void>(`/api/topics/${encodeURIComponent(id)}`, getAccessToken, { method: "DELETE" });
}
