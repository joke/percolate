package io.github.joke.percolate.reactor

import com.google.testing.compile.Compilation
import com.google.testing.compile.Compiler
import com.google.testing.compile.JavaFileObjects
import io.github.joke.percolate.processor.PercolateProcessor
import spock.lang.Specification
import spock.lang.Tag

import javax.tools.JavaFileObject

/**
 * End-to-end guard for the reactor container family (change {@code add-reactor-modules}, tasks 3.x). Mirrors the proven
 * Beast/Creature bean-field container pattern: a target bean field of reactive type, sourced via {@code @Map} from a
 * source bean field, with the element transform delegated to a declared {@code @Map}-annotated method. Proves the spike
 * finding in real generated code with no engine change.
 */
@Tag('integration')
class ReactorContainerEndToEndSpec extends Specification {

    def 'Flux<DTO> field to Flux<DAO> field composes flux.map over the element transform'() {
        when:
        def body = body(compileFieldMapper('Flux<PersonDTO>', 'Flux<PersonDAO>'))

        then: 'maps the Flux through the element transform — no Stream, no blocking'
        body.contains('.map(')
        body.contains('mapOne(')
        !body.contains('.stream()')
        !body.contains('.block(')
    }

    def 'Mono<DTO> field to Mono<DAO> field composes mono.map (mapPresence)'() {
        when:
        def body = body(compileFieldMapper('Mono<PersonDTO>', 'Mono<PersonDAO>'))

        then:
        body.contains('.map(')
        body.contains('mapOne(')
        !body.contains('.block(')
    }

    def 'Mono<DTO> field to Flux<DAO> field crosses to the shared Flux intermediate via mono.flux()'() {
        when:
        def body = body(compileFieldMapper('Mono<PersonDTO>', 'Flux<PersonDAO>'))

        then: 'the Mono is opened to a Flux, then mapped — non-blocking'
        body.contains('.flux()')
        body.contains('.map(')
        !body.contains('.block(')
    }

    def 'Optional<DTO> field to Mono<DAO> field bridges via Mono.justOrEmpty'() {
        when:
        def body = body(compileFieldMapper('Optional<PersonDTO>', 'Mono<PersonDAO>'))

        then:
        body.contains('justOrEmpty')
        !body.contains('.block(')
    }

    def 'List<DTO> field to Flux<DAO> field bridges via Flux.fromStream over the element stream'() {
        when:
        def body = body(compileFieldMapper('List<PersonDTO>', 'Flux<PersonDAO>'))

        then:
        body.contains('fromStream')
        !body.contains('.block(')
    }

    def 'Flux<DTO> field to Mono<List<DAO>> field reduces via collectList (stays reactive)'() {
        when:
        def body = body(compileFieldMapper('Flux<PersonDTO>', 'Mono<List<PersonDAO>>'))

        then:
        body.contains('collectList()')
        !body.contains('.block(')
    }

    def 'Flux<DTO> field to Mono<DAO> field reduces via single() and never next/last'() {
        when:
        def body = body(compileFieldMapper('Flux<PersonDTO>', 'Mono<PersonDAO>'))

        then:
        body.contains('.single()')
        !body.contains('.next(')
        !body.contains('.last(')
        !body.contains('.block(')
    }

    def 'Mono<DTO> field to Mono<Optional<DAO>> field uses singleOptional'() {
        when:
        def body = body(compileFieldMapper('Mono<PersonDTO>', 'Mono<Optional<PersonDAO>>'))

        then:
        body.contains('singleOptional()')
        !body.contains('.block(')
    }

    def 'direct Flux<DTO> -> Flux<DAO> container-return method maps element-wise without self-bridging'() {
        when: 'a top-level container-return method (no bean wrapper), enabled by the engine self-bridge + root-id fix'
        def mapper = JavaFileObjects.forSourceLines(
                'io.github.joke.percolate.reactor.test.PersonMapper',
                'package io.github.joke.percolate.reactor.test;',
                'import io.github.joke.percolate.Mapper;',
                'import io.github.joke.percolate.Map;',
                'import reactor.core.publisher.Flux;',
                '@Mapper',
                'public interface PersonMapper {',
                '    Flux<PersonDAO> map(Flux<PersonDTO> people);',
                '    @Map(target = "name", source = "dto.name")',
                '    PersonDAO mapOne(PersonDTO dto);',
                '}')
        def body = body(Compiler.javac().withProcessors(new PercolateProcessor()).compile(DTO, DAO, mapper))

        then: 'maps the Flux through the element transform — never return this.map(people)'
        body.contains('.map(')
        body.contains('mapOne(')
        !body.contains('this.map(')
        !body.contains('.block(')
    }

    // ---- harness -------------------------------------------------------------------------------------------

    private static final DTO = JavaFileObjects.forSourceLines(
            'io.github.joke.percolate.reactor.test.PersonDTO',
            'package io.github.joke.percolate.reactor.test;',
            'public final class PersonDTO {',
            '    private final String name;',
            '    public PersonDTO(final String name) { this.name = name; }',
            '    public String getName() { return name; }',
            '}')

    private static final DAO = JavaFileObjects.forSourceLines(
            'io.github.joke.percolate.reactor.test.PersonDAO',
            'package io.github.joke.percolate.reactor.test;',
            'public final class PersonDAO {',
            '    private final String name;',
            '    public PersonDAO(final String name) { this.name = name; }',
            '    public String getName() { return name; }',
            '}')

    private static JavaFileObject bean(final String name, final String type) {
        JavaFileObjects.forSourceLines(
                'io.github.joke.percolate.reactor.test.' + name,
                'package io.github.joke.percolate.reactor.test;',
                'import java.util.List;',
                'import java.util.Optional;',
                'import reactor.core.publisher.Flux;',
                'import reactor.core.publisher.Mono;',
                'public final class ' + name + ' {',
                "    private final ${type} people;",
                "    public ${name}(final ${type} people) { this.people = people; }",
                "    public ${type} getPeople() { return people; }",
                '}')
    }

    /** A bean-field mapper: a {@code Tgt.people} of {@code tgtType} sourced from {@code Src.people} of {@code srcType}. */
    private static Compilation compileFieldMapper(final String srcType, final String tgtType) {
        def mapper = JavaFileObjects.forSourceLines(
                'io.github.joke.percolate.reactor.test.PersonMapper',
                'package io.github.joke.percolate.reactor.test;',
                'import io.github.joke.percolate.Mapper;',
                'import io.github.joke.percolate.Map;',
                '@Mapper',
                'public interface PersonMapper {',
                '    @Map(target = "people", source = "src.people")',
                '    Tgt map(Src src);',
                '    @Map(target = "name", source = "dto.name")',
                '    PersonDAO mapOne(PersonDTO dto);',
                '}')
        Compiler.javac()
                .withProcessors(new PercolateProcessor())
                .compile(DTO, DAO, bean('Src', srcType), bean('Tgt', tgtType), mapper)
    }

    private static String body(final Compilation compilation) {
        assert compilation.errors().empty
        def file = compilation.generatedSourceFile('io.github.joke.percolate.reactor.test.PersonMapperImpl')
        assert file.present
        file.get().getCharContent(true).toString()
    }
}
