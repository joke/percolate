package io.github.joke.percolate.processor

import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.TypeElement
import java.util.List

@Tag('unit')
class PipelineSpec extends Specification {

    def 'process invokes nine stages in order'() {
        given:
        def typeElement = Mock(TypeElement)
        def stage1 = Mock(Stage) { run(_) >> { } }
        def stage2 = Mock(Stage) { run(_) >> { } }
        def stage3 = Mock(Stage) { run(_) >> { } }
        def stage4 = Mock(Stage) { run(_) >> { } }
        def stage5 = Mock(Stage) { run(_) >> { } }
        def stage6 = Mock(Stage) { run(_) >> { } }
        def stage7 = Mock(Stage) { run(_) >> { } }
        def stage8 = Mock(Stage) { run(_) >> { } }
        def stage9 = Mock(Stage) { run(_) >> { } }
        def stages = List.of(stage1, stage2, stage3, stage4, stage5, stage6, stage7, stage8, stage9)
        Pipeline pipeline = new Pipeline(stages)

        when:
        pipeline.process(typeElement)

        then:
        1 * stage1.run(_)
        1 * stage2.run(_)
        1 * stage3.run(_)
        1 * stage4.run(_)
        1 * stage5.run(_)
        1 * stage6.run(_)
        1 * stage7.run(_)
        1 * stage8.run(_)
        1 * stage9.run(_)
    }

    def 'a fresh MapperContext is constructed per process invocation'() {
        given:
        def typeElement1 = Mock(TypeElement)
        def typeElement2 = Mock(TypeElement)
        def stage1 = Mock(Stage) { run(_) >> { } }
        def stage2 = Mock(Stage) { run(_) >> { } }
        def stage3 = Mock(Stage) { run(_) >> { } }
        def stage4 = Mock(Stage) { run(_) >> { } }
        def stage5 = Mock(Stage) { run(_) >> { } }
        def stage6 = Mock(Stage) { run(_) >> { } }
        def stage7 = Mock(Stage) { run(_) >> { } }
        def stage8 = Mock(Stage) { run(_) >> { } }
        def stage9 = Mock(Stage) { run(_) >> { } }
        def stages = List.of(stage1, stage2, stage3, stage4, stage5, stage6, stage7, stage8, stage9)
        Pipeline pipeline = new Pipeline(stages)

        when:
        pipeline.process(typeElement1)
        pipeline.process(typeElement2)

        then:
        2 * stage1.run(_)
        2 * stage2.run(_)
        2 * stage3.run(_)
        2 * stage4.run(_)
        2 * stage5.run(_)
        2 * stage6.run(_)
        2 * stage7.run(_)
        2 * stage8.run(_)
        2 * stage9.run(_)
    }
}
