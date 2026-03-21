import { useEffect, useState } from "react";
import { useSearchParams } from "react-router-dom";
import api from "../services/api";
import "../styles/pages.css";

function WorkflowExecution() {
  const [searchParams] = useSearchParams();
  const urlWorkflowId  = searchParams.get("workflowId");
  const urlExecutionId = searchParams.get("executionId");

  const [workflows, setWorkflows]                             = useState([]);
  const [selectedWorkflowId, setSelectedWorkflowId]           = useState(urlWorkflowId || "");
  const [selectedWorkflowVersion, setSelectedWorkflowVersion] = useState(null);
  const [schema, setSchema]                                   = useState({});
  const [formData, setFormData]                               = useState({});
  const [startedBy, setStartedBy]                             = useState("");
  const [fieldErrors, setFieldErrors]                         = useState({});
  const [executionData, setExecutionData]                     = useState(null);
  const [logs, setLogs]                                       = useState([]);
  const [viewOnlyMode, setViewOnlyMode]                       = useState(!!urlExecutionId);
  const [loading, setLoading]                                 = useState(false);
  const [approveEmail, setApproveEmail]                       = useState("");
  const [errorMessage, setErrorMessage]                       = useState("");
  const [successMessage, setSuccessMessage]                   = useState("");

  /* ── Load all active workflows for the selector ── */
  useEffect(() => {
    api.get("/workflows", { params: { page: 0, size: 100 } })
      .then(r => {
        const items = (r.data.content || []).map(item => item.workflow || item);
        setWorkflows(items.filter(w => w.isActive));
      })
      .catch(() => setErrorMessage("Failed to load workflows."));
  }, []);

  /* ── Load workflow schema ── */
  const loadWorkflowDetails = async (id) => {
    if (!id) return;
    try {
      setLoading(true); setErrorMessage("");
      const r  = await api.get(`/workflows/${id}`);
      const wf = r.data.workflow || r.data;
      let parsed = {};
      try {
        parsed =
          typeof wf.inputSchema === "string"
            ? JSON.parse(wf.inputSchema || "{}")
            : wf.inputSchema || {};
      } catch {
        parsed = {};
      }
      setSchema(parsed);
      setSelectedWorkflowVersion(wf.version ?? null);
      const init = {};
      Object.entries(parsed).forEach(([k, c]) => {
        init[k] = c?.type === "boolean" ? false : "";
      });
      setFormData(init);
      setFieldErrors({});
    } catch {
      setErrorMessage("Failed to load workflow schema.");
    } finally {
      setLoading(false);
    }
  };

  /* ── FIX F3: load execution details using the dedicated logs endpoint
        GET /api/v1/executions/:id/logs  (added to ExecutionController per analysis Change 2)
        No hardcoded localhost fallback — everything goes through the `api` axios instance. ── */
  const loadExecutionDetails = async (id) => {
    try {
      setLoading(true);
      const r  = await api.get(`/executions/${id}`);
      const ex = r.data;
      setExecutionData({
        id:              ex.id || id,
        status:          ex.status || "",
        startedAt:       ex.startedAt || "",
        completedAt:     ex.completedAt || "",
        startedBy:       ex.startedBy || startedBy,
        workflowVersion: ex.workflowVersion || "",
        retryCount:      ex.retryCount ?? 0,
      });

      // FIX F3: use the proper /executions/:id/logs endpoint via api instance
      try {
        const logsR = await api.get(`/executions/${id}/logs`);
        // endpoint returns a plain List<ExecutionLog>
        const raw = logsR.data;
        setLogs(Array.isArray(raw) ? raw : (raw?.content || []));
      } catch {
        // Graceful degradation: endpoint may not exist yet on older backends
        setLogs([]);
      }
    } catch {
      setErrorMessage("Failed to load execution details.");
    } finally {
      setLoading(false);
    }
  };

  const handleWorkflowChange = (id) => {
    setSelectedWorkflowId(id);
    setExecutionData(null);
    setLogs([]);
    setErrorMessage("");
    setSuccessMessage("");
    if (!id) {
      setSchema({});
      setFormData({});
      setSelectedWorkflowVersion(null);
      return;
    }
    loadWorkflowDetails(id);
  };

  useEffect(() => {
    if (urlExecutionId) {
      setViewOnlyMode(true);
      loadExecutionDetails(urlExecutionId);
    } else if (urlWorkflowId) {
      setViewOnlyMode(false);
      loadWorkflowDetails(urlWorkflowId);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [urlWorkflowId, urlExecutionId]);

  /* ── Validation ── */
  const validateForm = () => {
    const errs = {};
    Object.entries(schema).forEach(([k, c]) => {
      if (c?.required && (formData[k] === "" || formData[k] == null)) {
        errs[k] = `${k} is required.`;
      }
    });
    setFieldErrors(errs);
    return Object.keys(errs).length === 0;
  };

  const buildPayload = () => {
    const p = {};
    Object.entries(schema).forEach(([k, c]) => {
      const v = formData[k];
      if (c?.type === "number")       p[k] = v === "" ? null : Number(v);
      else if (c?.type === "boolean") p[k] = v === "true" || v === true;
      else                            p[k] = v;
    });
    return p;
  };

  /* ── Execute ── */
  const handleExecute = async () => {
    if (!selectedWorkflowId) { setErrorMessage("Select a workflow first."); return; }
    if (!startedBy.trim())   { setErrorMessage("Started By is required."); return; }
    if (!validateForm())     { setErrorMessage("Please fill in all required fields."); return; }
    try {
      setLoading(true); setErrorMessage(""); setSuccessMessage("");
      const r = await api.post("/executions/start", {
        workflowId: selectedWorkflowId,
        input:      buildPayload(),
        startedBy:  startedBy.trim(),
      });
      setSuccessMessage("Workflow executed successfully!");
      await loadExecutionDetails(r.data.id);
    } catch (err) {
      const msg =
        err?.response?.data?.error ||
        err?.response?.data?.message ||
        err?.response?.data;
      setErrorMessage(
        typeof msg === "string" ? msg : "Execution failed. Check your inputs."
      );
    } finally {
      setLoading(false);
    }
  };

  /* ── Cancel / Retry / Approve / Reject ── */
  const handleAction = async (action, body = {}) => {
    if (!executionData?.id) return;
    try {
      setLoading(true); setErrorMessage(""); setSuccessMessage("");
      await api.post(`/executions/${executionData.id}/${action}`, body);
      setSuccessMessage(
        `${action.charAt(0).toUpperCase() + action.slice(1)} successful.`
      );
      await loadExecutionDetails(executionData.id);
    } catch (err) {
      setErrorMessage(
        err?.response?.data?.error || `${action} failed.`
      );
    } finally {
      setLoading(false);
    }
  };

  /* ── Field renderer — respects allowed_values from schema (FIX F1 synergy) ── */
  const renderField = (field, config) => {
    const val    = formData[field] ?? "";
    const err    = !!fieldErrors[field];
    const cls    = `exec-input${err ? " exec-input--error" : ""}`;
    const selCls = `exec-select${err ? " exec-select--error" : ""}`;

    const onChange = (v) => {
      setFormData(p => ({ ...p, [field]: v }));
      setFieldErrors(p => ({ ...p, [field]: "" }));
    };

    if (config?.type === "boolean") {
      return (
        <select
          className={selCls}
          value={String(val)}
          onChange={e => onChange(e.target.value === "true")}
        >
          <option value="false">false</option>
          <option value="true">true</option>
        </select>
      );
    }

    // allowed_values renders as a dropdown (works once WorkflowEditor saves it — FIX F1)
    if (Array.isArray(config?.allowed_values) && config.allowed_values.length > 0) {
      return (
        <select
          className={selCls}
          value={val}
          onChange={e => onChange(e.target.value)}
        >
          <option value="">— Select —</option>
          {config.allowed_values.map(v => (
            <option key={v} value={v}>{v}</option>
          ))}
        </select>
      );
    }

    return (
      <input
        className={cls}
        type={config?.type === "number" ? "number" : "text"}
        value={val}
        placeholder={config?.type || "value"}
        onChange={e => onChange(e.target.value)}
      />
    );
  };

  const isWaiting   = executionData?.status?.toLowerCase() === "waiting_for_approval";
  const isFailed    = executionData?.status?.toLowerCase() === "failed";
  const isCompleted = executionData?.status?.toLowerCase() === "completed";

  return (
    <div className="workflow-execution-page">

      {/* ── Header ── */}
      <div className="page-header">
        <div className="page-header-left">
          <h2 className="page-title">
            {viewOnlyMode ? "Execution Details" : "Workflow Execution"}
          </h2>
          <p className="page-subtitle">
            Trigger a workflow run and manage step approvals in real-time.
          </p>
        </div>
      </div>

      {/* ── Banners ── */}
      {errorMessage && (
        <div className="execution-banner execution-banner--error">
          <span>⚠</span>
          <span style={{ flex: 1 }}>{errorMessage}</span>
          <button
            className="execution-banner-close"
            onClick={() => setErrorMessage("")}
          >
            ✕
          </button>
        </div>
      )}
      {successMessage && (
        <div className="execution-banner execution-banner--success">
          <span>✓</span>
          <span style={{ flex: 1 }}>{successMessage}</span>
          <button
            className="execution-banner-close"
            onClick={() => setSuccessMessage("")}
          >
            ✕
          </button>
        </div>
      )}

      <div className="exec-layout">

        {/* ── Left panel: Start Execution ── */}
        {!viewOnlyMode && (
          <div className="exec-start-panel">
            <div className="exec-panel-header">
              <h3 className="exec-panel-title">⚡ Start Execution</h3>
            </div>
            <div className="exec-panel-body">

              {/* Workflow selector */}
              <div className="exec-field">
                <label className="exec-field-label">Workflow</label>
                <select
                  className="exec-select"
                  value={selectedWorkflowId}
                  onChange={e => handleWorkflowChange(e.target.value)}
                >
                  <option value="">— Select workflow —</option>
                  {workflows.map(wf => (
                    <option key={wf.id} value={wf.id}>
                      {wf.name} (v{wf.version})
                    </option>
                  ))}
                </select>
              </div>

              {/* Started by */}
              <div className="exec-field">
                <label className="exec-field-label">
                  Started By <span className="required-mark">*</span>
                </label>
                <input
                  className="exec-input"
                  type="text"
                  placeholder="Enter your name or email"
                  value={startedBy}
                  onChange={e => setStartedBy(e.target.value)}
                />
              </div>

              {/* Dynamic schema fields */}
              {Object.keys(schema).length > 0 && (
                <>
                  <hr className="exec-input-divider" />
                  <div className="exec-subsection-title">Input Data</div>
                  {Object.entries(schema).map(([field, config]) => (
                    <div className="exec-field" key={field}>
                      <label className="exec-field-label">
                        {field}
                        {config?.required && (
                          <span className="required-mark"> *</span>
                        )}
                        <span className="exec-field-label-type">
                          {" "}({config?.type})
                        </span>
                      </label>
                      {renderField(field, config)}
                      {fieldErrors[field] && (
                        <span className="exec-field-error">
                          ⚠ {fieldErrors[field]}
                        </span>
                      )}
                    </div>
                  ))}
                </>
              )}

              {loading && !Object.keys(schema).length && selectedWorkflowId && (
                <div
                  style={{
                    display:    "flex",
                    alignItems: "center",
                    gap:        8,
                    color:      "var(--color-text-muted)",
                    fontSize:   13,
                  }}
                >
                  <span className="spinner spinner--sm" /> Loading schema…
                </div>
              )}
            </div>

            <div className="exec-panel-footer">
              <button
                className="btn btn--primary btn--full exec-submit-btn"
                onClick={handleExecute}
                disabled={loading}
              >
                {loading
                  ? <><span className="spinner spinner--sm" /> Executing…</>
                  : "▶  Execute Workflow"}
              </button>
            </div>
          </div>
        )}

        {/* ── Right panel: Status + Logs ── */}
        <div className="exec-right-panel">

          {/* ── Execution status card ── */}
          {executionData && (
            <div className="exec-status-card">
              <div className="exec-status-header">
                <h3 className="exec-status-title">Execution Status</h3>
                <span
                  className={`exec-status-badge exec-status-badge--${executionData.status?.toLowerCase()}`}
                >
                  {executionData.status}
                </span>
              </div>
              <div className="exec-status-body">
                <div className="exec-meta-grid">
                  <div className="exec-meta-item">
                    <span className="exec-meta-label">Execution ID</span>
                    <span className="exec-meta-value exec-meta-value--mono">
                      {executionData.id?.slice(0, 16)}…
                    </span>
                  </div>
                  <div className="exec-meta-item">
                    <span className="exec-meta-label">Started By</span>
                    <span className="exec-meta-value">
                      {executionData.startedBy || startedBy || "—"}
                    </span>
                  </div>
                  <div className="exec-meta-item">
                    <span className="exec-meta-label">Start Time</span>
                    <span className="exec-meta-value">
                      {executionData.startedAt
                        ? new Date(executionData.startedAt).toLocaleString()
                        : "—"}
                    </span>
                  </div>
                  <div className="exec-meta-item">
                    <span className="exec-meta-label">End Time</span>
                    <span className="exec-meta-value">
                      {executionData.completedAt
                        ? new Date(executionData.completedAt).toLocaleString()
                        : "—"}
                    </span>
                  </div>
                  <div className="exec-meta-item">
                    <span className="exec-meta-label">Version</span>
                    <span className="exec-meta-value exec-meta-value--mono">
                      v{executionData.workflowVersion || selectedWorkflowVersion || "—"}
                    </span>
                  </div>
                  <div className="exec-meta-item">
                    <span className="exec-meta-label">Retry Count</span>
                    <span className="exec-meta-value">
                      {executionData.retryCount ?? 0}
                    </span>
                  </div>
                </div>

                {/* Approval bar */}
                {isWaiting && (
                  <div className="approval-bar">
                    <p className="approval-prompt">
                      ⏳ Waiting for <strong>approval</strong> to continue to the next step.
                    </p>
                    <div className="approval-actions">
                      <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
                        <input
                          className="exec-input"
                          type="email"
                          placeholder="Approver email"
                          value={approveEmail}
                          onChange={e => setApproveEmail(e.target.value)}
                          style={{ height: 30, fontSize: 12 }}
                        />
                        <div style={{ display: "flex", gap: 8 }}>
                          <button
                            className="btn btn--success btn--sm"
                            disabled={loading}
                            onClick={() =>
                              handleAction("approve", { approverEmail: approveEmail })
                            }
                          >
                            ✓ Approve
                          </button>
                          <button
                            className="btn btn--danger btn--sm"
                            disabled={loading}
                            onClick={() =>
                              handleAction("reject", { reason: "Rejected by user" })
                            }
                          >
                            ✕ Reject
                          </button>
                        </div>
                      </div>
                    </div>
                  </div>
                )}

                {/* Action buttons */}
                <div style={{ display: "flex", gap: 10, marginTop: 12 }}>
                  {!isCompleted && !isWaiting && (
                    <button
                      className="btn btn--secondary btn--sm"
                      onClick={() => handleAction("cancel")}
                      disabled={loading}
                    >
                      ✕ Cancel
                    </button>
                  )}
                  {isFailed && (
                    <button
                      className="btn btn--primary btn--sm"
                      onClick={() => handleAction("retry")}
                      disabled={loading}
                    >
                      ↺ Retry
                    </button>
                  )}
                  <button
                    className="btn btn--ghost btn--sm"
                    onClick={() => loadExecutionDetails(executionData.id)}
                    disabled={loading}
                  >
                    ↻ Refresh
                  </button>
                </div>
              </div>
            </div>
          )}

          {/* ── Execution logs table ── */}
          <div className="exec-logs-card">
            <div className="exec-logs-header">
              <h3 className="exec-logs-title">Execution Logs</h3>
              {logs.length > 0 && (
                <span className="exec-logs-count">{logs.length} steps</span>
              )}
            </div>
            <div className="table-scroll">
              <table className="exec-logs-table">
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
                  {logs.length === 0 ? (
                    <tr>
                      <td colSpan={7} style={{ textAlign: "center", padding: "44px 20px" }}>
                        <div style={{ color: "var(--color-text-muted)", fontSize: 13 }}>
                          {executionData
                            ? "No step logs recorded yet."
                            : "Start an execution to see step-by-step logs here."}
                        </div>
                      </td>
                    </tr>
                  ) : (
                    logs.map(log => {
                      let dur = "—";
                      if (log.startedAt && log.endedAt) {
                        const ms = new Date(log.endedAt) - new Date(log.startedAt);
                        dur = `${ms} ms`;
                      } else if (log.duration != null) {
                        dur = `${log.duration} ms`;
                      }
                      return (
                        <tr key={log.id}>
                          <td style={{ fontWeight: 600 }}>{log.stepName}</td>
                          <td>
                            <span className={`badge badge--${log.stepType}`}>
                              <span className="badge-dot" />
                              {log.stepType}
                            </span>
                          </td>
                          <td>
                            <span
                              className={`exec-status-badge exec-status-badge--${log.status?.toLowerCase()}`}
                            >
                              {log.status}
                            </span>
                          </td>
                          <td>
                            <span
                              className="log-rule-code"
                              title={log.evaluatedRules}
                            >
                              {log.evaluatedRules || "—"}
                            </span>
                          </td>
                          <td style={{ fontSize: 12, color: "var(--color-text-muted)" }}>
                            {log.selectedNextStepId || "END"}
                          </td>
                          <td className="log-duration">{dur}</td>
                          <td>
                            {log.errorMessage ? (
                              <span
                                className="log-error-text"
                                title={log.errorMessage}
                              >
                                {log.errorMessage.slice(0, 60)}
                                {log.errorMessage.length > 60 ? "…" : ""}
                              </span>
                            ) : (
                              <span style={{ color: "var(--color-text-muted)" }}>—</span>
                            )}
                          </td>
                        </tr>
                      );
                    })
                  )}
                </tbody>
              </table>
            </div>
          </div>

        </div>
      </div>
    </div>
  );
}

export default WorkflowExecution;
