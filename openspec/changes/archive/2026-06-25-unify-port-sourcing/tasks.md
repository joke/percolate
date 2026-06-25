## 1. SPI: explicit Port sourcing mode (Thread A)

- [x] 1.1 Load the java11 + lombok + null-safety + spock coding-convention skills before writing any code
- [x] 1.2 Add a closed `Sourcing` enum `{SUBTARGET, REUSE, REUSE_OR_MINT}` (Java 11, no `sealed`) and a `sourcing` field on `Port`; document the three modes and the extensibility note (a future by-name binding mode slots in beside them)
- [x] 1.3 Default the primary concrete `Port` constructor and the template-port constructor to `REUSE_OR_MINT`; restate `Port.reuse(...)` as the `REUSE` factory; add a `SUBTARGET` factory/constructor
- [x] 1.4 Remove the `reuseOnly` boolean; express the former `isReuseOnly()` as `sourcing == REUSE` (replace call sites with `sourcing()`)

## 2. Built-in strategies stamp their port modes (Thread A)

- [x] 2.1 `ConstructorCall`: emit each constructor-parameter port as `SUBTARGET`
- [x] 2.2 Verify `DirectAssign`, `NullnessCrossing` (`requireNonNull` + both `coalesce` forms), and `Container.unwrap` emit `REUSE` ports via the restated factory
- [x] 2.3 Verify every other strategy (`Conversion` / `Accessor` bases, `MethodCallBridge`, `Container` collect/wrap/iterate/map, `StreamMap`) uses the `REUSE_OR_MINT` default with no behavior change
- [x] 2.4 Make `Grounding` preserve a template port's sourcing mode when instantiating a grounded concrete port (do not reset it to the default)

## 3. Engine: dispatch binding on the declared mode (Threads A, B, C)

- [x] 3.1 Rewrite `ExpandStage.sourceForPort` as a dispatch on `port.sourcing()`; remove the `children.contains(port.getName())` branch and the `port.isReuseOnly()` branch
- [x] 3.2 Drop the `declaredChildren` set from the `land` / `sourceForPort` binding path; keep it feeding only `DemandView.declaredChildren()` (assembly gating in the demand)
- [x] 3.3 Thread B: fold the directive-pinned source into a single ranked `SourceCandidates` lookup (pinned-first, then `Value::id`); remove the separate pinned-vs-matching branch from `sourceForPort`
- [x] 3.4 Thread C: extract one `landOperation` primitive shared by the producer path (`land`) and the accessor-descent path (`descendSegment`); keep the two control flows distinct (backward work-list vs forward target-bound descent) and keep the self-call guard + work-list enqueue on the produce path only

## 4. graph-model: align ACCESS documentation (Thread D)

- [x] 4.1 Update the `Location` `Role` javadoc: `ACCESS` is produced forward by target-bound descent (a base case for `expand`), never "re-demanding the parent path"; keep `ACCESS` distinct from `LEAF`; leave `ExtractedPlan.isBaseCase` unchanged and record why `ACCESS` is retained (the producerless-`ACCESS` safety distinction)

## 5. Tests

- [x] 5.1 `Port` mode unit spec: each built-in stamps the expected mode (`ConstructorCall` → `SUBTARGET`; reuse strategies → `REUSE`; conversions → `REUSE_OR_MINT`); the mode set is exactly the three
- [x] 5.2 Driver spec: `sourceForPort` dispatches per mode (`SUBTARGET` mints a child demand; `REUSE` binds-or-declines; `REUSE_OR_MINT` binds-or-mints), consulting no declared-children name-match
- [x] 5.3 Guard spec: `ConstructorCall` fires only when its parameter-name set equals the declared-children set (pins the `SUBTARGET` semantic-equivalence argument from design D2)
- [x] 5.4 Ranking spec: a directive-pinned source wins over a same-typed sibling; absent a pin, selection is `min` by `Value::id`
- [x] 5.5 No generated-output drift: the processor's expansion/codegen Spock fixtures (graph dumps, extracted plans, generated sources) are unchanged, and a regenerated `percolate-integration` `*.dot` / generated-source set diffs clean against the current output

## 6. Verify and commit

- [x] 6.1 Run `./gradlew check` and read the full output (do not pipe to `tail`); fix every violation before proceeding — never continue with violations
- [x] 6.2 Commit the change with /commit-commands:commit
