import assert from "node:assert/strict";
import test from "node:test";
import worker from "./categorizer.mjs";

const env = {
  KANAK_ACCESS_TOKEN: "test_token_for_kanak_categorization",
  GEMINI_API_KEY: "test-server-key",
  GEMINI_MODEL: "gemini-3.5-flash-lite",
  CATEGORIZATION_LIMIT: { limit: async () => ({ success: true }) },
};

function request(payload, token = env.KANAK_ACCESS_TOKEN) {
  return new Request("https://kanak.example/categorize", {
    method: "POST",
    headers: { Authorization: `Bearer ${token}` },
    body: JSON.stringify(payload),
  });
}

test("forwards the approved description and returns an allowlisted category", async (t) => {
  const upstream = t.mock.method(globalThis, "fetch", async (url, options) => {
    assert.match(url, /gemini-3\.5-flash-lite:generateContent$/);
    assert.equal(options.headers["x-goog-api-key"], env.GEMINI_API_KEY);
    assert.equal(options.redirect, "manual");
    const { contents, generationConfig } = JSON.parse(options.body);
    assert.equal(generationConfig.thinkingConfig.thinkingLevel, "minimal");
    assert.equal(generationConfig.maxOutputTokens, 1024);
    assert.deepEqual(contents, [{ role: "user", parts: [{ text: "acetaminophen" }] }]);
    return Response.json({ candidates: [{ content: { parts: [{ text: '{"category":"HEALTH"}' }] } }] });
  });
  const response = await worker.fetch(request({ description: "acetaminophen" }), env);
  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), { category: "HEALTH" });
  assert.equal(upstream.mock.callCount(), 1);
});

test("rejects unauthenticated, oversized, or excessive requests before calling Gemini", async (t) => {
  const upstream = t.mock.method(globalThis, "fetch");
  assert.equal((await worker.fetch(request({ description: "item" }, "x".repeat(32)), env)).status, 401);
  assert.equal((await worker.fetch(request({ description: "a".repeat(4096) }), env)).status, 400);
  assert.equal((await worker.fetch(request({ description: "item", amount: 100 }), env)).status, 400);
  assert.equal((await worker.fetch(request({ description: "item 100" }), env)).status, 400);
  const limited = { ...env, CATEGORIZATION_LIMIT: { limit: async () => ({ success: false }) } };
  assert.equal((await worker.fetch(request({ description: "item" }), limited)).status, 429);
  assert.equal(upstream.mock.callCount(), 0);
});

test("returns a null suggestion for NONE and fails closed on unknown provider output", async (t) => {
  let category = "NONE";
  t.mock.method(globalThis, "fetch", async () =>
    Response.json({ candidates: [{ content: { parts: [{ text: JSON.stringify({ category }) }] } }] }),
  );
  const noMatch = await worker.fetch(request({ description: "item" }), env);
  assert.deepEqual(await noMatch.json(), { category: null });
  category = "UNKNOWN_CATEGORY";
  assert.equal((await worker.fetch(request({ description: "item" }), env)).status, 502);
});

test("reports provider status without forwarding the provider error body", async (t) => {
  t.mock.method(globalThis, "fetch", async () =>
    Response.json({ error: { message: "internal provider details" } }, { status: 403 }),
  );
  const response = await worker.fetch(request({ description: "item" }), env);
  assert.equal(response.status, 502);
  assert.deepEqual(await response.json(), { error: "provider_unavailable", providerStatus: 403 });
});

test("distinguishes exhausted output and timeouts from upstream HTTP errors", async (t) => {
  let timedOut = false;
  t.mock.method(globalThis, "fetch", async () => {
    if (timedOut) throw new DOMException("request expired", "TimeoutError");
    return Response.json({ candidates: [{ finishReason: "MAX_TOKENS" }] });
  });
  const truncated = await worker.fetch(request({ description: "item" }), env);
  assert.equal(truncated.status, 502);
  assert.deepEqual(await truncated.json(), { error: "provider_output_truncated" });
  timedOut = true;
  const timeout = await worker.fetch(request({ description: "item" }), env);
  assert.equal(timeout.status, 504);
  assert.deepEqual(await timeout.json(), { error: "provider_timeout" });
});

test("rejects redirects without forwarding the Gemini key to another URL", async (t) => {
  const upstream = t.mock.method(globalThis, "fetch", async () =>
    Response.redirect("https://unexpected.example", 302),
  );
  const response = await worker.fetch(request({ description: "item" }), env);
  assert.equal(response.status, 502);
  assert.deepEqual(await response.json(), { error: "provider_unavailable", providerStatus: 302 });
  assert.equal(upstream.mock.callCount(), 1);
});

test("suggests Tech and Games only to apps that support those categories", async (t) => {
  let category = "TECH";
  let allowedCategories;
  t.mock.method(globalThis, "fetch", async (_url, options) => {
    allowedCategories = JSON.parse(options.body).generationConfig.responseSchema.properties.category.enum;
    return Response.json({ candidates: [{ content: { parts: [{ text: JSON.stringify({ category }) }] } }] });
  });

  const legacy = await worker.fetch(request({ description: "graphics card" }), env);
  assert.equal(legacy.status, 502);
  assert.equal(allowedCategories.includes("TECH"), false);
  assert.equal(allowedCategories.includes("GAMES"), false);

  for (const expected of ["TECH", "GAMES"]) {
    category = expected;
    const current = request({ description: expected === "TECH" ? "graphics card" : "Hades" });
    current.headers.set("X-Kanak-Category-Version", "2");
    const response = await worker.fetch(current, env);
    assert.equal(response.status, 200);
    assert.equal(allowedCategories.includes(expected), true);
    assert.deepEqual(await response.json(), { category: expected });
  }
});
