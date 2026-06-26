package io.github.joke.percolate.reactor

import com.google.testing.compile.Compilation
import com.google.testing.compile.JavaFileObjects
import io.github.joke.percolate.test.PercolateCompiler
import spock.lang.Specification
import spock.lang.Tag

/**
 * The boundary-direction guard (change {@code add-reactor-modules}, tasks 5.x): with only the {@code reactor} module
 * present, an async-to-sync (upward) crossing is never satisfied with a blocking call. The engine either reports "no
 * plan" or (for a scalar root that the engine can self-bridge — a pre-existing, paradigm-agnostic percolate quirk)
 * produces non-blocking code; in no case does it invent a {@code .block()}/{@code .toStream()} — the D4 invariant
 * realised for reactor. Blocking edges live only in the opt-in {@code reactor-blocking} module.
 */
@Tag('integration')
class ReactorBoundaryNegativeSpec extends Specification {

    def 'Flux<DTO> to synchronous List<DAO> reports no plan, never collectList().block()'() {
        when:
        def compilation = compile('java.util.List<PersonDAO> map(Flux<PersonDTO> src);')

        then: 'reported, not silently satisfied with a blocking reduction'
        !compilation.errors().empty
        compilation.errors().any { it.getMessage(null).contains('no plan') }
    }

    def 'Mono<DTO> to scalar DAO never invents a .block()'() {
        when:
        def body = bodyOrEmpty(compile('PersonDAO map(Mono<PersonDTO> src);'))

        then: 'no blocking call is ever auto-generated'
        !body.contains('.block(')
        !body.contains('.toStream(')
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

    private static Compilation compile(final String method) {
        def mapper = JavaFileObjects.forSourceLines(
                'io.github.joke.percolate.reactor.test.PersonMapper',
                'package io.github.joke.percolate.reactor.test;',
                'import io.github.joke.percolate.Mapper;',
                'import reactor.core.publisher.Flux;',
                'import reactor.core.publisher.Mono;',
                '@Mapper',
                'public interface PersonMapper {',
                '    ' + method,
                '}')
        PercolateCompiler.compile(DTO, DAO, mapper)
    }

    private static String bodyOrEmpty(final Compilation compilation) {
        if (!compilation.errors().empty) {
            return ''
        }
        def file = compilation.generatedSourceFile('io.github.joke.percolate.reactor.test.PersonMapperImpl')
        file.present ? file.get().getCharContent(true).toString() : ''
    }
}
