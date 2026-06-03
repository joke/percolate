package io.github.joke.percolate.processor.stages.generate

import com.google.testing.compile.Compilation
import com.google.testing.compile.Compiler
import com.google.testing.compile.JavaFileObjects
import io.github.joke.percolate.processor.PercolateProcessor
import spock.lang.Specification
import spock.lang.Tag

@Tag('integration')
class TypeConversionEndToEndSpec extends Specification {

    static final String PKG = 'io.github.joke.percolate.processor.test.fixtures'
    static final String IMPL = PKG + '.CvMapperImpl'

    def 'lossless conversion resolves, compiles, and uses the right coercion (#srcType -> #tgtType)'() {
        when:
        def compilation = compile(srcType, tgtType)

        then:
        compilation.errors().empty
        def generated = compilation.generatedSourceFile(IMPL)
        generated.present
        generated.get().getCharContent(true).toString().contains(token)

        where:
        srcType   | tgtType   | token
        'int'     | 'Integer' | 'Integer.valueOf'
        'Integer' | 'int'     | '.intValue()'
        'int'     | 'long'    | '(long)'
        'int'     | 'Long'    | 'Long.valueOf'
        'Integer' | 'long'    | '.intValue()'
        'Integer' | 'Long'    | 'Long.valueOf'
    }

    def 'cross-product int -> Long uses the minimal widen-then-box chain with no spurious hops'() {
        when:
        def content = compile('int', 'Long').generatedSourceFile(IMPL).get().getCharContent(true).toString()

        then: 'box and widen present'
        content.contains('Long.valueOf')
        content.contains('(long)')

        and: 'no spurious unbox or re-box detour (cheapest path selected; dead ends not generated)'
        !content.contains('intValue')
        !content.contains('Integer.valueOf')
    }

    def 'narrowing conversion does not resolve (#srcType -> #tgtType)'() {
        when:
        def compilation = compile(srcType, tgtType)

        then: 'no plan is found; compilation fails with an unresolved-target diagnostic (no mapper emitted)'
        !compilation.errors().empty
        compilation.errors().any { it.getMessage(null).contains('tgt[age]') }

        where:
        srcType   | tgtType
        'long'    | 'int'
        'double'  | 'int'
        'Integer' | 'Byte'
    }

    private static Compilation compile(final String srcType, final String tgtType) {
        def source = JavaFileObjects.forSourceLines(
                PKG + '.CvSource',
                "package ${PKG};",
                'public final class CvSource {',
                "    private final ${srcType} age;",
                "    public CvSource(final ${srcType} age) { this.age = age; }",
                "    public ${srcType} getAge() { return age; }",
                '}')

        def target = JavaFileObjects.forSourceLines(
                PKG + '.CvTarget',
                "package ${PKG};",
                'public final class CvTarget {',
                "    private final ${tgtType} age;",
                "    public CvTarget(final ${tgtType} age) { this.age = age; }",
                "    public ${tgtType} getAge() { return age; }",
                '}')

        def mapper = JavaFileObjects.forSourceLines(
                PKG + '.CvMapper',
                "package ${PKG};",
                'import io.github.joke.percolate.Mapper;',
                'import io.github.joke.percolate.Map;',
                '@Mapper',
                'public interface CvMapper {',
                '    @Map(target = "age", source = "source.age")',
                '    CvTarget map(CvSource source);',
                '}')

        Compiler.javac().withProcessors(new PercolateProcessor()).compile(source, target, mapper)
    }
}
