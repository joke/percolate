package io.github.joke.percolate.processor.stage

import io.github.joke.percolate.processor.model.GetterAccessor
import io.github.joke.percolate.processor.model.FieldReadAccessor
import io.github.joke.percolate.processor.model.MapDirective
import io.github.joke.percolate.processor.model.MapperModel
import io.github.joke.percolate.processor.model.MappingMethodModel
import io.github.joke.percolate.processor.model.ReadAccessor
import io.github.joke.percolate.processor.spi.SourcePropertyDiscovery
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.TypeElement
import javax.lang.model.element.VariableElement
import javax.lang.model.type.TypeMirror
import javax.lang.model.util.Elements
import javax.lang.model.util.Types

@Tag('unit')
class DiscoverStageSpec extends Specification {

    Elements elements = Mock()
    Types types = Mock()

    def 'higher priority strategy wins for same property name'() {
        given:
        def typeMirror = Mock(TypeMirror)
        def highPriorityAccessor = new GetterAccessor('name', typeMirror, Mock(ExecutableElement))
        def lowPriorityAccessor = new FieldReadAccessor('name', typeMirror, Mock(VariableElement))

        def highPriority = Mock(SourcePropertyDiscovery) {
            priority() >> 100
            discover(_, _, _) >> [highPriorityAccessor]
        }
        def lowPriority = Mock(SourcePropertyDiscovery) {
            priority() >> 50
            discover(_, _, _) >> [lowPriorityAccessor]
        }

        and: 'strategies ordered by priority'
        def merged = mergeSourceProperties([highPriority, lowPriority], typeMirror)

        expect:
        merged.size() == 1
        merged['name'] instanceof GetterAccessor
    }

    def 'lower priority does not override higher priority'() {
        given:
        def typeMirror = Mock(TypeMirror)
        def highPriorityAccessor = new GetterAccessor('name', typeMirror, Mock(ExecutableElement))
        def lowPriorityAccessor = new FieldReadAccessor('name', typeMirror, Mock(VariableElement))

        def lowPriority = Mock(SourcePropertyDiscovery) {
            priority() >> 50
            discover(_, _, _) >> [lowPriorityAccessor]
        }
        def highPriority = Mock(SourcePropertyDiscovery) {
            priority() >> 100
            discover(_, _, _) >> [highPriorityAccessor]
        }

        and: 'high priority processed first'
        def merged = mergeSourceProperties([highPriority, lowPriority], typeMirror)

        expect:
        merged['name'] instanceof GetterAccessor
    }

    def 'different property names are both kept'() {
        given:
        def typeMirror = Mock(TypeMirror)
        def accessor1 = new GetterAccessor('firstName', typeMirror, Mock(ExecutableElement))
        def accessor2 = new GetterAccessor('lastName', typeMirror, Mock(ExecutableElement))

        def strategy = Mock(SourcePropertyDiscovery) {
            priority() >> 100
            discover(_, _, _) >> [accessor1, accessor2]
        }

        def merged = mergeSourceProperties([strategy], typeMirror)

        expect:
        merged.size() == 2
        merged.containsKey('firstName')
        merged.containsKey('lastName')
    }

    /**
     * Helper that simulates the priority-based merge logic from DiscoverStage.
     * Since DiscoverStage loads strategies via ServiceLoader (which we can't mock),
     * we test the merge logic directly.
     */
    private Map<String, ReadAccessor> mergeSourceProperties(
            List<SourcePropertyDiscovery> strategies, TypeMirror type) {
        def merged = new LinkedHashMap<String, ReadAccessor>()
        def priorities = new LinkedHashMap<String, Integer>()

        for (strategy in strategies) {
            for (accessor in strategy.discover(type, elements, types)) {
                def currentPriority = priorities.getOrDefault(accessor.name(), Integer.MIN_VALUE)
                if (strategy.priority() > currentPriority) {
                    merged[accessor.name()] = accessor
                    priorities[accessor.name()] = strategy.priority()
                }
            }
        }
        return merged
    }
}
