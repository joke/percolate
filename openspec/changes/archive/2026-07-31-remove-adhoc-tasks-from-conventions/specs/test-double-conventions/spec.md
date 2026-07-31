## MODIFIED Requirements

### Requirement: Residual statics are stubbed with SpyStatic

Where a method legitimately remains `static`, a spec that needs to control it SHALL stub it via
Spock's `SpyStatic(Class)` rather than exercising the real implementation incidentally or
restructuring production code to route around it. Because `SpyStatic` is provided only by the
mockito mock maker and Mockito's static mocking is confined to the registering thread, a spec that
uses `SpyStatic` SHALL run single-threaded: it SHALL carry `@spock.lang.Isolated` (or an
equivalent per-spec execution-mode declaration) so that Spock's parallel execution — which is
enabled for ordinary test runs — does not run its features on threads the static mock was never
registered on. The repo-wide `runner { parallel { enabled false } }` that this requirement
previously relied on no longer exists; parallelism is now disabled only while pitest runs, via
`spock.parallel.disabled`.

#### Scenario: A published static helper is stubbed
- **WHEN** a spec needs `spi.LiteralCoercion` or `spi.Subjects` to return a controlled value
- **THEN** it registers `SpyStatic(LiteralCoercion)` and stubs the call, rather than constructing
  inputs that coax the real implementation into the desired result

#### Scenario: Static stubbing keeps its thread guarantee
- **WHEN** a spec uses `SpyStatic`
- **THEN** that spec runs single-threaded by its own declaration, since Mockito's static mock is
  registered per-thread and the surrounding suite runs in parallel
