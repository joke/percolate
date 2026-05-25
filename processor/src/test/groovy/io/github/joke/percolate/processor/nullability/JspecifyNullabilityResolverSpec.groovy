package io.github.joke.percolate.processor.nullability

import io.github.joke.percolate.spi.Nullability
import spock.lang.Specification
import spock.lang.Tag

import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.Processor
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.SourceVersion
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.TypeElement
import javax.lang.model.util.Elements
import javax.tools.JavaCompiler
import javax.tools.JavaFileObject
import javax.tools.SimpleJavaFileObject
import javax.tools.ToolProvider
import java.util.concurrent.atomic.AtomicReference

@Tag('unit')
class JspecifyNullabilityResolverSpec extends Specification {

    def 'direct @Nullable on a parameter type returns NULLABLE'() {
        given:
        def src = source('test.Holder', '''
                package test;
                public class Holder {
                    public void consume(final @org.jspecify.annotations.Nullable String s) {}
                }''')

        expect:
        resolveParam(src, 'test.Holder', 'consume', NullabilityAnnotations.jspecifyDefaults()) == Nullability.NULLABLE
    }

    def '@NullMarked on enclosing class returns NON_NULL for un-annotated parameter'() {
        given:
        def src = source('test.Holder', '''
                package test;
                @org.jspecify.annotations.NullMarked
                public class Holder {
                    public void consume(final String s) {}
                }''')

        expect:
        resolveParam(src, 'test.Holder', 'consume', NullabilityAnnotations.jspecifyDefaults()) == Nullability.NON_NULL
    }

    def '@NullUnmarked nested inside @NullMarked returns UNKNOWN'() {
        given:
        def src = source('test.Holder', '''
                package test;
                @org.jspecify.annotations.NullMarked
                public class Holder {
                    @org.jspecify.annotations.NullUnmarked
                    public void consume(final String s) {}
                }''')

        expect:
        resolveParam(src, 'test.Holder', 'consume', NullabilityAnnotations.jspecifyDefaults()) == Nullability.UNKNOWN
    }

    def 'no annotation anywhere returns UNKNOWN'() {
        given:
        def src = source('test.Holder', '''
                package test;
                public class Holder {
                    public void consume(final String s) {}
                }''')

        expect:
        resolveParam(src, 'test.Holder', 'consume', NullabilityAnnotations.jspecifyDefaults()) == Nullability.UNKNOWN
    }

    def 'custom @Nullable FQN from configuration is detected'() {
        given:
        def annotation = source('test.CustomNullable', '''
                package test;
                @java.lang.annotation.Target({java.lang.annotation.ElementType.PARAMETER,
                                              java.lang.annotation.ElementType.TYPE_USE})
                @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
                public @interface CustomNullable {}''')
        def holder = source('test.Holder', '''
                package test;
                public class Holder {
                    public void consume(final @CustomNullable String s) {}
                }''')
        def custom = new NullabilityAnnotations(
                ['org.jspecify.annotations.Nullable', 'test.CustomNullable'] as Set,
                ['org.jspecify.annotations.NullMarked'] as Set,
                ['org.jspecify.annotations.NullUnmarked'] as Set)

        expect:
        resolveParamMulti([annotation, holder], 'test.Holder', 'consume', custom) == Nullability.NULLABLE
    }

    private static Nullability resolveParam(
            final JavaFileObject src, final String className, final String method, final NullabilityAnnotations cfg) {
        resolveParamMulti([src], className, method, cfg)
    }

    private static Nullability resolveParamMulti(
            final List<JavaFileObject> srcs, final String className, final String method, final NullabilityAnnotations cfg) {
        final result = new AtomicReference<Nullability>()
        final Processor processor = new ProbeProcessor(className, method, cfg, result)
        final JavaCompiler compiler = ToolProvider.systemJavaCompiler
        final task = compiler.getTask(null, null, null, ['-proc:only'], null, srcs)
        task.processors = [processor]
        if (!task.call()) {
            throw new IllegalStateException('compilation failed')
        }
        result.get() ?: { throw new IllegalStateException('resolver did not run') }()
    }

    private static JavaFileObject source(final String name, final String code) {
        new SimpleJavaFileObject(URI.create('string:///' + name.replace('.', '/') + '.java'),
                JavaFileObject.Kind.SOURCE) {
            @Override
            CharSequence getCharContent(final boolean ignoreEncodingErrors) {
                code
            }
        }
    }

    private static final class ProbeProcessor extends AbstractProcessor {
        private final String className
        private final String methodName
        private final NullabilityAnnotations cfg
        private final AtomicReference<Nullability> result

        ProbeProcessor(final String c, final String m, final NullabilityAnnotations cfg, final AtomicReference<Nullability> r) {
            this.className = c
            this.methodName = m
            this.cfg = cfg
            this.result = r
        }

        @Override
        boolean process(final Set<? extends TypeElement> annotations, final RoundEnvironment env) {
            if (env.processingOver()) {
                return false
            }
            final Elements elements = processingEnv.elementUtils
            final TypeElement holder = elements.getTypeElement(className)
            if (holder == null) {
                return false
            }
            final ExecutableElement target = holder.enclosedElements.find {
                it.simpleName.contentEquals(methodName) && it instanceof ExecutableElement
            } as ExecutableElement
            final param = target.parameters[0]
            final resolver = new JspecifyNullabilityResolver(cfg, elements)
            result.set(resolver.resolve(param.asType(), param))
            true
        }

        @Override
        Set<String> getSupportedAnnotationTypes() {
            ['*'] as Set
        }

        @Override
        SourceVersion getSupportedSourceVersion() {
            SourceVersion.latestSupported()
        }
    }
}
