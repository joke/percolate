package io.github.joke.percolate.processor.stage

import spock.lang.Specification
import spock.lang.Tag

import javax.annotation.processing.Filer

@Tag('unit')
class GenerateStageSpec extends Specification {

    def 'stage can be instantiated with Filer'() {
        given:
        final filer = Stub(Filer)

        expect:
        new GenerateStage(filer) != null
    }
}
