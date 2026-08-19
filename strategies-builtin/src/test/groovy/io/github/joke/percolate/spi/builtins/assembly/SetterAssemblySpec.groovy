package io.github.joke.percolate.spi.builtins.assembly

import io.github.joke.percolate.lib.javapoet.ClassName
import io.github.joke.percolate.lib.javapoet.CodeBlock
import io.github.joke.percolate.spi.IncomingValues
import io.github.joke.percolate.spi.MemberRequest
import io.github.joke.percolate.spi.Nullability
import io.github.joke.percolate.spi.OperationCodegen
import io.github.joke.percolate.spi.ResolveCtx
import io.github.joke.percolate.spi.Weights
import io.github.joke.percolate.spi.builtins.test.Demands
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.Element
import javax.lang.model.element.ElementKind
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.Modifier
import javax.lang.model.element.Name
import javax.lang.model.element.TypeElement
import javax.lang.model.element.VariableElement
import javax.lang.model.type.TypeMirror
import javax.lang.model.type.TypeVisitor
import java.util.stream.Stream

/**
 * {@link SetterAssembly} unit-tested mock-only over the {@link ResolveCtx} type-query seam: member reflection is
 * stubbed on a mocked {@code ResolveCtx} over opaque {@link ExecutableElement}/{@link VariableElement} member
 * tokens. No javac, no {@code ResolveCtxBuilder}, no shape fixtures.
 */
@Tag('unit')
class SetterAssemblySpec extends Specification {

    ResolveCtx ctx = Mock()
    SetterAssembly setterAssembly = new SetterAssembly()
    TypeMirror targetType = declared('com.example', 'Person')
    TypeElement typeElement = Mock()

    def 'emits no operation when the target type is not DECLARED'() {
        ctx.asTypeElement(targetType) >> Optional.empty()

        expect: 'declared children are present, so only the missing element can stop the assembly'
        setterAssembly.expand(Demands.assembling(targetType, ['name'] as Set), ctx).toList().empty
    }

    def 'a leaf demand (no declared children) is never assembled, even for a bean that would otherwise assemble'() {
        bean([noArgCtor(), setter('setName', declared('java.lang', 'String'))])
        typeElement.simpleName >> nameOf('Person')
        ctx.option('percolate.construction.preference') >> Optional.empty()

        expect: 'the very same bean assembles when a child is declared, so only the empty declaration stops it'
        setterAssembly.expand(Demands.forTarget(targetType), ctx).toList().empty
        !setterAssembly.expand(Demands.assembling(targetType, ['name'] as Set), ctx).toList().empty
    }

    def 'emits one n-ary operation with one sub-target port per declared child, typed from its setter'() {
        def nameType = declared('java.lang', 'String')
        def ageType = declared('java.lang', 'Integer')
        def setName = setter('setName', nameType)
        def setAge = setter('setAge', ageType)
        bean([noArgCtor(), setName, setAge])
        typeElement.simpleName >> nameOf('Person')
        ctx.option('percolate.construction.preference') >> Optional.empty()

        when:
        def declared = ['name', 'age'] as Set
        def specs = setterAssembly.expand(Demands.assembling(targetType, declared), ctx)*.spec

        then:
        specs.size() == 1

        expect:
        with(specs[0]) {
            childScope.empty
            codegen instanceof OperationCodegen
            outputType.is(targetType)
            outputNullness == Nullability.NON_NULL
            ports.size() == 2
            (ports*.name as Set) == declared
            ports.every { it.subTarget }
            ports[0].type.is(nameType)
            ports[1].type.is(ageType)
            ports.every { it.nullness == Nullability.NON_NULL }
            label == 'new Person().setName, setAge'
        }
    }

    def 'a declared subset of the setters still assembles, carrying only the declared ports'() {
        def nameType = declared('java.lang', 'String')
        bean([noArgCtor(), setter('setName', nameType), setter('setNickname', declared('java.lang', 'String'))])
        typeElement.simpleName >> nameOf('Person')
        ctx.option('percolate.construction.preference') >> Optional.empty()

        when:
        def specs = setterAssembly.expand(Demands.assembling(targetType, ['name'] as Set), ctx)*.spec

        then:
        specs.size() == 1

        expect:
        specs[0].ports*.name == ['name']
    }

    def 'a declared child with no matching setter yields no offer'() {
        bean([noArgCtor(), setter('setName', declared('java.lang', 'String'))])

        expect:
        setterAssembly.expand(Demands.assembling(targetType, ['name', 'missing'] as Set), ctx).toList().empty
    }

    def 'a target with no no-argument constructor yields no offer'() {
        ExecutableElement ctor = Mock()
        ctor.parameters >> [Mock(VariableElement)]
        ctx.isConstructor(ctor) >> true
        ctx.isPrivate(ctor) >> false
        bean([ctor, setter('setName', declared('java.lang', 'String'))])

        expect:
        setterAssembly.expand(Demands.assembling(targetType, ['name'] as Set), ctx).toList().empty
    }

    def 'a zero-argument method is not a constructor, so it cannot stand in for one'() {
        ExecutableElement getter = Mock()
        getter.parameters >> []
        ctx.isMethod(getter) >> true
        ctx.isPrivate(getter) >> false
        bean([getter, setter('setName', declared('java.lang', 'String'))])
        typeElement.simpleName >> nameOf('Person')
        ctx.option('percolate.construction.preference') >> Optional.empty()

        expect: 'the bean declares no constructor at all, only a no-argument getter'
        setterAssembly.expand(Demands.assembling(targetType, ['name'] as Set), ctx).toList().empty
    }

    def 'a target whose only no-argument constructor is private yields no offer'() {
        ExecutableElement ctor = Mock()
        ctor.parameters >> []
        ctx.isConstructor(ctor) >> true
        ctx.isPrivate(ctor) >> true
        bean([ctor, setter('setName', declared('java.lang', 'String'))])
        typeElement.simpleName >> nameOf('Person')
        ctx.option('percolate.construction.preference') >> Optional.empty()

        expect: 'everything else about the bean matches, so only the private constructor stops it'
        setterAssembly.expand(Demands.assembling(targetType, ['name'] as Set), ctx).toList().empty
    }

    def 'an abstract class or an interface target yields no offer'() {
        ctx.asTypeElement(targetType) >> Optional.of(typeElement)
        typeElement.kind >> kind
        typeElement.modifiers >> modifiers

        expect:
        setterAssembly.expand(Demands.assembling(targetType, ['name'] as Set), ctx).toList().empty

        where:
        kind                   | modifiers
        ElementKind.CLASS      | [Modifier.ABSTRACT] as Set
        ElementKind.INTERFACE  | [] as Set
        ElementKind.ENUM       | [] as Set
    }

    def 'an inherited setter matches, because member reflection reports inherited members'() {
        def salaryType = declared('java.lang', 'Long')
        bean([noArgCtor(), setter('setName', declared('java.lang', 'String')), setter('setSalary', salaryType)])
        typeElement.simpleName >> nameOf('Employee')
        ctx.option('percolate.construction.preference') >> Optional.empty()

        when:
        def specs = setterAssembly.expand(Demands.assembling(targetType, ['name', 'salary'] as Set), ctx)*.spec

        then:
        specs.size() == 1

        expect:
        specs[0].ports*.name == ['name', 'salary']
    }

    def 'a this-returning setter matches, because the return type is not part of the match'() {
        def ownerType = declared('java.lang', 'String')
        // The setter's return type is never stubbed: reading it at all would fail this spec.
        bean([noArgCtor(), setter('setOwner', ownerType)])
        typeElement.simpleName >> nameOf('Account')
        ctx.option('percolate.construction.preference') >> Optional.empty()

        when:
        def specs = setterAssembly.expand(Demands.assembling(targetType, ['owner'] as Set), ctx)*.spec

        then:
        specs.size() == 1

        expect:
        specs[0].ports*.name == ['owner']
    }

    def 'a mutator named after the child rather than setX is not recognised'() {
        bean([noArgCtor(), setter('name', declared('java.lang', 'String'))])

        expect:
        setterAssembly.expand(Demands.assembling(targetType, ['name'] as Set), ctx).toList().empty
    }

    def 'a private setter is not recognised'() {
        ExecutableElement hidden = Mock()
        hidden.simpleName >> nameOf('setName')
        hidden.parameters >> [Mock(VariableElement)]
        ctx.isMethod(hidden) >> true
        ctx.isPrivate(hidden) >> true
        bean([noArgCtor(), hidden])

        expect:
        setterAssembly.expand(Demands.assembling(targetType, ['name'] as Set), ctx).toList().empty
    }

    def 'a same-named method taking no argument is not a setter'() {
        ExecutableElement zeroArg = Mock()
        zeroArg.simpleName >> nameOf('setName')
        zeroArg.parameters >> []
        ctx.isMethod(zeroArg) >> true
        ctx.isPrivate(zeroArg) >> false
        bean([noArgCtor(), zeroArg])

        expect:
        setterAssembly.expand(Demands.assembling(targetType, ['name'] as Set), ctx).toList().empty
    }

    def 'setterName capitalises the child, and degrades to the bare prefix for an empty child'() {
        expect:
        setterAssembly.setterName(child) == expected

        where:
        child  | expected
        'name' | 'setName'
        'a'    | 'setA'
        'URL'  | 'setURL'
        ''     | 'set'
    }

    def 'the emitted spec declares exactly one method member request carrying the setter sequence'() {
        def nameType = declared('java.lang', 'String')
        bean([noArgCtor(), setter('setName', nameType)])
        typeElement.simpleName >> nameOf('Person')
        ctx.option('percolate.construction.preference') >> Optional.empty()

        when:
        def specs = setterAssembly.expand(Demands.assembling(targetType, ['name'] as Set), ctx)*.spec

        then:
        specs[0].memberRequests.size() == 1

        expect:
        with(specs[0].memberRequests[0] as MemberRequest.Method) {
            nameHint == 'assemblePerson'
            returnType.toString() == 'com.example.Person'
            dedupKey == 'setter:com.example.Person:name'
            parameters*.name == ['name']
            parameters*.type*.toString() == ['java.lang.String']
            body.toString() == 'com.example.Person result = new com.example.Person();\n' +
                    'result.setName(name);\n' +
                    'return result;\n'
        }
    }

    def 'the member request dedup key carries the assembly form, the target and the ordered children'() {

        expect:
        setterAssembly.dedupKey(targetType, ['name', 'age']) == 'setter:com.example.Person:name,age'
        setterAssembly.dedupKey(targetType, ['age', 'name']) == 'setter:com.example.Person:age,name'
        setterAssembly.dedupKey(targetType, ['name']) == 'setter:com.example.Person:name'
    }

    def 'the helper local is renamed when a declared child would collide with it'() {
        def resultType = declared('java.lang', 'String')
        bean([noArgCtor(), setter('setResult', resultType)])
        typeElement.simpleName >> nameOf('Person')
        targetType.toString() >> 'com.example.Box'
        resultType.toString() >> 'java.lang.String'
        ctx.option('percolate.construction.preference') >> Optional.empty()

        when:
        def specs = setterAssembly.expand(Demands.assembling(targetType, ['result'] as Set), ctx)*.spec

        then:
        specs.size() == 1

        expect: 'the parameter keeps the child name and the local takes the disambiguated one'
        with(specs[0].memberRequests[0] as MemberRequest.Method) {
            parameters*.name == ['result']
            body.toString().startsWith('com.example.Person result_ = new com.example.Person();')
            body.toString().contains('result_.setResult(result);')
        }
    }

    def 'the codegen renders one call to the requested member, feeding each port by name in declared order'() {
        def nameType = declared('java.lang', 'String')
        def ageType = declared('java.lang', 'Integer')
        bean([noArgCtor(), setter('setName', nameType), setter('setAge', ageType)])
        typeElement.simpleName >> nameOf('Person')
        ctx.option('percolate.construction.preference') >> Optional.empty()

        when:
        def specs = setterAssembly.expand(Demands.assembling(targetType, ['name', 'age'] as Set), ctx)*.spec

        then:
        specs.size() == 1

        expect:
        specs[0].codegen.render(inputs(
                [name: CodeBlock.of('$N', 'n'), age: CodeBlock.of('$N', 'a')],
                ['setter:com.example.Person:name,age': CodeBlock.of('$N', 'assemblePerson')])).toString() ==
                'assemblePerson(n, a)'
    }

    def 'the strategy prices itself from its own rank in the construction preference'() {
        bean([noArgCtor(), setter('setName', declared('java.lang', 'String'))])
        typeElement.simpleName >> nameOf('Person')
        ctx.option('percolate.construction.preference') >> raw

        when:
        def specs = setterAssembly.expand(Demands.assembling(targetType, ['name'] as Set), ctx)*.spec

        then:
        specs.size() == 1

        expect:
        specs[0].weight == weight

        where:
        raw                          | weight
        Optional.empty()             | Weights.EXPENSIVE + 1
        Optional.of('setter')        | Weights.STEP
        Optional.of('builder')       | Weights.EXPENSIVE + 1
        Optional.of('builder,setter') | Weights.EXPENSIVE
    }

    // ---- helpers ----------------------------------------------------------------------------------------------

    private void bean(final List<Element> members) {
        ctx.asTypeElement(targetType) >> Optional.of(typeElement)
        typeElement.kind >> ElementKind.CLASS
        typeElement.modifiers >> ([] as Set)
        ctx.membersOf(typeElement) >> { Stream.of(*members) }
    }

    private ExecutableElement noArgCtor() {
        ExecutableElement ctor = Mock()
        ctor.parameters >> []
        ctx.isConstructor(ctor) >> true
        ctx.isPrivate(ctor) >> false
        ctor
    }

    // A mocked TypeMirror that TypeName.get can render: JavaPoet resolves it through the type visitor.
    private TypeMirror declared(final String packageName, final String simpleName) {
        TypeMirror mirror = Mock()
        mirror.accept({ it instanceof TypeVisitor }, null) >> ClassName.get(packageName, simpleName)
        mirror.toString() >> packageName + '.' + simpleName
        mirror
    }

    private ExecutableElement setter(final String name, final TypeMirror parameterType) {
        VariableElement parameter = Mock()
        parameter.asType() >> parameterType
        ExecutableElement method = Mock()
        method.simpleName >> nameOf(name)
        method.parameters >> [parameter]
        ctx.isMethod(method) >> true
        ctx.isPrivate(method) >> false
        method
    }

    // The member lookup is keyed, so a codegen asking for the wrong dedup key renders a null reference.
    private IncomingValues inputs(final Map<String, CodeBlock> values, final Map<String, CodeBlock> members) {
        [byName: { String slot -> values[slot] }, member: { String key -> members[key] }] as IncomingValues
    }

    private Name nameOf(final String value) {
        [contentEquals: { CharSequence cs -> cs.toString() == value }, toString: { value }] as Name
    }
}
