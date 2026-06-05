## MODIFIED Requirements

### Requirement: Multi-parameter methods are filtered

`DiscoverCallableMethods` SHALL exclude any method with more than one declared parameter. Methods with exactly one parameter are eligible. Methods with zero parameters are eligible only if they have a non-`void` return type, but for v1 they remain eligible (single-input bridges still work for them when the single input is the bare receiver — though `MethodCallBridge` itself only emits for one-parameter cases; see expansion-strategy-spi).

In v1, only single-parameter, non-static, non-void methods are practically usable. Multi-parameter methods are deferred to a future multi-argument assembly strategy (an `AssemblyStrategy`, analogous to `ConstructorCall` but over a callable method).

#### Scenario: Two-parameter method is excluded
- **WHEN** the mapper declares `Pet adopt(Dog d, Owner o)`
- **THEN** the produced index does NOT contain a `MethodCandidate` for `adopt(Dog, Owner)`

#### Scenario: Single-parameter method is included
- **WHEN** the mapper declares `Pet adopt(Dog d)`
- **THEN** the produced index contains a `MethodCandidate` for `adopt(Dog)`
