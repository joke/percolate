package io.github.joke.percolate.spi.test

import com.sun.source.util.JavacTask

import javax.lang.model.element.AnnotationMirror
import javax.lang.model.element.AnnotationValue
import javax.lang.model.element.Element
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.ModuleElement
import javax.lang.model.element.Name
import javax.lang.model.element.PackageElement
import javax.lang.model.element.TypeElement
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.TypeKind
import javax.lang.model.type.TypeMirror
import javax.lang.model.util.Elements
import javax.lang.model.util.Types
import javax.tools.JavaCompiler
import javax.tools.ToolProvider
import java.util.concurrent.ConcurrentHashMap

/**
 * A private, non-shared javac substrate — one fresh {@link JavacTask} per instance, never a static singleton.
 *
 * <p>Some codegen sites genuinely need a real compiler-backed {@link TypeMirror}: {@code AssembleMapperType}'s
 * override-signature construction and {@code BuildMethodBodies}'s hoisted-local declaration render via JavaPoet's
 * {@code TypeName.get(TypeMirror)} for exact JLS fidelity, so their specs will always need a real javac substrate
 * rather than a mocked type-query seam. Under the {@code type-query-seam} change this fixture is kept
 * transitionally for those specs (and the strategy specs deferred to {@code features-as-documentation}); the
 * {@code processor}/{@code spi} unit specs move to a mocked {@code ResolveCtx} instead.
 *
 * <p>Unlike {@link TypeUniverse}'s <b>static</b> {@link JavacTask} shared by every spec in the JVM, each instance
 * here is exclusive to one spec class (typically a {@code @Shared} field, created once per spec class) — no
 * cross-spec symbol-table contention. But a single spec class routinely drives its own instance from several
 * feature methods, and javac's lazy symbol completion is not reentrant-safe under real concurrent execution
 * (Spock's parallel runner, or a threaded pitest minion): the same synchronized-lookup and
 * {@code SynchronizedElements} guard {@link TypeUniverse} uses is required here too, just keyed per-instance
 * instead of per-class.
 */
final class PrivateTypeUniverse {

    private final Types typeUtils
    private final Elements elementUtils
    private final Elements synchronizedElements
    private final Map<String, TypeElement> elementCache = new ConcurrentHashMap<>()
    private final Set<String> completed = [] as Set

    final TypeMirror INT
    final TypeMirror LONG
    final TypeMirror INTEGER
    final TypeMirror LONG_TYPE
    final TypeMirror STRING
    final TypeMirror DAY_OF_WEEK
    final TypeMirror LIST_OF_INT
    final TypeMirror LIST_OF_STRING

    PrivateTypeUniverse() {
        final JavacTask task = createTask()
        typeUtils = task.types
        elementUtils = task.elements
        synchronizedElements = new SynchronizedElements(elementUtils)
        primeJdkQuirks()

        INT = typeUtils.getPrimitiveType(TypeKind.INT)
        LONG = typeUtils.getPrimitiveType(TypeKind.LONG)
        INTEGER = element('java.lang.Integer').asType()
        LONG_TYPE = element('java.lang.Long').asType()
        STRING = element('java.lang.String').asType()
        DAY_OF_WEEK = element('java.time.DayOfWeek').asType()
        LIST_OF_INT = typeUtils.getDeclaredType(element('java.util.List'), INTEGER)
        LIST_OF_STRING = typeUtils.getDeclaredType(element('java.util.List'), STRING)
    }

    Types types() {
        typeUtils
    }

    Elements elements() {
        synchronizedElements
    }

    /**
     * A single spec class routinely drives one {@code PrivateTypeUniverse} from several feature methods; under
     * Spock's parallel runner (or a threaded pitest minion) those calls can overlap. javac's lazy symbol
     * completion is not reentrant-safe, so the whole lookup — cache check plus the completion walk it triggers —
     * is serialized on this instance, mirroring {@link TypeUniverse#lookup}.
     */
    TypeElement element(final String qualifiedName) {
        synchronized (this) {
            elementCache.computeIfAbsent(qualifiedName) { name ->
                final found = elementUtils.getTypeElement(name)
                if (found == null) {
                    throw new NullPointerException("type not found on classpath: ${name}")
                }
                completeClosure(found)
                found
            }
        }
    }

    /** Resolve a type from a {@link Class} literal — a rename-safe, IDE-tracked alternative to a raw string. */
    TypeElement of(final Class<?> type) {
        element(type.canonicalName)
    }

    private JavacTask createTask() {
        final JavaCompiler compiler = ToolProvider.systemJavaCompiler
        Objects.requireNonNull(compiler, 'no system Java compiler available; JDK required at runtime')
        final classpath = System.getProperty('java.class.path')
        (JavacTask) compiler.getTask(null, null, null, ['-cp', classpath], null, null)
    }

    private String completionKey(final TypeElement type) {
        final qualified = type.qualifiedName as String
        qualified.empty ? type.toString() : qualified
    }

    /**
     * On Java 21+, {@code java.util.Collection} extends {@code SequencedCollection}; javac lazy-loads classes on
     * first use and hits an internal re-entrancy assertion if one fill starts while a related one is still
     * mid-flight (see {@link TypeUniverse}'s identical priming, which this mirrors per-instance). Unrelated to
     * sharing — a quirk of javac's own lazy-completion order — so a private task still needs it once, up front.
     */
    private void primeJdkQuirks() {
        ['java.util.SequencedCollection', 'java.util.SequencedSet', 'java.util.SequencedMap',
         'java.lang.Object', 'java.lang.Iterable',
         'java.util.Collection', 'java.util.List', 'java.util.Set', 'java.util.Map',
         'java.util.Optional',
         'java.lang.Number', 'java.lang.Boolean', 'java.lang.Byte', 'java.lang.Short', 'java.lang.Character',
         'java.lang.Integer', 'java.lang.Long', 'java.lang.Float', 'java.lang.Double'].each { fqn ->
            final elem = elementUtils.getTypeElement(fqn)
            if (elem != null) {
                elem.superclass
                elem.interfaces
                elem.enclosedElements
            }
        }
        [TypeKind.BOOLEAN, TypeKind.BYTE, TypeKind.SHORT, TypeKind.CHAR,
         TypeKind.INT, TypeKind.LONG, TypeKind.FLOAT, TypeKind.DOUBLE].each { kind ->
            final boxed = typeUtils.boxedClass(typeUtils.getPrimitiveType(kind))
            typeUtils.unboxedType(boxed.asType())
        }
        final iterable = elementUtils.getTypeElement('java.lang.Iterable')
        if (iterable != null) {
            ['java.util.List', 'java.util.Set', 'java.util.Collection'].each { fqn ->
                final elem = elementUtils.getTypeElement(fqn)
                if (elem != null) {
                    typeUtils.isAssignable(typeUtils.erasure(elem.asType()), typeUtils.erasure(iterable.asType()))
                }
            }
        }
    }

    /** Force the declared/nested-type closure eagerly, single-threaded — see {@link TypeUniverse}'s identical logic. */
    private void completeClosure(final TypeElement type) {
        if (!completed.add(completionKey(type))) {
            return
        }
        completeSupertype(type.superclass)
        type.interfaces.each { iface -> completeSupertype(iface) }
        type.enclosedElements.each { member ->
            if (member instanceof TypeElement) {
                completeClosure(member)
            }
        }
    }

    private void completeSupertype(final TypeMirror supertype) {
        if (supertype instanceof DeclaredType) {
            completeClosure((TypeElement) ((DeclaredType) supertype).asElement())
        }
    }

    /** Instance-scoped mirror of {@link TypeUniverse.SynchronizedElements}, guarding on the outer instance. */
    private final class SynchronizedElements implements Elements {
        private final Elements delegate

        SynchronizedElements(final Elements d) {
            this.delegate = d
        }

        TypeElement getTypeElement(final CharSequence qualifiedName) {
            synchronized (PrivateTypeUniverse.this) { delegate.getTypeElement(qualifiedName) }
        }

        List<? extends Element> getAllMembers(final TypeElement classElement) {
            synchronized (PrivateTypeUniverse.this) { delegate.getAllMembers(classElement) }
        }

        List<? extends Element> getEnclosedElements(final Element element) {
            synchronized (PrivateTypeUniverse.this) { delegate.getEnclosedElements(element) }
        }

        Set<? extends PackageElement> getPackages() {
            delegate.packages()
        }

        PackageElement getPackageElement(final CharSequence qualifiedName) {
            delegate.getPackageElement(qualifiedName)
        }

        Name getName(final CharSequence sequence) {
            delegate.getName(sequence)
        }

        boolean isFunctionalInterface(final TypeElement typeElement) {
            delegate.isFunctionalInterface(typeElement)
        }

        void printElements(final Writer writer, final Element... elements) {
            delegate.printElements(writer, elements)
        }

        Element getNoElement() {
            delegate.noElement()
        }

        boolean hides(final Element hiding, final Element hidden) {
            delegate.hides(hiding, hidden)
        }

        boolean overrides(final ExecutableElement method, final ExecutableElement overriden, final TypeElement type) {
            delegate.overrides(method, overriden, type)
        }

        String getConstantExpression(final Object value) {
            delegate.getConstantExpression(value)
        }

        ModuleElement getModuleElement(final CharSequence moduleName) {
            delegate.getModuleElement(moduleName)
        }

        Element getBinaryElement(final TypeElement classElement, final CharSequence flatName) {
            delegate.getBinaryElement(classElement, flatName)
        }

        Element getElement(final DeclaredType declaredType, final TypeElement... typeParameters) {
            delegate.getElement(declaredType, typeParameters)
        }

        Element getUnknownModuleElement(final String moduleName) {
            delegate.getUnknownModuleElement(moduleName)
        }

        Map<? extends AnnotationMirror, ? extends AnnotationValue> getElementValuesWithDefaults(final AnnotationMirror annotation) {
            synchronized (PrivateTypeUniverse.this) { delegate.getElementValuesWithDefaults(annotation) }
        }

        String getDocComment(final Element element) {
            synchronized (PrivateTypeUniverse.this) { delegate.getDocComment(element) }
        }

        boolean isDeprecated(final Element element) {
            synchronized (PrivateTypeUniverse.this) { delegate.isDeprecated(element) }
        }

        Name getBinaryName(final TypeElement typeElement) {
            synchronized (PrivateTypeUniverse.this) { delegate.getBinaryName(typeElement) }
        }

        PackageElement getPackageOf(final Element element) {
            synchronized (PrivateTypeUniverse.this) { delegate.getPackageOf(element) }
        }

        List<? extends AnnotationMirror> getAllAnnotationMirrors(final Element element) {
            synchronized (PrivateTypeUniverse.this) { delegate.getAllAnnotationMirrors(element) }
        }
    }
}
