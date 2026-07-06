package io.github.joke.percolate.processor.internal.stages.expand

import io.github.joke.percolate.spi.PortType
import io.github.joke.percolate.spi.ResolveCtx
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.TypeElement
import javax.lang.model.type.TypeMirror

/**
 * {@link Unifier} unit-tested mock-only over the {@link ResolveCtx} type-query seam: every {@link TypeMirror} is an
 * opaque, never-interrogated token. {@code unify}'s dispatch to {@code bindVariable}/{@code unifyApp} and
 * {@code unifyApp}'s per-argument recursion back into {@code unify} are the collaborator's genuine self-recursion
 * (design D4/D5 of change {@code decompose-engine-stages}) — isolated here with a {@code Spy} on the subject; every
 * other method ({@code bindVariable}, {@code isGroundable}, {@code unifyApp}'s own guards) is tested directly against
 * a mocked {@code ResolveCtx}.
 */
@Tag('unit')
class UnifierSpec extends Specification {

    ResolveCtx ctx = Mock()
    TypeMirror source = Mock()
    TypeMirror concreteType = Mock()

    // ---- unify: depth bound, then dispatch to the sibling method for each template shape -----------------

    def 'unify aborts past the recursion bound without any ctx interaction'() {
        Unifier unifier = new Unifier(ctx)

        when:
        def result = unifier.unify(PortType.concrete(concreteType), source, [:], 33)

        then:
        0 * ctx._

        expect:
        !result
    }

    def 'unify at exactly the recursion bound still dispatches on the template shape'() {
        Unifier unifier = new Unifier(ctx)
        ctx.isSameType(concreteType, source) >> true

        expect:
        unifier.unify(PortType.concrete(concreteType), source, [:], 32)
    }

    def 'unify dispatches a Var template to bindVariable, returning its verdict'() {
        Unifier unifier = Spy(constructorArgs: [ctx])
        def bindings = [:]

        when:
        def result = unifier.unify(PortType.variable(2), source, bindings, 5)

        then:
        1 * unifier.bindVariable(2, source, bindings) >> verdict
        1 * unifier._
        0 * _

        expect:
        result == verdict

        where:
        verdict << [true, false]
    }

    def 'unify dispatches an App template to unifyApp, returning its verdict'() {
        Unifier unifier = Spy(constructorArgs: [ctx])
        def template = PortType.app(Mock(TypeElement), [])
        def bindings = [:]

        when:
        def result = unifier.unify(template, source, bindings, 5)

        then:
        1 * unifier.unifyApp(template, source, bindings, 5) >> verdict
        1 * unifier._
        0 * _

        expect:
        result == verdict

        where:
        verdict << [true, false]
    }

    // ---- unify: Concrete template matches by isSameType, ignoring bindings ---------------------------------

    def 'unify a Concrete template checks isSameType against the source'() {
        Unifier unifier = new Unifier(ctx)
        ctx.isSameType(concreteType, source) >> same

        expect:
        unifier.unify(PortType.concrete(concreteType), source, [:], 0) == same

        where:
        same << [true, false]
    }

    // ---- bindVariable: groundable/non-groundable, fresh/existing binding -----------------------------------

    def 'bindVariable refuses a non-groundable source'() {
        Unifier unifier = new Unifier(ctx)
        ctx.isDeclared(source) >> false
        ctx.isArray(source) >> false

        expect:
        !unifier.bindVariable(0, source, [:])
    }

    def 'bindVariable stores a fresh binding for a groundable source'() {
        Unifier unifier = new Unifier(ctx)
        ctx.isDeclared(source) >> true
        def bindings = [:]

        when:
        def result = unifier.bindVariable(3, source, bindings)

        then:
        result
        bindings[3].is(source)
    }

    def 'bindVariable re-checks an existing binding by isSameType'() {
        Unifier unifier = new Unifier(ctx)
        ctx.isDeclared(source) >> true
        ctx.isSameType(concreteType, source) >> consistent
        def bindings = [1: concreteType]

        expect:
        unifier.bindVariable(1, source, bindings) == consistent

        where:
        consistent << [true, false]
    }

    // ---- isGroundable: declared or array, never neither ------------------------------------------------------

    def 'isGroundable is true for a declared source, without asking whether it is an array'() {
        Unifier unifier = new Unifier(ctx)
        ctx.isDeclared(source) >> true

        when:
        def result = unifier.isGroundable(source)

        then:
        0 * ctx.isArray(_)

        expect:
        result
    }

    def 'isGroundable is true for a non-declared array source'() {
        Unifier unifier = new Unifier(ctx)
        ctx.isDeclared(source) >> false
        ctx.isArray(source) >> true

        expect:
        unifier.isGroundable(source)
    }

    def 'isGroundable is false for a source that is neither declared nor an array'() {
        Unifier unifier = new Unifier(ctx)
        ctx.isDeclared(source) >> false
        ctx.isArray(source) >> false

        expect:
        !unifier.isGroundable(source)
    }

    // ---- unifyApp: guards short-circuit before any argument is unified ----------------------------------------

    def 'unifyApp never unifies a non-declared source, checking nothing further'() {
        Unifier unifier = new Unifier(ctx)

        when:
        def result = unifier.unifyApp(PortType.app(Mock(TypeElement), [PortType.variable(0)]), source, [:], 0)

        then:
        1 * ctx.isDeclared(source) >> false
        0 * ctx.erasure(_)

        expect:
        !result
    }

    def 'unifyApp never unifies a source whose erasure differs from the template\'s, checking no argument arity'() {
        Unifier unifier = new Unifier(ctx)
        TypeElement erasureElement = Mock()
        TypeMirror erasureType = Mock()
        TypeMirror sourceErasure = Mock()
        erasureElement.asType() >> erasureType
        def template = PortType.app(erasureElement, [PortType.variable(0)])

        when:
        def result = unifier.unifyApp(template, source, [:], 0)

        then:
        1 * ctx.isDeclared(source) >> true
        1 * ctx.erasure(source) >> sourceErasure
        1 * ctx.erasure(erasureType) >> erasureType
        1 * ctx.isSameType(sourceErasure, erasureType) >> false
        0 * ctx.typeArgumentCount(_)

        expect:
        !result
    }

    def 'unifyApp never unifies a source with a different type-argument arity, checking no argument'() {
        Unifier unifier = new Unifier(ctx)
        TypeElement erasureElement = Mock()
        TypeMirror erasureType = Mock()
        erasureElement.asType() >> erasureType
        ctx.isDeclared(source) >> true
        ctx.erasure(source) >> erasureType
        ctx.erasure(erasureType) >> erasureType
        ctx.isSameType(erasureType, erasureType) >> true
        ctx.typeArgumentCount(source) >> 2
        def template = PortType.app(erasureElement, [PortType.variable(0)])

        when:
        def result = unifier.unifyApp(template, source, [:], 0)

        then:
        0 * ctx.typeArgument(_, _)

        expect:
        !result
    }

    // ---- unifyApp: the arity-matching path recurses into unify per argument (self-recursion, isolated by Spy) ----

    def 'unifyApp unifies a single-argument template by recursing into unify at depth + 1'() {
        TypeElement erasureElement = Mock()
        TypeMirror erasureType = Mock()
        TypeMirror argType = Mock()
        TypeMirror argSource = Mock()
        Unifier unifier = Spy(constructorArgs: [ctx])
        def argTemplate = PortType.concrete(argType)
        def template = PortType.app(erasureElement, [argTemplate])
        def bindings = [:]

        when:
        def result = unifier.unifyApp(template, source, bindings, 0)

        then:
        1 * erasureElement.asType() >> erasureType
        1 * ctx.isDeclared(source) >> true
        1 * ctx.erasure(source) >> erasureType
        1 * ctx.erasure(erasureType) >> erasureType
        1 * ctx.isSameType(erasureType, erasureType) >> true
        1 * ctx.typeArgumentCount(source) >> 1
        1 * ctx.typeArgument(source, 0) >> argSource
        1 * unifier.unify(argTemplate, argSource, bindings, 1) >> true
        1 * unifier._
        0 * _

        expect:
        result
    }

    def 'unifyApp short-circuits on the first argument that fails to unify, never checking the rest'() {
        TypeElement erasureElement = Mock()
        TypeMirror erasureType = Mock()
        TypeMirror firstArgSource = Mock()
        Unifier unifier = Spy(constructorArgs: [ctx])
        def firstTemplate = PortType.variable(0)
        def secondTemplate = PortType.variable(1)
        def template = PortType.app(erasureElement, [firstTemplate, secondTemplate])
        def bindings = [:]

        when:
        def result = unifier.unifyApp(template, source, bindings, 0)

        then:
        1 * erasureElement.asType() >> erasureType
        1 * ctx.isDeclared(source) >> true
        1 * ctx.erasure(source) >> erasureType
        1 * ctx.erasure(erasureType) >> erasureType
        1 * ctx.isSameType(erasureType, erasureType) >> true
        1 * ctx.typeArgumentCount(source) >> 2
        1 * ctx.typeArgument(source, 0) >> firstArgSource
        1 * unifier.unify(firstTemplate, firstArgSource, bindings, 1) >> false
        1 * unifier._
        0 * _

        expect:
        !result
    }
}
