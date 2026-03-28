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
        def element = Mock(TypeElement)
        def mapperModel = Mock(MapperModel)
        def discoveredModel = Mock(DiscoveredModel)
        def mappingGraph = Mock(MappingGraph)
        def javaFile = JavaFile.builder('com.example', TypeSpec.classBuilder('Test').build()).build()

        analyzeStage.execute(element) >> StageResult.success(mapperModel)
        discoverStage.execute(mapperModel) >> StageResult.success(discoveredModel)
        buildGraphStage.execute(discoveredModel) >> StageResult.success(mappingGraph)
        validateStage.execute(mappingGraph) >> StageResult.success(mappingGraph)
        generateStage.execute(mappingGraph) >> StageResult.success(javaFile)

        when:
        def result = pipeline.process(element)

        then:
        result != null
    }

    def 'analyze failure stops pipeline and reports errors'() {
        given:
        def element = Mock(TypeElement)
        def diagnostic = new Diagnostic(Mock(Element), 'analysis failed', Kind.ERROR)
        analyzeStage.execute(element) >> StageResult.failure([diagnostic])

        when:
        def result = pipeline.process(element)

        then:
        result == null
        1 * messager.printMessage(Kind.ERROR, 'analysis failed', _)
    }

    def 'validate failure stops pipeline and reports errors'() {
        given:
        def element = Mock(TypeElement)
        def mapperModel = Mock(MapperModel)
        def discoveredModel = Mock(DiscoveredModel)
        def mappingGraph = Mock(MappingGraph)
        def diagnostic = new Diagnostic(Mock(Element), 'unmapped target', Kind.ERROR)

        analyzeStage.execute(element) >> StageResult.success(mapperModel)
        discoverStage.execute(mapperModel) >> StageResult.success(discoveredModel)
        buildGraphStage.execute(discoveredModel) >> StageResult.success(mappingGraph)
        validateStage.execute(mappingGraph) >> StageResult.failure([diagnostic])

        when:
        def result = pipeline.process(element)

        then:
        result == null
        1 * messager.printMessage(Kind.ERROR, 'unmapped target', _)
        0 * generateStage.execute(_)
    }
}
