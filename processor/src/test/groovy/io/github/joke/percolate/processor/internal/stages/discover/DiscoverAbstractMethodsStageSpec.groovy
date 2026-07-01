package io.github.joke.percolate.processor.internal.stages.discover

import io.github.joke.percolate.processor.MapperContext
import io.github.joke.percolate.processor.test.fixtures.Human
import io.github.joke.percolate.processor.test.fixtures.Person
import io.github.joke.percolate.processor.test.fixtures.PersonMapper
import io.github.joke.percolate.spi.test.TypeUniverse
import spock.lang.Isolated
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.ElementKind
import javax.lang.model.element.ExecutableElement

/**
 * {@link DiscoverAbstractMethodsStage} seam, unit-tested directly: the mapper {@code TypeElement} (read off a compiled
 * {@code @Mapper} fixture via {@link TypeUniverse}, no compile) is reduced to a {@code MapperShape} of its abstract,
 * non-{@code Object} methods. Each case isolates a step — the whole reduction, the {@code run} wiring, and the
 * {@code isAbstract}/{@code isObjectMethod}/{@code filter} predicates.
 */
@Tag('unit')
@Isolated // bridge: shares the static TypeUniverse javac; serialise until the type-universe redesign (see openspec/notes.md)
class DiscoverAbstractMethodsStageSpec extends Specification {

    DiscoverAbstractMethodsStage stage = new DiscoverAbstractMethodsStage(TypeUniverse.elements(), TypeUniverse.types())

    def setupSpec() {
        // prime the mapper + its method's parameter/return fixture closures single-threaded (see ExpandStageDriverSpec)
        TypeUniverse.of(Person)
        TypeUniverse.of(Human)
        TypeUniverse.of(PersonMapper)
    }

    def 'reduces a @Mapper interface to its single abstract method, excluding Object methods'() {
        when:
        def shape = stage.apply(TypeUniverse.of(PersonMapper))

        then:
        shape.type == TypeUniverse.of(PersonMapper)
        shape.abstractMethods.collect { it.simpleName.toString() } == ['map']
    }

    def 'run installs the discovered shape on the context'() {
        given:
        def ctx = new MapperContext(TypeUniverse.of(PersonMapper))

        when:
        stage.run(ctx)

        then:
        ctx.shape != null
        ctx.shape.abstractMethods.collect { it.simpleName.toString() } == ['map']
    }

    def 'isAbstract distinguishes an abstract method from a concrete one'() {
        expect:
        stage.isAbstract(method(PersonMapper, 'map'))
        !stage.isAbstract(method(Object, 'toString'))
    }

    def 'isObjectMethod is true only for a method declared on Object'() {
        given:
        def objectType = TypeUniverse.of(Object)

        expect:
        stage.isObjectMethod(method(Object, 'toString'), objectType)
        !stage.isObjectMethod(method(PersonMapper, 'map'), objectType)
    }

    def 'filter keeps abstract non-Object methods and drops the rest'() {
        given:
        def objectType = TypeUniverse.of(Object)
        def methods = [method(PersonMapper, 'map'), method(Object, 'toString')]

        when:
        def shape = stage.filter(TypeUniverse.of(PersonMapper), methods, objectType)

        then:
        shape.abstractMethods == [method(PersonMapper, 'map')]
    }

    private static ExecutableElement method(final Class<?> type, final String name) {
        TypeUniverse.of(type).enclosedElements.find {
            it.kind == ElementKind.METHOD && it.simpleName.toString() == name
        } as ExecutableElement
    }
}
