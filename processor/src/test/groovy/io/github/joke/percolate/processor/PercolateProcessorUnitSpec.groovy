package io.github.joke.percolate.processor

import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.SourceVersion

@Tag('unit')
class PercolateProcessorUnitSpec extends Specification {

    PercolateProcessor processor = new PercolateProcessor()

    def 'getSupportedSourceVersion() returns latest supported'() {
        expect:
        processor.getSupportedSourceVersion() == SourceVersion.latestSupported()
    }

    def 'supports @Mapper annotation type'() {
        expect:
        processor.getSupportedAnnotationTypes() == ['io.github.joke.percolate.Mapper'] as Set
    }
}
