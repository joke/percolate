package io.github.joke.percolate.processor

import com.google.auto.common.BasicAnnotationProcessor.Step
import com.google.testing.compile.Compilation
import com.google.testing.compile.JavaFileObjects
import spock.lang.Specification
import spock.lang.Tag

import java.lang.reflect.Field

import static com.google.testing.compile.CompilationSubject.assertThat
import static com.google.testing.compile.Compiler.javac

@Tag('integration')
class PercolateProcessorSpec extends Specification {

    def 'processes @Mapper annotated interface without errors'() {
        given:
        def source = JavaFileObjects.forSourceString('test.TestMapper', '''
            import io.github.joke.percolate.Mapper;

            @Mapper
            public interface TestMapper {
            }
        ''')

        when:
        Compilation compilation = javac()
                .withProcessors(new PercolateProcessor())
                .compile(source)

        then:
        compilation.status() == Compilation.Status.SUCCESS
    }

    def 'processor covers branch when component is already initialized'() {
        given:
        def processor = new PercolateProcessor()
        def component = Mock(ProcessorComponent)
        def mapperStep = Mock(MapperStep)
        component.mapperStep() >> mapperStep

        when:
        Field componentField = PercolateProcessor.class.getDeclaredField('component')
        componentField.setAccessible(true)
        componentField.set(processor, component)
        def steps = processor.steps()

        then:
        steps.size() == 1
        componentField.get(processor) == component
    }
}
