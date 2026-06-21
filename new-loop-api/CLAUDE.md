# CLAUDE.md

## Project: LOOP Backend

LOOP is a backend API for controlling truck route stock operations.
The system manages merchandise exit controls, entry controls, driver approvals, products, branches, routes, trucks, audit logs and external integrations.

The backend must be built in a professional, modular and maintainable way.

---

## Tech Stack

Use the following backend stack:

* Java 21
* Spring Boot
* Maven
* Spring Web
* Spring Data JPA
* PostgreSQL
* Spring Security
* JWT authentication
* Bean Validation
* Lombok
* Flyway migrations
* OpenFeign for external integrations
* Springdoc OpenAPI / Swagger for API documentation
* JUnit and Mockito for tests

---

## Main Goal

Build a clean and modular backend API for LOOP.

The system must support:

* User authentication
* Role-based access control
* Branch management
* Product management
* Route/truck management
* Exit stock controls
* Entry stock controls
* Driver approval
* Audit logging
* Integration logs
* Future integration with Aguas, Powerfleet and Odoo

---

## Business Context

LOOP is used to control merchandise that leaves and returns in delivery trucks.

A truck/route has products loaded before going out.
When the truck returns, the system records what came back and calculates differences.
The driver must approve the control.
Every sensitive action must be audited.

The system is used by different roles:

* ADMIN
* SUPERVISOR
* CONTROLADOR
* REPARTIDOR
* PICKER
* CARGADOR_DISPENSERS

---

## Core Business Rules

### General rules

* Every control must belong to a branch.
* Every control must belong to a route/truck.
* Every control must have a responsible user.
* Products must be created before being used in controls.
* Inactive products must not appear in new controls.
* Inactive users must not be able to log in.
* Important changes must be audited.
* External integrations must store success or error information.
* Data must always be saved locally before sending it to external systems.
* A driver-approved control must not be modified without an audit reason.

---

## Initial Modules

Use this package structure:

```text
src/main/java/com/loop/
  LoopApiApplication.java

  auth/
    controller/
    service/
    dto/
    security/

  users/
    controller/
    service/
    repository/
    entity/
    dto/

  branches/
    controller/
    service/
    repository/
    entity/
    dto/

  products/
    controller/
    service/
    repository/
    entity/
    dto/

  routes/
    controller/
    service/
    repository/
    entity/
    dto/

  stockcontrols/
    controller/
    service/
    repository/
    entity/
    dto/

  approvals/
    controller/
    service/
    repository/
    entity/
    dto/

  audit/
    controller/
    service/
    repository/
    entity/
    dto/

  integrations/
    common/
    aguas/
      client/
      dto/
      service/
    powerfleet/
      client/
      dto/
      service/
    odoo/
      client/
      dto/
      service/

  common/
    config/
    exception/
    response/
    security/
    enums/
    util/
```

Keep code organized by business module, not by generic technical layers only.

---

## Backend Architecture Rules

Each module should follow this structure:

```text
module/
  controller/
  service/
  repository/
  entity/
  dto/
```

Controllers must be thin.

Controllers should only:

* Receive HTTP requests
* Validate input through DTOs
* Call services
* Return responses

Services must contain business logic.

Repositories must only handle database access.

Do not put business logic inside controllers.

---

## Naming Conventions

Use English names in code.

Examples:

* `User`
* `Branch`
* `Product`
* `Route`
* `StockControl`
* `StockControlItem`
* `AuditLog`
* `IntegrationLog`

Use clear method names:

* `createProduct`
* `deactivateProduct`
* `createExitControl`
* `createEntryControl`
* `approveControl`
* `registerAuditLog`
* `sendControlToAguas`

Avoid vague names like:

* `process`
* `doStuff`
* `handleData`
* `manage`

---

## Entities

### User

Represents a system user.

Fields:

* id
* firstName
* lastName
* email
* passwordHash
* role
* branch
* active
* createdAt
* updatedAt

Roles:

```java
ADMIN,
SUPERVISOR,
CONTROLADOR,
REPARTIDOR,
PICKER,
CARGADOR_DISPENSERS
```

---

### Branch

Represents a branch or plant.

Initial branches:

* Ciudadela
* La Plata
* NAFA

Fields:

* id
* name
* code
* address
* locality
* province
* cuit
* ivaCondition
* active
* createdAt
* updatedAt

Rules:

* Branch code must be unique.
* Inactive branches must not be used in new operations.

---

### Product

Represents a product used in controls or disposable orders.

Fields:

* id
* code
* name
* displayOrder
* description
* type
* unit
* packQuantity
* active
* createdAt
* updatedAt

Product types:

```java
RETORNABLE,
DESCARTABLE
```

Rules:

* Product code must be unique.
* Inactive products must not appear in new controls.
* Disposable products are used in driver orders.
* Returnable products are used in entry and exit controls.

---

### Route

Represents a delivery route or truck route.

Fields:

* id
* code
* branch
* driver
* truckPlate
* active
* observations
* createdAt
* updatedAt

Rules:

* A route belongs to a branch.
* A route may have a driver assigned.
* A route may have a truck plate assigned.
* The truck plate will later be used for Powerfleet integration.

---

### StockControl

Represents an entry or exit control.

Fields:

* id
* type
* status
* branch
* route
* controller
* driver
* truckOrdered
* createdAt
* confirmedAt
* approvedAt
* sentToAguas
* aguasSentAt
* observations

Control types:

```java
ENTRY,
EXIT
```

Control statuses:

```java
CONTROLLED,
PENDING_DRIVER_APPROVAL,
ACCEPTED_BY_DRIVER,
REJECTED_BY_DRIVER,
WITH_DIFFERENCES,
SENT_TO_AGUAS,
AGUAS_ERROR,
CANCELLED
```

Rules:

* Exit control records what leaves.
* Entry control records what returns.
* Entry control should be compared against exit control.
* Differences must be visible.
* Driver approval must be recorded.
* Changes must be audited.

---

### StockControlItem

Represents product quantities inside a stock control.

Fields:

* id
* stockControl
* product
* totalQuantity
* fullQuantity
* exchangeQuantity
* differenceQuantity
* observations

Rules:

* Quantities cannot be negative.
* Each item belongs to one stock control.
* Each item references one product.
* Differences are calculated during entry control.

---

### DriverApproval

Represents driver approval or rejection of a control.

Fields:

* id
* stockControl
* driver
* status
* comment
* createdAt

Statuses:

```java
ACCEPTED,
REJECTED
```

Rules:

* Driver approval must be associated with a stock control.
* Date and time must be stored.
* Driver user must be stored.
* If rejected, comment must be mandatory.
* Approval must be audited.

---

### AuditLog

Represents a system audit event.

Fields:

* id
* userId
* userRole
* action
* entityName
* entityId
* oldValue
* newValue
* reason
* source
* ipAddress
* createdAt

Sources:

```java
ADMIN_WEB,
MOBILE_APP,
SYSTEM
```

Rules:

* Audit logs should not be editable by normal users.
* Sensitive changes must always create audit logs.
* Approved controls must not be changed without audit reason.

---

### IntegrationLog

Represents an external integration attempt.

Fields:

* id
* integrationName
* operationType
* entityName
* entityId
* status
* requestPayload
* responsePayload
* errorMessage
* retryCount
* createdAt
* sentAt

Integration names:

```java
AGUAS,
POWERFLEET,
ODOO
```

Statuses:

```java
PENDING,
SENT,
ERROR,
RETRYING,
CANCELLED
```

Rules:

* Integration attempts must be stored.
* Successful and failed attempts must be visible.
* Failed integrations must be retryable later.
* Business logic must not depend directly on external systems.

---

## Sprint 1 Scope

The first sprint must focus only on backend foundation and core master data.

Sprint 1 should include:

1. Initial Spring Boot project setup
2. PostgreSQL connection
3. Flyway setup
4. Basic project structure
5. Global exception handling
6. Health endpoint
7. User entity
8. Role enum
9. Authentication base
10. JWT base
11. Branch CRUD
12. Product CRUD
13. Route CRUD
14. Basic audit service

Do not implement stock controls before users, branches, products and routes are stable.

---

## Do Not Build Yet

Do not implement these features in Sprint 1 unless explicitly requested:

* Stock exit control
* Stock entry control
* Driver approval
* Disposable orders
* Picking
* Dispensers
* Aguas real integration
* Powerfleet real integration
* Odoo integration
* Advanced reports
* Push notifications
* Redis
* Queues
* Batch jobs

---

## API Design Rules

Use RESTful endpoints.

Examples:

```text
POST   /auth/login
GET    /auth/me

POST   /users
GET    /users
GET    /users/{id}
PATCH  /users/{id}
PATCH  /users/{id}/deactivate

POST   /branches
GET    /branches
GET    /branches/{id}
PATCH  /branches/{id}
PATCH  /branches/{id}/deactivate

POST   /products
GET    /products
GET    /products/{id}
PATCH  /products/{id}
PATCH  /products/{id}/deactivate

POST   /routes
GET    /routes
GET    /routes/{id}
PATCH  /routes/{id}
PATCH  /routes/{id}/deactivate
```

Later endpoints:

```text
POST   /stock-controls/exit
POST   /stock-controls/entry
GET    /stock-controls
GET    /stock-controls/{id}
POST   /stock-controls/{id}/confirm
POST   /stock-controls/{id}/approve
POST   /stock-controls/{id}/reject
```

---

## DTO Rules

Use DTOs for all requests and responses.

Do not expose JPA entities directly in API responses.

Use request DTOs:

* `CreateProductRequest`
* `UpdateProductRequest`
* `CreateBranchRequest`
* `UpdateBranchRequest`
* `CreateRouteRequest`
* `UpdateRouteRequest`
* `LoginRequest`

Use response DTOs:

* `ProductResponse`
* `BranchResponse`
* `RouteResponse`
* `UserResponse`
* `LoginResponse`

Use Bean Validation annotations:

```java
@NotBlank
@NotNull
@Email
@Positive
@PositiveOrZero
@Size
```

---

## Security Rules

Use Spring Security with JWT.

Rules:

* Public endpoints:

  * `POST /auth/login`
  * `GET /health`

* Protected endpoints:

  * Everything else

Role rules:

* ADMIN can manage users, branches, products and routes.
* SUPERVISOR can view controls, reports and audit logs.
* CONTROLADOR can create entry and exit controls.
* REPARTIDOR can approve or reject controls.
* PICKER can manage disposable orders.
* CARGADOR_DISPENSERS can scan dispenser movements.

Do not rely only on frontend restrictions.
Always enforce permissions in the backend.

---

## Database Rules

Use PostgreSQL.

Use Flyway for schema migrations.

Do not use Hibernate `ddl-auto: create` in serious development.

Recommended JPA setting:

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate
```

Create migrations under:

```text
src/main/resources/db/migration
```

Migration names:

```text
V1__create_users_table.sql
V2__create_branches_table.sql
V3__create_products_table.sql
V4__create_routes_table.sql
V5__create_audit_logs_table.sql
```

---

## Error Handling

Use a global exception handler.

Create a standard API error response.

Example fields:

* timestamp
* status
* error
* message
* path

Do not return stack traces to the client.

Use business exceptions for domain errors.

Examples:

* `ProductCodeAlreadyExistsException`
* `BranchCodeAlreadyExistsException`
* `UserNotFoundException`
* `InactiveUserException`

---

## Auditing Rules

Create an `AuditService`.

Use it from business services.

Example:

```java
auditService.register(
    currentUser,
    "CREATE_PRODUCT",
    "Product",
    productId,
    null,
    productResponse,
    "ADMIN_WEB"
);
```

For Sprint 1, basic auditing is enough.

Audit at least:

* User creation
* User update
* Branch creation
* Branch update
* Product creation
* Product update
* Route creation
* Route update

---

## Integration Rules

External integrations must be isolated under:

```text
integrations/
```

Do not call Aguas, Powerfleet or Odoo directly from controllers.

Do not mix external DTOs with internal DTOs.

Use adapter/service classes.

Example:

```text
StockControlService
  creates and saves the control locally

AguasIntegrationService
  transforms local data into Aguas request
  calls AguasClient
  updates IntegrationLog
```

Use OpenFeign for HTTP clients.

---

## Coding Style

Use:

* `@Service`
* `@RestController`
* `@Repository`
* `@RequiredArgsConstructor`
* `@Transactional`
* `@Valid`
* `@PreAuthorize`

Prefer constructor injection.

Use Lombok carefully:

* `@Getter`
* `@Setter`
* `@Builder`
* `@NoArgsConstructor`
* `@AllArgsConstructor`
* `@RequiredArgsConstructor`

Avoid putting `@Data` on JPA entities when relationships are involved.

---

## Transaction Rules

Use `@Transactional` in service methods that modify data.

Examples:

```java
@Transactional
public ProductResponse createProduct(CreateProductRequest request) {
    ...
}
```

Use transactions especially for future stock controls, where multiple records must be saved consistently.

---

## Testing Rules

Add tests for important business logic.

Prioritize tests for:

* Authentication
* Product validation
* Branch validation
* Route creation
* Stock control creation
* Difference calculation
* Driver approval
* Audit logging

For Sprint 1, add tests when business logic is not trivial.

---

## Commit Style

Use Conventional Commits with Jira ticket prefix.

Examples:

```text
LOOP-1 chore: initial spring boot project setup
LOOP-2 feat: add user authentication
LOOP-3 feat: add branch management
LOOP-4 feat: add product catalog
LOOP-5 feat: add route management
LOOP-6 test: add product service tests
LOOP-7 fix: prevent duplicated branch codes
```

Use feature branches:

```text
feature/LOOP-1-initial-project-setup
feature/LOOP-2-auth-login
feature/LOOP-3-branch-management
feature/LOOP-4-product-catalog
```

Keep `main` stable.

---

## Development Workflow

When starting a Jira story:

1. Checkout main
2. Pull latest changes
3. Create feature branch
4. Implement the story
5. Test manually
6. Add automated tests when needed
7. Commit meaningful changes
8. Merge to main when stable

Commands:

```bash
git checkout main
git pull origin main
git checkout -b feature/LOOP-2-auth-login

git add .
git commit -m "LOOP-2 feat: add user login"

git checkout main
git merge feature/LOOP-2-auth-login
git push origin main
```

---

## Definition of Done

A backend story is done only when:

* The code compiles
* The application starts correctly
* The endpoint works manually
* Validation errors are handled
* Security rules are respected
* Database migrations are included if needed
* Audit is registered if needed
* Tests are added when needed
* Swagger documentation is updated if applicable
* Code is committed with a meaningful commit message
* The story is moved to Done in Jira

---

## Assistant Behavior

When helping with this project:

* Keep the solution simple and professional.
* Do not over-engineer.
* Do not introduce unnecessary frameworks.
* Respect the module structure.
* Prefer clear code over clever code.
* Explain decisions briefly.
* Ask before adding major architectural changes.
* Always consider auditability.
* Always consider role-based security.
* Always consider future integration with Aguas and Odoo.
* Keep external integrations isolated.
* Do not skip validation.
* Do not expose entities directly in API responses.
* Do not create stock control logic before base modules are stable.

---

## Current Priority

The current priority is Sprint 1:

1. Backend project setup
2. PostgreSQL and Flyway
3. Authentication and users
4. Roles and permissions
5. Branch CRUD
6. Product CRUD
7. Route CRUD
8. Basic auditing

Focus only on these items unless explicitly instructed otherwise.
