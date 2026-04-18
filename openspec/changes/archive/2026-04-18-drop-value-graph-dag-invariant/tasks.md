## 1. Update spec

- [x] 1.1 Edit `openspec/specs/value-graph/spec.md`: drop the "The graph is a DAG (no cycles)" bullet from the `ValueGraph invariants` requirement and delete the "Graph is acyclic" scenario; add a paragraph stating that cycles are allowed and that inverse strategy pairs form 2-cycles
- [x] 1.2 Add the "Inverse strategy pair forms a 2-cycle" scenario to the `ValueGraph invariants` requirement

## 2. Strip DAG enforcement from BuildValueGraphStage

- [x] 2.1 Remove the `wouldCreateCycle` helper method (`processor/src/main/java/io/github/joke/percolate/processor/stage/BuildValueGraphStage.java`)
- [x] 2.2 Remove the `wouldCreateCycle` call from the fixpoint edge-add site (around line 286)
- [x] 2.3 Remove the `wouldCreateCycle` call from the post-fixpoint `LiftEdge` materialisation site (around line 311)
- [x] 2.4 Remove the `CycleDetector.detectCycles()` check from `assertInvariants` (around lines 460-462)
- [x] 2.5 Remove the `org.jgrapht.alg.cycle.CycleDetector` import
- [x] 2.6 Update the class-level javadoc bullet that lists "DAG" among asserted invariants
- [x] 2.7 Update the `sortBySourceTargetReachability` docstring so it no longer claims to preserve DAG flow — describe it as a proposal-ordering heuristic

## 3. Update tests

- [x] 3.1 Remove the "well-formed ValueGraph is acyclic" Spock test from `processor/src/test/groovy/io/github/joke/percolate/processor/graph/ValueGraphSpec.groovy`
- [x] 3.2 Remove the `org.jgrapht.alg.cycle.CycleDetector` import from `ValueGraphSpec.groovy`

## 4. Verify

- [x] 4.1 Run `./gradlew :processor:test` from `/home/joke/Projects/joke/percolate` and confirm all green
- [x] 4.2 Run `./gradlew :mappers:classes` from `/home/joke/Projects/joke/percolate-integration` and confirm `PersonMapper.mapHuman` compiles and the generated `PersonMapperImpl.java` populates the `address` slot
- [x] 4.3 Inspect the generated `PersonMapper_mapHuman_valuegraph.dot` and confirm both `OptionalUnwrapStrategy` reverse edges and the `StreamMapStrategy` lift edge are now present
- [x] 4.4 Revert the temporary `-Apercolate.debug.graphs=true` change in `percolate-integration/mappers/build.gradle` if it was added for diagnosis
