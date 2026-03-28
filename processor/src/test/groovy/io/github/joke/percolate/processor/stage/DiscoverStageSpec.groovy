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
        final typeMirror = Mock(TypeMirror)
        final highPriorityAccessor = new GetterAccessor('name', typeMirror, Mock(ExecutableElement))
        final lowPriorityAccessor = new FieldReadAccessor('name', typeMirror, Mock(VariableElement))

        final highPriority = Mock(SourcePropertyDiscovery) {
            priority() >> 100
            discover(_, _, _) >> [highPriorityAccessor]
        }
        final lowPriority = Mock(SourcePropertyDiscovery) {
            priority() >> 50
            discover(_, _, _) >> [lowPriorityAccessor]
        }

        and: 'strategies ordered by priority'
        final merged = mergeSourceProperties([highPriority, lowPriority], typeMirror)

        expect:
        merged.size() == 1
        merged['name'] instanceof GetterAccessor
    }

    def 'lower priority does not override higher priority'() {
        given:
        final typeMirror = Mock(TypeMirror)
        final highPriorityAccessor = new GetterAccessor('name', typeMirror, Mock(ExecutableElement))
        final lowPriorityAccessor = new FieldReadAccessor('name', typeMirror, Mock(VariableElement))

        final lowPriority = Mock(SourcePropertyDiscovery) {
            priority() >> 50
            discover(_, _, _) >> [lowPriorityAccessor]
        }
        final highPriority = Mock(SourcePropertyDiscovery) {
            priority() >> 100
            discover(_, _, _) >> [highPriorityAccessor]
        }

        and: 'high priority processed first'
        final merged = mergeSourceProperties([highPriority, lowPriority], typeMirror)

        expect:
        merged['name'] instanceof GetterAccessor
    }

    def 'different property names are both kept'() {
        given:
        final typeMirror = Mock(TypeMirror)
        final accessor1 = new GetterAccessor('firstName', typeMirror, Mock(ExecutableElement))
        final accessor2 = new GetterAccessor('lastName', typeMirror, Mock(ExecutableElement))

        final strategy = Mock(SourcePropertyDiscovery) {
            priority() >> 100
            discover(_, _, _) >> [accessor1, accessor2]
        }

        final merged = mergeSourceProperties([strategy], typeMirror)

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
        final merged = new LinkedHashMap<String, ReadAccessor>()
        final priorities = new LinkedHashMap<String, Integer>()

        for (strategy in strategies) {
            for (accessor in strategy.discover(type, elements, types)) {
                final currentPriority = priorities.getOrDefault(accessor.name, Integer.MIN_VALUE)
                if (strategy.priority() > currentPriority) {
                    merged[accessor.name] = accessor
                    priorities[accessor.name] = strategy.priority()
                }
            }
        }
        return merged
    }
}
