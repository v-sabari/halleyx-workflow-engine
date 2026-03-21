# Halleyx Workflow Engine

A full-stack workflow automation system — design workflows, define rules, execute processes, and track every step.

---

## Tech Stack

| Layer    | Technology                             |
|----------|----------------------------------------|
| Frontend | React 19, Vite, React Router v7, Axios |
| Backend  | Spring Boot 3.3.5, Java 21             |
| Database | MySQL 8+                               |
| ORM      | Spring Data JPA / Hibernate            |
| Mail     | Spring Mail (Gmail SMTP)               |

---

## Prerequisites

- Java 21+
- Node.js 18+
- MySQL 8+
- Maven 3.9+

---

## Setup Instructions

### 1. Database

```sql
CREATE DATABASE workflow_engine_db;
```

### 2. Backend

```bash
cd workflow-engine
```

Edit `src/main/resources/application.properties` and update:

```properties
spring.datasource.password=YOUR_MYSQL_PASSWORD
spring.mail.username=YOUR_GMAIL
spring.mail.password=YOUR_APP_PASSWORD
```

Run:

```bash
./mvnw spring-boot:run
```

Backend runs on `http://localhost:8080`

---

### 3. Frontend

```bash
cd FrontEnd
npm install
npm run dev
```

Frontend runs on `http://localhost:5173`

---

## API Endpoints

| Method | URL                             | Description                   |
|--------|---------------------------------|-------------------------------|
| POST   | /api/v1/workflows               | Create workflow                |
| GET    | /api/v1/workflows               | List workflows                 |
| GET    | /api/v1/workflows/:id           | Get workflow with steps        |
| PUT    | /api/v1/workflows/:id           | Update workflow (new version)  |
| DELETE | /api/v1/workflows/:id           | Delete workflow                |
| POST   | /api/v1/workflows/:id/steps     | Add step                       |
| PUT    | /api/v1/steps/:id               | Update step                    |
| DELETE | /api/v1/steps/:id               | Delete step                    |
| POST   | /api/v1/steps/:id/rules         | Add rule                       |
| GET    | /api/v1/steps/:id/rules         | List rules                     |
| PUT    | /api/v1/rules/:id               | Update rule                    |
| DELETE | /api/v1/rules/:id               | Delete rule                    |
| POST   | /api/v1/executions/start        | Start execution                |
| GET    | /api/v1/executions/:id          | Get execution status           |
| GET    | /api/v1/executions/:id/logs     | Get step logs                  |
| POST   | /api/v1/executions/:id/approve  | Approve step                   |
| POST   | /api/v1/executions/:id/reject   | Reject step                    |
| POST   | /api/v1/executions/:id/cancel   | Cancel execution               |
| POST   | /api/v1/executions/:id/retry    | Retry failed step              |
| GET    | /notifications/unread           | Get unread notifications       |
| GET    | /audit-logs                     | Get execution audit logs       |

---

## Sample Workflow: Expense Approval

### Input Schema

```json
{
  "amount":     { "type": "number", "required": true },
  "country":    { "type": "string", "required": true },
  "department": { "type": "string", "required": false },
  "priority":   { "type": "string", "required": true, "allowed_values": ["High", "Medium", "Low"] }
}
```

### Steps

1. **Manager Approval** (APPROVAL) — `{ "assignee_email": "manager@example.com" }`
2. **Finance Notification** (NOTIFICATION) — `{ "notification_channel": "EMAIL", "assignee_email": "finance@example.com" }`
3. **CEO Approval** (APPROVAL) — `{ "assignee_email": "ceo@example.com" }`
4. **Task Rejection** (TASK)

### Rules for Manager Approval

| Priority | Condition                                               | Next Step            |
|----------|---------------------------------------------------------|----------------------|
| 1        | amount > 100 && country == 'US' && priority == 'High'  | Finance Notification |
| 2        | amount <= 100                                           | CEO Approval         |
| 3        | priority == 'Low' && country != 'US'                   | Task Rejection       |
| 4        | DEFAULT                                                 | Task Rejection       |

---

## Sample Execution

### Input

```json
{ "amount": 250, "country": "US", "department": "Finance", "priority": "High" }
```

### Expected Flow

```
Manager Approval → Finance Notification → Completed
```

### Step Log

```json
{
  "stepName": "Manager Approval",
  "stepType": "APPROVAL",
  "evaluatedRules": "amount > 100 && country == 'US' && priority == 'High'",
  "status": "APPROVED",
  "selectedNextStepId": "<finance-notification-step-id>"
}
```

---

## Rule Engine

- Rules are evaluated in priority order (1 = highest)
- The first matching rule is selected
- DEFAULT rule acts as fallback if no condition matches
- Supported operators: `==`, `!=`, `>`, `<`, `>=`, `<=`, `&&`, `||`
- Supported functions: `contains()`, `startsWith()`, `endsWith()`
- Invalid conditions mark the step as `FAILED`

---

## Running Tests

```bash
cd workflow-engine
./mvnw test
```

Tests cover:

- `RuleEvaluationService`
- `InputSchemaValidatorService`
- `RuleService`
- `WorkflowService`
