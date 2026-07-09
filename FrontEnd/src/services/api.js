import axios from "axios";
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
