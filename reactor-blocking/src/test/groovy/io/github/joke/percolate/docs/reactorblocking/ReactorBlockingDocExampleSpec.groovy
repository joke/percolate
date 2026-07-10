package io.github.joke.percolate.docs.reactorblocking

import com.google.testing.compile.Compilation
import com.google.testing.compile.JavaFileObjects
import io.github.joke.percolate.test.PercolateCompiler
import spock.lang.Specification
import spock.lang.Tag

/**
 * Backs the manual's Reactive to blocking bridge page. Compiled through the real {@code compile-testing}
 * harness (docTags on) rather than an ordinary {@code compileTestJava} run: this module has no source of its
 * own to hang a real {@code @Mapper} fixture on, so, like the compile-time-switches reference, the spec is the
 * sole materialiser of its generated output (see {@code openspec/changes/archive/2026-06-27-single-source-
 * manual-examples/design.md}, D2). One representative case is shown here; the full block/blockOptional/
 * collectList/toStream matrix is exhaustively covered by {@link ReactorBlockingEndToEndSpec}, an
 * engine-correctness regression spec this doc-e2e does not duplicate.
 */
@Tag('integration')
class ReactorBlockingDocExampleSpec extends Specification {

    private static final DTO = JavaFileObjects.forSourceLines(
            'examples.reactorblocking.PersonDTO',
            'package examples.reactorblocking;',
            'public final class PersonDTO {',
            '    private final String name;',
            '    public PersonDTO(final String name) { this.name = name; }',
            '    public String getName() { return name; }',
            '}')

    private static final DAO = JavaFileObjects.forSourceLines(
            'examples.reactorblocking.PersonDAO',
            'package examples.reactorblocking;',
            'public final class PersonDAO {',
            '    private final String name;',
            '    public PersonDAO(final String name) { this.name = name; }',
            '    public String getName() { return name; }',
            '}')

    private static final MAPPER = JavaFileObjects.forSourceLines(
            'examples.reactorblocking.PersonMapper',
            'package examples.reactorblocking;',
            'import io.github.joke.percolate.Map;',
            'import io.github.joke.percolate.Mapper;',
            'import reactor.core.publisher.Mono;',
            '@Mapper',
            'public interface PersonMapper {',
            '    PersonDAO map(Mono<PersonDTO> source);',
            '    @Map(target = "name", source = "dto.name")',
            '    PersonDAO toDao(PersonDTO dto);',
            '}')

    def 'a Mono<DTO> source reduces to a scalar DAO target via block-then-map'() {
        when:
        Compilation compilation = PercolateCompiler.compileWith(['-Apercolate.docTags=true'], DTO, DAO, MAPPER)

        then:
        compilation.errors().empty
        def generated = compilation.generatedSourceFile('examples.reactorblocking.PersonMapperImpl')
        generated.present
        def content = generated.get().getCharContent(true).toString()
        content.contains('.block()')

        and:
        materialise('PersonMapperImpl.java', content)
    }

    private static void materialise(final String relativePath, final String content) {
        def file = new File("build/generated-doc-examples/reactor-blocking/${relativePath}")
        file.parentFile.mkdirs()
        file.text = content
    }
}
