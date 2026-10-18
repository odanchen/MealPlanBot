<!--
Sync Impact Report — temporary review material; remove before committing.
Version change: 1.0.0 → 2.0.0.
Reason: remove prescriptive requirements and broaden the project's purpose.
Modified principles: I–V condensed; core intent retained; device-specific UI,
validation mechanics, test setup, and operational prescriptions removed.
Scope expanded to include adaptability; technology context removed.
Added sections: none.
Removed sections: none.
Follow-up TODOs: none.
-->
# MealPlanBot Constitution

## Core Principles

### I. Pragmatic Simplicity

Choose simple, maintainable solutions. Keep dependencies and infrastructure minimal;
justify complexity through concrete needs or demonstrable value.

### II. Separate Business Logic

Keep business rules independent of persistence, APIs, and presentation so they remain
understandable, testable, and easy to change.

### III. Authoritative Data and Integrity

Maintain a single source of truth for application data. Preserve its correctness and
consistency as the application evolves.

### IV. Usability and Accessibility

Make everyday workflows clear, usable, and accessible. Treat the quality of the user
experience as part of correctness.

### V. Meaningful Verification and Simple Operations

Test important business rules and critical workflows rather than implementation details.
Keep deployment, maintenance, and troubleshooting straightforward.

## Scope and Decision Boundaries

Build a useful household meal planner that can evolve beyond its initial use. Favor polish, adaptability, and clear design over feature count or scale. Features, technologies, architecture details, deployment targets, and UI design belong in specifications and plans.

## Incremental Development

Make small, verifiable changes that remain easy to modify. Review AI-assisted work with the
same care as other changes. Keep process and documentation proportional to the project.

## Governance

This constitution takes precedence over conflicting project guidance. Check relevant principles
during planning and review. The maintainer may amend it by recording the reason, updating the
amendment date, and incrementing the version: major for incompatible changes or removals, minor
for expanded guidance, patch for clarifications. Preserve the ratification date.

**Version**: 2.0.0 | **Ratified**: 2026-10-17 | **Last Amended**: 2026-10-17
