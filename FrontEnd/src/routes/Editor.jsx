import { createFileRoute } from "@tanstack/react-router";
import WorkflowEditor from "@/features/WorkflowEditor";

export const Route = createFileRoute("/editor")({
  validateSearch: (search) => ({
    workflowId:
      typeof search.workflowId === "string" ? search.workflowId : undefined,
  }),
  head: () => ({
    meta: [
      { title: "Workflow Editor | Halleyx Workflow Engine" },
      {
        name: "description",
        content:
          "Create and edit workflow definitions, input schemas, and steps.",
      },
    ],
  }),
  component: EditorPage,
});

function EditorPage() {
  const { workflowId } = Route.useSearch();
  return <WorkflowEditor key={workflowId ?? "new"} workflowId={workflowId} />;
}
