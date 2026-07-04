package io.github.joke.percolate.spi.builtins

import com.palantir.javapoet.CodeBlock
import io.github.joke.percolate.spi.Containers
import io.github.joke.percolate.spi.Nullability
import io.github.joke.percolate.spi.OperationCodegen
import io.github.joke.percolate.spi.ResolveCtx
import io.github.joke.percolate.spi.Weights
import io.github.joke.percolate.spi.builtins.test.Demands
import io.github.joke.percolate.spi.builtins.test.ResolveCtxBuilder
import io.github.joke.percolate.spi.test.PrivateTypeUniverse
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.type.TypeMirror

@Tag('unit')
class ListContainerSpec extends Specification {

    @Shared PrivateTypeUniverse javac = new PrivateTypeUniverse()
    @Shared ResolveCtx ctx = new ResolveCtxBuilder(javac).build()
    @Shared TypeMirror listOfString
    @Shared TypeMirror setOfString
    @Shared TypeMirror streamOfString

    def setupSpec() {
        ['java.lang.Iterable', 'java.util.Collection', 'java.util.SequencedCollection',
         'java.util.List', 'java.util.Set'].each { javac.elements().getTypeElement(it) }
        listOfString = javac.LIST_OF_STRING
        setOfString = javac.types().getDeclaredType(
                javac.elements().getTypeElement('java.util.Set'), javac.STRING)
        streamOfString = javac.types().getDeclaredType(
                javac.elements().getTypeElement('java.util.stream.Stream'), javac.STRING)
        Containers.isList(listOfString, ctx)
    }

    def 'iterates a List into a Stream via .stream(), a plain operation with no child scope'() {
        when:
        def specs = new ListContainer().expand(Demands.forTarget(streamOfString), ctx).toList()

        then:
        specs.size() == 1
        def iterate = specs[0]
        iterate.childScope.empty
        iterate.codegen instanceof OperationCodegen
        iterate.weight == Weights.CONTAINER
        ctx.types().isSameType(iterate.ports[0].type, listOfString)
        ctx.types().isSameType(iterate.outputType, streamOfString)
        new ListContainer().iterate().get().render(CodeBlock.of('$N', 'xs')).toString().contains('.stream()')
    }

    def 'collects a Stream into a List and offers a plain single-element List.of wrap'() {
        when:
        def specs = new ListContainer().expand(Demands.forTarget(listOfString), ctx).toList()

        then: 'a plain collect Stream<String> -> List<String>'
        def collect = specs.find { ctx.types().isSameType(it.ports[0].type, streamOfString) }
        collect != null
        collect.childScope.empty
        collect.codegen instanceof OperationCodegen
        ctx.types().isSameType(collect.outputType, listOfString)
        new ListContainer().collect().get().render(CodeBlock.of('$N', 's')).toString().contains('toList()')

        and: 'a plain single-element wrap String -> List<String>, no child scope'
        def wrap = specs.find { ctx.types().isSameType(it.ports[0].type, javac.STRING) }
        wrap != null
        wrap.childScope.empty
        wrap.codegen instanceof OperationCodegen
        ctx.types().isSameType(wrap.outputType, listOfString)
        wrap.outputNullness == Nullability.NON_NULL

        and: 'no operation carries a child scope (no fused element mapping)'
        specs.every { it.childScope.empty }
    }

    def 'declines a target that is neither a List nor a Stream'() {
        expect:
        new ListContainer().expand(Demands.forTarget(setOfString), ctx).toList().empty
    }
}
