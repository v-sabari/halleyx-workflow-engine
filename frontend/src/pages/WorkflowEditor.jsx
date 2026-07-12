import { useEffect, useState, useCallback } from "react";
import { useSearchParams, useNavigate } from "react-router-dom";
import api from "../services/api";
import "../styles/WorkflowEditor.css";

const STEP_TYPES  = ["TASK", "APPROVAL", "NOTIFICATION"];
const FIELD_TYPES = ["string", "number", "boolean"];

const blankStep = () => ({
  stepName: "", stepType: "TASK", sequenceOrder: 1, configuration: ""
});

// FIX F1: added allowedValues to blank field
const blankField = () => ({
  name: "", type: "string", required: true, defaultValue: "", allowedValues: ""
});

function WorkflowEditor() {
  const [searchParams] = useSearchParams();
  const navigate       = useNavigate();
  const workflowId     = searchParams.get("workflowId");

  /* ── Workflow state ── */
  const [name, setName]               = useState("");
  const [description, setDescription] = useState("");
  const [isActive, setIsActive]       = useState(true);
  const [version, setVersion]         = useState(null);
  const [schemaFields, setSchemaFields] = useState([]);

  /* ── Steps state ── */
  const [steps, setSteps]               = useState([]);
  const [stepForm, setStepForm]         = useState(blankStep());
  const [editingStepId, setEditingStepId] = useState(null);
  const [showStepForm, setShowStepForm] = useState(false);

  /* ── UI state ── */
  const [loading, setLoading]           = useState(false);
  const [stepLoading, setStepLoading]   = useState(false);
  const [errorMessage, setErrorMessage] = useState("");
  const [successMessage, setSuccessMessage] = useState("");
  const [nameError, setNameError]       = useState("");

  /* ── Load existing workflow ── */
  const loadWorkflow = useCallback(async (id) => {
    try {
      setLoading(true);
      const r = await api.get(`/workflows/${id}`);
      const { workflow, steps: loadedSteps } = r.data;
      setName(workflow.name || "");
      setDescription(workflow.description || "");
      setIsActive(workflow.isActive ?? true);
      setVersion(workflow.version);
      setSteps(loadedSteps || []);

      // FIX F1: parse and restore allowedValues when loading existing schema
      try {
        const schema =
          typeof workflow.inputSchema === "string"
            ? JSON.parse(workflow.inputSchema || "{}")
            : workflow.inputSchema || {};
        setSchemaFields(
          Object.entries(schema).map(([k, v]) => ({
            name:          k,
            type:          v.type || "string",
            required:      v.required ?? true,
            defaultValue:  v.defaultValue || "",
            // Restore allowed_values array → comma-separated string for the input
            allowedValues: Array.isArray(v.allowed_values)
              ? v.allowed_values.join(", ")
              : (v.allowed_values || ""),
          }))
        );
      } catch {
        setSchemaFields([]);
      }
    } catch {
      setErrorMessage("Failed to load workflow.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (workflowId) loadWorkflow(workflowId);
  }, [workflowId, loadWorkflow]);

  /* ── Schema helpers ── */

  // FIX F1: buildSchemaJSON now serialises allowed_values when present
  const buildSchemaJSON = () => {
    const obj = {};
    schemaFields.forEach(f => {
      if (!f.name.trim()) return;
      const fieldDef = {
        type:     f.type,
        required: f.required,
        ...(f.defaultValue ? { defaultValue: f.defaultValue } : {}),
      };
      if (f.allowedValues && f.allowedValues.trim()) {
        fieldDef.allowed_values = f.allowedValues
          .split(",")
          .map(v => v.trim())
          .filter(Boolean);
      }
      obj[f.name.trim()] = fieldDef;
    });
    return JSON.stringify(obj, null, 2);
  };

  const addField    = () => setSchemaFields(f => [...f, blankField()]);
  const removeField = (i) => setSchemaFields(f => f.filter((_, idx) => idx !== i));
  const updateField = (i, key, value) =>
    setSchemaFields(f =>
      f.map((item, idx) => (idx === i ? { ...item, [key]: value } : item))
    );

  /* ── Save workflow ── */
  const handleSave = async () => {
    if (!name.trim()) { setNameError("Workflow name is required."); return; }
    setNameError(""); setErrorMessage(""); setSuccessMessage("");
    try {
      setLoading(true);
      const payload = {
        name:        name.trim(),
        description: description.trim(),
        isActive,
        inputSchema: buildSchemaJSON(),
      };
      if (workflowId) {
        await api.put(`/workflows/${workflowId}`, payload);
        setSuccessMessage("Workflow updated successfully.");
        loadWorkflow(workflowId);
      } else {
        const r = await api.post("/workflows", payload);
        setSuccessMessage("Workflow created successfully.");
        navigate(`/editor?workflowId=${r.data.id}`, { replace: true });
      }
    } catch (err) {
      setErrorMessage(err?.response?.data?.error || "Failed to save workflow.");
    } finally {
      setLoading(false);
    }
  };

  /* ── Steps ── */
  const handleSaveStep = async () => {
    if (!stepForm.stepName.trim()) return;
    if (!workflowId) {
      setErrorMessage("Save the workflow first before adding steps.");
      return;
    }
    try {
      setStepLoading(true); setErrorMessage(""); setSuccessMessage("");
      if (editingStepId) {
        await api.put(`/steps/${editingStepId}`, stepForm);
        setSuccessMessage("Step updated.");
      } else {
        await api.post(`/workflows/${workflowId}/steps`, stepForm);
        setSuccessMessage("Step added.");
      }
      setStepForm(blankStep()); setEditingStepId(null); setShowStepForm(false);
      loadWorkflow(workflowId);
    } catch (err) {
      setErrorMessage(err?.response?.data?.error || "Failed to save step.");
    } finally {
      setStepLoading(false);
    }
  };

  const handleDeleteStep = async (id) => {
    if (!window.confirm("Delete this step? All its rules will also be deleted.")) return;
    try {
      setStepLoading(true);
      await api.delete(`/steps/${id}`);
      setSuccessMessage("Step deleted.");
      loadWorkflow(workflowId);
    } catch (err) {
      setErrorMessage(err?.response?.data?.error || "Failed to delete step.");
    } finally {
      setStepLoading(false);
    }
  };

  const startEditStep = (step) => {
    setStepForm({
      stepName:      step.stepName,
      stepType:      step.stepType,
      sequenceOrder: step.sequenceOrder,
      configuration: step.configuration || "",
    });
    setEditingStepId(step.id);
    setShowStepForm(true);
  };

  return (
    <div className="workflow-editor-page">

      {/* ── Header ── */}
      <div className="page-header">
        <div className="page-header-left">
          <h2 className="page-title">
            {workflowId ? "Edit Workflow" : "New Workflow"}
          </h2>
          <p className="page-subtitle">
            {workflowId
              ? "Update workflow metadata, schema fields and steps."
              : "Create a new workflow definition."}
          </p>
        </div>
        {version && (
          <div className="editor-version-badge">
            Current version <strong>v{version}</strong>
          </div>
        )}
      </div>

      {/* ── Banners ── */}
      {errorMessage && (
        <div className="editor-banner editor-banner--error">
          <span>⚠</span>
          <span style={{ flex: 1 }}>{errorMessage}</span>
          <button className="editor-banner-close" onClick={() => setErrorMessage("")}>✕</button>
        </div>
      )}
      {successMessage && (
        <div className="editor-banner editor-banner--success">
          <span>✓</span>
          <span style={{ flex: 1 }}>{successMessage}</span>
          <button className="editor-banner-close" onClick={() => setSuccessMessage("")}>✕</button>
        </div>
      )}

      {/* ── Basic Info ── */}
      <div className="editor-section">
        <div className="editor-section-header">
          <h3 className="editor-section-title">
            <span className="editor-section-icon">ℹ</span>
            Basic Information
          </h3>
        </div>
        <div className="editor-section-body">
          {loading && !name ? (
            <div className="editor-loading">
              <div className="spinner spinner--sm" /> Loading…
            </div>
          ) : (
            <>
              <div className="editor-field">
                <label className="editor-label">
                  Workflow Name <span className="required-mark">*</span>
                </label>
                <input
                  className={`editor-input${nameError ? " editor-input--error" : ""}`}
                  type="text"
                  placeholder="e.g. Expense Approval"
                  value={name}
                  onChange={e => { setName(e.target.value); setNameError(""); }}
                />
                {nameError && (
                  <span style={{ fontSize: 12, color: "var(--color-danger-600)" }}>
                    {nameError}
                  </span>
                )}
              </div>

              <div className="editor-field">
                <label className="editor-label">Description</label>
                <textarea
                  className="editor-textarea"
                  placeholder="Brief description of what this workflow does…"
                  value={description}
                  onChange={e => setDescription(e.target.value)}
                />
              </div>

              <div className="editor-field editor-field--inline">
                <label className="editor-label">Status</label>
                <label className="editor-toggle">
                  <input
                    type="checkbox"
                    checked={isActive}
                    onChange={e => setIsActive(e.target.checked)}
                  />
                  <span className="editor-toggle-slider" />
                </label>
                <span
                  style={{
                    fontSize: 13,
                    color: isActive
                      ? "var(--color-success-600)"
                      : "var(--color-text-muted)",
                    fontWeight: 600,
                  }}
                >
                  {isActive ? "Active" : "Inactive"}
                </span>
              </div>

              <div className="editor-btn-row">
                <button
                  className="btn btn--primary"
                  onClick={handleSave}
                  disabled={loading}
                >
                  {loading
                    ? <><span className="spinner spinner--sm" /> Saving…</>
                    : workflowId ? "Update Workflow" : "Create Workflow"}
                </button>
                <button
                  className="btn btn--secondary"
                  onClick={() => navigate("/")}
                >
                  Cancel
                </button>
              </div>
            </>
          )}
        </div>
      </div>

      {/* ── Input Schema ── */}
      <div className="editor-section">
        <div className="editor-section-header">
          <h3 className="editor-section-title">
            <span className="editor-section-icon">📐</span>
            Input Schema
          </h3>
          <button className="btn btn--secondary btn--sm" onClick={addField}>
            + Add Field
          </button>
        </div>
        <div className="editor-section-body">
          <div className="editor-notice">
            ℹ Define the data fields this workflow accepts as input when executed.
          </div>

          {schemaFields.length === 0 ? (
            <p style={{ fontSize: 13, color: "var(--color-text-muted)", fontStyle: "italic" }}>
              No fields defined. Click &quot;+ Add Field&quot; to start building your schema.
            </p>
          ) : (
            <>
              {/* Column headers */}
              <div
                style={{
                  display: "grid",
                  gridTemplateColumns: "1fr 130px 100px 160px 160px 34px",
                  gap: 8,
                  paddingBottom: 8,
                  borderBottom: "1px solid var(--color-border)",
                  marginBottom: 8,
                }}
              >
                {["Field Name", "Type", "Required", "Default Value", "Allowed Values", ""].map(
                  (h, i) => (
                    <span
                      key={i}
                      style={{
                        fontSize: 11,
                        fontWeight: 700,
                        color: "var(--color-text-muted)",
                        textTransform: "uppercase",
                        letterSpacing: "0.8px",
                      }}
                    >
                      {h}
                    </span>
                  )
                )}
              </div>

              {/* FIX F1: schema rows now include Allowed Values column */}
              {schemaFields.map((f, i) => (
                <div
                  className="schema-field-row"
                  key={i}
                  style={{
                    gridTemplateColumns: "1fr 130px 100px 160px 160px 34px",
                  }}
                >
                  <input
                    className="editor-input"
                    placeholder="field_name"
                    value={f.name}
                    onChange={e => updateField(i, "name", e.target.value)}
                  />
                  <select
                    className="editor-select"
                    value={f.type}
                    onChange={e => updateField(i, "type", e.target.value)}
                  >
                    {FIELD_TYPES.map(t => (
                      <option key={t} value={t}>{t}</option>
                    ))}
                  </select>
                  <select
                    className="editor-select"
                    value={f.required ? "true" : "false"}
                    onChange={e =>
                      updateField(i, "required", e.target.value === "true")
                    }
                  >
                    <option value="true">Required</option>
                    <option value="false">Optional</option>
                  </select>
                  <input
                    className="editor-input"
                    placeholder="default…"
                    value={f.defaultValue}
                    onChange={e => updateField(i, "defaultValue", e.target.value)}
                  />
                  {/* FIX F1: Allowed Values input — comma-separated */}
                  <input
                    className="editor-input"
                    placeholder="High, Medium, Low"
                    title="Comma-separated allowed values (renders as dropdown on execution)"
                    value={f.allowedValues}
                    onChange={e => updateField(i, "allowedValues", e.target.value)}
                  />
                  <button
                    className="btn btn--danger btn--sm"
                    onClick={() => removeField(i)}
                    style={{ padding: "0 8px" }}
                  >
                    ✕
                  </button>
                </div>
              ))}
            </>
          )}

          {schemaFields.length > 0 && (
            <details className="schema-json-preview">
              <summary>Preview JSON schema</summary>
              <pre>{buildSchemaJSON()}</pre>
            </details>
          )}
        </div>
      </div>

      {/* ── Steps ── */}
      {workflowId && (
        <div className="editor-section">
          <div className="editor-section-header">
            <h3 className="editor-section-title">
              <span className="editor-section-icon">📋</span>
              Steps ({steps.length})
            </h3>
            <button
              className="btn btn--primary btn--sm"
              onClick={() => {
                setStepForm(blankStep());
                setEditingStepId(null);
                setShowStepForm(true);
              }}
            >
              + Add Step
            </button>
          </div>

          {showStepForm && (
            <div
              style={{
                padding:       "var(--space-5) var(--space-6)",
                borderBottom:  "1px solid var(--color-border)",
                background:    "var(--brand-50)",
              }}
            >
              <h4
                style={{
                  fontSize:     14,
                  fontWeight:   600,
                  color:        "var(--brand-800)",
                  marginBottom: 16,
                }}
              >
                {editingStepId ? "Edit Step" : "Add Step"}
              </h4>
              <div
                style={{
                  display:             "grid",
                  gridTemplateColumns: "1fr 160px 100px 1fr",
                  gap:                 16,
                  marginBottom:        16,
                }}
              >
                <div className="editor-field">
                  <label className="editor-label">
                    Step Name <span className="required-mark">*</span>
                  </label>
                  <input
                    className="editor-input"
                    placeholder="e.g. Manager Approval"
                    value={stepForm.stepName}
                    onChange={e =>
                      setStepForm(f => ({ ...f, stepName: e.target.value }))
                    }
                  />
                </div>
                <div className="editor-field">
                  <label className="editor-label">Type</label>
                  <select
                    className="editor-select"
                    value={stepForm.stepType}
                    onChange={e =>
                      setStepForm(f => ({ ...f, stepType: e.target.value }))
                    }
                  >
                    {STEP_TYPES.map(t => (
                      <option key={t} value={t}>{t}</option>
                    ))}
                  </select>
                </div>
                <div className="editor-field">
                  <label className="editor-label">Order</label>
                  <input
                    className="editor-input"
                    type="number"
                    min={1}
                    value={stepForm.sequenceOrder}
                    onChange={e =>
                      setStepForm(f => ({
                        ...f,
                        sequenceOrder: Number(e.target.value),
                      }))
                    }
                  />
                </div>
                <div className="editor-field">
                  <label className="editor-label">Config (JSON)</label>
                  <input
                    className="editor-input"
                    placeholder='{"assignee_email":"..."}'
                    value={stepForm.configuration}
                    onChange={e =>
                      setStepForm(f => ({ ...f, configuration: e.target.value }))
                    }
                  />
                </div>
              </div>
              <div className="editor-btn-row">
                <button
                  className="btn btn--primary"
                  onClick={handleSaveStep}
                  disabled={stepLoading}
                >
                  {stepLoading ? <span className="spinner spinner--sm" /> : null}
                  {editingStepId ? "Update Step" : "Save Step"}
                </button>
                <button
                  className="btn btn--secondary"
                  onClick={() => {
                    setShowStepForm(false);
                    setEditingStepId(null);
                    setStepForm(blankStep());
                  }}
                >
                  Cancel
                </button>
              </div>
            </div>
          )}

          <div className="table-scroll">
            <table className="editor-steps-table">
              <thead>
                <tr>
                  <th>Order</th>
                  <th>Name</th>
                  <th>Type</th>
                  <th>Start Step?</th>
                  <th>Config</th>
                  <th style={{ textAlign: "right" }}>Actions</th>
                </tr>
              </thead>
              <tbody>
                {steps.length === 0 ? (
                  <tr>
                    <td
                      colSpan={6}
                      style={{
                        textAlign: "center",
                        padding:   "32px 20px",
                        color:     "var(--color-text-muted)",
                        fontSize:  13,
                      }}
                    >
                      No steps yet. Click &quot;+ Add Step&quot; to get started.
                    </td>
                  </tr>
                ) : (
                  [...steps]
                    .sort((a, b) => (a.sequenceOrder || 0) - (b.sequenceOrder || 0))
                    .map(step => (
                      <tr key={step.id}>
                        <td
                          style={{
                            fontFamily: "var(--font-mono)",
                            fontSize:   13,
                            fontWeight: 700,
                            color:      "var(--brand-600)",
                          }}
                        >
                          #{step.sequenceOrder}
                        </td>
                        <td>
                          <span className="td-name">{step.stepName}</span>
                        </td>
                        <td>
                          <span className={`badge badge--${step.stepType}`}>
                            <span className="badge-dot" />
                            {step.stepType}
                          </span>
                        </td>
                        <td>
                          <span
                            className={
                              step.sequenceOrder === 1
                                ? "start-badge--yes"
                                : "start-badge--no"
                            }
                          >
                            {step.sequenceOrder === 1 ? "Yes" : "No"}
                          </span>
                        </td>
                        <td>
                          {step.configuration ? (
                            <span className="metadata-preview">
                              {step.configuration}
                            </span>
                          ) : (
                            <span
                              style={{
                                color:    "var(--color-text-muted)",
                                fontSize: 12,
                              }}
                            >
                              —
                            </span>
                          )}
                        </td>
                        <td style={{ textAlign: "right" }}>
                          <div
                            className="action-group"
                            style={{ justifyContent: "flex-end" }}
                          >
                            <button
                              className="btn btn--secondary btn--sm"
                              onClick={() => startEditStep(step)}
                              disabled={stepLoading}
                            >
                              Edit
                            </button>
                            <button
                              className="btn btn--secondary btn--sm"
                              onClick={() =>
                                navigate(
                                  `/rules?workflowId=${workflowId}&stepId=${step.id}`
                                )
                              }
                            >
                              Rules
                            </button>
                            <button
                              className="btn btn--danger btn--sm"
                              onClick={() => handleDeleteStep(step.id)}
                              disabled={stepLoading}
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
      )}
    </div>
  );
}

export default WorkflowEditor;
