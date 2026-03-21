import { useEffect, useRef, useState } from "react";
import api from "../services/api";

function NotificationBell() {
  const [notifications, setNotifications] = useState([]);
  const [open, setOpen]           = useState(false);
  const [loading, setLoading]     = useState(false);
  const [fetchError, setFetchError] = useState(false);
  const containerRef = useRef(null);

  const fetchUnread = async () => {
    try {
      // NotificationController is at /notifications (no /api/v1 prefix)
      const res = await fetch("http://localhost:8080/notifications/unread");
      if (!res.ok) throw new Error("Failed");
      const data = await res.json();
      setNotifications(data || []);
      setFetchError(false);
    } catch {
      setFetchError(true);
    }
  };

  useEffect(() => {
    fetchUnread();
    const interval = setInterval(fetchUnread, 15000);
    return () => clearInterval(interval);
  }, []);

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
      await fetch("http://localhost:8080/notifications/read-all", { method: "PUT" });
      setNotifications([]);
      setOpen(false);
    } catch {
      console.error("Failed to mark all read");
    } finally {
      setLoading(false);
    }
  };

  const handleMarkOneRead = async (id) => {
    try {
      await fetch(`http://localhost:8080/notifications/${id}/read`, { method: "PUT" });
      setNotifications(prev => prev.filter(n => n.id !== id));
    } catch {
      console.error("Failed to dismiss notification");
    }
  };

  const fmt = (v) => v ? new Date(v).toLocaleString() : "";

  return (
    <div className="notif-container" ref={containerRef}>
      <button
        className="notif-bell-btn"
        onClick={() => setOpen(prev => !prev)}
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

          {fetchError ? (
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
                  >✕</button>
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