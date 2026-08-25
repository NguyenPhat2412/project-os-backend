# Backend Documentation Structure Standardization Plan

**Goal:** Make `be/docs` the single canonical location for Project OS backend architecture, plans, decisions, operations, database notes, and durable project memory.

**Rules:**

- New plans go under `docs/plans/`.
- Current architecture rules go under `docs/architecture/`.
- Important choices go under `docs/decisions/`.
- Database migration and integrity notes go under `docs/database/`.
- Operational runbooks go under `docs/operations/`.
- Durable project context goes under `docs/memory/`.
- Historical material goes under `docs/archive/` and is not treated as current guidance.

**Verification:** Every canonical directory has an index or README, old plans/specifications remain readable after relocation, and the root documentation index links to all current sections.
