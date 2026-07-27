package io.github.joke.percolate.spi.builtins

import io.github.joke.percolate.lib.javapoet.CodeBlock
import io.github.joke.percolate.spi.BodyCodegen
import io.github.joke.percolate.spi.BodyRenderContext
import io.github.joke.percolate.spi.DirectiveInput
import io.github.joke.percolate.spi.Nullability
import io.github.joke.percolate.spi.Offer
import io.github.joke.percolate.spi.PortType
import io.github.joke.percolate.spi.ResolveCtx
import io.github.joke.percolate.spi.Subjects
import io.github.joke.percolate.spi.SwitchStyle
import io.github.joke.percolate.spi.Weights
import io.github.joke.percolate.spi.builtins.test.Demands
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.SourceVersion
import javax.lang.model.element.Element
import javax.lang.model.element.ElementKind
import javax.lang.model.element.Name
import javax.lang.model.element.TypeElement
import javax.lang.model.type.TypeMirror

/**
 * {@link EnumConversion} unit-tested mock-only: {@code expand} declares the bounded type-variable port and weight
 * over the {@link ResolveCtx} seam only; the non-enum and uncovered-constant vetoes live in {@code sourceBound}
 * (design D6 of change {@code decouple-engine-from-strategy-semantics}), so {@code render} itself never fails —
 * the source-shape-dependent decisions (name-matching, {@code @MapEnum} precedence, classic-vs-arrow coverage)
 * live in the pure {@code buildMapping}/{@code renderArrow}/{@code renderClassic} helpers, tested directly on plain
 * data. A rendered {@code $T} placeholder bound to a mocked {@link TypeMirror} is never stringified here (it needs
 * a real compiler type to resolve, per the {@code AbsoluteTemporalConversion} precedent).
 */
@Tag('unit')
class EnumConversionSpec extends Specification {

    ResolveCtx ctx = Mock()
    TypeMirror targetType = Mock()

    // ---- expand ---------------------------------------------------------------------------------------------

    def 'expand yields no production when the demanded target is not an enum'() {
        ctx.isEnum(targetType) >> false

        expect:
        new EnumConversion().expand(Demands.forTarget(targetType), ctx).toList().empty
    }

    def 'expand declares one type-variable port at Weights.EXPENSIVE, so a declared method always wins'() {
        ctx.isEnum(targetType) >> true

        when:
        def specs = new EnumConversion().expand(Demands.forTarget(targetType), ctx)*.spec

        then:
        specs.size() == 1
        def spec = specs[0]
        spec.outputType.is(targetType)
        spec.outputNullness == Nullability.NON_NULL
        spec.weight == Weights.EXPENSIVE
        spec.weight > Weights.METHOD
        spec.ports.size() == 1
        spec.ports[0].name == 'value'
        spec.ports[0].nullness == Nullability.NON_NULL
        spec.ports[0].template == PortType.variable(0)
        spec.codegen instanceof BodyCodegen
    }

    def 'expand\'s codegen carries the demand\'s @MapEnum overrides through to coverage on the classic tier'() {
        ctx.isEnum(targetType) >> true
        TypeMirror sourceType = Mock()
        TypeElement sourceElement = Mock()
        TypeElement targetElement = Mock()
        BodyRenderContext context = Mock()
        context.resolveCtx() >> ctx
        context.portType('value') >> sourceType
        ctx.isEnum(sourceType) >> true
        ctx.asTypeElement(sourceType) >> Optional.of(sourceElement)
        ctx.membersOf(sourceElement) >> { [constant('NEW')].stream() }
        ctx.asTypeElement(targetType) >> Optional.of(targetElement)
        ctx.membersOf(targetElement) >> { [constant('CREATED')].stream() }
        context.switchStyle() >> SwitchStyle.CLASSIC
        context.sourceVersion() >> SourceVersion.RELEASE_11
        context.single() >> CodeBlock.of('v')
        def demand = Demands.withEnumOverrides(targetType, [enumOverride('NEW', 'CREATED')])

        when:
        def spec = new EnumConversion().expand(demand, ctx).toList().first().spec

        then: 'NEW has no same-name match in the target — only the override covers it, so no coverage exception'
        ((BodyCodegen) spec.codegen).render(context) != null
    }

    // ---- effectiveOverrides: only a real target constant is stamped consumed (design D3) ----------------------------

    def 'effectiveOverrides keeps only the overrides naming a real target constant'() {
        TypeElement targetElement = Mock()
        ctx.asTypeElement(targetType) >> Optional.of(targetElement)
        ctx.membersOf(targetElement) >> [constant('CREATED')].stream()
        def valid = enumOverride('NEW', 'CREATED')
        def invalid = enumOverride('OLD', 'ARCHIVED')

        expect:
        EnumConversion.effectiveOverrides(targetType, [valid, invalid], ctx) == [valid] as Set
    }

    // ---- resolveStyle -----------------------------------------------------------------------------------------

    def 'resolveStyle AUTO resolves to ARROW on Java 14 and newer'() {
        expect:
        EnumConversion.resolveStyle(SwitchStyle.AUTO, SourceVersion.valueOf('RELEASE_14')) == SwitchStyle.ARROW
        EnumConversion.resolveStyle(SwitchStyle.AUTO, SourceVersion.valueOf('RELEASE_17')) == SwitchStyle.ARROW
    }

    def 'resolveStyle AUTO resolves to CLASSIC below Java 14'() {
        expect:
        EnumConversion.resolveStyle(SwitchStyle.AUTO, SourceVersion.RELEASE_11) == SwitchStyle.CLASSIC
    }

    def 'resolveStyle honours an explicit CLASSIC or ARROW regardless of the target version'() {
        expect:
        EnumConversion.resolveStyle(SwitchStyle.CLASSIC, SourceVersion.valueOf('RELEASE_17')) == SwitchStyle.CLASSIC
        EnumConversion.resolveStyle(SwitchStyle.ARROW, SourceVersion.RELEASE_11) == SwitchStyle.ARROW
    }

    // ---- buildMapping (task 6.2: name-match, then @MapEnum precedence) --------------------------------------------

    def 'buildMapping same-name-matches every source constant with an identically-named target constant'() {
        expect:
        EnumConversion.buildMapping(['CREATED', 'FULFILLED'], ['CREATED', 'FULFILLED', 'ARCHIVED'], []) ==
                [CREATED: 'CREATED', FULFILLED: 'FULFILLED']
    }

    def 'buildMapping leaves an unmatched, un-overridden source constant uncovered'() {
        expect:
        EnumConversion.buildMapping(['NEW'], ['CREATED'], []) == [:]
    }

    def 'buildMapping applies a @MapEnum override with precedence over a coincidental same-name match'() {
        expect:
        EnumConversion.buildMapping(['NEW'], ['NEW', 'CREATED'], [enumOverride('NEW', 'CREATED')]) == [NEW: 'CREATED']
    }

    def 'buildMapping applies a @MapEnum override for a source with no same-name match at all'() {
        expect:
        EnumConversion.buildMapping(['NEW'], ['CREATED'], [enumOverride('NEW', 'CREATED')]) == [NEW: 'CREATED']
    }

    // ---- renderClassic: coverage is guaranteed by sourceBound before render ever runs ------------------------------

    def 'renderClassic renders every constant when every source constant is covered'() {
        expect:
        EnumConversion.renderClassic(CodeBlock.of('v'), targetType, ['NEW', 'COMPLETED'],
                [NEW: 'CREATED', COMPLETED: 'FULFILLED']) != null
    }

    // ---- renderArrow: coverage is always deferred to javac's exhaustiveness check -----------------------------------

    def 'renderArrow never throws, even for an uncovered constant — coverage is deferred to javac'() {
        expect:
        EnumConversion.renderArrow(CodeBlock.of('v'), targetType, ['NEW', 'CANCELLED'], [NEW: 'CREATED']) != null
    }

    // ---- render: dispatches by the effective style, and the classic/arrow coverage split holds end-to-end ----------

    def 'render defers coverage entirely to javac on the arrow tier — no throw for the same incomplete coverage'() {
        TypeMirror sourceType = Mock()
        TypeElement sourceElement = Mock()
        TypeElement targetElement = Mock()
        BodyRenderContext context = Mock()
        context.resolveCtx() >> ctx
        context.portType('value') >> sourceType
        ctx.isEnum(sourceType) >> true
        ctx.asTypeElement(sourceType) >> Optional.of(sourceElement)
        ctx.membersOf(sourceElement) >> [constant('NEW'), constant('CANCELLED')].stream()
        ctx.asTypeElement(targetType) >> Optional.of(targetElement)
        ctx.membersOf(targetElement) >> [constant('NEW')].stream()
        context.switchStyle() >> SwitchStyle.ARROW
        context.sourceVersion() >> SourceVersion.RELEASE_17
        context.single() >> CodeBlock.of('v')

        expect:
        EnumConversion.render(context, targetType, []) != null
    }

    // ---- sourceBound: vetoes a non-enum source or an uncovered one before render ever runs (design D6) -------------

    def 'sourceBound refuses a non-enum source, naming it, without inspecting its constants'() {
        TypeMirror sourceType = Mock()
        ctx.isEnum(sourceType) >> false

        when:
        def refusal = EnumConversion.sourceBound(targetType, []).check(sourceType, ctx)

        then:
        0 * ctx.asTypeElement(_)

        expect:
        refusal.present
    }

    def 'sourceBound refuses an enum source with an uncovered constant, naming it'() {
        TypeMirror sourceType = Mock()
        TypeElement sourceElement = Mock()
        TypeElement targetElement = Mock()
        ctx.isEnum(sourceType) >> true
        ctx.asTypeElement(sourceType) >> Optional.of(sourceElement)
        ctx.membersOf(sourceElement) >> [constant('NEW'), constant('CANCELLED')].stream()
        ctx.asTypeElement(targetType) >> Optional.of(targetElement)
        ctx.membersOf(targetElement) >> [constant('NEW')].stream()

        when:
        def refusal = EnumConversion.sourceBound(targetType, []).check(sourceType, ctx)

        then:
        refusal.present
        ((Offer.Refusal) refusal.get()).message.contains('CANCELLED')
    }

    def 'sourceBound accepts an enum source whose constants are fully covered'() {
        TypeMirror sourceType = Mock()
        TypeElement sourceElement = Mock()
        TypeElement targetElement = Mock()
        ctx.isEnum(sourceType) >> true
        ctx.asTypeElement(sourceType) >> Optional.of(sourceElement)
        ctx.membersOf(sourceElement) >> [constant('NEW')].stream()
        ctx.asTypeElement(targetType) >> Optional.of(targetElement)
        ctx.membersOf(targetElement) >> [constant('NEW')].stream()

        expect:
        EnumConversion.sourceBound(targetType, []).check(sourceType, ctx).empty
    }

    // ---- enumConstantNames -------------------------------------------------------------------------------------

    def 'enumConstantNames returns only the declared enum constants, in order, excluding other members'() {
        TypeElement element = Mock()
        ctx.asTypeElement(targetType) >> Optional.of(element)
        ctx.membersOf(element) >> [constant('NEW'), constant('COMPLETED'), nonConstantMember()].stream()

        expect:
        EnumConversion.enumConstantNames(ctx, targetType) == ['NEW', 'COMPLETED']
    }

    def 'enumConstantNames is empty for a type with no backing element'() {
        ctx.asTypeElement(targetType) >> Optional.empty()

        expect:
        EnumConversion.enumConstantNames(ctx, targetType).empty
    }

    private static DirectiveInput enumOverride(final String source, final String target) {
        DirectiveInput.structured('enum', [source: source, target: target], Subjects.none())
    }

    private static Element constant(final String simpleName) {
        [getKind: { -> ElementKind.ENUM_CONSTANT }, getSimpleName: { -> name(simpleName) }] as Element
    }

    private static Element nonConstantMember() {
        [getKind: { -> ElementKind.METHOD }, getSimpleName: { -> name('values') }] as Element
    }

    private static Name name(final String value) {
        [toString: { -> value }] as Name
    }
}
