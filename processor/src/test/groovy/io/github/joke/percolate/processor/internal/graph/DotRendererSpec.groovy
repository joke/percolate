package io.github.joke.percolate.processor.internal.graph

import io.github.joke.percolate.processor.test.HarnessScope
import io.github.joke.percolate.spi.Codegen
import io.github.joke.percolate.spi.Nullability
import io.github.joke.percolate.spi.Port
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.Name
import javax.lang.model.element.TypeElement
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.TypeKind
import javax.lang.model.type.TypeMirror

/**
 * Unit-tested mock-only: {@link DotRenderer} is boundary-exempt, structurally inspecting a raw {@link TypeMirror}
 * ({@code getKind()}, {@code instanceof DeclaredType}, {@code asElement().getSimpleName()}) rather than going through
 * {@code ResolveCtx} — so its spec stubs that shape directly (a local {@link #declaredType(String)} helper), with no
 * shared {@code FakeType} substrate.
 */
@Tag('unit')
class DotRendererSpec extends Specification {

    @Shared TypeMirror STRING = declaredType('String')
    @Shared TypeMirror INT = Stub(TypeMirror) { getKind() >> TypeKind.INT }

    final MapperGraph graph = new MapperGraph()
    final Scope scope = new HarnessScope('m()')
    final DotRenderer renderer = new DotRenderer()

    def 'renders Operations as boxes, Values as ellipses, and labels edges by port'() {
        given:
        graph.apply(new AddOperation('new Address', Stub(Codegen), 1, false,
                [port('number', INT), port('street', STRING)],
                target('addr', STRING), Optional.empty(), [] as Set, []))

        when:
        def dot = renderer.render(graph.bipartiteView(), scope.encode()) { false }

        then: 'the Operation is a box carrying its typed label; Values are ellipses; ports label the edges'
        dot.contains('shape="box"')
        dot.contains('shape="ellipse"')
        dot.contains('new Address (1)')
        dot.contains('label="number"')
        dot.contains('label="street"')
        dot.contains(scope.encode())
        !dot.contains('$$Lambda')
    }

    def 'value types render simple names with a JSpecify nullness mark, no package qualifiers'() {
        given: 'a non-null String target and a nullable String source'
        graph.apply(new AddOperation('assign', Stub(Codegen), 0, false,
                [new PortBinding(new Port('value', STRING, Nullability.NULLABLE),
                        new AddValue(scope, new SourceLocation(AccessPath.of('s')), STRING, Nullability.NULLABLE))],
                target('name', STRING), Optional.empty(), [] as Set, []))

        when:
        def dot = renderer.render(graph.bipartiteView(), scope.encode()) { false }

        then: 'the non-null target reads String! and the nullable source reads String?, never the FQN in the label'
        dot.contains('String!')
        dot.contains('String?')
        !dot.contains('java.lang.String!')
        !dot.contains('java.lang.String?')
    }

    def 'unreachable vertices are dimmed (grey, dashed) while reachable ones are not'() {
        given:
        graph.apply(new AddOperation('new Address', Stub(Codegen), 1, false,
                [port('street', STRING)], target('addr', STRING), Optional.empty(), [] as Set, []))

        when: 'every Operation is marked unreachable'
        def dot = renderer.render(graph.bipartiteView(), scope.encode()) { it instanceof Operation }

        then: 'the dimmed Operation box is grey and dashed; the un-dimmed Value keeps its fill'
        dot.contains('style="filled,dashed"')
        dot.contains('fillcolor="#DDDDDD"')
        dot.contains('#D7F0D0')
    }

    def 'quote: escapes an embedded quote after doubling a literal backslash'() {
        expect:
        DotRenderer.quote('a\\b"c') == '"a\\\\b\\"c"'
    }

    def 'valueLabel: combines the location segment with the formatted type'() {
        def value = new Value(new SourceLocation(AccessPath.of('addr')), scope, Optional.of(STRING), Optional.of(Nullability.NON_NULL))

        expect:
        DotRenderer.valueLabel(value) == new SourceLocation(AccessPath.of('addr')).segment() + '\\nString!'
    }

    def 'valueLabel: an untyped value falls back to the unknown-type marker'() {
        def value = new Value(new SourceLocation(AccessPath.of('addr')), scope, Optional.empty(), Optional.empty())

        expect:
        DotRenderer.valueLabel(value) == new SourceLocation(AccessPath.of('addr')).segment() + '\\n?'
    }

    def 'formatType: a non-declared kind (e.g. a primitive) renders unmarked regardless of nullness'() {
        def primitive = Stub(TypeMirror) { getKind() >> TypeKind.INT; toString() >> 'int' }

        expect:
        DotRenderer.formatType(primitive, Nullability.NON_NULL) == 'int'
    }

    def 'formatType: a declared type carries its top-level nullness mark'() {
        expect:
        DotRenderer.formatType(STRING, Nullability.NULLABLE) == 'String?'
    }

    def 'formatType: unset (null) nullness carries no mark at all'() {
        expect:
        DotRenderer.formatType(STRING, null) == 'String'
    }

    def 'body: a non-declared type falls back to its raw toString'() {
        def raw = Stub(TypeMirror) { getKind() >> TypeKind.INT; toString() >> 'int' }

        expect:
        DotRenderer.body(raw) == 'int'
    }

    def 'body: a declared type with type arguments renders simple-name generics, marking a nullable argument'() {
        def nullableArg = Stub(DeclaredType) {
            getKind() >> TypeKind.DECLARED
            getTypeArguments() >> []
            asElement() >> Stub(TypeElement) { getSimpleName() >> Stub(Name) { toString() >> 'String' } }
            getAnnotationMirrors() >> [nullableAnnotationMirror()]
        }
        def nonNullArg = declaredType('Integer')
        def list = Stub(DeclaredType) {
            getKind() >> TypeKind.DECLARED
            getTypeArguments() >> [nullableArg, nonNullArg]
            asElement() >> Stub(TypeElement) { getSimpleName() >> Stub(Name) { toString() >> 'Map' } }
        }

        expect:
        DotRenderer.body(list) == 'Map<String?, Integer>'
    }

    def 'topMark: NULLABLE renders ?, NON_NULL renders !, unset renders nothing'() {
        expect:
        DotRenderer.topMark(Nullability.NULLABLE) == '?'
        DotRenderer.topMark(Nullability.NON_NULL) == '!'
        DotRenderer.topMark(null) == ''
    }

    def 'fillColor: each location kind renders its own fill, with a fallback for anything else'() {
        expect:
        DotRenderer.fillColor(valueAt(new SourceLocation(AccessPath.of('s')))) == '#CFE8FF'
        DotRenderer.fillColor(valueAt(new TargetLocation(TargetPath.of('t')))) == '#D7F0D0'
        DotRenderer.fillColor(valueAt(new ElementLocation())) == '#FFE0B3'
        DotRenderer.fillColor(valueAt(new ConstantLocation('k'))) == 'white'
    }

    def 'vertexAttributes: an Operation is a filled grey box labelled with its weight'() {
        def operation = graph.apply(new AddOperation('new Address', Stub(Codegen), 3, false,
                [port('street', STRING)], target('addr', STRING), Optional.empty(), [] as Set, []))

        expect:
        DotRenderer.vertexAttributes(operation, false) == [
                label    : 'new Address (3)',
                shape    : 'box',
                style    : 'filled',
                fillcolor: '#EEEEEE',
        ]
    }

    def 'vertexAttributes: dimming overrides style and fill for both Operations and Values'() {
        def operation = graph.apply(new AddOperation('new Address', Stub(Codegen), 3, false,
                [port('street', STRING)], target('addr', STRING), Optional.empty(), [] as Set, []))
        def value = valueAt(new SourceLocation(AccessPath.of('s')))

        expect:
        DotRenderer.vertexAttributes(operation, true).style == 'filled,dashed'
        DotRenderer.vertexAttributes(operation, true).fillcolor == '#DDDDDD'
        DotRenderer.vertexAttributes(value, true).style == 'filled,dashed'
        DotRenderer.vertexAttributes(value, true).fillcolor == '#DDDDDD'
    }

    private Value valueAt(final Location loc) {
        new Value(loc, scope, Optional.of(STRING), Optional.of(Nullability.NON_NULL))
    }

    private javax.lang.model.element.AnnotationMirror nullableAnnotationMirror() {
        Stub(javax.lang.model.element.AnnotationMirror) {
            getAnnotationType() >> Stub(DeclaredType) {
                asElement() >> Stub(TypeElement) { getSimpleName() >> Stub(Name) { contentEquals('Nullable') >> true } }
            }
        }
    }

    private AddValue leaf(final String slot, final TypeMirror type) {
        new AddValue(scope, new SourceLocation(AccessPath.of(slot)), type, Nullability.NON_NULL)
    }

    private AddValue target(final String slot, final TypeMirror type) {
        new AddValue(scope, new TargetLocation(TargetPath.of(slot)), type, Nullability.NON_NULL)
    }

    private PortBinding port(final String name, final TypeMirror type) {
        new PortBinding(new Port(name, type, Nullability.NON_NULL), leaf(name, type))
    }

    /** A declared type whose {@code asElement().getSimpleName()} renders as {@code simpleName}, no type arguments. */
    private DeclaredType declaredType(final String simpleName) {
        Stub(DeclaredType) {
            getKind() >> TypeKind.DECLARED
            getTypeArguments() >> []
            asElement() >> Stub(TypeElement) { getSimpleName() >> Stub(Name) { toString() >> simpleName } }
        }
    }
}
