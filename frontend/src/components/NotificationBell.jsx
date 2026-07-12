import { useEffect, useRef, useState } from "react";
import axios from "axios";

/**
 * NotificationBell
 *
 * BUGS FIXED IN THIS VERSION:
 *
 * BUG A (CRITICAL) — X-API-Key injected at axios.create() time (module init).
 *   If VITE_API_KEY is empty at module load the header was permanently absent
 *   for the lifetime of the module — no retries, no hot-reload recovery.
 *   FIX: moved key injection into a request interceptor (runs at call time,
 *   identical pattern to api.js) so it always reads the current env value.
 *
 * BUG C (CRITICAL) — SecurityConfig.anyRequest().hasRole("API_CLIENT") covers
 *   /notifications/* so every notifApi call must carry X-API-Key.
 *   The static-header approach in the previous version broke whenever the key
 *   was set after module init. The interceptor approach fixes this definitively.
 *
 * BUG F (MEDIUM) — Polling interval + in-flight fetch continued after unmount,
 *   causing React "state update on unmounted component" warnings and stale
 *   closure bugs on slow connections. FIX: AbortController cancels in-flight
 *   requests; clearInterval cleans up the timer on unmount.
 *
 * BUG H (LOW) — No response interceptor on notifApi meant 401/429 errors
 *   silently swallowed. FIX: response interceptor normalises error messages
 *   consistently with api.js.
 */

// NotificationController lives at /notifications — NO /api/v1 prefix.
// Strip /api/v1 from VITE_API_URL to get the bare backend origin.
const BACKEND_ORIGIN =
  (import.meta.env.VITE_API_URL || "http://localhost:8080/api/v1")
    .replace(/\/api\/v1\/?$/, "");

const notifApi = axios.create({
  baseURL: `${BACKEND_ORIGIN}/notifications`,
  headers: { "Content-Type": "application/json" },
});

// ── Request interceptor: inject API key at CALL time, not at init time ────────
// This is the same pattern as api.js and is the only correct approach when
// VITE_API_KEY may not be defined at module-evaluation time (e.g. during SSR,
// test environments, or when the dev server starts before .env.local is saved).
notifApi.interceptors.request.use(
  (config) => {
    const apiKey = import.meta.env.VITE_API_KEY;
    if (apiKey) config.headers["X-API-Key"] = apiKey;
    return config;
  },
  (error) => Promise.reject(error)
);

// ── Response interceptor: normalise errors (consistent with api.js) ───────────
notifApi.interceptors.response.use(
  (res) => res,
  (err) => {
    const status     = err?.response?.status;
    const data       = err?.response?.data;
    const backendMsg =
      typeof data?.error   === "string" ? data.error   :
      typeof data?.message === "string" ? data.message :
      null;

    if (status === 401) {
      const noKey = !import.meta.env.VITE_API_KEY;
      err.message = noKey
        ? "API key not configured. Set VITE_API_KEY in .env.local (see .env.example)."
        : backendMsg || "Unauthorised. Your API key may be invalid or revoked.";
    } else if (status === 429) {
      const retryAfter = err.response.headers?.["retry-after"];
      err.message = retryAfter
        ? `Rate limit exceeded. Retry in ${retryAfter}s.`
        : "Rate limit exceeded.";
    } else if (backendMsg) {
      err.message = backendMsg;
    }

    return Promise.reject(err);
  }
);

// ── Component ──────────────────────────────────────────────────────────────────

function NotificationBell() {
  const [notifications, setNotifications] = useState([]);
  const [open, setOpen]                   = useState(false);
  const [loading, setLoading]             = useState(false);
  const [fetchError, setFetchError]       = useState(false);
  const [initialLoading, setInitialLoading] = useState(true);
  const containerRef = useRef(null);

  /**
   * Fetch unread notifications.
   *
   * BUG F FIX: accepts an AbortSignal so the request can be cancelled when
   * the component unmounts or when a newer fetch supersedes this one.
   */
  const fetchUnread = async (signal) => {
    try {
      const res = await notifApi.get("/unread", { signal });
      setNotifications(res.data || []);
      setFetchError(false);
    } catch (err) {
      // Ignore AbortError — it's an intentional cancellation, not an error.
      if (axios.isCancel(err) || err?.code === "ERR_CANCELED") return;
      setFetchError(true);
    } finally {
      setInitialLoading(false);
    }
  };

  /**
   * BUG F FIX: cleanup on unmount.
   * - The AbortController cancels any in-flight fetchUnread call.
   * - clearInterval stops the polling timer.
   * Both prevent "setState on unmounted component" React warnings.
   */
  useEffect(() => {
    const controller = new AbortController();

    fetchUnread(controller.signal);
    const interval = setInterval(() => fetchUnread(controller.signal), 15000);

    return () => {
      controller.abort();
      clearInterval(interval);
    };
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  // Close on outside click
  useEffect(() => {
    const handler = (e) => {
      if (containerRef.current && !containerRef.current.contains(e.target)) {
        setOpen(false);
      }
    };
    if (open) document.addEventListener("mousedown", handler);
    return () => document.removeEventListener("mousedown", handler);
  }, [open]);

  const handleMarkAllRead = async () => {
    try {
      setLoading(true);
      await notifApi.put("/read-all");
      setNotifications([]);
      setOpen(false);
    } catch {
      // Silent — the bell will re-fetch on the next 15s poll
    } finally {
      setLoading(false);
    }
  };

  const handleMarkOneRead = async (id) => {
    try {
      await notifApi.put(`/${id}/read`);
      setNotifications((prev) => prev.filter((n) => n.id !== id));
    } catch {
      // Silent — UI stays consistent; stale entry disappears on next poll
    }
  };

  const fmt = (v) => (v ? new Date(v).toLocaleString() : "");

  return (
    <div className="notif-container" ref={containerRef}>
      <button
        className="notif-bell-btn"
        onClick={() => setOpen((prev) => !prev)}
        aria-label={`Notifications${notifications.length > 0 ? `, ${notifications.length} unread` : ""}`}
        aria-expanded={open}
      >
        🔔
        {notifications.length > 0 && (
          <span className="notif-count-badge">
            {notifications.length > 99 ? "99+" : notifications.length}
          </span>
        )}
      </button>

      {open && (
        <div className="notif-dropdown" role="dialog" aria-label="Notifications panel">
          <div className="notif-dropdown-header">
            <strong className="notif-dropdown-title">Notifications</strong>
            {notifications.length > 0 && (
              <button
                className="notif-mark-all-btn"
                onClick={handleMarkAllRead}
                disabled={loading}
              >
                {loading ? "Clearing…" : "Mark all read"}
              </button>
            )}
          </div>

          {initialLoading ? (
            <p className="notif-empty-msg">
              <span className="spinner spinner--sm" /> Loading…
            </p>
          ) : fetchError ? (
            <p className="notif-error-msg">
              <span>⚠️</span>
              Could not load notifications.
            </p>
          ) : notifications.length === 0 ? (
            <p className="notif-empty-msg">
              <span className="notif-empty-icon">🔔</span>
              No unread notifications
            </p>
          ) : (
            <div className="notif-list">
              {notifications.map((n) => (
                <div className="notif-item" key={n.id}>
                  <div className="notif-unread-dot" />
                  <div className="notif-item-body">
                    <p className="notif-step-name">{n.stepName || "Notification"}</p>
                    <p className="notif-message">
                      {n.message && n.message.length > 100
                        ? n.message.substring(0, 100) + "…"
                        : n.message}
                    </p>
                    <p className="notif-meta">
                      {fmt(n.createdAt)}
                      {n.channel ? ` · ${n.channel}` : ""}
                    </p>
                  </div>
                  <button
                    className="notif-dismiss-btn"
                    onClick={() => handleMarkOneRead(n.id)}
                    aria-label="Dismiss"
                    title="Mark as read"
                  >
                    ✕
                  </button>
                </div>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  );
}

export default NotificationBell;
