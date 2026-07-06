package io.github.joke.percolate.processor.internal.stages.expand

import io.github.joke.percolate.processor.internal.graph.Value
import io.github.joke.percolate.processor.model.MapperShape
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.ExecutableElement

/**
 * {@link ExpansionLoop} unit-tested by mocking {@link Seeder} and the injected {@link ExpansionLoop.Expander} —
 * drives the demand work-list to fixpoint, holding no expansion-specific logic itself (decomposed out of
 * {@code ExpandStage.Driver} by change {@code decompose-engine-stages}).
 */
@Tag('unit')
class ExpansionLoopSpec extends Specification {

    Seeder seeder = Mock()
    ExpansionLoop.Expander expander = Mock()
    ExpansionLoop loop = new ExpansionLoop(seeder, expander)

    def 'seedAndExpand seeds one return-root per abstract method, then expands each'() {
        ExecutableElement method0 = Mock()
        ExecutableElement method1 = Mock()
        MapperShape shape = Mock()
        Value root0 = Mock()
        Value root1 = Mock()

        when:
        loop.seedAndExpand(shape)

        then:
        1 * shape.abstractMethods >> [method0, method1]
        1 * seeder.seed(method0) >> root0
        1 * seeder.seed(method1) >> root1
        1 * expander.expand(root0) { it != null }
        1 * expander.expand(root1) { it != null }
        0 * _
    }

    def 'a value the expander re-enqueues on its own first visit is not expanded twice'() {
        ExecutableElement method = Mock()
        MapperShape shape = Mock()
        Value root = Mock()

        when:
        loop.seedAndExpand(shape)

        then:
        1 * shape.abstractMethods >> [method]
        1 * seeder.seed(method) >> root
        1 * expander.expand(root) { it != null } >> { Value v, enqueue -> enqueue.accept(root) }
        0 * _
    }

    def 'a follow-up demand the expander enqueues is drained by a later iteration'() {
        ExecutableElement method = Mock()
        MapperShape shape = Mock()
        Value root = Mock()
        Value follow = Mock()

        when:
        loop.seedAndExpand(shape)

        then:
        1 * shape.abstractMethods >> [method]
        1 * seeder.seed(method) >> root
        1 * expander.expand(root) { it != null } >> { Value v, enqueue -> enqueue.accept(follow) }
        1 * expander.expand(follow) { it != null }
        0 * _
    }

    def 'enqueue adds a value the next seedAndExpand pass drains'() {
        ExecutableElement method = Mock()
        MapperShape shape = Mock()
        Value root = Mock()
        Value manuallyQueued = Mock()

        when:
        loop.enqueue(manuallyQueued)
        loop.seedAndExpand(shape)

        then:
        1 * shape.abstractMethods >> [method]
        1 * seeder.seed(method) >> root
        1 * expander.expand(manuallyQueued) { it != null }
        1 * expander.expand(root) { it != null }
        0 * _
    }
}
