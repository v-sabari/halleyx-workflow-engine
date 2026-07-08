import { createFileRoute } from "@tanstack/react-router";
import StepRuleEditor from "@/features/StepRuleEditor";

export const Route = createFileRoute("/rules")({
  validateSearch: (search) => ({
    workflowId:
      typeof search.workflowId === "string" ? search.workflowId : undefined,
    stepId: typeof search.stepId === "string" ? search.stepId : undefined,
  }),
  head: () => ({
    meta: [
      { title: "Step Rule Editor | Halleyx Workflow Engine" },
      {
        name: "description",
        content:
          "Configure dynamic conditions, rule priority, and next-step routing.",
      },
    ],
  }),
  component: RulesPage,
});

function RulesPage() {
  const { workflowId, stepId } = Route.useSearch();
  return (
    <StepRuleEditor
      key={`${workflowId ?? ""}:${stepId ?? ""}`}
      urlWorkflowId={workflowId}
      urlStepId={stepId}
    />
  );
}
