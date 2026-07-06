package io.github.joke.percolate.processor.internal.stages.expand

import io.github.joke.percolate.spi.ChildScopeSpec
import io.github.joke.percolate.spi.Codegen
import io.github.joke.percolate.spi.Nullability
import io.github.joke.percolate.spi.OperationSpec
import io.github.joke.percolate.spi.Port
import io.github.joke.percolate.spi.PortType
import io.github.joke.percolate.spi.ResolveCtx
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.TypeElement
import javax.lang.model.type.TypeMirror

/**
 * {@link SpecInstantiator} unit-tested mock-only over the {@link ResolveCtx} type-query seam. {@code instantiate},
 * {@code groundPort}, {@code groundChild}, and {@code groundOr} each delegate to a sibling method, so each is tested
 * in isolation with a {@code Spy} stubbing that sibling; {@code ground}'s recursion over a nested {@code App}'s
 * arguments is the collaborator's genuine self-recursion (design D4/D5 of change {@code decompose-engine-stages}),
 * isolated the same way.
 */
@Tag('unit')
class SpecInstantiatorSpec extends Specification {

    ResolveCtx ctx = Mock()
    Codegen codegen = Mock()
    TypeMirror outputType = Mock()
    TypeMirror concreteType = Mock()

    // ---- instantiate: grounds every port, then builds the concrete spec shape (plain / partial / mapping) ------

    def 'instantiate a plain spec grounds each port and builds it via OperationSpec.of'() {
        SpecInstantiator instantiator = Spy(constructorArgs: [ctx])
        def port0 = new Port('a', Mock(TypeMirror), Nullability.NON_NULL, PortType.variable(0))
        def port1 = new Port('b', Mock(TypeMirror), Nullability.NON_NULL, PortType.variable(1))
        def groundedPort0 = new Port('a', concreteType, Nullability.NON_NULL)
        def groundedPort1 = new Port('b', concreteType, Nullability.NON_NULL)
        def spec = OperationSpec.of('op', codegen, 7, [port0, port1], outputType, Nullability.NON_NULL)
        def bindings = [:]

        when:
        def result = instantiator.instantiate(spec, bindings)

        then:
        1 * instantiator.groundPort(port0, bindings) >> groundedPort0
        1 * instantiator.groundPort(port1, bindings) >> groundedPort1
        1 * instantiator._
        0 * _

        expect:
        result.label == 'op'
        result.codegen.is(codegen)
        result.weight == 7
        result.ports == [groundedPort0, groundedPort1]
        result.outputType.is(outputType)
        result.outputNullness == Nullability.NON_NULL
        result.childScope.empty
        !result.partial
    }

    def 'instantiate a partial spec grounds each port and builds it via OperationSpec.ofPartial'() {
        SpecInstantiator instantiator = Spy(constructorArgs: [ctx])
        def port = new Port('a', Mock(TypeMirror), Nullability.NON_NULL, PortType.variable(0))
        def groundedPort = new Port('a', concreteType, Nullability.NON_NULL)
        def spec = OperationSpec.ofPartial('firstOrThrow', codegen, 3, [port], outputType, Nullability.NON_NULL)
        def bindings = [:]

        when:
        def result = instantiator.instantiate(spec, bindings)

        then:
        1 * instantiator.groundPort(port, bindings) >> groundedPort
        1 * instantiator._
        0 * _

        expect:
        result.partial
        result.childScope.empty
        result.ports == [groundedPort]
        result.label == 'firstOrThrow'
        result.codegen.is(codegen)
        result.weight == 3
        result.outputType.is(outputType)
        result.outputNullness == Nullability.NON_NULL
    }

    def 'instantiate a mapping spec grounds the port and the child scope, building it via OperationSpec.mapping'() {
        SpecInstantiator instantiator = Spy(constructorArgs: [ctx])
        def port = new Port('a', Mock(TypeMirror), Nullability.NON_NULL, PortType.variable(0))
        def groundedPort = new Port('a', concreteType, Nullability.NON_NULL)
        def child = ChildScopeSpec.lifted(PortType.variable(0), Nullability.NON_NULL, outputType, Nullability.NON_NULL)
        def groundedChild = new ChildScopeSpec(concreteType, Nullability.NON_NULL, outputType, Nullability.NON_NULL)
        def spec = OperationSpec.mapping('map', codegen, 5, [port], outputType, Nullability.NON_NULL, child)
        def bindings = [:]

        when:
        def result = instantiator.instantiate(spec, bindings)

        then:
        1 * instantiator.groundPort(port, bindings) >> groundedPort
        1 * instantiator.groundChild(child, bindings) >> groundedChild
        1 * instantiator._
        0 * _

        expect:
        result.childScope.get().is(groundedChild)
        result.ports == [groundedPort]
        result.label == 'map'
        result.codegen.is(codegen)
        result.weight == 5
        result.outputType.is(outputType)
        result.outputNullness == Nullability.NON_NULL
        !result.partial
    }

    // ---- groundPort: pass-through, or substitute the template via ground ----------------------------------------

    def 'groundPort passes a template-free port through unchanged'() {
        SpecInstantiator instantiator = new SpecInstantiator(ctx)
        def port = new Port('src', concreteType, Nullability.NON_NULL)

        expect:
        instantiator.groundPort(port, [:]).is(port)
    }

    def 'groundPort substitutes a template port\'s type via ground, preserving name, nullness, and sourcing mode'() {
        SpecInstantiator instantiator = Spy(constructorArgs: [ctx])
        def template = PortType.variable(0)
        def port = new Port('src', Mock(TypeMirror), Nullability.NULLABLE, template, Port.Sourcing.SUBTARGET)
        def bindings = [:]

        when:
        def grounded = instantiator.groundPort(port, bindings)

        then:
        1 * instantiator.ground(template, bindings) >> concreteType
        1 * instantiator._
        0 * _

        expect:
        grounded.name == 'src'
        grounded.type.is(concreteType)
        grounded.nullness == Nullability.NULLABLE
        grounded.template == null
        grounded.sourcing == Port.Sourcing.SUBTARGET
    }

    // ---- groundChild: substitutes each element type via groundOr, preserving nullness ----------------------------

    def 'groundChild substitutes each element type via groundOr, preserving nullness'() {
        SpecInstantiator instantiator = Spy(constructorArgs: [ctx])
        def inTemplate = PortType.variable(0)
        def outTemplate = PortType.variable(1)
        def elementIn = Mock(TypeMirror)
        def elementOut = Mock(TypeMirror)
        def groundedIn = Mock(TypeMirror)
        def groundedOut = Mock(TypeMirror)
        def child = new ChildScopeSpec(elementIn, Nullability.NON_NULL, elementOut, Nullability.NULLABLE, inTemplate, outTemplate)
        def bindings = [:]

        when:
        def grounded = instantiator.groundChild(child, bindings)

        then:
        1 * instantiator.groundOr(inTemplate, elementIn, bindings) >> groundedIn
        1 * instantiator.groundOr(outTemplate, elementOut, bindings) >> groundedOut
        1 * instantiator._
        0 * _

        expect:
        grounded.elementIn.is(groundedIn)
        grounded.elementInNullness == Nullability.NON_NULL
        grounded.elementOut.is(groundedOut)
        grounded.elementOutNullness == Nullability.NULLABLE
    }

    // ---- groundOr: pass the concrete type through, or substitute via ground --------------------------------------

    def 'groundOr returns the concrete type unchanged when there is no template'() {
        SpecInstantiator instantiator = new SpecInstantiator(ctx)

        expect:
        instantiator.groundOr(null, concreteType, [:]).is(concreteType)
    }

    def 'groundOr substitutes via ground when a template is present'() {
        SpecInstantiator instantiator = Spy(constructorArgs: [ctx])
        def template = PortType.variable(0)
        def bindings = [:]

        when:
        def grounded = instantiator.groundOr(template, concreteType, bindings)

        then:
        1 * instantiator.ground(template, bindings) >> outputType
        1 * instantiator._
        0 * _

        expect:
        grounded.is(outputType)
    }

    // ---- ground: Concrete leaf, Var lookup (bound / ungrounded), App recursion (self-recursion, isolated by Spy) --

    def 'ground a Concrete template returns its type directly'() {
        SpecInstantiator instantiator = new SpecInstantiator(ctx)

        expect:
        instantiator.ground(PortType.concrete(concreteType), [:]).is(concreteType)
    }

    def 'ground a Var template returns its binding'() {
        SpecInstantiator instantiator = new SpecInstantiator(ctx)

        expect:
        instantiator.ground(PortType.variable(2), [2: concreteType]).is(concreteType)
    }

    def 'ground an ungrounded Var template fails fast, naming the offending template'() {
        SpecInstantiator instantiator = new SpecInstantiator(ctx)
        def template = PortType.variable(5)

        when:
        instantiator.ground(template, [:])

        then:
        def error = thrown(IllegalStateException)

        expect:
        error.message == "Ungrounded type variable while instantiating: ${template}".toString()
    }

    def 'ground an App template with no arguments builds the declared type with no arguments'() {
        SpecInstantiator instantiator = new SpecInstantiator(ctx)
        TypeElement erasureElement = Mock()
        ctx.declaredType(erasureElement) >> outputType
        def template = PortType.app(erasureElement, [])

        expect:
        instantiator.ground(template, [:]).is(outputType)
    }

    def 'ground an App template recurses into each argument via ground before building the declared type'() {
        TypeElement erasureElement = Mock()
        SpecInstantiator instantiator = Spy(constructorArgs: [ctx])
        def argTemplate0 = PortType.variable(0)
        def argTemplate1 = PortType.variable(1)
        def argType0 = Mock(TypeMirror)
        def argType1 = Mock(TypeMirror)
        def template = PortType.app(erasureElement, [argTemplate0, argTemplate1])
        def bindings = [:]

        when:
        def grounded = instantiator.ground(template, bindings)

        then:
        1 * instantiator.ground(argTemplate0, bindings) >> argType0
        1 * instantiator.ground(argTemplate1, bindings) >> argType1
        1 * ctx.declaredType(erasureElement, argType0, argType1) >> outputType
        1 * instantiator._
        0 * _

        expect:
        grounded.is(outputType)
    }
}
