## MODIFIED Requirements

### Requirement: ReadAccessor models source property access
`ReadAccessor` SHALL carry the property name, type (`TypeMirror`), and the element used for access (getter method or field). Concrete types: `GetterAccessor` (wraps `ExecutableElement`) and `FieldReadAccessor` (wraps `VariableElement`). Accessor methods SHALL follow JavaBean naming conventions (`getName()`, `getType()`). `ReadAccessor` SHALL use Lombok `@Getter` and `@RequiredArgsConstructor(access = AccessLevel.PROTECTED)` to generate accessors and constructor. Leaf subclasses SHALL use Lombok `@Getter` for their own fields.

#### Scenario: GetterAccessor provides method element
- **WHEN** a `GetterAccessor` is created for `getFirstName()`
- **THEN** `getName()` SHALL return `firstName` and `getMethod()` SHALL return the method's `ExecutableElement`

### Requirement: WriteAccessor models target property access
`WriteAccessor` SHALL carry the property name, type (`TypeMirror`), and the element used for writing. Concrete types: `ConstructorParamAccessor` (wraps constructor `ExecutableElement` and parameter index) and `FieldWriteAccessor` (wraps `VariableElement`). Accessor methods SHALL follow JavaBean naming conventions (`getName()`, `getType()`). `WriteAccessor` SHALL use Lombok `@Getter` and `@RequiredArgsConstructor(access = AccessLevel.PROTECTED)` to generate accessors and constructor. Leaf subclasses SHALL use Lombok `@Getter` for their own fields.

#### Scenario: ConstructorParamAccessor provides constructor and index
- **WHEN** a `ConstructorParamAccessor` is created for constructor param `givenName` at index 0
- **THEN** `getName()` SHALL return `givenName`, `getConstructor()` SHALL return the constructor element, and `getParamIndex()` SHALL return 0
