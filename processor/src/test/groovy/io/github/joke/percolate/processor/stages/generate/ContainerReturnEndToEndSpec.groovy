package io.github.joke.percolate.processor.stages.generate

import com.google.testing.compile.Compilation
import com.google.testing.compile.Compiler
import com.google.testing.compile.JavaFileObjects
import io.github.joke.percolate.processor.PercolateProcessor
import spock.lang.Specification
import spock.lang.Tag

import javax.tools.JavaFileObject

/**
 * Direct container-return mappers (a top-level method whose return type is itself a container) — the case the
 * reactor change recorded as a pre-existing limitation. Generation requires fixing two independent engine bugs:
 * the self-bridge (a method must not satisfy its own return root) and the type-blind return-root (over-emitted
 * typed siblings at the return location must not be treated as roots). Both are exercised here.
 */
@Tag('integration')
class ContainerReturnEndToEndSpec extends Specification {

    private static final String PKG = 'io.github.joke.percolate.processor.test.fixtures'

    def 'a direct container-return mapper delegates per element and never self-bridges'() {
        given:
        def dto = bean('AddrDto', 'getStreet')
        def dao = beanWithCtor('AddrDao', 'street')
        def mapper = JavaFileObjects.forSourceLines(
                "${PKG}.AddrMapper",
                "package ${PKG};",
                '',
                'import io.github.joke.percolate.Mapper;',
                'import io.github.joke.percolate.Map;',
                'import java.util.List;',
                'import java.util.Set;',
                '',
                '@Mapper',
                'public interface AddrMapper {',
                '    @Map(target = "street", source = "dto.street")',
                '    AddrDao mapOne(AddrDto dto);',
                '',
                '    List<AddrDao> mapMany(Set<AddrDto> dtos);',
                '}')

        when:
        Compilation compilation = Compiler.javac()
                .withProcessors(new PercolateProcessor())
                .compile(dto, dao, mapper)

        then: 'no spurious "no plan" — the dead typed siblings at the return location are not diagnosed'
        compilation.errors().empty
        compilation.diagnostics().every { !it.message.contains('no plan') }

        and: 'the impl is generated'
        def generated = compilation.generatedSourceFile("${PKG}.AddrMapperImpl")
        generated.present
        def content = generated.get().getCharContent(true).toString()

        and: 'the container method delegates per element to the sibling method, collecting to the target container'
        content.contains('public List<AddrDao> mapMany(Set<AddrDto> dtos)')
        content.contains('.stream()')
        content.contains('mapOne(')
        content.contains('.collect(')

        and: 'it never self-bridges (no return this.mapMany(...))'
        !content.contains('this.mapMany(')
        !content.contains('return mapMany(')
    }

    def 'legitimate self-recursion through a container element is preserved'() {
        given: 'a self-similar type whose children list maps element-wise via the same method'
        def dto = JavaFileObjects.forSourceLines(
                "${PKG}.CatDto",
                "package ${PKG};",
                'import java.util.List;',
                'public final class CatDto {',
                '    private final String name;',
                '    private final List<CatDto> children;',
                '    public CatDto(final String name, final List<CatDto> children) { this.name = name; this.children = children; }',
                '    public String getName() { return name; }',
                '    public List<CatDto> getChildren() { return children; }',
                '}')
        def dao = JavaFileObjects.forSourceLines(
                "${PKG}.CatDao",
                "package ${PKG};",
                'import java.util.List;',
                'public final class CatDao {',
                '    private final String name;',
                '    private final List<CatDao> children;',
                '    public CatDao(final String name, final List<CatDao> children) { this.name = name; this.children = children; }',
                '    public String getName() { return name; }',
                '    public List<CatDao> getChildren() { return children; }',
                '}')
        def mapper = JavaFileObjects.forSourceLines(
                "${PKG}.CatMapper",
                "package ${PKG};",
                '',
                'import io.github.joke.percolate.Mapper;',
                'import io.github.joke.percolate.Map;',
                '',
                '@Mapper',
                'public interface CatMapper {',
                '    @Map(target = "name", source = "src.name")',
                '    @Map(target = "children", source = "src.children")',
                '    CatDao mapCat(CatDto src);',
                '}')

        when:
        Compilation compilation = Compiler.javac()
                .withProcessors(new PercolateProcessor())
                .compile(dto, dao, mapper)

        then: 'it compiles and the children element transform recurses into the same method'
        compilation.errors().empty
        def content = compilation.generatedSourceFile("${PKG}.CatMapperImpl").get().getCharContent(true).toString()

        and: 'the root is assembled (not a self-call), and the element transform delegates back to mapCat'
        content.contains('new CatDao(')
        content.findAll(/mapCat\(/).size() >= 2
    }

    private static JavaFileObject bean(final String name, final String getter) {
        final var field = getter.substring(3).toLowerCase()
        JavaFileObjects.forSourceLines(
                "${PKG}.${name}",
                "package ${PKG};",
                '',
                "public final class ${name} {",
                "    private final String ${field};",
                "    public ${name}(final String ${field}) { this.${field} = ${field}; }",
                "    public String ${getter}() { return ${field}; }",
                '}')
    }

    private static JavaFileObject beanWithCtor(final String name, final String field) {
        JavaFileObjects.forSourceLines(
                "${PKG}.${name}",
                "package ${PKG};",
                '',
                "public final class ${name} {",
                "    private final String ${field};",
                "    public ${name}(final String ${field}) { this.${field} = ${field}; }",
                "    public String get${field.capitalize()}() { return ${field}; }",
                '}')
    }
}
