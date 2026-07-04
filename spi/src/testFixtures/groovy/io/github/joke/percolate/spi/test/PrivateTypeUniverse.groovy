package io.github.joke.percolate.spi.test

import com.sun.source.util.JavacTask

import javax.lang.model.element.TypeElement
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.TypeKind
import javax.lang.model.type.TypeMirror
import javax.lang.model.util.Elements
import javax.lang.model.util.Types
import javax.tools.JavaCompiler
import javax.tools.ToolProvider

/**
 * A private, non-shared javac substrate — one fresh {@link JavacTask} per instance, never a static singleton.
 *
 * <p>Some codegen sites are permanently exempt from the owned {@code TypeRef} model (change
 * {@code evict-javax-model}, design D7 amendment): {@code AssembleMapperType}'s override-signature construction
 * and {@code BuildMethodBodies}'s hoisted-local declaration must render a <em>real</em> {@link TypeMirror} via
 * JavaPoet's {@code TypeName.get(TypeMirror)} for exact JLS fidelity. Their specs (unlike the other 14 of the 16
 * {@code @Isolated} specs, which migrate to {@code TestTypes}/literal {@code TypeSpace} fixtures per design D8)
 * therefore cannot become mirror-free — they will always need a real compiler-backed {@link TypeMirror} to
 * exercise the real rendering path they test.
 *
 * <p>{@link TypeUniverse}'s actual hazard was never "real {@code javax.lang.model}" — it was the single
 * <b>static</b> {@link JavacTask} every spec shared, raced by concurrent {@code Types}/{@code Elements} calls
 * under threaded pitest. An instance nobody else touches cannot race: a spec that needs real mirrors gets its
 * own {@code PrivateTypeUniverse} (typically a {@code @Shared} field, created once per spec class), never
 * shared with any other spec, so no synchronization and no cross-spec symbol-table contention are needed.
 */
final class PrivateTypeUniverse {

    private final Types typeUtils
    private final Elements elementUtils
    private final Map<String, TypeElement> elementCache = [:]
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
        elementUtils
    }

    TypeElement element(final String qualifiedName) {
        elementCache.computeIfAbsent(qualifiedName) { name ->
            final found = elementUtils.getTypeElement(name)
            if (found == null) {
                throw new NullPointerException("type not found on classpath: ${name}")
            }
            completeClosure(found)
            found
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
}
