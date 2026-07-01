package io.github.joke.percolate.processor.internal.stages.discover

import io.github.joke.percolate.processor.MapperContext
import io.github.joke.percolate.processor.internal.graph.MethodScope
import io.github.joke.percolate.processor.model.MapperShape
import io.github.joke.percolate.processor.test.fixtures.DirectiveFixtures
import io.github.joke.percolate.processor.test.fixtures.Human
import io.github.joke.percolate.processor.test.fixtures.Person
import io.github.joke.percolate.spi.test.TypeUniverse
import spock.lang.Isolated
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.ElementKind
import javax.lang.model.element.ExecutableElement

/**
 * {@link DiscoverMappingsStage} seam, unit-tested directly: the {@code @Map}/{@code @MapList} mirrors are read off the
 * compiled {@code DirectiveFixtures} type via {@link TypeUniverse} (CLASS-retained, so present in bytecode — no
 * annotation-processing round). Presence of {@code source}/{@code constant}/{@code defaultValue} is decided against
 * the {@code Map.UNSET} sentinel with the annotation's declared defaults; an empty string is present, not absent.
 */
@Tag('unit')
@Isolated // bridge: shares the static TypeUniverse javac; serialise until the type-universe redesign (see openspec/notes.md)
class DiscoverMappingsStageSpec extends Specification {

    @Shared DiscoverMappingsStage stage = new DiscoverMappingsStage(TypeUniverse.elements())

    def setupSpec() {
        // prime the fixture + its methods' parameter/return closures single-threaded (see ExpandStageDriverSpec)
        TypeUniverse.of(Person)
        TypeUniverse.of(Human)
        TypeUniverse.of(DirectiveFixtures)
    }

    def 'a source directive with a default is discovered with both present and no constant'() {
        when:
        def directives = stage.extractDirectives(method('sourceWithDefault').annotationMirrors)

        then:
        directives.size() == 1
        def directive = directives[0]
        directive.target == 'name'
        directive.hasSource()
        directive.source == 'in.name'
        directive.hasDefaultValue()
        directive.defaultValue == 'unknown'
        !directive.hasConstant()
    }

    def 'a constant directive is discovered with a present constant and no source'() {
        when:
        def directives = stage.extractDirectives(method('constantOnly').annotationMirrors)

        then:
        directives.size() == 1
        directives[0].hasConstant()
        directives[0].constant == 'ACTIVE'
        !directives[0].hasSource()
        directives[0].source == null
    }

    def 'an empty-string constant is present, not absent (sentinel, not isEmpty)'() {
        when:
        def directive = stage.extractDirectives(method('emptyConstant').annotationMirrors)[0]

        then:
        directive.hasConstant()
        directive.constant == ''
        !directive.hasSource()
    }

    def 'repeated @Map directives are unwrapped from the @MapList container, in order'() {
        when:
        def directives = stage.extractDirectives(method('repeated').annotationMirrors)

        then:
        directives*.target == ['first', 'second']
        directives.every { it.hasSource() && !it.hasConstant() }
    }

    def 'a method with no @Map yields no directives'() {
        expect:
        stage.extractDirectives(method('none').annotationMirrors).empty
    }

    def 'run installs the mappings and a per-method-scope goal spec carrying every declared binding'() {
        given:
        def ctx = new MapperContext(TypeUniverse.of(DirectiveFixtures))
        ctx.shape = new MapperShape(TypeUniverse.of(DirectiveFixtures), [method('repeated')])

        when:
        stage.run(ctx)

        then: 'the goal spec is reachable by the method scope and declares both children'
        ctx.mappings != null
        def goal = ctx.goalSpecs[new MethodScope(method('repeated'))]
        goal != null
        goal.declaredChildren('') == ['first', 'second'] as Set
        goal.bindingFor('first').present
        goal.bindingFor('second').present
    }

    def 'a non-@Map, non-@MapList annotation contributes no directive'() {
        expect: 'the @Deprecated mirror on sink() is neither @Map nor @MapList, so it is skipped'
        stage.extractDirectives(method('sink').annotationMirrors).empty
    }

    def 'run is a no-op when discovery produced no shape'() {
        def ctx = new MapperContext(TypeUniverse.of(DirectiveFixtures))

        when:
        stage.run(ctx)

        then:
        ctx.mappings == null
    }

    private ExecutableElement method(final String name) {
        TypeUniverse.of(DirectiveFixtures).enclosedElements.find {
            it.kind == ElementKind.METHOD && it.simpleName.toString() == name
        } as ExecutableElement
    }
}
