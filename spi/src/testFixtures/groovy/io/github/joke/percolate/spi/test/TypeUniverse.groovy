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
    // Symbols whose inheritance closure has already been forced. Mutated only under the TypeUniverse
    // lock (completeClosure is reached only from the synchronized lookup). Declared before the type
    // constants below, which call lookup -> completeClosure during static initialisation.
    private static final Set<String> COMPLETED = [] as Set

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
         'java.util.Optional',
         // Primitive wrappers + Number: the same mid-traversal lazy-load hazard. Conversion strategies
         // (boxing/unboxing/widening) resolve these via Types.boxedClass/unboxedType, so prime their symbols
         // here rather than letting one spec trigger the fill mid-traversal of another's hierarchy walk.
         'java.lang.Number', 'java.lang.Boolean', 'java.lang.Byte', 'java.lang.Short', 'java.lang.Character',
         'java.lang.Integer', 'java.lang.Long', 'java.lang.Float', 'java.lang.Double'].each { fqn ->
            final var elem = ELEMENT_UTILS.getTypeElement(fqn)
            if (elem != null) {
                elem.superclass
                elem.interfaces
                elem.enclosedElements
            }
        }
        // Prime the primitive↔wrapper boxing maps eagerly so getPrimitiveType / boxedClass / unboxedType never
        // trigger a mid-traversal symbol fill from within a spec.
        [TypeKind.BOOLEAN, TypeKind.BYTE, TypeKind.SHORT, TypeKind.CHAR,
         TypeKind.INT, TypeKind.LONG, TypeKind.FLOAT, TypeKind.DOUBLE].each { kind ->
            final var boxed = TYPE_UTILS.boxedClass(TYPE_UTILS.getPrimitiveType(kind))
            TYPE_UTILS.unboxedType(boxed.asType())
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
    static final TypeMirror LIST_OF_INT = TYPE_UTILS.getDeclaredType(lookup('java.util.List'), INTEGER)
    static final TypeMirror LIST_OF_STRING = TYPE_UTILS.getDeclaredType(lookup('java.util.List'), STRING)

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

    static TypeElement element(final String qualifiedName) {
        lookup(qualifiedName)
    }

    /**
     * Resolve a type from a {@link Class} literal through the same javac substrate as {@link #element}. A
     * rename-safe, IDE-tracked alternative to passing a fully-qualified string — preferred for fixture types
     * that exist as compiled classes on the test classpath.
     */
    static TypeElement of(final Class<?> type) {
        lookup(type.canonicalName)
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
                completeClosure(element)
                element
            }
        }
    }

    /**
     * Force javac to fully load a type and its entire supertype / interface / nested-type closure the
     * first time it is resolved, single-threaded and in a controlled order. javac completes symbols
     * lazily; if one load starts while another is mid-flight (getAllMembers walking a supertype closure
     * is the usual trigger), ClassFinder throws "Filling X during Y". That assertion is unconditional —
     * com.sun.tools.javac.util.Assert throws regardless of -ea/-da — so the only cure is to never let a
     * fill start mid-traversal. Priming the whole closure up front makes the symbol table complete
     * before any spec touches it. This generalises the static initialiser's fixed JDK list to every
     * type a test resolves: a record fixture pulls in java.lang.Record, an enum java.lang.Enum, etc.,
     * with no per-fixture additions. The walk follows the inheritance and nesting graph only (not member
     * signatures), which is exactly the closure getAllMembers / Types.closure would otherwise fill lazily.
     */
    private static void completeClosure(final Element element) {
        if (!(element instanceof TypeElement)) {
            return
        }
        final TypeElement type = (TypeElement) element
        if (!COMPLETED.add(completionKey(type))) {
            return
        }
        completeSupertype(type.superclass)
        type.interfaces.each { iface -> completeSupertype(iface) }
        type.enclosedElements.each { member -> completeClosure(member) }
    }

    private static void completeSupertype(final TypeMirror supertype) {
        if (supertype instanceof DeclaredType) {
            completeClosure(((DeclaredType) supertype).asElement())
        }
    }

    private static String completionKey(final TypeElement type) {
        final String qualified = type.qualifiedName as String
        qualified.empty ? type.toString() : qualified
    }
}
