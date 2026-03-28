package io.github.joke.percolate.processor

import spock.lang.Specification
import spock.lang.Tag

import javax.annotation.processing.Filer
import javax.annotation.processing.Messager
import javax.annotation.processing.ProcessingEnvironment
import javax.lang.model.util.Elements
import javax.lang.model.util.Types

@Tag('unit')
class ProcessorModuleSpec extends Specification {

    ProcessingEnvironment processingEnvironment = Mock()
    ProcessorModule module = new ProcessorModule(processingEnvironment)

    def 'elements() returns element utilities from processing environment'() {
        given:
        final elements = Mock(Elements)
        processingEnvironment.elementUtils >> elements

        expect:
        module.elements() == elements
    }

    def 'types() returns type utilities from processing environment'() {
        given:
        final types = Mock(Types)
        processingEnvironment.typeUtils >> types

        expect:
        module.types() == types
    }

    def 'messager() returns messager from processing environment'() {
        given:
        final messager = Mock(Messager)
        processingEnvironment.messager >> messager

        expect:
        module.messager() == messager
    }

    def 'filer() returns filer from processing environment'() {
        given:
        final filer = Mock(Filer)
        processingEnvironment.filer >> filer

        expect:
        module.filer() == filer
    }
}
