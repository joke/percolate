package io.github.joke.percolate.spi.impl

import com.google.testing.compile.Compilation
import com.google.testing.compile.Compiler
import com.google.testing.compile.JavaFileObjects
import io.github.joke.percolate.processor.PercolateProcessor
import spock.lang.Specification

import static com.google.testing.compile.CompilationSubject.assertThat

class ListMappingStrategySpec extends Specification {

    def "generates List<A> to List<B> conversion via converter method"() {
        given:
        def mapper = JavaFileObjects.forSourceLines('test.EventMapper',
            'package test;',
            'import io.github.joke.percolate.Mapper;',
            '@Mapper',
            'public interface EventMapper {',
            '    EventDto map(Event event);',
            '    ItemDto mapItem(Item item);',
            '}',
        )
        def event = JavaFileObjects.forSourceLines('test.Event',
            'package test;',
            'import java.util.List;',
            'public class Event {',
            '    public String getName() { return ""; }',
            '    public List<Item> getItems() { return null; }',
            '}',
        )
        def eventDto = JavaFileObjects.forSourceLines('test.EventDto',
            'package test;',
            'import java.util.List;',
            'public class EventDto {',
            '    public final String name;',
            '    public final List<ItemDto> items;',
            '    public EventDto(String name, List<ItemDto> items) {',
            '        this.name = name;',
            '        this.items = items;',
            '    }',
            '}',
        )
        def item = JavaFileObjects.forSourceLines('test.Item',
            'package test;',
            'public class Item {',
            '    public String getTitle() { return ""; }',
            '}',
        )
        def itemDto = JavaFileObjects.forSourceLines('test.ItemDto',
            'package test;',
            'public class ItemDto {',
            '    public final String title;',
            '    public ItemDto(String title) {',
            '        this.title = title;',
            '    }',
            '}',
        )

        when:
        Compilation compilation = Compiler.javac()
            .withProcessors(new PercolateProcessor())
            .compile(mapper, event, eventDto, item, itemDto)

        then:
        assertThat(compilation).succeeded()
        assertThat(compilation)
            .generatedSourceFile('test.EventMapperImpl')
            .contentsAsUtf8String()
            .contains('event.getItems().stream().map(this::mapItem).collect(java.util.stream.Collectors.toList())')
    }
}
