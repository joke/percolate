package io.github.joke.percolate.processor.internal.stages.generate

import com.google.testing.compile.Compilation
import com.google.testing.compile.JavaFileObjects
import io.github.joke.percolate.test.PercolateCompiler
import spock.lang.Specification
import spock.lang.Tag

import javax.tools.JavaFileObject

/**
 * {@link AssembleMapperType}'s render/{@code Filer}-write leaf, covered end-to-end by a real compile through the
 * {@link PercolateCompiler} (change dissolve-private-type-universe, design D3): the pure finality/extends-vs-implements
 * decisions moved to {@code MapperTypeDecisions} (unit-tested), leaving the {@code TypeName.get(mirror)} render leaf to
 * the compile-based feature-e2e layer. The interface ({@code implements}) branch, {@code @Generated}, package
 * placement, and the finality switches are already exercised by the {@code compile-time-switches} doc-e2e over an
 * interface {@code ProductMapper}; this fills the one gap — a <b>class</b>-based {@code @Mapper}, whose impl
 * {@code extends} the mapper — which also drives the readers over a class.
 */
@Tag('integration')
class AssembleMapperTypeFeatureSpec extends Specification {

    private static final JavaFileObject CLASS_MAPPER = JavaFileObjects.forSourceLines(
            'examples.assemble.ClassMapper',
            'package examples.assemble;',
            '',
            'import io.github.joke.percolate.Map;',
            'import io.github.joke.percolate.Mapper;',
            '',
            '@Mapper',
            'public abstract class ClassMapper {',
            '',
            '    @Map(target = "name", source = "in.name")',
            '    public abstract Target map(Source in);',
            '}',
            '',
            'final class Source {',
            '    private final String name;',
            '    Source(String name) { this.name = name; }',
            '    public String getName() { return name; }',
            '}',
            '',
            'final class Target {',
            '    private final String name;',
            '    Target(String name) { this.name = name; }',
            '    public String getName() { return name; }',
            '}')

    def 'a class-based @Mapper generates a public <Name>Impl that extends the mapper, generated, in its package'() {
        when:
        Compilation compilation = PercolateCompiler.compile(CLASS_MAPPER)

        then:
        compilation.errors().empty
        def source = sourceOf(compilation, 'examples.assemble.ClassMapperImpl')
        source.contains('package examples.assemble;')
        source.contains('public class ClassMapperImpl extends ClassMapper')
        source.contains('@Generated')
        source.contains('public ClassMapperImpl()')
        source.contains('public Target map(Source in)')
    }

    private static String sourceOf(final Compilation compilation, final String qualifiedName) {
        def generated = compilation.generatedSourceFile(qualifiedName)
        assert generated.present
        generated.get().getCharContent(true).toString()
    }
}
