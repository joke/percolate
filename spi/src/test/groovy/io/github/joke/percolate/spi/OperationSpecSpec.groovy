package io.github.joke.percolate.spi

import io.github.joke.percolate.javapoet.CodeBlock
import io.github.joke.percolate.javapoet.TypeName
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.ExecutableElement
import javax.lang.model.type.TypeMirror

/**
 * {@link OperationSpec} is a plain {@code @Value} data carrier for a single expansion-strategy production. Its four
 * factories each wire a different shape — {@link OperationSpec#of} (plain total), {@link OperationSpec#ofPartial}
 * (plain, may throw), {@link OperationSpec#callOf} (a method-call production carrying its call target), and
 * {@link OperationSpec#mapping} (a scope-owning element mapping carrying its child scope) — and the two
 * {@code with*} methods layer the additive, optional neutral facts ({@code consumedOptionKeys}/
 * {@code memberRequests}) onto an existing spec, defaulting to empty otherwise. Unit-tested over opaque
 * {@link TypeMirror} tokens and mocked {@link Codegen}/{@link ExecutableElement}/{@link MemberRequest}; no javac.
 */
@Tag('unit')
class OperationSpecSpec extends Specification {

    Codegen codegen = Mock()
    TypeMirror outputType = Mock()
    TypeMirror portType = Mock()
    ExecutableElement callTarget = Mock()

    def 'of wires a plain total operation from a defensive copy of the ports: no child scope, not partial, no call target, no options or members'() {
        def ports = [new Port('value', portType, Nullability.NON_NULL)]

        when:
        def spec = OperationSpec.of('label', codegen, 3, ports, outputType, Nullability.NON_NULL)
        ports.clear()

        then:
        spec.label == 'label'
        spec.codegen.is(codegen)
        spec.weight == 3
        spec.ports.size() == 1
        spec.ports[0].name == 'value'
        spec.outputType.is(outputType)
        spec.outputNullness == Nullability.NON_NULL
        spec.childScope.empty
        !spec.partial
        spec.callTarget.empty
        spec.consumedOptionKeys.empty
        spec.memberRequests.empty
    }

    def 'ofPartial wires the same shape as of, but marked partial'() {
        def ports = [new Port('value', portType, Nullability.NON_NULL)]

        when:
        def spec = OperationSpec.ofPartial('label', codegen, 3, ports, outputType, Nullability.NON_NULL)
        ports.clear()

        then:
        spec.label == 'label'
        spec.codegen.is(codegen)
        spec.weight == 3
        spec.ports.size() == 1
        spec.ports[0].name == 'value'
        spec.outputType.is(outputType)
        spec.outputNullness == Nullability.NON_NULL
        spec.childScope.empty
        spec.partial
        spec.callTarget.empty
        spec.consumedOptionKeys.empty
        spec.memberRequests.empty
    }

    def 'callOf wires a total method-call production carrying its call target'() {
        def ports = [new Port('value', portType, Nullability.NON_NULL)]

        when:
        def spec = OperationSpec.callOf('label', codegen, 1, ports, outputType, Nullability.NON_NULL, callTarget)

        then:
        spec.label == 'label'
        spec.codegen.is(codegen)
        spec.weight == 1
        spec.ports.size() == 1
        spec.outputType.is(outputType)
        spec.outputNullness == Nullability.NON_NULL
        spec.childScope.empty
        !spec.partial
        spec.callTarget.present
        spec.callTarget.get().is(callTarget)
        spec.consumedOptionKeys.empty
        spec.memberRequests.empty
    }

    def 'mapping wires a scope-owning element mapping carrying its child scope'() {
        def child = new ChildScopeSpec(portType, Nullability.NON_NULL, outputType, Nullability.NON_NULL)
        def ports = [new Port('source', portType, Nullability.NON_NULL)]

        when:
        def spec = OperationSpec.mapping('map', codegen, 2, ports, outputType, Nullability.NON_NULL, child)

        then:
        spec.label == 'map'
        spec.codegen.is(codegen)
        spec.weight == 2
        spec.ports.size() == 1
        spec.outputType.is(outputType)
        spec.outputNullness == Nullability.NON_NULL
        spec.childScope.present
        spec.childScope.get().is(child)
        !spec.partial
        spec.callTarget.empty
        spec.consumedOptionKeys.empty
        spec.memberRequests.empty
    }

    def 'withConsumedOptionKeys replaces the option-key set with a defensive copy, preserving every other field'() {
        def original = OperationSpec.of(
                'label', codegen, 1, [new Port('value', portType, Nullability.NON_NULL)], outputType, Nullability.NON_NULL)
        def keys = ['format'] as Set

        when:
        def spec = original.withConsumedOptionKeys(keys)
        keys.clear()

        then:
        spec.consumedOptionKeys == ['format'] as Set
        spec.label == original.label
        spec.codegen.is(original.codegen)
        spec.weight == original.weight
        spec.ports == original.ports
        spec.outputType.is(original.outputType)
        spec.outputNullness == original.outputNullness
        spec.childScope == original.childScope
        spec.partial == original.partial
        spec.callTarget == original.callTarget
        spec.memberRequests == original.memberRequests
    }

    def 'withMemberRequests replaces the member-request list with a defensive copy, preserving every other field'() {
        def original = OperationSpec.of(
                'label', codegen, 1, [new Port('value', portType, Nullability.NON_NULL)], outputType, Nullability.NON_NULL)
        def request = new MemberRequest(TypeName.INT, CodeBlock.of('$L', 0), 'key')
        def requests = [request]

        when:
        def spec = original.withMemberRequests(requests)
        requests.clear()

        then:
        spec.memberRequests == [request]
        spec.label == original.label
        spec.codegen.is(original.codegen)
        spec.weight == original.weight
        spec.ports == original.ports
        spec.outputType.is(original.outputType)
        spec.outputNullness == original.outputNullness
        spec.childScope == original.childScope
        spec.partial == original.partial
        spec.callTarget == original.callTarget
        spec.consumedOptionKeys == original.consumedOptionKeys
    }
}
