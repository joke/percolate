package io.github.joke.percolate.processor

import com.palantir.javapoet.JavaFile
import com.palantir.javapoet.TypeSpec
import io.github.joke.percolate.processor.graph.MappingGraph
import io.github.joke.percolate.processor.model.DiscoveredModel
import io.github.joke.percolate.processor.model.MapperModel
import io.github.joke.percolate.processor.stage.AnalyzeStage
import io.github.joke.percolate.processor.stage.BuildGraphStage
import io.github.joke.percolate.processor.stage.DiscoverStage
import io.github.joke.percolate.processor.stage.GenerateStage
import io.github.joke.percolate.processor.stage.ValidateStage
import spock.lang.Specification
import spock.lang.Tag

import javax.annotation.processing.Messager
import javax.lang.model.element.Element
import javax.lang.model.element.TypeElement
import javax.tools.Diagnostic.Kind

@Tag('unit')
class PipelineSpec extends Specification {

    AnalyzeStage analyzeStage = Mock()
    DiscoverStage discoverStage = Mock()
    BuildGraphStage buildGraphStage = Mock()
    ValidateStage validateStage = Mock()
    GenerateStage generateStage = Mock()
    Messager messager = Mock()

    Pipeline pipeline = new Pipeline(analyzeStage, discoverStage, buildGraphStage, validateStage, generateStage, messager)

    def 'successful pipeline returns JavaFile'() {
        given:
        final element = Mock(TypeElement)
        final mapperModel = Mock(MapperModel)
        final discoveredModel = Mock(DiscoveredModel)
        final mappingGraph = Mock(MappingGraph)
        final javaFile = JavaFile.builder('com.example', TypeSpec.classBuilder('Test').build()).build()

        when:
        final result = pipeline.process(element)

        then:
        1 * analyzeStage.execute(element) >> StageResult.success(mapperModel)
        1 * discoverStage.execute(mapperModel) >> StageResult.success(discoveredModel)
        1 * buildGraphStage.execute(discoveredModel) >> StageResult.success(mappingGraph)
        1 * validateStage.execute(mappingGraph) >> StageResult.success(mappingGraph)
        1 * generateStage.execute(mappingGraph) >> StageResult.success(javaFile)
        0 * _

        expect:
        result != null
    }

    def 'analyze failure stops pipeline and reports errors'() {
        given:
        final element = Mock(TypeElement)
        final diagnostic = new Diagnostic(Mock(Element), 'analysis failed', Kind.ERROR)
        when:
        final result = pipeline.process(element)

        then:
        1 * analyzeStage.execute(element) >> StageResult.failure([diagnostic])
        1 * messager.printMessage(Kind.ERROR, 'analysis failed', _)
        0 * _

        expect:
        result == null
    }

    def 'validate failure stops pipeline and reports errors'() {
        given:
        final element = Mock(TypeElement)
        final mapperModel = Mock(MapperModel)
        final discoveredModel = Mock(DiscoveredModel)
        final mappingGraph = Mock(MappingGraph)
        final diagnostic = new Diagnostic(Mock(Element), 'unmapped target', Kind.ERROR)

        when:
        final result = pipeline.process(element)

        then:
        1 * analyzeStage.execute(element) >> StageResult.success(mapperModel)
        1 * discoverStage.execute(mapperModel) >> StageResult.success(discoveredModel)
        1 * buildGraphStage.execute(discoveredModel) >> StageResult.success(mappingGraph)
        1 * validateStage.execute(mappingGraph) >> StageResult.failure([diagnostic])
        1 * messager.printMessage(Kind.ERROR, 'unmapped target', _)
        0 * _

        expect:
        result == null
    }
}
