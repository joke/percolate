package io.github.joke.percolate.processor.internal.stages.dump

import io.github.joke.percolate.processor.MapperContext
import io.github.joke.percolate.processor.internal.graph.GraphVertex
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Tag

import javax.lang.model.element.TypeElement

@Tag('unit')
class DumpFullGraphStageSpec extends Specification {

    GraphDumpWriter writer = Mock()
    @Subject
    DumpFullGraphStage stage = new DumpFullGraphStage(writer)

    def ctx = new MapperContext(Stub(TypeElement))

    def 'dumps the full view for every vertex, dimming unreachable ones'() {
        when:
        stage.run(ctx)

        then:
        1 * writer.dump(ctx, 'full', { it.test(Stub(GraphVertex)) }, true)
        0 * _
    }
}
