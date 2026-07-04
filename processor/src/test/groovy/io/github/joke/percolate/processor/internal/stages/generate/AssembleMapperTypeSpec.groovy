package io.github.joke.percolate.processor.internal.stages.generate

import com.palantir.javapoet.CodeBlock
import io.github.joke.percolate.processor.MapperContext
import io.github.joke.percolate.processor.test.fixtures.CallableFixtures
import io.github.joke.percolate.processor.test.fixtures.DirectiveFixtures
import io.github.joke.percolate.processor.test.fixtures.Human
import io.github.joke.percolate.processor.test.fixtures.Person
import io.github.joke.percolate.processor.test.fixtures.PersonMapper
import io.github.joke.percolate.spi.test.PrivateTypeUniverse
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Tag

import javax.annotation.processing.Filer
import javax.lang.model.element.ElementKind
import javax.lang.model.element.ExecutableElement
import javax.tools.JavaFileObject

/**
 * {@link AssembleMapperType} seam, unit-tested directly: it assembles a {@code <Name>Impl} type in the mapper's
 * package — public final, {@code @Generated}, an empty constructor, and one {@code @Override} per method body — then
 * writes it via the {@link Filer}. The written source is captured through a stub Filer/Writer. Real mapper types come
 * from {@link PrivateTypeUniverse}, exercising the interface ({@code implements}) vs class ({@code extends}) branch.
 */
@Tag('unit')
class AssembleMapperTypeSpec extends Specification {

    @Shared PrivateTypeUniverse javac = new PrivateTypeUniverse()

    def writer = new StringWriter()
    def filer = Mock(Filer) {
        createSourceFile(_, _) >> Mock(JavaFileObject) { openWriter() >> writer }
    }
    def stage = new AssembleMapperType(filer, javac.elements())

    def setupSpec() {
        javac.of(Person)
        javac.of(Human)
        javac.of(PersonMapper)
        javac.of(CallableFixtures)
        javac.of(DirectiveFixtures)
    }

    def 'a void mapper method renders a void return type'() {
        def ctx = new MapperContext(javac.of(DirectiveFixtures))
        def bodies = [new MethodImpl(method(DirectiveFixtures, 'sink'), CodeBlock.of(''), [] as Set)]

        when:
        stage.assemble(ctx, bodies)

        then:
        writer.toString().contains('void sink(')
    }

    def 'assembles a public final <Name>Impl implementing a @Mapper interface, with a generated annotation'() {
        given:
        def ctx = new MapperContext(javac.of(PersonMapper))
        def bodies = [new MethodImpl(method(PersonMapper, 'map'), CodeBlock.of('return null;\n'), [] as Set)]

        when:
        stage.assemble(ctx, bodies)
        def source = writer.toString()

        then: 'the impl lives in the mapper package, is public final, generated, and implements the interface'
        source.contains('package io.github.joke.percolate.processor.test.fixtures;')
        source.contains('public final class PersonMapperImpl implements PersonMapper')
        source.contains('@Generated')
        source.contains('public PersonMapperImpl()')

        and: 'the one method body is emitted as an @Override'
        source.contains('@Override')
        source.contains('map(')
        source.contains('return null;')
    }

    def 'extends (not implements) when the mapper type is a class'() {
        given:
        def ctx = new MapperContext(javac.of(CallableFixtures))
        def bodies = [new MethodImpl(method(CallableFixtures, 'makeHuman'), CodeBlock.of('return null;\n'), [] as Set)]

        when:
        stage.assemble(ctx, bodies)

        then:
        writer.toString().contains('class CallableFixturesImpl extends CallableFixtures')
    }

    def 'the mapper interface is detected by element kind'() {
        expect:
        javac.of(PersonMapper).kind == ElementKind.INTERFACE
        javac.of(CallableFixtures).kind == ElementKind.CLASS
    }

    private ExecutableElement method(final Class<?> type, final String name) {
        javac.of(type).enclosedElements.find {
            it.kind == ElementKind.METHOD && it.simpleName.toString() == name
        } as ExecutableElement
    }
}
