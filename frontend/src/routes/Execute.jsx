import { createFileRoute } from "@tanstack/react-router";
import WorkflowExecution from "@/features/WorkflowExecution";

export const Route = createFileRoute("/execute")({
  validateSearch: (search) => ({
    workflowId:
      typeof search.workflowId === "string" ? search.workflowId : undefined,
    executionId:
      typeof search.executionId === "string" ? search.executionId : undefined,
  }),
  head: () => ({
    meta: [
      { title: "Workflow Execution | Halleyx Workflow Engine" },
      {
        name: "description",
        content:
          "Trigger a workflow run and manage step approvals in real time.",
      },
    ],
  }),
  component: ExecutePage,
});

function ExecutePage() {
  const { workflowId, executionId } = Route.useSearch();
  return (
    <WorkflowExecution
      key={`${workflowId ?? ""}:${executionId ?? ""}`}
      urlWorkflowId={workflowId}
      urlExecutionId={executionId}
    />
  );
}
