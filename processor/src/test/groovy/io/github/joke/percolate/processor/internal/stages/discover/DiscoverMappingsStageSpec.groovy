package io.github.joke.percolate.processor.internal.stages.discover

import io.github.joke.percolate.processor.MapperContext
import io.github.joke.percolate.processor.internal.graph.MethodScope
import io.github.joke.percolate.processor.model.MapperShape
import io.github.joke.percolate.processor.test.fixtures.DirectiveFixtures
import io.github.joke.percolate.processor.test.fixtures.Human
import io.github.joke.percolate.processor.test.fixtures.Person
import io.github.joke.percolate.spi.test.PrivateTypeUniverse
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.ElementKind
import javax.lang.model.element.ExecutableElement

/**
 * {@link DiscoverMappingsStage} seam, unit-tested directly: the {@code @Map}/{@code @MapList} mirrors are read off the
 * compiled {@code DirectiveFixtures} type via {@link PrivateTypeUniverse} (CLASS-retained, so present in bytecode —
 * no annotation-processing round). Presence of {@code source}/{@code constant}/{@code defaultValue} is decided
 * against the {@code Map.UNSET} sentinel with the annotation's declared defaults; an empty string is present, not
 * absent.
 */
@Tag('unit')
class DiscoverMappingsStageSpec extends Specification {

    @Shared PrivateTypeUniverse javac = new PrivateTypeUniverse()
    @Shared DiscoverMappingsStage stage = new DiscoverMappingsStage(javac.elements())

    def setupSpec() {
        // prime the fixture + its methods' parameter/return closures single-threaded (see ExpandStageDriverSpec)
        javac.of(Person)
        javac.of(Human)
        javac.of(DirectiveFixtures)
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

    def 'a format directive is discovered with format present and zone absent'() {
        when:
        def directive = stage.extractDirectives(method('formatted').annotationMirrors)[0]

        then:
        directive.hasFormat()
        directive.format == 'yyyy-MM-dd'
        !directive.hasZone()
        directive.zone == null
    }

    def 'a zone directive is discovered with zone present'() {
        when:
        def directive = stage.extractDirectives(method('zoned').annotationMirrors)[0]

        then:
        directive.hasZone()
        directive.zone == 'Europe/Berlin'
    }

    def 'absent format and zone are reported absent'() {
        when:
        def directive = stage.extractDirectives(method('sourceWithDefault').annotationMirrors)[0]

        then:
        !directive.hasFormat()
        !directive.hasZone()
        directive.format == null
        directive.zone == null
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
        def ctx = new MapperContext(javac.of(DirectiveFixtures))
        ctx.shape = new MapperShape(javac.of(DirectiveFixtures), [method('repeated')])

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
        def ctx = new MapperContext(javac.of(DirectiveFixtures))

        when:
        stage.run(ctx)

        then:
        ctx.mappings == null
    }

    private ExecutableElement method(final String name) {
        javac.of(DirectiveFixtures).enclosedElements.find {
            it.kind == ElementKind.METHOD && it.simpleName.toString() == name
        } as ExecutableElement
    }
}
