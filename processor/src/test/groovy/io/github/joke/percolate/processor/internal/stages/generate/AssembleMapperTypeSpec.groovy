package io.github.joke.percolate.processor.internal.stages.generate

import com.palantir.javapoet.CodeBlock
import io.github.joke.percolate.processor.MapperContext
import io.github.joke.percolate.processor.ProcessorOptions
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
 * package — public (final iff {@code percolate.classes.final}), {@code @Generated}, an empty constructor, and one
 * {@code @Override} per method body (final iff {@code percolate.methods.final}, its parameters final iff
 * {@code percolate.parameters.final}) — then writes it via the {@link Filer}. The written source is captured through
 * a stub Filer/Writer. Real mapper types come from {@link PrivateTypeUniverse}, exercising the interface
 * ({@code implements}) vs class ({@code extends}) branch.
 */
@Tag('unit')
class AssembleMapperTypeSpec extends Specification {

    @Shared PrivateTypeUniverse javac = new PrivateTypeUniverse()

    def writer = new StringWriter()
    def filer = Mock(Filer) {
        createSourceFile(_, _) >> Mock(JavaFileObject) { openWriter() >> writer }
    }
    def stage = stageWith()

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
        stage.assemble(ctx, new MethodBodies(bodies, []))

        then:
        writer.toString().contains('void sink(')
    }

    def 'assembles a non-final public <Name>Impl implementing a @Mapper interface by default, with a generated annotation'() {
        given:
        def ctx = new MapperContext(javac.of(PersonMapper))
        def bodies = [new MethodImpl(method(PersonMapper, 'map'), CodeBlock.of('return null;\n'), [] as Set)]

        when:
        stage.assemble(ctx, new MethodBodies(bodies, []))
        def source = writer.toString()

        then: 'the impl lives in the mapper package, is public (not final), generated, and implements the interface'
        source.contains('package io.github.joke.percolate.processor.test.fixtures;')
        source.contains('public class PersonMapperImpl implements PersonMapper')
        source.contains('@Generated')
        source.contains('public PersonMapperImpl()')

        and: 'the one method body is emitted as a non-final @Override with a non-final parameter'
        source.contains('@Override')
        source.contains('public Human map(Person arg0)')
        source.contains('return null;')

        and: 'a mapper whose strategies requested no members declares no fields'
        !source.contains('private static final')
    }

    def 'percolate.classes.final renders a final class'() {
        given:
        def ctx = new MapperContext(javac.of(PersonMapper))
        def bodies = [new MethodImpl(method(PersonMapper, 'map'), CodeBlock.of('return null;\n'), [] as Set)]

        when:
        stageWith(true, false, false).assemble(ctx, new MethodBodies(bodies, []))

        then:
        writer.toString().contains('public final class PersonMapperImpl implements PersonMapper')
    }

    def 'percolate.methods.final renders a final method'() {
        given:
        def ctx = new MapperContext(javac.of(PersonMapper))
        def bodies = [new MethodImpl(method(PersonMapper, 'map'), CodeBlock.of('return null;\n'), [] as Set)]

        when:
        stageWith(false, true, false).assemble(ctx, new MethodBodies(bodies, []))

        then:
        writer.toString().contains('public final Human map(Person arg0)')
    }

    def 'percolate.parameters.final renders a final parameter'() {
        given:
        def ctx = new MapperContext(javac.of(PersonMapper))
        def bodies = [new MethodImpl(method(PersonMapper, 'map'), CodeBlock.of('return null;\n'), [] as Set)]

        when:
        stageWith(false, false, true).assemble(ctx, new MethodBodies(bodies, []))

        then:
        writer.toString().contains('public Human map(final Person arg0)')
    }

    def 'the three finality switches compose independently'() {
        given:
        def ctx = new MapperContext(javac.of(PersonMapper))
        def bodies = [new MethodImpl(method(PersonMapper, 'map'), CodeBlock.of('return null;\n'), [] as Set)]

        when:
        stageWith(true, true, false).assemble(ctx, new MethodBodies(bodies, []))
        def source = writer.toString()

        then:
        source.contains('public final class PersonMapperImpl implements PersonMapper')
        source.contains('public final Human map(Person arg0)')
    }

    def 'extends (not implements) when the mapper type is a class'() {
        given:
        def ctx = new MapperContext(javac.of(CallableFixtures))
        def bodies = [new MethodImpl(method(CallableFixtures, 'makeHuman'), CodeBlock.of('return null;\n'), [] as Set)]

        when:
        stage.assemble(ctx, new MethodBodies(bodies, []))

        then:
        writer.toString().contains('class CallableFixturesImpl extends CallableFixtures')
    }

    def 'strategy-requested members are emitted as private static final fields'() {
        given:
        def ctx = new MapperContext(javac.of(PersonMapper))
        def bodies = [new MethodImpl(method(PersonMapper, 'map'), CodeBlock.of('return null;\n'), [] as Set)]
        def field = com.palantir.javapoet.FieldSpec.builder(
                com.palantir.javapoet.ClassName.get('java.time.format', 'DateTimeFormatter'),
                'DATE_TIME_FORMATTER',
                javax.lang.model.element.Modifier.PRIVATE, javax.lang.model.element.Modifier.STATIC,
                javax.lang.model.element.Modifier.FINAL)
                .initializer('$T.ofPattern($S)', com.palantir.javapoet.ClassName.get('java.time.format', 'DateTimeFormatter'), 'yyyy-MM-dd')
                .build()

        when:
        stage.assemble(ctx, new MethodBodies(bodies, [field]))
        def source = writer.toString()

        then:
        source.contains('private static final DateTimeFormatter DATE_TIME_FORMATTER')
        source.contains('DateTimeFormatter.ofPattern("yyyy-MM-dd")')
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

    private AssembleMapperType stageWith(classesFinal = false, methodsFinal = false, parametersFinal = false) {
        new AssembleMapperType(filer, javac.elements(), ProcessorOptions.builder()
                .debugGraphs(false)
                .customNullableAnnotations([] as Set)
                .localsFinal(false)
                .localsVar(false)
                .parametersFinal(parametersFinal)
                .methodsFinal(methodsFinal)
                .classesFinal(classesFinal)
                .docTags(false)
                .timeZone(Optional.empty())
                .build())
    }
}
