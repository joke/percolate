package io.github.joke.percolate.spi.impl

import com.google.testing.compile.Compilation
import com.google.testing.compile.Compiler
import com.google.testing.compile.JavaFileObjects
import io.github.joke.percolate.processor.PercolateProcessor
import spock.lang.Specification

import static com.google.testing.compile.CompilationSubject.assertThat

class EnumMappingStrategySpec extends Specification {

    def "generates enum-to-enum mapping via valueOf(name())"() {
        given:
        def mapper = JavaFileObjects.forSourceLines('test.ObjMapper',
            'package test;',
            'import io.github.joke.percolate.Mapper;',
            '@Mapper',
            'public interface ObjMapper {',
            '    TargetObj map(SourceObj source);',
            '}',
        )
        def sourceObj = JavaFileObjects.forSourceLines('test.SourceObj',
            'package test;',
            'public class SourceObj {',
            '    public SourceStatus getStatus() { return null; }',
            '}',
        )
        def targetObj = JavaFileObjects.forSourceLines('test.TargetObj',
            'package test;',
            'public class TargetObj {',
            '    public final TargetStatus status;',
            '    public TargetObj(TargetStatus status) {',
            '        this.status = status;',
            '    }',
            '}',
        )
        def sourceStatus = JavaFileObjects.forSourceLines('test.SourceStatus',
            'package test;',
            'public enum SourceStatus { ACTIVE, INACTIVE }',
        )
        def targetStatus = JavaFileObjects.forSourceLines('test.TargetStatus',
            'package test;',
            'public enum TargetStatus { ACTIVE, INACTIVE }',
        )

        when:
        Compilation compilation = Compiler.javac()
            .withProcessors(new PercolateProcessor())
            .compile(mapper, sourceObj, targetObj, sourceStatus, targetStatus)

        then:
        assertThat(compilation).succeeded()
        assertThat(compilation)
            .generatedSourceFile('test.ObjMapperImpl')
            .contentsAsUtf8String()
            .contains('test.TargetStatus.valueOf(source.getStatus().name())')
    }
}
