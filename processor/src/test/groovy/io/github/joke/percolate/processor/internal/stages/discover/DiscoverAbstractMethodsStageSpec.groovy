package io.github.joke.percolate.processor.internal.stages.discover

import io.github.joke.percolate.processor.MapperContext
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.TypeElement

/**
 * {@link DiscoverAbstractMethodsStage} glue, unit-tested mock-only: the stage reads the mapper type's members through
 * the {@link AbstractMethodReader} and reduces them to a {@code MapperShape} of the {@link AbstractMethodFilter}'s
 * kept methods, installing it on the context. The collaborators are mocked; every {@code AbstractMethodDescriptor} and
 * {@code ExecutableElement} is an opaque, never-stubbed token. The reader's javac member enumeration and the filter's
 * keep/drop logic are covered by their own specs and the compile-based feature-e2e layer — no javac substrate here.
 */
@Tag('unit')
class DiscoverAbstractMethodsStageSpec extends Specification {

    AbstractMethodReader reader = Mock()
    AbstractMethodFilter filter = Mock()
    DiscoverAbstractMethodsStage stage = new DiscoverAbstractMethodsStage(reader, filter)

    def 'apply reduces the type members to a shape of the filter-kept abstract methods'() {
        TypeElement mapperType = Mock()
        ExecutableElement kept = Mock()
        AbstractMethodDescriptor descriptor = Mock()

        when:
        def shape = stage.apply(mapperType)

        then:
        1 * reader.readMethods(mapperType) >> [descriptor]
        1 * filter.abstractMethods([descriptor]) >> [kept]
        0 * _

        expect:
        shape.type.is(mapperType)
        shape.abstractMethods == [kept]
    }

    def 'run installs the discovered shape on the context'() {
        TypeElement mapperType = Mock()
        ExecutableElement kept = Mock()
        AbstractMethodDescriptor descriptor = Mock()
        def ctx = new MapperContext(mapperType)

        when:
        stage.run(ctx)

        then:
        1 * reader.readMethods(mapperType) >> [descriptor]
        1 * filter.abstractMethods([descriptor]) >> [kept]
        0 * _

        expect:
        ctx.shape != null
        ctx.shape.type.is(mapperType)
        ctx.shape.abstractMethods == [kept]
    }
}
