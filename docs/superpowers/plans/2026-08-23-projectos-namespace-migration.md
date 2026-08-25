# Project OS Namespace Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Replace the legacy vendor-specific Java namespace and Maven group ID with the canonical `com.projectos.backend` namespace across the complete backend repository.

**Architecture:** Keep the existing modular monolith boundaries unchanged. This is a mechanical namespace migration across active modules, shared libraries, monolith application, legacy gateway and root legacy source; it must not alter public routes, database table names, Flyway versions or business behavior.

**Tech Stack:** Java 21, Spring Boot 4.1, Maven reactor, PostgreSQL/Flyway, JUnit/Testcontainers.

**Spec:** `docs/architecture/backend-naming-convention.md` and the approved modular monolith plan.

## Global Constraints

- `application/monolith-app` remains the only executable monolith runtime.
- Public `/api/v1/**` routes and PostgreSQL schema/table names remain unchanged.
- No production database, deployment, remote repository or generated `target/` output is modified.
- Every tracked source and build reference to the legacy namespace must be migrated to `com.projectos.backend`.
- Java directory paths must match their package declarations.
- Verification must run on Java 21 with PostgreSQL Testcontainers.

### Task 1: Inventory and migration scope

Files: all tracked files outside `target/` containing the old namespace. Confirm that source, tests, Maven metadata and documentation are included.

### Task 2: Mechanical namespace migration

Replace the exact old namespace in Java package/import declarations, Maven group IDs, test metadata, configuration and documentation. Move every Java source directory from the former vendor-specific path to `com/projectos/backend` in `src`, `api-gateway`, `modules`, `shared` and `application`.

### Task 3: Reference and structure verification

Confirm no old namespace remains outside intentional historical documentation, all Java package paths match declarations, and monolith component/entity/repository scans still use the new root package.

### Task 4: Build and acceptance verification

Run the focused monolith startup test and the complete reactor verify. Confirm Flyway, Hibernate validation, module contract tests and executable packaging still pass.

### Task 5: Documentation memory update

Update the naming convention, active plan and durable backend memory with the new canonical namespace and verification evidence.

## Completion checkpoint 2026-08-23

- Replaced the former vendor-specific namespace with `com.projectos.backend` in source, tests, Maven group IDs, scan annotations and documentation.
- Moved 24 Java package roots to paths matching their package declarations and removed empty legacy namespace directories.
- Namespace verification passed: zero old references, zero package-path mismatches, zero old Java directories and zero old Maven group IDs.
- Focused monolith startup/integrity test passed: 3 tests, 0 failures, 0 errors.
- Full Maven reactor verification passed, including `api-gateway` and `monolith-app`: `./mvnw verify` — BUILD SUCCESS.
