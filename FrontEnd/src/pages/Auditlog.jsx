import { useEffect, useState, useCallback } from "react";
import api from "../services/api";
import "../styles/pages.css";

/**
 * AuditLog — FIX F2
 *
 * The spec's Audit Log table is execution-level, not step-level:
 *   Execution ID | Workflow | Version | Status | Started By | Start Time | End Time | Actions
 *
 * This component now:
 *   1. Fetches GET /api/v1/executions (paginated, filterable by status)
 *   2. Shows one row per execution matching the spec table
 *   3. Provides a "View Logs" drill-down that fetches the step logs
 *      for that execution from GET /audit-logs?executionId=...
 */

async function fetchExecutions(params) {
  // Uses the api axios instance (baseURL = /api/v1)
  // Requires GET /api/v1/executions endpoint — see analysis Change 8 / ExecutionController
  const r = await api.get("/executions", { params });
  return r.data; // Page<Execution>
}

async function fetchStepLogs(execId) {
  // Step-level logs live at /audit-logs (no /api/v1 prefix)
  const r = await api.get(`/executions/${execId}/logs`);
  return Array.isArray(r.data) ? r.data : (r.data?.content || []);
}

function AuditLog() {
  const [executions, setExecutions]       = useState([]);
  const [loading, setLoading]             = useState(false);
  const [errorMessage, setErrorMessage]   = useState("");
  const [page, setPage]                   = useState(0);
  const [size]                            = useState(10);
  const [totalPages, setTotalPages]       = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [statusFilter, setStatusFilter]   = useState("");

  // Drill-down state
  const [selectedId, setSelectedId]       = useState(null);
  const [detail, setDetail]               = useState(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailError, setDetailError]     = useState("");

  /* ── Load executions ── */
  const loadExecutions = useCallback(async (pg, status) => {
    try {
      setLoading(true); setErrorMessage("");
      const params = { page: pg, size };
      if (status) params.status = status;
      const d = await fetchExecutions(params);
      setExecutions(d.content || []);
      setTotalPages(d.totalPages || 0);
      setTotalElements(d.totalElements || 0);
    } catch (err) {
      setErrorMessage(err?.response?.data?.error || err.message || "Failed to load audit log.");
    } finally {
      setLoading(false);
    }
  }, [size]);

  useEffect(() => {
    loadExecutions(page, statusFilter);
  }, [page, statusFilter, loadExecutions]);

  /* ── View step logs for one execution ── */
  const handleViewLogs = async (exec) => {
    if (selectedId === exec.id) {
      setSelectedId(null); setDetail(null); return;
    }
    setSelectedId(exec.id);
    setDetail(null); setDetailError(""); setDetailLoading(true);
    try {
      const logs = await fetchStepLogs(exec.id);
      setDetail({ id: exec.id, logs });
    } catch {
      setDetailError("Failed to load step logs.");
    } finally {
      setDetailLoading(false);
    }
  };

  /* ── Helpers ── */
  const statusBadgeClass = (s) => {
    if (!s) return "status-badge";
    const map = {
      completed:            "completed",
      failed:               "failed",
      running:              "running",
      in_progress:          "running",
      pending:              "pending",
      waiting_for_approval: "waiting_for_approval",
      canceled:             "canceled",
      cancelled:            "canceled",
    };
    return `status-badge status-badge--${map[s.toLowerCase()] || ""}`;
  };

  const fmt   = (v) => (v ? new Date(v).toLocaleString() : "—");
  const label = (s) => (s ? s.replace(/_/g, " ") : "—");

  const calcDur = (log) => {
    if (log.startedAt && log.endedAt) {
      const ms = new Date(log.endedAt) - new Date(log.startedAt);
      return `${ms} ms`;
    }
    return "—";
  };

  const start = totalElements === 0 ? 0 : page * size + 1;
  const end   = Math.min((page + 1) * size, totalElements);

  return (
    <div className="audit-page">

      {/* ── Header ── */}
      <div className="page-header">
        <div className="page-header-left">
          <h2 className="page-title">Audit Log</h2>
          <p className="page-subtitle">
            Complete history of all workflow executions for tracking and compliance.
          </p>
        </div>
        <div className="page-header-actions">
          {/* Status filter */}
          <select
            className="audit-filter-select"
            value={statusFilter}
            onChange={e => { setStatusFilter(e.target.value); setPage(0); }}
          >
            <option value="">All Statuses</option>
            <option value="COMPLETED">Completed</option>
            <option value="FAILED">Failed</option>
            <option value="RUNNING">Running</option>
            <option value="PENDING">Pending</option>
            <option value="WAITING_FOR_APPROVAL">Waiting for Approval</option>
            <option value="CANCELLED">Cancelled</option>
          </select>

          <button
            className="audit-refresh-btn"
            onClick={() => loadExecutions(page, statusFilter)}
            disabled={loading}
          >
            {loading ? <span className="spinner spinner--sm" /> : "↻"} Refresh
          </button>
        </div>
      </div>

      {/* ── Error banner ── */}
      {errorMessage && (
        <div className="audit-error-banner">
          <span>⚠ {errorMessage}</span>
          <button onClick={() => setErrorMessage("")}>✕</button>
        </div>
      )}

      {/* ── Main table ── */}
      <div className="audit-card">
        <div className="table-scroll">
          <table className="audit-table">
            <thead>
              <tr>
                <th>Execution ID</th>
                <th>Workflow ID</th>
                <th>Version</th>
                <th>Status</th>
                <th>Started By</th>
                <th>Start Time</th>
                <th>End Time</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {loading ? (
                <tr>
                  <td colSpan={8} style={{ textAlign: "center", padding: 52 }}>
                    <div
                      style={{
                        display:       "flex",
                        flexDirection: "column",
                        alignItems:    "center",
                        gap:           12,
                      }}
                    >
                      <div className="spinner spinner--xl" />
                      <span style={{ fontSize: 13, color: "var(--color-text-muted)" }}>
                        Loading audit log…
                      </span>
                    </div>
                  </td>
                </tr>
              ) : executions.length === 0 ? (
                <tr>
                  <td colSpan={8}>
                    <div className="empty-state">
                      <div className="empty-state-icon">📊</div>
                      <div className="empty-state-title">No executions found</div>
                      <p className="empty-state-body">
                        Execution records appear here once workflows are triggered.
                      </p>
                    </div>
                  </td>
                </tr>
              ) : (
                executions.map(exec => (
                  <>
                    {/* ── Execution row (spec-compliant columns) ── */}
                    <tr key={exec.id}>
                      <td>
                        <span className="execution-id" title={exec.id}>
                          {exec.id?.slice(0, 12)}…
                        </span>
                      </td>
                      <td>
                        <span
                          className="execution-id"
                          title={exec.workflowId}
                          style={{ maxWidth: 130 }}
                        >
                          {exec.workflowId?.slice(0, 12)}…
                        </span>
                      </td>
                      <td>
                        <span className="version-chip">v{exec.workflowVersion ?? "—"}</span>
                      </td>
                      <td>
                        <span className={statusBadgeClass(exec.status)}>
                          {label(exec.status)}
                        </span>
                      </td>
                      <td style={{ fontWeight: 600 }}>
                        {exec.startedBy || "—"}
                      </td>
                      <td style={{ fontSize: 12, color: "var(--color-text-muted)" }}>
                        {fmt(exec.startedAt)}
                      </td>
                      <td style={{ fontSize: 12, color: "var(--color-text-muted)" }}>
                        {fmt(exec.completedAt)}
                      </td>
                      <td>
                        <button
                          className="view-log-btn"
                          onClick={() => handleViewLogs(exec)}
                        >
                          {selectedId === exec.id ? "Hide" : "View Logs"}
                        </button>
                      </td>
                    </tr>

                    {/* ── Step-log drill-down row ── */}
                    {selectedId === exec.id && (
                      <tr key={`${exec.id}-detail`}>
                        <td colSpan={8} className="detail-cell">
                          {detailLoading ? (
                            <div
                              style={{
                                padding:    24,
                                display:    "flex",
                                alignItems: "center",
                                gap:        10,
                              }}
                            >
                              <span className="spinner spinner--md" />
                              <span className="audit-loading">Loading step logs…</span>
                            </div>
                          ) : detailError ? (
                            <p className="audit-detail-error">⚠ {detailError}</p>
                          ) : detail ? (
                            <div className="execution-detail-panel">
                              <h4>
                                Step Logs — Execution{" "}
                                <code style={{ fontSize: 12 }}>
                                  {detail.id?.slice(0, 12)}…
                                </code>
                              </h4>
                              {detail.logs?.length > 0 ? (
                                <div className="table-scroll audit-detail-table">
                                  <table className="audit-table">
                                    <thead>
                                      <tr>
                                        <th>Step</th>
                                        <th>Type</th>
                                        <th>Status</th>
                                        <th>Evaluated Rule</th>
                                        <th>Next Step</th>
                                        <th>Duration</th>
                                        <th>Error</th>
                                      </tr>
                                    </thead>
                                    <tbody>
                                      {detail.logs.map(l => (
                                        <tr key={l.id}>
                                          <td style={{ fontWeight: 600 }}>
                                            {l.stepName}
                                          </td>
                                          <td>
                                            {l.stepType ? (
                                              <span className={`badge badge--${l.stepType}`}>
                                                <span className="badge-dot" />
                                                {l.stepType}
                                              </span>
                                            ) : "—"}
                                          </td>
                                          <td>
                                            <span className={statusBadgeClass(l.status)}>
                                              {label(l.status)}
                                            </span>
                                          </td>
                                          <td>
                                            <code style={{ fontSize: 11 }}>
                                              {l.evaluatedRules || "—"}
                                            </code>
                                          </td>
                                          <td style={{ fontSize: 12 }}>
                                            {l.selectedNextStepId || "END"}
                                          </td>
                                          <td
                                            style={{
                                              fontFamily: "var(--font-mono)",
                                              fontSize:   12,
                                            }}
                                          >
                                            {calcDur(l)}
                                          </td>
                                          <td>
                                            {l.errorMessage ? (
                                              <span className="log-error-text">
                                                {l.errorMessage}
                                              </span>
                                            ) : "—"}
                                          </td>
                                        </tr>
                                      ))}
                                    </tbody>
                                  </table>
                                </div>
                              ) : (
                                <p className="audit-loading">
                                  No step logs recorded for this execution.
                                </p>
                              )}
                            </div>
                          ) : null}
                        </td>
                      </tr>
                    )}
                  </>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>

      {/* ── Pagination ── */}
      <div className="audit-pagination-bar">
        {totalElements > 0 && (
          <span className="audit-record-info">
            Showing {start}–{end} of {totalElements} records
          </span>
        )}
        <div className="audit-pagination-controls">
          <button
            className="page-btn"
            disabled={page === 0 || loading}
            onClick={() => setPage(p => p - 1)}
          >
            ← Previous
          </button>
          <span className="page-info">
            Page {page + 1} of {totalPages || 1}
          </span>
          <button
            className="page-btn"
            disabled={page + 1 >= totalPages || loading}
            onClick={() => setPage(p => p + 1)}
          >
            Next →
          </button>
        </div>
      </div>
    </div>
  );
}

export default AuditLog;
