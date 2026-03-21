import { useEffect, useMemo, useState, useRef } from "react";
import { useSearchParams } from "react-router-dom";
import api from "../services/api";
import "../styles/pages.css";

function StepRuleEditor() {
  const [searchParams] = useSearchParams();
  const urlWorkflowId = searchParams.get("workflowId");
  const urlStepId     = searchParams.get("stepId");

  const [workflows, setWorkflows]           = useState([]);
  const [steps, setSteps]                   = useState([]);
  const [rules, setRules]                   = useState([]);
  const [selectedWorkflow, setSelectedWorkflow] = useState(urlWorkflowId || "");
  const [selectedStep, setSelectedStep]     = useState(urlStepId || "");

  // Rule form — backend field is "condition" (Rule.java @Column name="rule_condition")
  const [condition, setCondition]           = useState("");
  const [nextStepId, setNextStepId]         = useState("");
  const [priority, setPriority]             = useState(1);
  const [editingRuleId, setEditingRuleId]   = useState("");

  const [loading, setLoading]               = useState(false);
  const [errorMessage, setErrorMessage]     = useState("");
  const [successMessage, setSuccessMessage] = useState("");

  const dragItem       = useRef(null);
  const rulesSnapshot  = useRef([]);

  const stepNameMap = useMemo(() => {
    const m = {};
    steps.forEach(s => { m[s.id] = `${s.stepName} (${s.stepType})`; });
    return m;
  }, [steps]);

  const clearMessages = () => { setErrorMessage(""); setSuccessMessage(""); };
  const resetForm     = () => { setCondition(""); setNextStepId(""); setPriority(1); setEditingRuleId(""); };

  // Load workflows on mount
  useEffect(() => {
    api.get("/workflows", { params: { page: 0, size: 100 } })
      .then(r => {
        const items = (r.data.content || []).map(item => item.workflow || item);
        setWorkflows(items);
      })
      .catch(() => setErrorMessage("Failed to load workflows. Check your connection."));
  }, []);

  // Auto-load from URL params
  useEffect(() => {
    if (urlWorkflowId) {
      loadSteps(urlWorkflowId);
      if (urlStepId) loadRules(urlStepId);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [urlWorkflowId, urlStepId]);

  const loadSteps = async (wfId) => {
    try {
      setLoading(true);
      const r = await api.get(`/workflows/${wfId}`);
      setSteps(r.data.steps || []);
    } catch {
      setErrorMessage("Failed to load steps.");
      setSteps([]);
    } finally {
      setLoading(false);
    }
  };

  const loadRules = async (stepId) => {
    try {
      setLoading(true);
      const r = await api.get(`/steps/${stepId}/rules`);
      const data = Array.isArray(r.data) ? r.data : (r.data.content || r.data || []);
      setRules([...data].sort((a, b) => (a.priority || 0) - (b.priority || 0)));
    } catch {
      setErrorMessage("Failed to load rules.");
      setRules([]);
    } finally {
      setLoading(false);
    }
  };

  const validate = () => {
    if (!selectedWorkflow) { setErrorMessage("Select a workflow first."); return false; }
    if (!selectedStep) { setErrorMessage("Select a step first."); return false; }
    if (!condition.trim()) { setErrorMessage("Condition expression is required."); return false; }
    if (Number(priority) < 1) { setErrorMessage("Priority must be ≥ 1."); return false; }
    return true;
  };

  const handleWorkflowChange = async (id) => {
    setSelectedWorkflow(id);
    setSelectedStep("");
    setSteps([]);
    setRules([]);
    resetForm();
    clearMessages();
    if (id) await loadSteps(id);
  };

  const handleStepChange = async (id) => {
    setSelectedStep(id);
    setRules([]);
    resetForm();
    clearMessages();
    if (id) await loadRules(id);
  };

  const handleSaveRule = async () => {
    if (!validate()) return;
    try {
      setLoading(true); clearMessages();
      // Backend Rule entity: field "condition" (mapped to "rule_condition" column)
      const payload = {
        condition: condition.trim(),
        nextStepId: nextStepId || null,
        priority: Number(priority)
      };
      if (editingRuleId) {
        await api.put(`/rules/${editingRuleId}`, payload);
        setSuccessMessage("Rule updated successfully.");
      } else {
        await api.post(`/steps/${selectedStep}/rules`, payload);
        setSuccessMessage("Rule added successfully.");
      }
      resetForm();
      await loadRules(selectedStep);
    } catch (err) {
      setErrorMessage(err?.response?.data?.error || err?.response?.data?.message || "Failed to save rule.");
    } finally {
      setLoading(false);
    }
  };

  const handleDelete = async (ruleId) => {
    if (!window.confirm("Delete this rule?")) return;
    try {
      setLoading(true); clearMessages();
      await api.delete(`/rules/${ruleId}`);
      setSuccessMessage("Rule deleted.");
      await loadRules(selectedStep);
    } catch (err) {
      setErrorMessage(err?.response?.data?.error || "Failed to delete rule.");
    } finally {
      setLoading(false);
    }
  };

  // Drag-to-reorder
  const handleDragStart = (i) => { dragItem.current = i; rulesSnapshot.current = [...rules]; };
  const handleDragEnter = (i) => {
    const upd = [...rules];
    const [moved] = upd.splice(dragItem.current, 1);
    upd.splice(i, 0, moved);
    dragItem.current = i;
    setRules(upd);
  };
  const handleDragEnd = async () => {
    try {
      setLoading(true); clearMessages();
      await Promise.all(
        rules.map((r, i) =>
          api.put(`/rules/${r.id}`, {
            condition: r.condition,
            nextStepId: r.nextStepId || null,
            priority: i + 1
          })
        )
      );
      setSuccessMessage("Rule order saved.");
      await loadRules(selectedStep);
    } catch {
      setRules(rulesSnapshot.current);
      setErrorMessage("Failed to save order. Restored original order.");
    } finally {
      setLoading(false);
      dragItem.current = null;
    }
  };

  return (
    <div className="step-rule-page">
      <div className="page-header">
        <div className="page-header-left">
          <h2 className="page-title">Step Rule Editor</h2>
          <p className="page-subtitle">Configure dynamic conditions, rule priority, and next-step routing.</p>
        </div>
      </div>

      {errorMessage && (
        <div className="rule-banner rule-banner--error">
          <span>⚠</span>
          <span style={{ flex: 1 }}>{errorMessage}</span>
          <button className="rule-banner-close" onClick={() => setErrorMessage("")}>✕</button>
        </div>
      )}
      {successMessage && (
        <div className="rule-banner rule-banner--success">
          <span>✓</span>
          <span style={{ flex: 1 }}>{successMessage}</span>
          <button className="rule-banner-close" onClick={() => setSuccessMessage("")}>✕</button>
        </div>
      )}

      {/* Selectors */}
      <div className="rule-selector-row">
        <div className="rule-selector-card">
          <label className="rule-selector-label">Select Workflow</label>
          <select
            className="rule-select"
            value={selectedWorkflow}
            onChange={e => handleWorkflowChange(e.target.value)}
          >
            <option value="">— Select Workflow —</option>
            {workflows.map(wf => (
              <option key={wf.id} value={wf.id}>{wf.name} (v{wf.version})</option>
            ))}
          </select>
          {!loading && workflows.length === 0 && (
            <p className="rule-empty-hint">No workflows found. Create one first.</p>
          )}
        </div>
        <div className="rule-selector-card">
          <label className="rule-selector-label">Select Step</label>
          <select
            className="rule-select"
            value={selectedStep}
            onChange={e => handleStepChange(e.target.value)}
            disabled={!selectedWorkflow || loading}
          >
            <option value="">— Select Step —</option>
            {[...steps].sort((a,b)=>(a.sequenceOrder||0)-(b.sequenceOrder||0)).map(s => (
              <option key={s.id} value={s.id}>#{s.sequenceOrder} {s.stepName} ({s.stepType})</option>
            ))}
          </select>
        </div>
      </div>

      {/* Add/Edit form */}
      {selectedStep && (
        <div className="rule-form-card">
          <div className="rule-form-header">
            <h3 className="rule-form-title">
              {editingRuleId ? "✏️ Edit Rule" : "+ Add Rule"}
            </h3>
            {editingRuleId && (
              <button className="btn btn--ghost btn--sm" onClick={() => { resetForm(); clearMessages(); }}>
                Cancel Edit
              </button>
            )}
          </div>
          <div className="rule-form-body">
            <div className="rule-form-grid">
              <div>
                <label className="rule-form-label">
                  Condition Expression <span className="required-mark">*</span>
                </label>
                <input
                  className="rule-input"
                  type="text"
                  placeholder="e.g. amount > 100 && priority == 'High' or DEFAULT"
                  value={condition}
                  onChange={e => setCondition(e.target.value)}
                />
                <p className="rule-help">
                  Supported: <code>==</code> <code>!=</code> <code>&gt;</code> <code>&lt;</code>{" "}
                  <code>&amp;&amp;</code> <code>||</code> <code>contains()</code>{" "}
                  <code>startsWith()</code> <code>endsWith()</code>. Use <strong>DEFAULT</strong> as fallback.
                </p>
              </div>
              <div>
                <label className="rule-form-label">Next Step</label>
                <select
                  className="rule-select"
                  value={nextStepId}
                  onChange={e => setNextStepId(e.target.value)}
                >
                  <option value="">— End Workflow —</option>
                  {steps.filter(s => s.id !== selectedStep).map(s => (
                    <option key={s.id} value={s.id}>{s.stepName} ({s.stepType})</option>
                  ))}
                </select>
              </div>
              <div>
                <label className="rule-form-label">Priority</label>
                <input
                  className="rule-input rule-input--number"
                  type="number"
                  min={1}
                  value={priority}
                  onChange={e => setPriority(e.target.value)}
                />
              </div>
            </div>
            <div className="rule-form-actions">
              <button className="btn btn--primary" onClick={handleSaveRule} disabled={loading}>
                {loading ? <span className="spinner spinner--sm" /> : null}
                {editingRuleId ? "Update Rule" : "Add Rule"}
              </button>
              <button className="btn btn--secondary" onClick={() => { resetForm(); clearMessages(); }} disabled={loading}>
                {editingRuleId ? "Cancel" : "Clear"}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Rules table */}
      <div className="rule-table-card">
        <div className="rule-table-header">
          <h3 className="rule-table-title">
            Existing Rules
            {rules.length > 1 && (
              <span className="rule-drag-hint">⠿ drag rows to reorder</span>
            )}
            {rules.length > 0 && (
              <span className="rule-priority-chip" style={{ marginLeft: 8 }}>{rules.length}</span>
            )}
          </h3>
          {selectedStep && (
            <button className="btn btn--primary btn--sm" onClick={() => { resetForm(); clearMessages(); }}>
              + Add Rule
            </button>
          )}
        </div>
        <div className="table-scroll">
          <table className="rule-table">
            <thead>
              <tr>
                <th style={{ width: 36 }}>⠿</th>
                <th style={{ width: 80 }}>Priority</th>
                <th>Condition</th>
                <th>Next Step</th>
                <th style={{ textAlign: "right", width: 160 }}>Actions</th>
              </tr>
            </thead>
            <tbody>
              {rules.length === 0 ? (
                <tr>
                  <td colSpan={5} style={{ textAlign: "center", padding: "36px 20px", color: "var(--color-text-muted)", fontSize: 13 }}>
                    {selectedStep ? "No rules defined. Add a rule above." : "Select a step to view its rules."}
                  </td>
                </tr>
              ) : (
                rules.map((r, i) => (
                  <tr
                    key={r.id}
                    draggable
                    className="draggable-row"
                    onDragStart={() => handleDragStart(i)}
                    onDragEnter={() => handleDragEnter(i)}
                    onDragEnd={handleDragEnd}
                    onDragOver={e => e.preventDefault()}
                  >
                    <td className="rule-drag-cell">⠿</td>
                    <td><span className="rule-priority-chip">{r.priority}</span></td>
                    <td>
                      <span className="rule-condition-pill" title={r.condition}>{r.condition}</span>
                    </td>
                    <td>
                      {r.nextStepId
                        ? <span className="rule-next-step-pill">{stepNameMap[r.nextStepId] || r.nextStepId}</span>
                        : <span className="rule-end-pill">END</span>
                      }
                    </td>
                    <td style={{ textAlign: "right" }}>
                      <div className="action-group" style={{ justifyContent: "flex-end" }}>
                        <button
                          className="btn btn--secondary btn--sm"
                          disabled={loading}
                          onClick={() => {
                            setEditingRuleId(r.id);
                            setCondition(r.condition || "");
                            setNextStepId(r.nextStepId || "");
                            setPriority(r.priority || 1);
                          }}
                        >
                          Edit
                        </button>
                        <button
                          className="btn btn--danger btn--sm"
                          disabled={loading}
                          onClick={() => handleDelete(r.id)}
                        >
                          Delete
                        </button>
                      </div>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}

export default StepRuleEditor;