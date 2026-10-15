import { afterEach, describe, expect, it, vi } from "vitest";
import { createTopic, getTopics } from "./topicApi";
import { AuthenticationRequiredError } from "./auth";

function response(status = 200, body: unknown = []) {
  return { ok: status >= 200 && status < 300, status, json: async () => body } as Response;
}

afterEach(() => {
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});

describe("authenticated topic API", () => {
  it("refreshes a token before each call and sends it as a bearer token", async () => {
    const fetchMock = vi.fn().mockResolvedValueOnce(response(200, [])).mockResolvedValueOnce(response(201, { id: "1" }));
    const getToken = vi.fn().mockResolvedValueOnce("token-one").mockResolvedValueOnce("token-two");
    vi.stubGlobal("fetch", fetchMock);

    await getTopics(getToken);
    await createTopic({ name: "Climate", description: "Policy" }, getToken);

    expect(getToken).toHaveBeenCalledTimes(2);
    expect(new Headers(fetchMock.mock.calls[0][1].headers).get("Authorization")).toBe("Bearer token-one");
    expect(new Headers(fetchMock.mock.calls[1][1].headers).get("Authorization")).toBe("Bearer token-two");
  });

  it("does not send or replay a request when refresh fails or the API returns 401", async () => {
    const fetchMock = vi.fn().mockResolvedValue(response(401));
    vi.stubGlobal("fetch", fetchMock);
    await expect(getTopics(async () => { throw new Error("refresh failed"); })).rejects.toBeInstanceOf(AuthenticationRequiredError);
    expect(fetchMock).not.toHaveBeenCalled();

    await expect(createTopic({ name: "Climate", description: "Policy" }, async () => "expired"))
      .rejects.toBeInstanceOf(AuthenticationRequiredError);
    expect(fetchMock).toHaveBeenCalledOnce();
  });
});
