package io.github.joke.percolate.processor.stages.generate

import io.github.joke.percolate.processor.Diagnostics
import io.github.joke.percolate.processor.MapperContext
import io.github.joke.percolate.processor.model.MapperShape
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.Name
import javax.lang.model.element.TypeElement

@Tag('unit')
class GenerateStageFailureModesSpec extends Specification {

    def 'validation error skips the entire mapper'() {
        given:
        def mapperType = mockMapperType('BadMapper')
        def diagnostics = Mock(Diagnostics)
        diagnostics.hasErrorsFor(mapperType) >> true
        def buildMethodBodies = Mock(BuildMethodBodies)
        def assembleMapperType = Mock(AssembleMapperType)
        def stage = new GenerateStage(diagnostics, buildMethodBodies, assembleMapperType)
        def ctx = ctxWith(mapperType)

        when:
        stage.run(ctx)

        then:
        0 * diagnostics.error(_, _)
        0 * buildMethodBodies.build(_)
        0 * assembleMapperType.assemble(_, _)
    }

    def 'exception during BuildMethodBodies is caught and diagnosed'() {
        given:
        def mapperType = mockMapperType('FailingMapper')
        def diagnostics = Mock(Diagnostics)
        def buildMethodBodies = Mock(BuildMethodBodies)
        def assembleMapperType = Mock(AssembleMapperType)
        def stage = new GenerateStage(diagnostics, buildMethodBodies, assembleMapperType)
        def ctx = ctxWith(mapperType)

        when:
        stage.run(ctx)

        then:
        1 * buildMethodBodies.build(_) >> { throw new RuntimeException('graph is corrupted') }
        1 * diagnostics.error(mapperType, 'code generation failed: graph is corrupted')
        0 * assembleMapperType.assemble(_, _)
    }

    def 'exception during AssembleMapperType is caught and diagnosed'() {
        given:
        def mapperType = mockMapperType('AssembleFailingMapper')
        def diagnostics = Mock(Diagnostics)
        def buildMethodBodies = Mock(BuildMethodBodies)
        def assembleMapperType = Mock(AssembleMapperType)
        def stage = new GenerateStage(diagnostics, buildMethodBodies, assembleMapperType)
        def ctx = ctxWith(mapperType)

        when:
        stage.run(ctx)

        then:
        1 * buildMethodBodies.build(_) >> List.of()
        1 * assembleMapperType.assemble(_, _) >> { throw new IOException('filer error') }
        1 * diagnostics.error(mapperType, 'code generation failed: filer error')
    }

    def 'successful generation calls both phases without recording errors'() {
        given:
        def mapperType = mockMapperType('GoodMapper')
        def diagnostics = Mock(Diagnostics)
        diagnostics.hasErrorsFor(mapperType) >> false
        def buildMethodBodies = Mock(BuildMethodBodies)
        buildMethodBodies.build(_) >> List.of()
        def assembleMapperType = Mock(AssembleMapperType)
        def stage = new GenerateStage(diagnostics, buildMethodBodies, assembleMapperType)
        def ctx = ctxWith(mapperType)

        when:
        stage.run(ctx)

        then:
        1 * diagnostics.hasErrorsFor(mapperType)
        1 * assembleMapperType.assemble(_, _)
        0 * diagnostics.error(_, _)
    }

    def 'null shape returns gracefully without error'() {
        given:
        def mapperType = mockMapperType('NoShapeMapper')
        def diagnostics = Mock(Diagnostics)
        diagnostics.hasErrorsFor(mapperType) >> false
        def buildMethodBodies = Mock(BuildMethodBodies)
        def assembleMapperType = Mock(AssembleMapperType)
        def stage = new GenerateStage(diagnostics, buildMethodBodies, assembleMapperType)
        def ctx = new MapperContext(mapperType)
        ctx.graph = null
        ctx.shape = null

        when:
        stage.run(ctx)

        then:
        1 * diagnostics.hasErrorsFor(mapperType)
        noExceptionThrown()
    }

    private static MapperContext ctxWith(final TypeElement mapperType) {
        def ctx = new MapperContext(mapperType)
        ctx.graph = null
        ctx.shape = new MapperShape(mapperType, List.of())
        ctx
    }

    private TypeElement mockMapperType(final String name) {
        def element = Mock(TypeElement)
        def simpleName = Mock(Name)
        simpleName.toString() >> name
        simpleName.length() >> name.length()
        element.simpleName >> simpleName
        element
    }
}
