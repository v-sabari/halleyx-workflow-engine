import { useEffect, useState, useCallback } from "react";
import { useNavigate } from "react-router-dom";
import api from "../services/api";
import "../styles/WorkflowList.css";

function WorkflowList() {
  const navigate = useNavigate();

  const [workflows, setWorkflows] = useState([]);
  const [searchText, setSearchText] = useState("");
  const [statusFilter, setStatusFilter] = useState("");
  const [page, setPage] = useState(0);
  const [size] = useState(10);
  const [totalPages, setTotalPages] = useState(1);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [deleting, setDeleting] = useState(null);

  const fetchWorkflows = useCallback(async (search, status, pg) => {
    try {
      setLoading(true);
      setError("");
      const params = { page: pg, size };
      if (search) params.search = search;
      if (status !== "") params.isActive = status === "active";
      const res = await api.get("/workflows", { params });
      const data = res.data;
      // Backend returns Page<Map<String,Object>> — each item has .workflow, .steps, .stepCount
      const items = (data.content || []).map(item => ({
        ...(item.workflow || item),
        stepCount: item.stepCount ?? 0,
        steps: item.steps ?? [],
      }));
      setWorkflows(items);
      setTotalPages(data.totalPages || 1);
      setTotalElements(data.totalElements || 0);
      setPage(data.number ?? pg);
    } catch (err) {
      setError(err?.response?.data?.error || err?.message || "Failed to load workflows.");
      setWorkflows([]);
    } finally {
      setLoading(false);
    }
  }, [size]);

  useEffect(() => {
    fetchWorkflows(searchText, statusFilter, page);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page]);

  const handleSearch = () => { setPage(0); fetchWorkflows(searchText, statusFilter, 0); };
  const handleStatusChange = (e) => {
    const v = e.target.value;
    setStatusFilter(v);
    setPage(0);
    fetchWorkflows(searchText, v, 0);
  };
  const handleClear = () => {
    setSearchText(""); setStatusFilter("");
    setPage(0); fetchWorkflows("", "", 0);
  };

  const handleDelete = async (id, name) => {
    if (!window.confirm(`Delete workflow "${name}"? This cannot be undone.`)) return;
    try {
      setDeleting(id);
      await api.delete(`/workflows/${id}`);
      fetchWorkflows(searchText, statusFilter, page);
    } catch (err) {
      setError(err?.response?.data?.error || "Failed to delete workflow.");
    } finally {
      setDeleting(null);
    }
  };

  const activeCount   = workflows.filter(w => w.isActive).length;
  const inactiveCount = workflows.filter(w => !w.isActive).length;
  const start = totalElements === 0 ? 0 : page * size + 1;
  const end   = Math.min((page + 1) * size, totalElements);

  return (
    <div className="wf-list-page">

      {/* ── Header ── */}
      <div className="page-header">
        <div className="page-header-left">
          <h2 className="page-title">Workflows</h2>
          <p className="page-subtitle">Manage workflows, versions, execution access, and status.</p>
        </div>
        <div className="page-header-actions">
          <button className="btn btn--primary" onClick={() => navigate("/editor")}>
            + New Workflow
          </button>
        </div>
      </div>

      {/* ── Stats ── */}
      <div className="wf-stats-row">
        <div className="wf-stat">
          <div className="wf-stat-header">
            <span className="wf-stat-label">Total Workflows</span>
            <div className="wf-stat-icon wf-stat-icon--blue">⚙️</div>
          </div>
          <div className="wf-stat-value">{totalElements}</div>
        </div>
        <div className="wf-stat">
          <div className="wf-stat-header">
            <span className="wf-stat-label">Active</span>
            <div className="wf-stat-icon wf-stat-icon--green">✅</div>
          </div>
          <div className="wf-stat-value" style={{ color: "var(--color-success-600)" }}>{activeCount}</div>
        </div>
        <div className="wf-stat">
          <div className="wf-stat-header">
            <span className="wf-stat-label">Inactive</span>
            <div className="wf-stat-icon wf-stat-icon--orange">⏸️</div>
          </div>
          <div className="wf-stat-value">{inactiveCount}</div>
        </div>
        <div className="wf-stat">
          <div className="wf-stat-header">
            <span className="wf-stat-label">This Page</span>
            <div className="wf-stat-icon wf-stat-icon--red">📄</div>
          </div>
          <div className="wf-stat-value">{workflows.length}</div>
        </div>
      </div>

      {/* ── Error ── */}
      {error && (
        <div className="banner banner--error">
          <span>⚠</span>
          <span style={{ flex: 1 }}>{error}</span>
          <button className="banner-close" onClick={() => setError("")}>✕</button>
        </div>
      )}

      {/* ── Filter bar ── */}
      <div className="filter-bar">
        <div className="filter-bar-search-wrap">
          <span className="filter-bar-search-icon">🔍</span>
          <input
            className="form-control"
            type="text"
            placeholder="Search workflow name…"
            value={searchText}
            onChange={(e) => setSearchText(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && handleSearch()}
          />
        </div>
        <select className="filter-bar-select" value={statusFilter} onChange={handleStatusChange}>
          <option value="">All Status</option>
          <option value="active">Active</option>
          <option value="inactive">Inactive</option>
        </select>
        <button className="btn btn--primary" onClick={handleSearch} disabled={loading}>
          {loading ? <span className="spinner spinner--sm" /> : null}
          Search
        </button>
        <button className="btn btn--secondary" onClick={handleClear} disabled={loading}>Clear</button>
      </div>

      {/* ── Table card ── */}
      <div className="wf-table-card">
        <div className="table-scroll">
          <table className="data-table">
            <thead>
              <tr>
                <th>ID</th>
                <th>Name</th>
                <th>Steps</th>
                <th>Version</th>
                <th>Status</th>
                <th style={{ textAlign: "right" }}>Actions</th>
              </tr>
            </thead>
            <tbody>
              {loading ? (
                <tr>
                  <td colSpan={6} style={{ textAlign: "center", padding: "52px 20px" }}>
                    <div style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: 12 }}>
                      <div className="spinner spinner--xl" />
                      <span style={{ fontSize: 13, color: "var(--color-text-muted)" }}>Loading workflows…</span>
                    </div>
                  </td>
                </tr>
              ) : workflows.length === 0 ? (
                <tr>
                  <td colSpan={6} style={{ padding: 0, border: "none" }}>
                    <div className="empty-state">
                      <div className="empty-state-icon">📭</div>
                      <div className="empty-state-title">No workflows found</div>
                      <p className="empty-state-body">Try adjusting your filters or create your first workflow.</p>
                      <button className="btn btn--primary" onClick={() => navigate("/editor")}>
                        + Create Workflow
                      </button>
                    </div>
                  </td>
                </tr>
              ) : (
                workflows.map((wf) => (
                  <tr key={wf.id}>
                    <td>
                      <span className="td-id" title={wf.id}>{wf.id?.slice(0, 8)}…</span>
                    </td>
                    <td>
                      <div className="wf-name-cell">
                        <div className="td-name">{wf.name}</div>
                        {wf.description && (
                          <div className="wf-description">{wf.description}</div>
                        )}
                      </div>
                    </td>
                    <td>
                      <span className="step-count-chip">
                        {wf.stepCount ?? 0} steps
                      </span>
                    </td>
                    <td>
                      <span className="version-chip">v{wf.version}</span>
                    </td>
                    <td>
                      <span className={wf.isActive ? "badge badge--active" : "badge badge--inactive"}>
                        <span className="badge-dot" />
                        {wf.isActive ? "Active" : "Inactive"}
                      </span>
                    </td>
                    <td style={{ textAlign: "right" }}>
                      <div className="action-group" style={{ justifyContent: "flex-end" }}>
                        <button className="btn btn--secondary btn--sm"
                          onClick={() => navigate(`/editor?workflowId=${wf.id}`)}>
                          Edit
                        </button>
                        <button
                          className="btn btn--primary btn--sm"
                          onClick={() => navigate(`/execute?workflowId=${wf.id}`)}
                          disabled={!wf.isActive}
                          title={!wf.isActive ? "Activate workflow to execute" : ""}
                        >
                          ▶ Execute
                        </button>
                        <button
                          className="btn btn--danger btn--sm"
                          onClick={() => handleDelete(wf.id, wf.name)}
                          disabled={deleting === wf.id}
                        >
                          {deleting === wf.id ? <span className="spinner spinner--sm" /> : "Delete"}
                        </button>
                      </div>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>

        {/* ── Pagination ── */}
        {!loading && totalPages > 1 && (
          <div className="pagination">
            <span className="pagination-info">
              Showing <strong>{start}–{end}</strong> of <strong>{totalElements}</strong> workflows
            </span>
            <div className="pagination-controls">
              <button className="pagination-btn" disabled={page === 0} onClick={() => setPage(p => p - 1)}>←</button>
              {[...Array(Math.min(totalPages, 5))].map((_, i) => (
                <button key={i}
                  className={`pagination-btn${page === i ? " active" : ""}`}
                  onClick={() => setPage(i)}>
                  {i + 1}
                </button>
              ))}
              {totalPages > 5 && <span className="pagination-separator">…</span>}
              {totalPages > 5 && (
                <button
                  className={`pagination-btn${page === totalPages - 1 ? " active" : ""}`}
                  onClick={() => setPage(totalPages - 1)}>
                  {totalPages}
                </button>
              )}
              <button className="pagination-btn" disabled={page + 1 >= totalPages} onClick={() => setPage(p => p + 1)}>→</button>
            </div>
          </div>
        )}
      </div>

    </div>
  );
}

export default WorkflowList;