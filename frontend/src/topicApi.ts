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

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  let response: Response;
  try {
    response = await fetch(path, init);
  } catch {
    throw new Error("Could not reach the topic service. Check your connection and try again.");
  }

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

export function getTopics(): Promise<Topic[]> {
  return request<Topic[]>("/api/topics");
}

export function createTopic(input: TopicInput): Promise<Topic> {
  return request<Topic>("/api/topics", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(input),
  });
}

export function updateTopic(id: string, input: TopicInput): Promise<Topic> {
  return request<Topic>(`/api/topics/${encodeURIComponent(id)}`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(input),
  });
}

export function deleteTopic(id: string): Promise<void> {
  return request<void>(`/api/topics/${encodeURIComponent(id)}`, { method: "DELETE" });
}
