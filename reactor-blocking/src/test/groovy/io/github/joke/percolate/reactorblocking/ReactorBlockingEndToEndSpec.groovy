package io.github.joke.percolate.reactorblocking

import com.google.testing.compile.Compilation
import com.google.testing.compile.Compiler
import com.google.testing.compile.JavaFileObjects
import io.github.joke.percolate.processor.PercolateProcessor
import spock.lang.Specification
import spock.lang.Tag

import javax.tools.JavaFileObject

/**
 * End-to-end guard for the opt-in {@code reactor-blocking} module (change {@code fix-container-return-mappers}). With
 * both {@code reactor} and {@code reactor-blocking} on the annotation-processor classpath, the engine may cross the
 * paradigm boundary upward (async to sync). Each upward edge is reuse-only (the {@code unwrap} pattern) and weighted
 * strictly above any non-blocking alternative, so a blocking bridge composes as <b>block-then-map</b> (the reuse-only
 * port binds the in-scope reactive source field) and never out-prices a lazy reactive path that exists.
 */
@Tag('integration')
class ReactorBlockingEndToEndSpec extends Specification {

    def 'Mono<DTO> source to scalar DAO field blocks via .block() then maps the element'() {
        when:
        def body = body(compileFieldMapper('Mono<PersonDTO>', 'PersonDAO'))

        then:
        body.contains('.block()')
        body.contains('mapOne(')
        !body.contains('.single()')
    }

    def 'Mono<T> source to Optional<T> field bridges via .blockOptional() (total beats partial block)'() {
        when:
        def body = body(compileFieldMapper('Mono<PersonDTO>', 'Optional<PersonDTO>'))

        then: 'the empty-safe total bridge wins over block() + Optional.ofNullable (partial)'
        body.contains('blockOptional()')
        !body.contains('ofNullable')
    }

    def 'Flux<DTO> source to scalar DAO field reduces via single().block()'() {
        when:
        def body = body(compileFieldMapper('Flux<PersonDTO>', 'PersonDAO'))

        then:
        body.contains('.single().block()')
        body.contains('mapOne(')
    }

    def 'Flux<T> source to List<T> field buffers via collectList().block() (total beats partial single)'() {
        when:
        def body = body(compileFieldMapper('Flux<PersonDTO>', 'List<PersonDTO>'))

        then: 'the element-preserving total bridge wins over single().block() + List.of (partial)'
        body.contains('collectList().block()')
        !body.contains('single()')
    }

    def 'Flux<T> source to Stream<T> field streams via toStream() (total beats partial single)'() {
        when:
        def body = body(compileFieldMapper('Flux<PersonDTO>', 'Stream<PersonDTO>'))

        then: 'the lazily-streaming total bridge wins over single().block() + Stream.of (partial)'
        body.contains('toStream()')
        !body.contains('single()')
    }

    def 'Flux<DTO> param to List<DAO> transforms elements via the Flux->Stream grounding view (param-direct)'() {
        when: 'the param itself is the reactive source, isolating the projection fix from bean-field materialisation order'
        def body = paramBody(compileParamMapper('Flux<PersonDTO>', 'List<PersonDAO>'))

        then: 'the element type grounds against the Flux source, so elements map to DAO through the JDK container'
        body.contains('mapOne(')
        body.contains('.collect(')
        body.contains('toStream()') || body.contains('collectList().block()')
    }

    def 'Flux<DTO> field to List<DAO> field transforms elements via the Flux->Stream grounding view (bean-field)'() {
        when:
        def body = body(compileFieldMapper('Flux<PersonDTO>', 'List<PersonDAO>'))

        then:
        body.contains('mapOne(')
        body.contains('.collect(')
        body.contains('toStream()') || body.contains('collectList().block()')
    }

    def 'Mono<DTO> field to Optional<DAO> field transforms the element via the total Mono->Optional grounding view'() {
        when:
        def body = body(compileFieldMapper('Mono<PersonDTO>', 'Optional<PersonDAO>'))

        then: 'the presence-preserving total view grounds the element type, so the element maps to DAO'
        body.contains('blockOptional()')
        body.contains('mapOne(')
    }

    def 'no eager block: a lazy reactive path always out-prices the blocking one'() {
        when: 'Mono to Mono, where both lazy mono.map and eager Mono.just(map(block())) are possible'
        def body = body(compileFieldMapper('Mono<PersonDTO>', 'Mono<PersonDAO>'))

        then: 'the lazy, non-blocking path wins on cost — blocking is never invented when a reactive path exists'
        body.contains('.map(')
        body.contains('mapOne(')
        !body.contains('.block(')
    }

    // ---- harness -------------------------------------------------------------------------------------------

    private static final DTO = JavaFileObjects.forSourceLines(
            'io.github.joke.percolate.reactorblocking.test.PersonDTO',
            'package io.github.joke.percolate.reactorblocking.test;',
            'public final class PersonDTO {',
            '    private final String name;',
            '    public PersonDTO(final String name) { this.name = name; }',
            '    public String getName() { return name; }',
            '}')

    private static final DAO = JavaFileObjects.forSourceLines(
            'io.github.joke.percolate.reactorblocking.test.PersonDAO',
            'package io.github.joke.percolate.reactorblocking.test;',
            'public final class PersonDAO {',
            '    private final String name;',
            '    public PersonDAO(final String name) { this.name = name; }',
            '    public String getName() { return name; }',
            '}')

    private static JavaFileObject bean(final String name, final String type) {
        JavaFileObjects.forSourceLines(
                'io.github.joke.percolate.reactorblocking.test.' + name,
                'package io.github.joke.percolate.reactorblocking.test;',
                'import java.util.List;',
                'import java.util.Optional;',
                'import java.util.stream.Stream;',
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
                'io.github.joke.percolate.reactorblocking.test.PersonMapper',
                'package io.github.joke.percolate.reactorblocking.test;',
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
        def file = compilation.generatedSourceFile('io.github.joke.percolate.reactorblocking.test.PersonMapperImpl')
        assert file.present
        file.get().getCharContent(true).toString()
    }

    /** A param-direct mapper: the container method's parameter is itself the reactive source, delegating elements to mapOne. */
    private static Compilation compileParamMapper(final String srcType, final String tgtType) {
        def mapper = JavaFileObjects.forSourceLines(
                'io.github.joke.percolate.reactorblocking.test.DirectMapper',
                'package io.github.joke.percolate.reactorblocking.test;',
                'import io.github.joke.percolate.Mapper;',
                'import io.github.joke.percolate.Map;',
                'import java.util.List;',
                'import java.util.Optional;',
                'import java.util.stream.Stream;',
                'import reactor.core.publisher.Flux;',
                'import reactor.core.publisher.Mono;',
                '@Mapper',
                'public interface DirectMapper {',
                "    ${tgtType} map(${srcType} src);",
                '    @Map(target = "name", source = "dto.name")',
                '    PersonDAO mapOne(PersonDTO dto);',
                '}')
        Compiler.javac()
                .withProcessors(new PercolateProcessor())
                .compile(DTO, DAO, mapper)
    }

    private static String paramBody(final Compilation compilation) {
        assert compilation.errors().empty
        def file = compilation.generatedSourceFile('io.github.joke.percolate.reactorblocking.test.DirectMapperImpl')
        assert file.present
        file.get().getCharContent(true).toString()
    }
}
