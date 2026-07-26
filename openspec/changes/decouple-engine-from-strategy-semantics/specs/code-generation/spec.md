## ADDED Requirements

### Requirement: Conflicting class-member requests under one dedup key SHALL be an error

The processor SHALL report an error when two operations in the **winning** plan request a class member under the
same dedup key with a different field type or a different initializer, rather than silently emitting one member
and letting every requester share it. The message SHALL name the dedup key and both conflicting definitions, and
SHALL be marked permanent — a disagreement holds in every round.

Requests that agree on both field type and initializer SHALL continue to deduplicate to one member, as today.
The check SHALL be a comparison of declared facts over the chosen plan; it SHALL name no strategy and require
no SPI change.

#### Scenario: Two disagreeing requests are rejected
- **WHEN** two operations in the winning plan request the dedup key `fmt` with initializers `DateTimeFormatter.ofPattern("yyyy")` and `DateTimeFormatter.ofPattern("dd.MM")`
- **THEN** an error naming the key and both initializers is reported, and no implementation is generated

#### Scenario: Two requests differing only in field type are rejected
- **WHEN** two operations request one dedup key with different field types
- **THEN** an error naming the key and both field types is reported

#### Scenario: Agreeing requests still deduplicate
- **WHEN** several operations request one dedup key with an equal field type and an equal initializer
- **THEN** exactly one member is emitted and every requester references it, unchanged from today

#### Scenario: A conflict outside the winning plan is ignored
- **WHEN** two operations request one dedup key with different definitions but only one is reachable in the winning plan
- **THEN** no error is reported, because the generated code contains only the reachable request

#### Scenario: The conflict diagnostic is permanent
- **WHEN** a dedup-key conflict is reported
- **THEN** the diagnostic is marked permanent, so the mapper is consumed rather than deferred

### Requirement: A class-member conflict is positioned at the mapper type

A class member is a class-scoped resource with no owning declaration token, so its conflict diagnostic SHALL
carry `Subjects.none()` and resolve to the mapper `TypeElement`. The message SHALL carry the operation labels of
the conflicting requesters so the author can locate them.

#### Scenario: The conflict anchors at the mapper type
- **WHEN** a dedup-key conflict is emitted
- **THEN** it is positioned at the mapper's `TypeElement`

#### Scenario: The message identifies the requesters
- **WHEN** a dedup-key conflict is emitted
- **THEN** the message names the operation label of each conflicting requester
