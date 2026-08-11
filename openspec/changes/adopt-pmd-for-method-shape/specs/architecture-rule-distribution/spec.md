## MODIFIED Requirements

### Requirement: Architecture rules are authored once in a rules library

Percolate's architecture rules SHALL be authored exactly once, as `ArchRulesService` implementations in the
`archRules` source set of a single rules-library module (`architecture-tests`), which applies
`com.netflix.nebula.archrules.library`. No rule SHALL be re-implemented, copied, or wrapped per consuming
module. Rules SHALL be grouped into cohesive service classes by subject rather than one class per rule, and
each SHALL be exposed from `getRules()` under a stable rule name so that per-rule configuration remains
addressable.

Every rule in the library SHALL constrain a **relationship between types or packages** — a dependency, a
package coordinate, or a type name — which is what a whole-source-set bytecode view can see and a
single-compilation-unit analyser cannot. A rule constraining a **property of one declaration**, such as a
method's visibility or its `static` modifier, SHALL NOT be authored here; it belongs to PMD under the
`method-shape-analysis` capability. This keeps the library free of any rule whose correctness depends on
resolving subclasses, which per-source-set evaluation cannot do across a module boundary.

The library SHALL be authored in Java, not Groovy: the `archRules` variant is consumed as a plain jar by the
runner and carries no Groovy runtime. The module SHALL NOT be published to Maven Central.

#### Scenario: Every rule has exactly one authoring site

- **WHEN** the repository is searched for ArchUnit rule construction
- **THEN** every rule is constructed in `architecture-tests/src/archRules/java`, and no module declares a
  rule of its own

#### Scenario: Rules are grouped by subject

- **WHEN** the `ArchRulesService` implementations are inspected
- **THEN** rules are grouped by subject — module layering, engine encapsulation, and type boundaries —
  rather than one class per rule, and each rule is keyed by a stable name

#### Scenario: No rule constrains a single declaration's shape

- **WHEN** the `ArchRulesService` implementations are inspected
- **THEN** none constrains a method's visibility, its `static` modifier, or a class's method count, and none
  calls `getAllSubclasses` or otherwise depends on resolving types outside the evaluated source set

#### Scenario: The rules library is not published

- **WHEN** the publishing configuration is inspected
- **THEN** `architecture-tests` declares no Maven publication and contributes no artifact to the Central
  Portal deployment
