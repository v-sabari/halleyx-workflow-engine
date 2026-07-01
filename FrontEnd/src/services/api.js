import axios from "axios";

/**
 * api.js — central Axios instance for all /api/v1/* calls.
 *
 * FIXES applied:
 *
 * BUG 4 (CRITICAL) — No X-API-Key header was being sent.
 * The backend's SecurityConfig now requires "X-API-Key: <rawKey>" on every
 * request to protected endpoints (everything except POST /api/v1/keys/issue,
 * /actuator/**, and /health). Without this header Spring Security returns 401
 * and every page shows an error.
 *
 * FIX: a request interceptor reads VITE_API_KEY at call time (not at module
 * init time) and injects it into every outgoing request. Reading at call time
 * means hot-reload in dev works without a page refresh if the var changes.
 *
 * In development: add VITE_API_KEY=<your-raw-key> to .env.local
 * In production:  inject VITE_API_KEY via your CI/CD environment variables.
 *
 * BUG 5 — The response interceptor only logged the error; it did not
 * surface rate-limit (429) or auth (401) errors distinctly, so the UI
 * showed a generic "Failed" message instead of actionable feedback.
 *
 * FIX: the error interceptor now normalises the error message for 401 and 429
 * so every page's existing catch(err => setErrorMessage(err.message)) handler
 * automatically displays the right text without any page-level changes.
 */

const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL || "http://localhost:8080/api/v1",
  headers: {
    "Content-Type": "application/json",
  },
});

// ── Request interceptor: inject API key ───────────────────────────────────────
api.interceptors.request.use(
  (config) => {
    const apiKey = import.meta.env.VITE_API_KEY;
    if (apiKey) {
      config.headers["X-API-Key"] = apiKey;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

// ── Response interceptor: normalise errors ────────────────────────────────────
api.interceptors.response.use(
  (res) => res,
  (err) => {
    const status  = err?.response?.status;
    const data    = err?.response?.data;

    // Prefer the backend's structured { error: "..." } message
    const backendMsg =
      typeof data?.error === "string"   ? data.error   :
      typeof data?.message === "string" ? data.message :
      typeof data === "string"          ? data          :
      null;

    if (status === 401) {
      const noKey = !import.meta.env.VITE_API_KEY;
      err.message = noKey
        ? "API key not configured. Set VITE_API_KEY in .env.local (see .env.example)."
        : backendMsg || "Unauthorised. Your API key may be invalid or revoked.";
    } else if (status === 429) {
      const retryAfter = err.response.headers?.["retry-after"];
      err.message = retryAfter
        ? `Rate limit exceeded. Please wait ${retryAfter}s before retrying.`
        : "Rate limit exceeded. Please slow down.";
    } else if (status === 404) {
      err.message = backendMsg || "Resource not found.";
    } else if (status === 409) {
      err.message = backendMsg || "Conflict: this request is already being processed.";
    } else if (backendMsg) {
      err.message = backendMsg;
    }

    console.error("[API Error]", status, err?.config?.url, err.message);
    return Promise.reject(err);
  }
);

export default api;
