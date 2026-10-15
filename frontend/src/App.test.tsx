import { afterEach, beforeAll, describe, expect, it, vi } from "vitest";
import { act, cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import App from "./App";
import type { Topic } from "./topicApi";

const existingTopic: Topic = {
  id: "topic-1",
  name: "Climate policy",
  description: "Follow policy research.",
  createdAt: "2026-10-01T12:00:00Z",
};

const addedTopic: Topic = {
  id: "topic-2",
  name: "Urban forests",
  description: "Research on urban canopy.",
  createdAt: "2026-10-15T12:00:00Z",
};

function response(body: unknown, status = 200) {
  return { ok: status >= 200 && status < 300, status, json: async () => body } as Response;
}

function noContent() {
  return { ok: true, status: 204, json: async () => { throw new Error("204 has no body"); } } as unknown as Response;
}

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((resolvePromise) => { resolve = resolvePromise; });
  return { promise, resolve };
}

function openTopics() {
  fireEvent.click(screen.getByRole("button", { name: /^Topics/ }));
}

function openAddDialog() {
  fireEvent.click(screen.getByRole("button", { name: "Add topic" }));
}

function enterTopic(name: string, description: string) {
  fireEvent.change(screen.getByLabelText("Topic name"), { target: { value: name } });
  fireEvent.change(screen.getByLabelText("Description"), { target: { value: description } });
}

function submitTopic(buttonName: string) {
  fireEvent.click(within(screen.getByRole("dialog")).getByRole("button", { name: buttonName }));
}

beforeAll(() => {
  if (!HTMLDialogElement.prototype.showModal) {
    HTMLDialogElement.prototype.showModal = function showModal() { this.setAttribute("open", ""); };
  }
  if (!HTMLDialogElement.prototype.close) {
    HTMLDialogElement.prototype.close = function close() {
      this.removeAttribute("open");
      this.dispatchEvent(new Event("close"));
    };
  }
});

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
  window.history.replaceState(null, "", window.location.pathname);
});

describe("live topic management", () => {
  it("shows the initial load failure and retries the list", async () => {
    const fetchMock = vi.fn()
      .mockRejectedValueOnce(new TypeError("offline"))
      .mockResolvedValueOnce(response([existingTopic]));
    vi.stubGlobal("fetch", fetchMock);

    render(<App />);
    openTopics();
    expect(await screen.findByRole("alert")).toHaveTextContent("Could not reach the topic service");
    fireEvent.click(screen.getByRole("button", { name: "Retry" }));

    expect(await screen.findByRole("heading", { name: "Climate policy" })).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(fetchMock.mock.calls[0][0]).toBe("/api/topics");
  });

  it("creates, updates and deletes using server data and accepts an empty 204 delete", async () => {
    let topics = [existingTopic];
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const path = String(input);
      if (path === "/api/topics" && !init?.method) return response(topics);
      if (path === "/api/topics" && init?.method === "POST") {
        expect(JSON.parse(String(init.body))).toEqual({ name: "Urban forests", description: "Research on urban canopy." });
        topics = [addedTopic, ...topics];
        return response(addedTopic, 201);
      }
      if (path === "/api/topics/topic-1" && init?.method === "PUT") {
        expect(JSON.parse(String(init.body))).toEqual({ name: "Climate action", description: "Updated policy research." });
        const updated = { ...existingTopic, name: "Climate action", description: "Updated policy research." };
        topics = topics.map((topic) => topic.id === updated.id ? updated : topic);
        return response(updated);
      }
      if (path === "/api/topics/topic-2" && init?.method === "DELETE") {
        topics = topics.filter((topic) => topic.id !== "topic-2");
        return noContent();
      }
      throw new Error(`Unexpected request: ${init?.method ?? "GET"} ${path}`);
    });
    vi.stubGlobal("fetch", fetchMock);

    render(<App />);
    openTopics();
    expect(await screen.findByRole("heading", { name: "Climate policy" })).toBeInTheDocument();

    openAddDialog();
    enterTopic("Urban forests", "Research on urban canopy.");
    submitTopic("Add topic");
    expect(await screen.findByRole("heading", { name: "Urban forests" })).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Edit Climate policy" }));
    enterTopic("Climate action", "Updated policy research.");
    submitTopic("Save changes");
    expect(await screen.findByRole("heading", { name: "Climate action" })).toBeInTheDocument();

    vi.spyOn(window, "confirm").mockReturnValue(true);
    fireEvent.click(screen.getByRole("button", { name: "Delete Urban forests" }));
    await waitFor(() => expect(screen.queryByRole("heading", { name: "Urban forests" })).not.toBeInTheDocument());
    expect(await screen.findByText("Topic deleted")).toBeInTheDocument();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();

    expect(fetchMock.mock.calls.map(([url, init]) => [String(url), init?.method ?? "GET"])).toEqual([
      ["/api/topics", "GET"],
      ["/api/topics", "POST"],
      ["/api/topics", "GET"],
      ["/api/topics/topic-1", "PUT"],
      ["/api/topics", "GET"],
      ["/api/topics/topic-2", "DELETE"],
      ["/api/topics", "GET"],
    ]);
  });

  it("retains a failed form and does not delete when confirmation is cancelled", async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(response([existingTopic]))
      .mockResolvedValueOnce(response({ message: "invalid" }, 400))
      .mockResolvedValueOnce(response([existingTopic]));
    vi.stubGlobal("fetch", fetchMock);
    vi.spyOn(window, "confirm").mockReturnValue(false);

    render(<App />);
    openTopics();
    expect(await screen.findByRole("heading", { name: "Climate policy" })).toBeInTheDocument();

    openAddDialog();
    enterTopic("Bad save", "Keep this form open.");
    submitTopic("Add topic");
    expect(await screen.findByRole("alert")).toHaveTextContent("valid");
    expect(screen.getByLabelText("Topic name")).toHaveValue("Bad save");
    expect(screen.getByLabelText("Description")).toHaveValue("Keep this form open.");
    expect(screen.getByRole("dialog", { name: "Add a topic" })).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Cancel" }));
    fireEvent.click(screen.getByRole("button", { name: "Delete Climate policy" }));
    expect(window.confirm).toHaveBeenCalledOnce();
    expect(fetchMock.mock.calls.filter(([, init]) => init?.method === "DELETE")).toHaveLength(0);
  });

  it("keeps sample stories safe and distinct when live topics use unrelated IDs or are deleted", async () => {
    const liveTopic = { ...existingTopic, id: "a-server-uuid" };
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(response([liveTopic]))
      .mockResolvedValueOnce(noContent())
      .mockResolvedValueOnce(response([]));
    vi.stubGlobal("fetch", fetchMock);
    vi.spyOn(window, "confirm").mockReturnValue(true);

    render(<App />);
    expect(await screen.findByText("Live topic")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "PostgreSQL logical replication, in plain language" })).toBeInTheDocument();
    expect(screen.getByText("Climate & energy")).toBeInTheDocument();

    openTopics();
    expect(await screen.findByRole("heading", { name: "Climate policy" })).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Delete Climate policy" }));
    await waitFor(() => expect(screen.getByText("You haven't added any live topics yet.")).toBeInTheDocument());
    fireEvent.click(screen.getByRole("button", { name: "Feed" }));
    expect(screen.getByRole("heading", { name: "PostgreSQL logical replication, in plain language" })).toBeInTheDocument();
    expect(screen.getByText("Climate & energy")).toBeInTheDocument();
  });

  it("ignores an old initial list response after a successful create and canonical refresh", async () => {
    const initial = deferred<Response>();
    const fetchMock = vi.fn()
      .mockReturnValueOnce(initial.promise)
      .mockResolvedValueOnce(response(addedTopic, 201))
      .mockResolvedValueOnce(response([existingTopic, addedTopic]));
    vi.stubGlobal("fetch", fetchMock);

    render(<App />);
    openTopics();
    expect(screen.getByText("Loading topics…")).toBeInTheDocument();
    openAddDialog();
    enterTopic(addedTopic.name, addedTopic.description);
    submitTopic("Add topic");
    expect(await screen.findByRole("heading", { name: "Urban forests" })).toBeInTheDocument();
    expect(await screen.findByRole("heading", { name: "Climate policy" })).toBeInTheDocument();

    await act(async () => {
      initial.resolve(response([]));
      await initial.promise;
    });
    expect(screen.getByRole("heading", { name: "Urban forests" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Climate policy" })).toBeInTheDocument();
  });

  it("guards duplicate submits and blocks dismissal while a save is pending", async () => {
    const save = deferred<Response>();
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(response([]))
      .mockReturnValueOnce(save.promise)
      .mockResolvedValueOnce(response([addedTopic]));
    vi.stubGlobal("fetch", fetchMock);

    render(<App />);
    openAddDialog();
    enterTopic(addedTopic.name, addedTopic.description);
    const dialog = screen.getByRole("dialog");
    const form = dialog.querySelector("form");
    expect(form).not.toBeNull();

    fireEvent.submit(form!);
    expect(await screen.findByRole("button", { name: /Saving/ })).toBeDisabled();
    fireEvent.submit(form!);
    fireEvent.click(screen.getByRole("button", { name: "Close" }));
    fireEvent.click(screen.getByRole("button", { name: "Cancel" }));
    expect(dialog).toHaveAttribute("open");
    expect(fetchMock.mock.calls.filter(([, init]) => init?.method === "POST")).toHaveLength(1);

    await act(async () => {
      save.resolve(response(addedTopic, 201));
      await save.promise;
    });
    expect(await screen.findByRole("heading", { name: "Urban forests" })).toBeInTheDocument();
    expect(dialog).not.toHaveAttribute("open");
  });
});
