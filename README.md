# cloud-itonami-isco-9622

Open Occupation Blueprint for **ISCO-08 9622**: Odd-job Persons.

This repository designs a forkable OSS business for an independent handyman/odd-job practice: a general repair-support robot performs material handling and cleanup tasks under a governor-gated actor, so the practice keeps its own service records instead of renting a closed field-service SaaS.

**Maturity: `:implemented`.** `src/handyman/` implements the
`HandymanPracticeActor` as a `langgraph.graph/state-graph`
(`handyman.actor`) wired to a `HandymanAdvisor` (`handyman.advisor`) and an
independent `HandymanGovernor` (`handyman.governor`), following the
itonami actor pattern (ADR-2607011000): `:intake -> :advise -> :govern ->
:decide -+-> :commit (:ok?) +-> :request-approval (:escalate?, human-in-the-loop
interrupt) +-> :hold (:hard?)`. 14 tests / 29 assertions green
(`clojure -M:test`). HARD invariants (always hold, never overridable):
client provenance, no-actuation (`:effect` must be `:propose`), a registered
job basis for any task, the proposed handling weight not exceeding the job's
registered material-handling capacity (handling weight beyond registered
capacity is a strain/injury risk, not effort), and the proposed task
duration not exceeding the job's registered site-access time-box (the
client's site-access grant is time-boxed, not open-ended). Always-escalate
ops (human sign-off regardless of confidence, mapping this repo's Trust
Controls in [`docs/business-model.md`](docs/business-model.md)):
`:approve-electrical-plumbing-access` (no electrical/plumbing-system access
without the governor gate) and `:approve-working-at-height` (working-at-height
tasks require human sign-off).

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot performs
the physical domain work**. Here a general repair-support robot performs material handling, minor assembly and cleanup tasks under an actor that proposes
actions and an independent **Handyman Governor** that gates them. The governor never
dispatches hardware itself; `:high`/`:safety-critical` actions (such as
operating near electrical/plumbing systems, or working at height) require human sign-off.

A live sample of the operator console (robotics safety console, shared template) is rendered in [docs/samples/operator-console.html](docs/samples/operator-console.html) — pure-data HTML output of `kotoba.robotics.ui`.

## Core Contract

```text
service request + task scope + site access
        |
        v
Handyman Advisor -> Handyman Governor -> repair-support, or human sign-off
        |
        v
robot actions (gated) + operating records + audit ledger
```

No automated advice can dispatch a robot action the governor refuses, suppress
an operating record, or disclose sensitive data without governor approval and
audit evidence.

## Capability layer

Resolves via [`kotoba-lang/occupation`](https://github.com/kotoba-lang/occupation)
(ISCO-08 `9622`). Required capabilities:

- :robotics
- :forms
- :audit-ledger
- :bpmn

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md).

## License

AGPL-3.0-or-later.
