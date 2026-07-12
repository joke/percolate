## MODIFIED Requirements

### Requirement: Receiver abstraction with ThisReceiver as the v1 implementor

The `percolate-spi` module SHALL define an interface `Receiver`:

```java
public interface Receiver {
    CodeBlock asExpression();
}
```

`asExpression()` SHALL return an `io.github.joke.percolate.javapoet.CodeBlock` (the relocated JavaPoet type) that renders the receiver's call-expression form (e.g. `this`, or for a future cross-mapper `FieldReceiver`, `this.fieldName`).

The processor SHALL ship exactly one v1 implementation: `ThisReceiver`. `ThisReceiver.asExpression()` SHALL return `CodeBlock.of("this")`. `ThisReceiver` is a singleton or stateless instance; the discovery stage attaches it to every `MethodCandidate` produced in v1.

#### Scenario: ThisReceiver renders the literal token "this"
- **WHEN** `ThisReceiver.INSTANCE.asExpression()` is invoked
- **THEN** the returned `CodeBlock` renders to the literal string `this`

#### Scenario: All v1 candidates carry ThisReceiver
- **WHEN** `DiscoverCallableMethodsStage` produces an index and any `MethodCandidate` is inspected
- **THEN** the candidate's `receiver()` is the `ThisReceiver` instance
