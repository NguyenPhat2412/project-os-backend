# Training and Knowledge Three-Tier Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move Training, Documents, and Wiki business operations to Knowledge Service/PostgreSQL with one paginated frontend data layer.

**Architecture:** Reuse Knowledge Service's generic PostgreSQL resource model and project-scoped resource controller. Next.js routes proxy the service, while shared frontend hooks own query, mutation, pagination, search, and error behavior.

**Tech Stack:** Spring Boot, PostgreSQL, Flyway, Next.js, React Query, TypeScript.

**Spec:** `be/docs/architecture/2026-08-22-training-knowledge-three-tier-design.md`

## Global Constraints

- PostgreSQL is the only business-data source.
- Backend owns business rules and authorization.
- Frontend displays API data and must not fabricate business records.
- Preserve unrelated dirty-worktree changes.
- Do not deploy or push remote.

### Task 1: Register Training and add backend list filtering

**Files:**
- Modify: `be/modules/knowledge/src/main/resources/application.yml`
- Modify: `be/shared/resource/src/main/java/vn/uytinmang/projectos/resource/ResourceApplicationService.java`
- Modify: `be/shared/resource/src/main/java/vn/uytinmang/projectos/resource/ResourceController.java`
- Test: `be/modules/knowledge/src/test/java/vn/uytinmang/projectos/KnowledgeResourceContractTest.java`

- [ ] Add `training` to the knowledge resource catalog.
- [ ] Add backend search/filter parameters without loading the full dataset.
- [ ] Test pagination and project scope.

### Task 2: Add knowledge proxies and shared frontend client

**Files:**
- Create: `fe/src/app/api/v1/projects/[projectId]/[resource]/route.ts`
- Create: `fe/src/lib/api/knowledge.ts`
- Modify: `fe/src/lib/api/client.ts`
- Test: `fe/src/lib/api/knowledge.test.ts`

- [ ] Proxy GET/POST/PATCH/DELETE to Knowledge Service.
- [ ] Normalize paginated responses.
- [ ] Keep backend-unavailable responses explicit.

### Task 3: Convert Training and Documents UI mutations

**Files:**
- Modify: `fe/src/features/training/components/TrainingFeature.tsx`
- Modify: `fe/src/features/training/components/TrainingAttendeesFeature.tsx`
- Modify: `fe/src/features/docs/components/DocumentsFeature.tsx`
- Modify: `fe/src/features/docs/types/document.types.ts`

- [ ] Use server search/pagination.
- [ ] Replace local create/approve/update state mutations with API mutations followed by refetch.
- [ ] Remove business fallback values and hardcoded actor names.

### Task 4: Convert Wiki and add approval endpoint

**Files:**
- Modify: `fe/src/features/wiki/**`
- Modify: `be/modules/knowledge/src/main/java/vn/uytinmang/projectos/knowledge/**`
- Modify: `be/modules/knowledge/src/main/resources/db/migration/**`

- [ ] Reuse the shared client for Wiki.
- [ ] Add explicit approve transition with backend authorization and audit event.
- [ ] Add tests for allowed and denied role transitions.

### Task 5: Verification

- [ ] Run frontend typecheck/build/tests.
- [ ] Run Maven contract tests if Java is installed.
- [ ] Run E2E by role and organization.
- [ ] Report any environment blockers without claiming unverified success.
