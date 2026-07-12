import { createFileRoute } from "@tanstack/react-router";
import AuditLog from "@/features/AuditLog";

export const Route = createFileRoute("/audit")({
  head: () => ({
    meta: [
      { title: "Audit Log | Halleyx Workflow Engine" },
      {
        name: "description",
        content:
          "Complete history of all workflow executions for tracking and compliance.",
      },
    ],
  }),
  component: AuditLog,
});
