package io.github.joke.percolate.processor.test

import com.sun.source.util.JavacTask

import javax.lang.model.element.TypeElement
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
    private static final Map<String, TypeElement> ELEMENT_CACHE = new ConcurrentHashMap<>()

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
        ELEMENT_UTILS
    }

    static List<TypeMirror> pool() {
        TYPE_POOL
    }

    static TypeElement element(final String qualifiedName) {
        lookup(qualifiedName)
    }

    private static JavacTask createTask() {
        final JavaCompiler compiler = ToolProvider.systemJavaCompiler
        Objects.requireNonNull(compiler, 'no system Java compiler available; JDK required at runtime')
        (JavacTask) compiler.getTask(null, null, null, null, null, null)
    }

    private static TypeElement lookup(final String qualifiedName) {
        ELEMENT_CACHE.computeIfAbsent(qualifiedName) { name ->
            synchronized (ELEMENT_UTILS) {
                final element = ELEMENT_UTILS.getTypeElement(name)
                if (element == null) {
                    throw new NullPointerException("type not found on classpath: ${name}")
                }
                element
            }
        }
    }
}
