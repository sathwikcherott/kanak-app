import assert from "node:assert/strict";
import test from "node:test";
import worker from "./categorizer.mjs";

const env = {
  KANAK_ACCESS_TOKEN: "test_token_for_kanak_categorization",
  GEMINI_API_KEY: "test-server-key",
  GEMINI_MODEL: "gemini-3.8-flash",
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
    assert.match(url, /gemini-3\.8-flash:generateContent$/);
    assert.equal(options.headers["x-goog-api-key"], env.GEMINI_API_KEY);
    const { contents } = JSON.parse(options.body);
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
