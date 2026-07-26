package io.github.joke.percolate.processor.internal.stages.generate

import io.github.joke.percolate.processor.Diagnostic
import io.github.joke.percolate.processor.MapperContext
import io.github.joke.percolate.spi.Subjects
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Tag

import javax.lang.model.element.TypeElement

/**
 * {@link GenerateStage} seam, unit-tested directly with mocked collaborators: a clean, fully-realised mapper is built
 * and assembled; a mapper already carrying an error (scarred or unrealised) is skipped (incomplete graph, nothing to
 * emit); and a codegen failure is recorded as a permanent error rather than propagated.
 */
@Tag('unit')
class GenerateStageSpec extends Specification {

    def buildMethodBodies = Mock(BuildMethodBodies)
    def assembleMapperType = Mock(AssembleMapperType)
    @Subject
    def stage = new GenerateStage(buildMethodBodies, assembleMapperType)

    def mapperType = Mock(TypeElement)
    def ctx = new MapperContext(mapperType)

    def 'a clean, fully-realised mapper is built and then assembled'() {
        given:
        def methodBodies = new MethodBodies([], [])

        when:
        stage.run(ctx)

        then:
        1 * buildMethodBodies.build(ctx) >> methodBodies
        1 * assembleMapperType.assemble(ctx, methodBodies)

        expect:
        ctx.diagnostics.empty
    }

    def 'a mapper already carrying an error is skipped entirely'() {
        given:
        ctx.report(Diagnostic.error(Subjects.none(), 'duplicate target').asPermanent())

        when:
        stage.run(ctx)

        then:
        0 * buildMethodBodies.build(_)
        0 * assembleMapperType.assemble(*_)
    }

    def 'a mapper whose realisation is unsatisfied is skipped (incomplete graph)'() {
        given:
        ctx.report(Diagnostic.error(Subjects.none(), 'no plan for tgt[]'))

        when:
        stage.run(ctx)

        then:
        0 * buildMethodBodies.build(_)
        0 * assembleMapperType.assemble(*_)
    }

    def 'a codegen failure is recorded as a permanent error, not propagated'() {
        given:
        buildMethodBodies.build(ctx) >> { throw new IllegalStateException('boom') }

        when:
        stage.run(ctx)

        then:
        ctx.diagnostics.size() == 1

        expect:
        with(ctx.diagnostics[0]) {
            permanent
            message.contains('code generation failed')
            message.contains('boom')
        }
    }
}
