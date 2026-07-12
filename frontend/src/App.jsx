import { BrowserRouter, Routes, Route } from "react-router-dom";
import WorkflowList from "./pages/WorkflowList.jsx";
import WorkflowEditor from "./pages/WorkflowEditor.jsx";
import StepRuleEditor from "./pages/StepRuleEditor.jsx";
import WorkflowExecution from "./pages/WorkflowExecution.jsx";
import AuditLog from "./pages/AuditLog.jsx";
import AppShell from "./components/AppShell.jsx";
import "./styles/index.css";

function App() {
  return (
    <BrowserRouter>
      <AppShell>
        <Routes>
          <Route path="/" element={<WorkflowList />} />
          <Route path="/editor" element={<WorkflowEditor />} />
          <Route path="/rules" element={<StepRuleEditor />} />
          <Route path="/execute" element={<WorkflowExecution />} />
          <Route path="/audit" element={<AuditLog />} />
        </Routes>
      </AppShell>
    </BrowserRouter>
  );
}

export default App;