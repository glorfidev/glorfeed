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

export interface ResearchCitation {
  title: string;
  url: string;
}

export interface TopicResearchResult {
  topicId: string;
  responseId: string;
  model: string;
  text: string;
  citations: ResearchCitation[];
  sources: ResearchCitation[];
  inputTokens: number;
  outputTokens: number;
  webSearchCalls: number;
}

export type AccessTokenProvider = () => Promise<string>;

async function request<T>(
  path: string,
  getAccessToken: AccessTokenProvider,
  init?: RequestInit,
  options: {
    statusMessages?: Record<number, string>;
    networkError?: string;
    unexpectedStatusError?: string;
    unreadableResponseError?: string;
  } = {},
): Promise<T> {
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
    throw new Error(options.networkError ?? "Could not reach the topic service. Check your connection and try again.");
  }

  if (response.status === 401) throw new AuthenticationRequiredError();

  if (!response.ok) {
    const message = options.statusMessages?.[response.status] ?? (response.status === 400
      ? "Check that the topic name and description are valid."
      : response.status === 404
        ? "That topic no longer exists. Refresh the list and try again."
        : options.unexpectedStatusError ?? `The topic service returned an error (HTTP ${response.status}). Please try again.`);
    throw new Error(message);
  }

  if (response.status === 204) return undefined as T;

  try {
    return await response.json() as T;
  } catch {
    throw new Error(options.unreadableResponseError ?? "The topic service returned an unreadable response. Please try again.");
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

export function researchTopic(id: string, getAccessToken: AccessTokenProvider): Promise<TopicResearchResult> {
  return request<TopicResearchResult>(
    `/api/topics/${encodeURIComponent(id)}/research`,
    getAccessToken,
    { method: "POST" },
    {
      statusMessages: {
        404: "This topic is no longer available. Refresh your topics and try again.",
        409: "Another research run is active or this topic is not ready yet. Wait and check before trying again.",
        422: "The server's OpenAI key or provider configuration was rejected. Contact the service administrator.",
        502: "The research provider failed after the request started. The result may be unknown; check before running again.",
        503: "Manual research is currently disabled on this service.",
        504: "The research request timed out and its outcome may be unknown. Check before running it again.",
      },
      networkError: "The research request could not be confirmed. The provider may have received it; check before running again.",
      unexpectedStatusError: "The research service returned an unexpected response. The provider may have processed the request; check before running it again.",
      unreadableResponseError: "The research service returned an unreadable result. The provider may have processed the request; check before running it again.",
    },
  );
}
