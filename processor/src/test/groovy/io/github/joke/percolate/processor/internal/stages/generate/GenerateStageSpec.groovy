package io.github.joke.percolate.processor.internal.stages.generate

import io.github.joke.percolate.processor.Diagnostics
import io.github.joke.percolate.processor.MapperContext
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Tag

import javax.lang.model.element.TypeElement

/**
 * {@link GenerateStage} seam, unit-tested directly with mocked collaborators: a clean, fully-realised mapper is built
 * and assembled; a scarred mapper (existing errors) or one with unsatisfied realisation is skipped (incomplete graph,
 * nothing to emit); and a codegen failure is reported as an error rather than propagated.
 */
@Tag('unit')
class GenerateStageSpec extends Specification {

    def diagnostics = Mock(Diagnostics)
    def buildMethodBodies = Mock(BuildMethodBodies)
    def assembleMapperType = Mock(AssembleMapperType)
    @Subject
    def stage = new GenerateStage(diagnostics, buildMethodBodies, assembleMapperType)

    def mapperType = Mock(TypeElement)
    def ctx = new MapperContext(mapperType)

    def 'a clean, fully-realised mapper is built and then assembled'() {
        given:
        diagnostics.hasErrorsFor(mapperType) >> false

        when:
        stage.run(ctx)

        then:
        1 * buildMethodBodies.build(ctx) >> []
        1 * assembleMapperType.assemble(ctx, [])
        0 * diagnostics.error(*_)
    }

    def 'a scarred mapper with existing errors is skipped entirely'() {
        given:
        diagnostics.hasErrorsFor(mapperType) >> true

        when:
        stage.run(ctx)

        then:
        0 * buildMethodBodies.build(_)
        0 * assembleMapperType.assemble(*_)
    }

    def 'a mapper whose realisation is unsatisfied is skipped (incomplete graph)'() {
        given:
        diagnostics.hasErrorsFor(mapperType) >> false
        ctx.unsatisfiedRealisation = ['no plan for tgt[]']

        when:
        stage.run(ctx)

        then:
        0 * buildMethodBodies.build(_)
        0 * assembleMapperType.assemble(*_)
    }

    def 'a codegen failure is recorded as an error, not propagated'() {
        given:
        diagnostics.hasErrorsFor(mapperType) >> false
        buildMethodBodies.build(ctx) >> { throw new IllegalStateException('boom') }
        String reported = null

        when:
        stage.run(ctx)

        then:
        1 * diagnostics.error(mapperType, _ as String) >> { args -> reported = args[1] }

        expect:
        reported.contains('code generation failed')
        reported.contains('boom')
    }
}
