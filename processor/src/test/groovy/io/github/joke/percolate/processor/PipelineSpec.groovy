package io.github.joke.percolate.processor

import com.palantir.javapoet.JavaFile
import com.palantir.javapoet.TypeSpec
import io.github.joke.percolate.processor.graph.MappingGraph
import io.github.joke.percolate.processor.model.MapperModel
import io.github.joke.percolate.processor.stage.AnalyzeStage
import io.github.joke.percolate.processor.stage.BuildGraphStage
import io.github.joke.percolate.processor.stage.DumpPropertyGraphStage
import io.github.joke.percolate.processor.stage.DumpResolvedOverlayStage
import io.github.joke.percolate.processor.stage.DumpTransformGraphStage
import io.github.joke.percolate.processor.stage.GenerateStage
import io.github.joke.percolate.processor.stage.ResolveTransformsStage
import io.github.joke.percolate.processor.stage.ValidateTransformsStage
import io.github.joke.percolate.processor.transform.ResolvedModel
import spock.lang.Specification
import spock.lang.Tag

import javax.annotation.processing.Messager
import javax.lang.model.element.Element
import javax.lang.model.element.TypeElement
import javax.tools.Diagnostic.Kind

@Tag('unit')
class PipelineSpec extends Specification {

    AnalyzeStage analyzeStage = Mock()
    BuildGraphStage buildGraphStage = Mock()
    DumpPropertyGraphStage dumpPropertyGraphStage = Stub()
    ResolveTransformsStage resolveTransformsStage = Mock()
    DumpTransformGraphStage dumpTransformGraphStage = Stub()
    DumpResolvedOverlayStage dumpResolvedOverlayStage = Stub()
    ValidateTransformsStage validateTransformsStage = Mock()
    GenerateStage generateStage = Mock()
    Messager messager = Mock()

    Pipeline pipeline = new Pipeline(analyzeStage, buildGraphStage, dumpPropertyGraphStage,
            resolveTransformsStage, dumpTransformGraphStage, dumpResolvedOverlayStage,
            validateTransformsStage, generateStage, messager)

    def 'successful pipeline returns JavaFile'() {
        given:
        final element = Mock(TypeElement)
        final mapperModel = Mock(MapperModel)
        final mappingGraph = Mock(MappingGraph)
        final resolvedModel = Mock(ResolvedModel)
        final javaFile = JavaFile.builder('com.example', TypeSpec.classBuilder('Test').build()).build()

        when:
        final result = pipeline.process(element)

        then:
        1 * analyzeStage.execute(element) >> StageResult.success(mapperModel)
        1 * buildGraphStage.execute(mapperModel) >> StageResult.success(mappingGraph)
        1 * resolveTransformsStage.execute(mappingGraph) >> StageResult.success(resolvedModel)
        1 * validateTransformsStage.execute(resolvedModel) >> StageResult.success(resolvedModel)
        1 * generateStage.execute(resolvedModel) >> StageResult.success(javaFile)
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

    def 'validate transforms failure stops pipeline and reports errors'() {
        given:
        final element = Mock(TypeElement)
        final mapperModel = Mock(MapperModel)
        final mappingGraph = Mock(MappingGraph)
        final resolvedModel = Mock(ResolvedModel)
        final diagnostic = new Diagnostic(Mock(Element), 'unresolved transform', Kind.ERROR)

        when:
        final result = pipeline.process(element)

        then:
        1 * analyzeStage.execute(element) >> StageResult.success(mapperModel)
        1 * buildGraphStage.execute(mapperModel) >> StageResult.success(mappingGraph)
        1 * resolveTransformsStage.execute(mappingGraph) >> StageResult.success(resolvedModel)
        1 * validateTransformsStage.execute(resolvedModel) >> StageResult.failure([diagnostic])
        1 * messager.printMessage(Kind.ERROR, 'unresolved transform', _)
        0 * _

        expect:
        result == null
    }
}
