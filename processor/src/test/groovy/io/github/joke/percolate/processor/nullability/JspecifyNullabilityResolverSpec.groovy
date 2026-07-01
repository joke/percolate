package io.github.joke.percolate.processor.nullability

import io.github.joke.percolate.spi.Nullability
import spock.lang.Specification
import spock.lang.Tag

import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.Processor
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.SourceVersion
import javax.lang.model.element.AnnotationMirror
import javax.lang.model.element.Element
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.Name
import javax.lang.model.element.PackageElement
import javax.lang.model.element.TypeElement
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.TypeMirror
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

    // The package-fallback block (getPackageOf) is only reachable at the seam: in a real compile the
    // param -> method -> type -> package enclosing walk visits the package first, shadowing it. These
    // stub the javac model directly so the fallback and its declared-type/no-package edges are covered.

    def 'a package-level @NullMarked with no closer annotation resolves NON_NULL'() {
        def scope = stubScope([])
        def pkg = stubConstruct([annotationMirror('org.jspecify.annotations.NullMarked')])
        def elements = Stub(Elements) { getPackageOf(scope) >> pkg }
        def resolver = new JspecifyNullabilityResolver(NullabilityAnnotations.jspecifyDefaults(), elements)

        expect:
        resolver.resolve(stubType([]), scope) == Nullability.NON_NULL
    }

    def 'a package-level @NullUnmarked with no closer annotation resolves UNKNOWN'() {
        def scope = stubScope([])
        def pkg = stubConstruct([annotationMirror('org.jspecify.annotations.NullUnmarked')])
        def elements = Stub(Elements) { getPackageOf(scope) >> pkg }
        def resolver = new JspecifyNullabilityResolver(NullabilityAnnotations.jspecifyDefaults(), elements)

        expect:
        resolver.resolve(stubType([]), scope) == Nullability.UNKNOWN
    }

    def 'an annotation whose type does not resolve to a TypeElement is ignored'() {
        // a mirror whose annotation element is a non-TypeElement — its FQN is null and it is skipped
        def nonTypeMirror = Stub(AnnotationMirror) {
            getAnnotationType() >> Stub(DeclaredType) { asElement() >> Stub(Element) }
        }
        def scope = stubScope([])
        def pkg = stubConstruct([nonTypeMirror])
        def elements = Stub(Elements) { getPackageOf(scope) >> pkg }
        def resolver = new JspecifyNullabilityResolver(NullabilityAnnotations.jspecifyDefaults(), elements)

        expect:
        resolver.resolve(stubType([]), scope) == Nullability.UNKNOWN
    }

    def 'a scope with no enclosing package resolves UNKNOWN'() {
        def scope = stubScope([])
        def elements = Stub(Elements) { getPackageOf(scope) >> null }
        def resolver = new JspecifyNullabilityResolver(NullabilityAnnotations.jspecifyDefaults(), elements)

        expect:
        resolver.resolve(stubType([]), scope) == Nullability.UNKNOWN
    }

    private Element stubScope(final List<AnnotationMirror> mirrors) {
        Stub(Element) {
            getAnnotationMirrors() >> mirrors
            getEnclosingElement() >> null
        }
    }

    private TypeMirror stubType(final List<AnnotationMirror> mirrors) {
        Stub(TypeMirror) { getAnnotationMirrors() >> mirrors }
    }

    private PackageElement stubConstruct(final List<AnnotationMirror> mirrors) {
        Stub(PackageElement) { getAnnotationMirrors() >> mirrors }
    }

    private AnnotationMirror annotationMirror(final String fqn) {
        def element = Stub(TypeElement) { getQualifiedName() >> Stub(Name) { toString() >> fqn } }
        Stub(AnnotationMirror) { getAnnotationType() >> Stub(DeclaredType) { asElement() >> element } }
    }

    private Nullability resolveParam(
            final JavaFileObject src, final String className, final String method, final NullabilityAnnotations cfg) {
        resolveParamMulti([src], className, method, cfg)
    }

    private Nullability resolveParamMulti(
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

    private JavaFileObject source(final String name, final String code) {
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
