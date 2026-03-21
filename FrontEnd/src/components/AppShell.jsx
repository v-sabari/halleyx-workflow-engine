import { useEffect, useState } from "react";
import { NavLink, useLocation } from "react-router-dom";
import NotificationBell from "./NotificationBell.jsx";

const NAV = [
  { to: "/", label: "Workflows", icon: "⚡", end: true },
  { to: "/editor", label: "Editor", icon: "✏️", end: false },
  { to: "/rules", label: "Rule Editor", icon: "📋", end: false },
  { to: "/execute", label: "Execution", icon: "▶️", end: false },
  { to: "/audit", label: "Audit Log", icon: "📊", end: false },
];

function TopbarBreadcrumb() {
  const location = useLocation();

  const current =
    NAV.find((item) =>
      item.end
        ? location.pathname === item.to
        : location.pathname.startsWith(item.to)
    )?.label ?? "Dashboard";

  return (
    <span className="topbar-breadcrumb">
      <span className="topbar-breadcrumb-brand">Halleyx</span>
      <span className="topbar-breadcrumb-sep"> / </span>
      <span className="topbar-breadcrumb-current">{current}</span>
    </span>
  );
}

function AppShell({ children }) {
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const location = useLocation();

  useEffect(() => {
    const handleResize = () => {
      if (window.innerWidth >= 992) {
        setSidebarOpen(false);
      }
    };

    window.addEventListener("resize", handleResize);
    return () => window.removeEventListener("resize", handleResize);
  }, []);

  useEffect(() => {
    setSidebarOpen(false);
  }, [location.pathname]);

  useEffect(() => {
    document.body.style.overflow = sidebarOpen ? "hidden" : "";
    return () => {
      document.body.style.overflow = "";
    };
  }, [sidebarOpen]);

  return (
    <div className={`app-shell ${sidebarOpen ? "sidebar-open" : ""}`}>
      <div
        className="sidebar-backdrop"
        onClick={() => setSidebarOpen(false)}
        aria-hidden="true"
      />

      <aside className="app-sidebar">
        <button
          type="button"
          className="sidebar-close"
          onClick={() => setSidebarOpen(false)}
          aria-label="Close sidebar"
        >
          ✕
        </button>

        <div className="sidebar-brand">
          <div className="sidebar-brand-icon">⚡</div>
          <div>
            <div className="sidebar-brand-name">Halleyx</div>
            <div className="sidebar-brand-tag">Workflow Engine</div>
          </div>
        </div>

        <div className="sidebar-section">
          <div className="sidebar-section-label">Navigation</div>

          {NAV.map(({ to, label, icon, end }) => (
            <NavLink
              key={to}
              to={to}
              end={end}
              className={({ isActive }) =>
                isActive ? "sidebar-link active" : "sidebar-link"
              }
            >
              <span className="sidebar-link-icon">{icon}</span>
              <span className="sidebar-link-text">{label}</span>
            </NavLink>
          ))}
        </div>

        <div className="sidebar-footer">
          <div className="sidebar-user">
            <div className="sidebar-avatar">SV</div>
            <div className="sidebar-user-meta">
              <div className="sidebar-user-name">Sabari V</div>
              <div className="sidebar-user-role">Administrator</div>
            </div>
          </div>
        </div>
      </aside>

      <div className="app-main">
        <header className="app-topbar">
          <div className="topbar-left">
            <button
              type="button"
              className="sidebar-toggle"
              onClick={() => setSidebarOpen(true)}
              aria-label="Open sidebar"
            >
              ☰
            </button>

            <TopbarBreadcrumb />
          </div>

          <div className="topbar-right">
            <NotificationBell />
            <div className="sidebar-avatar topbar-avatar">SV</div>
          </div>
        </header>

        <main className="app-content">{children}</main>
      </div>
    </div>
  );
}

export default AppShell;