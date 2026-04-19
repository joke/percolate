package io.github.joke.percolate.processor

import com.palantir.javapoet.JavaFile
import com.palantir.javapoet.TypeSpec
import io.github.joke.percolate.processor.graph.ValueGraphResult
import io.github.joke.percolate.processor.match.MatchedModel
import io.github.joke.percolate.processor.match.MethodMatching
import io.github.joke.percolate.processor.match.ResolvedAssignment
import io.github.joke.percolate.processor.model.MapperModel
import io.github.joke.percolate.processor.stage.AnalyzeStage
import io.github.joke.percolate.processor.stage.BuildValueGraphStage
import io.github.joke.percolate.processor.stage.DumpGraphStage
import io.github.joke.percolate.processor.stage.GenerateStage
import io.github.joke.percolate.processor.stage.MatchMappingsStage
import io.github.joke.percolate.processor.stage.ResolvePathStage
import io.github.joke.percolate.processor.stage.ValidateMatchingStage
import io.github.joke.percolate.processor.stage.ValidateResolutionStage
import spock.lang.Specification
import spock.lang.Tag

import javax.annotation.processing.Messager
import javax.lang.model.element.Element
import javax.lang.model.element.TypeElement
import javax.tools.Diagnostic.Kind

@Tag('unit')
class PipelineSpec extends Specification {

    AnalyzeStage analyzeStage = Mock()
    MatchMappingsStage matchMappingsStage = Mock()
    ValidateMatchingStage validateMatchingStage = Mock()
    BuildValueGraphStage buildValueGraphStage = Mock()
    ResolvePathStage resolvePathStage = Mock()
    DumpGraphStage dumpGraphStage = Mock()
    ValidateResolutionStage validateResolutionStage = Mock()
    GenerateStage generateStage = Mock()
    Messager messager = Mock()

    Pipeline pipeline = new Pipeline(
            analyzeStage,
            matchMappingsStage,
            validateMatchingStage,
            buildValueGraphStage,
            resolvePathStage,
            dumpGraphStage,
            validateResolutionStage,
            generateStage,
            messager)

    def 'successful pipeline invokes the 8 stages in order and returns JavaFile'() {
        given:
        final element = Mock(TypeElement)
        final mapperType = Mock(TypeElement)
        final mapperModel = Mock(MapperModel)
        final matchedModel = Stub(MatchedModel) { getMapperType() >> mapperType }
        final valueGraphResult = Mock(ValueGraphResult)
        final resolveMap = [:] as Map<MethodMatching, List<ResolvedAssignment>>
        final validatedMap = [:] as Map<MethodMatching, List<ResolvedAssignment>>
        final javaFile = JavaFile.builder('com.example', TypeSpec.classBuilder('Test').build()).build()

        when:
        final result = pipeline.process(element)

        then:
        1 * analyzeStage.execute(element) >> StageResult.success(mapperModel)

        then:
        1 * matchMappingsStage.execute(mapperModel) >> StageResult.success(matchedModel)

        then:
        1 * validateMatchingStage.execute(matchedModel) >> StageResult.success(matchedModel)

        then:
        1 * buildValueGraphStage.execute(matchedModel) >> StageResult.success(valueGraphResult)

        then:
        1 * resolvePathStage.execute(valueGraphResult) >> StageResult.success(resolveMap)

        then:
        1 * dumpGraphStage.execute(mapperType, valueGraphResult, resolveMap)

        then:
        1 * validateResolutionStage.execute(mapperType, resolveMap) >> StageResult.success(validatedMap)

        then:
        1 * generateStage.execute(mapperType, validatedMap) >> StageResult.success(javaFile)
        0 * _

        expect:
        result == javaFile
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

    def 'match-mappings failure stops pipeline and reports errors'() {
        given:
        final element = Mock(TypeElement)
        final mapperModel = Mock(MapperModel)
        final diagnostic = new Diagnostic(Mock(Element), 'matching failed', Kind.ERROR)

        when:
        final result = pipeline.process(element)

        then:
        1 * analyzeStage.execute(element) >> StageResult.success(mapperModel)
        1 * matchMappingsStage.execute(mapperModel) >> StageResult.failure([diagnostic])
        1 * messager.printMessage(Kind.ERROR, 'matching failed', _)
        0 * _

        expect:
        result == null
    }

    def 'validate-matching failure stops pipeline before BuildValueGraphStage'() {
        given:
        final element = Mock(TypeElement)
        final mapperModel = Mock(MapperModel)
        final matchedModel = Mock(MatchedModel)
        final diagnostic = new Diagnostic(Mock(Element), 'duplicate target', Kind.ERROR)

        when:
        final result = pipeline.process(element)

        then:
        1 * analyzeStage.execute(element) >> StageResult.success(mapperModel)
        1 * matchMappingsStage.execute(mapperModel) >> StageResult.success(matchedModel)
        1 * validateMatchingStage.execute(matchedModel) >> StageResult.failure([diagnostic])
        1 * messager.printMessage(Kind.ERROR, 'duplicate target', _)
        0 * _

        expect:
        result == null
    }

    def 'validate-resolution failure stops pipeline before GenerateStage'() {
        given:
        final element = Mock(TypeElement)
        final mapperType = Mock(TypeElement)
        final mapperModel = Mock(MapperModel)
        final matchedModel = Stub(MatchedModel) { getMapperType() >> mapperType }
        final valueGraphResult = Mock(ValueGraphResult)
        final resolveMap = [:] as Map<MethodMatching, List<ResolvedAssignment>>
        final diagnostic = new Diagnostic(Mock(Element), 'unresolved transform', Kind.ERROR)

        when:
        final result = pipeline.process(element)

        then:
        1 * analyzeStage.execute(element) >> StageResult.success(mapperModel)
        1 * matchMappingsStage.execute(mapperModel) >> StageResult.success(matchedModel)
        1 * validateMatchingStage.execute(matchedModel) >> StageResult.success(matchedModel)
        1 * buildValueGraphStage.execute(matchedModel) >> StageResult.success(valueGraphResult)
        1 * resolvePathStage.execute(valueGraphResult) >> StageResult.success(resolveMap)
        1 * dumpGraphStage.execute(mapperType, valueGraphResult, resolveMap)
        1 * validateResolutionStage.execute(mapperType, resolveMap) >> StageResult.failure([diagnostic])
        1 * messager.printMessage(Kind.ERROR, 'unresolved transform', _)
        0 * _

        expect:
        result == null
    }
}
