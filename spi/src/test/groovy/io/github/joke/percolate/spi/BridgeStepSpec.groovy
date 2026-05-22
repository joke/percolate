package io.github.joke.percolate.spi

import com.palantir.javapoet.CodeBlock
import io.github.joke.percolate.spi.test.TypeUniverse
import spock.lang.Specification
import spock.lang.Tag

@Tag('unit')
class BridgeStepSpec extends Specification {

    static final EdgeCodegen NO_OP = { vars, inputs -> CodeBlock.of('$L', inputs.single()) }

    def 'six-argument constructor exposes its six fields'() {
        given:
        def step = new BridgeStep(
                TypeUniverse.STRING,
                TypeUniverse.INTEGER,
                Weights.STEP,
                NO_OP,
                ScopeTransition.ENTERING,
                'element')

        expect:
        step.inputType == TypeUniverse.STRING
        step.outputType == TypeUniverse.INTEGER
        step.weight == Weights.STEP
        step.codegen == NO_OP
        step.scopeTransition == ScopeTransition.ENTERING
        step.elementRole == 'element'
    }

    def 'two BridgeSteps are value-equal when their fields are equal'() {
        given:
        def a = new BridgeStep(
                TypeUniverse.STRING, TypeUniverse.INTEGER, Weights.STEP, NO_OP,
                ScopeTransition.PRESERVING, 'element')
        def b = new BridgeStep(
                TypeUniverse.STRING, TypeUniverse.INTEGER, Weights.STEP, NO_OP,
                ScopeTransition.PRESERVING, 'element')

        expect:
        a == b
        a.hashCode() == b.hashCode()
    }

    def 'PRESERVING is the default for same-scope bridges (four-arg constructor)'() {
        given:
        def step = new BridgeStep(TypeUniverse.STRING, TypeUniverse.INTEGER, Weights.STEP, NO_OP)

        expect:
        step.scopeTransition == ScopeTransition.PRESERVING
        step.elementRole == 'element'
    }

    def 'ENTERING scope identifies a scope-enter bridge'() {
        given:
        def step = new BridgeStep(
                TypeUniverse.LIST_OF_STRING, TypeUniverse.STRING, Weights.CONTAINER, NO_OP,
                ScopeTransition.ENTERING, 'element')

        expect:
        step.scopeTransition == ScopeTransition.ENTERING
        step.inputType == TypeUniverse.LIST_OF_STRING
        step.outputType == TypeUniverse.STRING
        step.elementRole == 'element'
    }

    def 'EXITING scope identifies a scope-exit bridge'() {
        given:
        def step = new BridgeStep(
                TypeUniverse.STRING, TypeUniverse.LIST_OF_STRING, Weights.CONTAINER, NO_OP,
                ScopeTransition.EXITING, 'element')

        expect:
        step.scopeTransition == ScopeTransition.EXITING
        step.inputType == TypeUniverse.STRING
        step.outputType == TypeUniverse.LIST_OF_STRING
        step.elementRole == 'element'
    }
}
