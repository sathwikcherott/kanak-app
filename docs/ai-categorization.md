# Cloud category suggestions

Quick Entry saves expenses locally. For a draft without a local category, **Ask AI for category** previews the description to send. The preview removes numeric text and currency symbols and is limited to 500 characters. Check it before sending, since names and numbers written as words may still appear. Gemini returns a built-in category. **Use category** applies it to the draft. Custom categories are available through the local picker and local matching.

## Deploy the private endpoint

The backend in `backend/` runs as a Cloudflare Worker. It requires a Gemini API key and a separate random app access token. Use Node.js 22 or later and run these commands from the repo root on your Mac.

```sh
cd backend
npx wrangler login
npx wrangler deploy
npx wrangler secret put GEMINI_API_KEY
openssl rand -hex 32
npx wrangler secret put KANAK_ACCESS_TOKEN
```

Enter the Gemini API key at the first secret prompt. Use the generated random token at the second. Keep both out of Git and chat. The worker rejects requests until both secrets are configured. The deployed model is set in `wrangler.jsonc`.

On the phone, open **Settings → AI Categorization → Configure**. Enter the deployed HTTPS URL with `/categorize` appended and the random app access token. The token is stored in app-private preferences and excluded from backup and device transfer. The Gemini API key stays on the server. **Disconnect** removes the connection details from the phone.

For a live test, enter an amount and use **acetaminophen** as the description. Ask for a category, verify the preview, then send it. Check that the suggestion only applies after tapping **Use category**. Try again offline and verify that saving still works.

## Run backend checks

```sh
node --test backend/categorizer.test.mjs
```

[Gemini API](https://ai.google.dev/api/generate-content) · [Cloudflare Worker secrets](https://developers.cloudflare.com/workers/configuration/secrets/)
