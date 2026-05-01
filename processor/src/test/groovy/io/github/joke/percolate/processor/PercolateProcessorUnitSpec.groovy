package io.github.joke.percolate.processor

import com.google.testing.compile.Compilation
import com.google.testing.compile.JavaFileObjects
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.SourceVersion

import static com.google.testing.compile.CompilationSubject.assertThat
import static com.google.testing.compile.Compiler.javac

@Tag('unit')
class PercolateProcessorUnitSpec extends Specification {

    PercolateProcessor processor = new PercolateProcessor()

    def 'getSupportedSourceVersion returns latest supported'() {
        expect:
        processor.getSupportedSourceVersion() == SourceVersion.latestSupported()
    }
}
