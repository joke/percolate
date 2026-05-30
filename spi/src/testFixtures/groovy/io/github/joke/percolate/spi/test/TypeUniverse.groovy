package io.github.joke.percolate.spi.test

import com.sun.source.util.JavacTask

import javax.lang.model.element.*
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.TypeKind
import javax.lang.model.type.TypeMirror
import javax.lang.model.util.Elements
import javax.lang.model.util.Types
import javax.tools.JavaCompiler
import javax.tools.ToolProvider
import java.util.concurrent.ConcurrentHashMap

final class TypeUniverse {

    private static final JavacTask JAVAC_TASK = createTask()
    private static final Types TYPE_UTILS = JAVAC_TASK.types
    private static final Elements ELEMENT_UTILS = JAVAC_TASK.elements
    private static final Elements SYNCHRONIZED_ELEMENTS = new SynchronizedElements(ELEMENT_UTILS)
    private static final Map<String, TypeElement> ELEMENT_CACHE = new ConcurrentHashMap<>()

    static {
        // On Java 21+, `java.util.Collection` extends `SequencedCollection`. javac
        // lazy-loads classes on first use, and if it starts filling Collection (e.g.
        // via a type-hierarchy traversal) before SequencedCollection has ever been
        // referenced, it hits an internal re-entrancy assertion. Preloading
        // SequencedCollection (and the Sequenced* siblings) primes the symbol table
        // so the recursive load chain never starts mid-traversal. We also eagerly
        // walk the supertype chain for collection types and the common JDK base
        // types so subsequent isAssignable / isSubtype calls do not trigger
        // mid-traversal class filling.
        ['java.util.SequencedCollection', 'java.util.SequencedSet', 'java.util.SequencedMap',
         'java.lang.Object', 'java.lang.Iterable',
         'java.util.Collection', 'java.util.List', 'java.util.Set', 'java.util.Map',
         'java.util.Optional'].each { fqn ->
            final var elem = ELEMENT_UTILS.getTypeElement(fqn)
            if (elem != null) {
                elem.superclass
                elem.interfaces
                elem.enclosedElements
            }
        }
        // Force the supertype-assignability fill eagerly. javac fills the List/Set -> ... -> Iterable
        // hierarchy lazily on first isAssignable; priming it here (rather than mid-test) makes isIterable /
        // isCollection deterministic regardless of which spec touches the symbol table first.
        final var iterable = ELEMENT_UTILS.getTypeElement('java.lang.Iterable')
        if (iterable != null) {
            ['java.util.List', 'java.util.Set', 'java.util.Collection'].each { fqn ->
                final var elem = ELEMENT_UTILS.getTypeElement(fqn)
                if (elem != null) {
                    TYPE_UTILS.isAssignable(TYPE_UTILS.erasure(elem.asType()), TYPE_UTILS.erasure(iterable.asType()))
                }
            }
        }
    }

    static final TypeMirror INT = TYPE_UTILS.getPrimitiveType(TypeKind.INT)
    static final TypeMirror LONG = TYPE_UTILS.getPrimitiveType(TypeKind.LONG)
    static final TypeMirror INTEGER = lookup('java.lang.Integer').asType()
    static final TypeMirror LONG_TYPE = lookup('java.lang.Long').asType()
    static final TypeMirror STRING = lookup('java.lang.String').asType()
    static final TypeMirror DAY_OF_WEEK = lookup('java.time.DayOfWeek').asType()
    static final TypeMirror LOCAL_DATE_TIME = lookup('java.time.LocalDateTime').asType()
    static final TypeMirror INSTANT = lookup('java.time.Instant').asType()
    static final TypeMirror LIST_OF_INT = TYPE_UTILS.getDeclaredType(lookup('java.util.List'), INTEGER)
    static final TypeMirror LIST_OF_STRING = TYPE_UTILS.getDeclaredType(lookup('java.util.List'), STRING)

    private static final List<TypeMirror> TYPE_POOL = [
            INT, LONG, INTEGER, LONG_TYPE, STRING, DAY_OF_WEEK, LOCAL_DATE_TIME, INSTANT, LIST_OF_INT, LIST_OF_STRING
    ].asImmutable()

    private TypeUniverse() {}

    static Types types() {
        TYPE_UTILS
    }

    static Elements elements() {
        SYNCHRONIZED_ELEMENTS
    }

    private static final class SynchronizedElements implements Elements {
        private final Elements delegate

        SynchronizedElements(final Elements d) {
            this.delegate = d
        }

        
        TypeElement getTypeElement(final CharSequence qualifiedName) {
            synchronized (TypeUniverse) { delegate.getTypeElement(qualifiedName) }
        }

        
        List<? extends Element> getAllMembers(final TypeElement classElement) {
            synchronized (TypeUniverse) { delegate.getAllMembers(classElement) }
        }

        
        List<? extends Element> getEnclosedElements(final Element element) {
            synchronized (TypeUniverse) { delegate.getEnclosedElements(element) }
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
            synchronized (TypeUniverse) { delegate.getElementValuesWithDefaults(annotation) }
        }

        String getDocComment(final Element element) {
            synchronized (TypeUniverse) { delegate.getDocComment(element) }
        }

        boolean isDeprecated(final Element element) {
            synchronized (TypeUniverse) { delegate.isDeprecated(element) }
        }

        Name getBinaryName(final TypeElement typeElement) {
            synchronized (TypeUniverse) { delegate.getBinaryName(typeElement) }
        }

        PackageElement getPackageOf(final Element element) {
            synchronized (TypeUniverse) { delegate.getPackageOf(element) }
        }

        List<? extends AnnotationMirror> getAllAnnotationMirrors(final Element element) {
            synchronized (TypeUniverse) { delegate.getAllAnnotationMirrors(element) }
        }
    }

    static List<TypeMirror> pool() {
        TYPE_POOL
    }

    static TypeElement element(final String qualifiedName) {
        lookup(qualifiedName)
    }

    /**
     * Convenience placeholder for tests that need an AnnotatedConstruct (Slot.producedFrom,
     * ResolvedSegment.producedFrom) but do not care about its nullability annotations.
     * Returns a real TypeElement that carries no JSpecify annotations.
     */
    static javax.lang.model.AnnotatedConstruct anyConstruct() {
        lookup('java.lang.String')
    }

    private static JavacTask createTask() {
        final JavaCompiler compiler = ToolProvider.systemJavaCompiler
        Objects.requireNonNull(compiler, 'no system Java compiler available; JDK required at runtime')
        final var classpath = System.getProperty('java.class.path')
        (JavacTask) compiler.getTask(null, null, null, ['-cp', classpath], null, null)
    }

    private static TypeElement lookup(final String qualifiedName) {
        synchronized (TypeUniverse) {
            ELEMENT_CACHE.computeIfAbsent(qualifiedName) { name ->
                final element = ELEMENT_UTILS.getTypeElement(name)
                if (element == null) {
                    throw new NullPointerException("type not found on classpath: ${name}")
                }
                element
            }
        }
    }
}
