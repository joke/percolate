package io.github.joke.percolate.stage

import com.google.testing.compile.Compiler
import com.google.testing.compile.JavaFileObjects
import io.github.joke.percolate.processor.PercolateProcessor
import spock.lang.Specification

import static com.google.testing.compile.CompilationSubject.assertThat

class ParseMapperStageSpec extends Specification {

    def "abstract method with @Map directive is parsed and compiles"() {
        given:
        def src = JavaFileObjects.forSourceLines('test.Src',
            'package test;',
            'public class Src { public String getFirstName() { return ""; } }')
        def tgt = JavaFileObjects.forSourceLines('test.Tgt',
            'package test;',
            'public class Tgt { public final String name;',
            '    public Tgt(String name) { this.name = name; } }')
        def mapper = JavaFileObjects.forSourceLines('test.MyMapper',
            'package test;',
            'import io.github.joke.percolate.Mapper;',
            'import io.github.joke.percolate.Map;',
            '@Mapper public interface MyMapper {',
            '    @Map(target = "name", source = "firstName")',
            '    Tgt map(Src src);',
            '}')

        when:
        def compilation = Compiler.javac()
            .withProcessors(new PercolateProcessor())
            .compile(src, tgt, mapper)

        then:
        assertThat(compilation).succeeded()
    }

    def "default method is parsed as non-abstract and compiles"() {
        given:
        def mapper = JavaFileObjects.forSourceLines('test.HelpMapper',
            'package test;',
            'import io.github.joke.percolate.Mapper;',
            '@Mapper public interface HelpMapper {',
            '    default String helper(String s) { return s.trim(); }',
            '}')

        when:
        def compilation = Compiler.javac()
            .withProcessors(new PercolateProcessor())
            .compile(mapper)

        then:
        assertThat(compilation).succeeded()
    }

    def "multiple @Map annotations on a method are all parsed"() {
        given:
        def src = JavaFileObjects.forSourceLines('test.Src',
            'package test;',
            'public class Src {',
            '    public String getFirstName() { return ""; }',
            '    public int getYears() { return 0; }',
            '}')
        def tgt = JavaFileObjects.forSourceLines('test.Tgt',
            'package test;',
            'public class Tgt { public final String name; public final int age;',
            '    public Tgt(String name, int age) { this.name = name; this.age = age; } }')
        def mapper = JavaFileObjects.forSourceLines('test.MultiMapper',
            'package test;',
            'import io.github.joke.percolate.Mapper;',
            'import io.github.joke.percolate.Map;',
            '@Mapper public interface MultiMapper {',
            '    @Map(target = "name", source = "firstName")',
            '    @Map(target = "age", source = "years")',
            '    Tgt map(Src src);',
            '}')

        when:
        def compilation = Compiler.javac()
            .withProcessors(new PercolateProcessor())
            .compile(src, tgt, mapper)

        then:
        assertThat(compilation).succeeded()
    }
}
