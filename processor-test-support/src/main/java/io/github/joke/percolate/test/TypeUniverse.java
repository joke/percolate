package io.github.joke.percolate.test;

import com.sun.source.util.JavacTask;
import java.util.List;
import java.util.Objects;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

public final class TypeUniverse {

    private static final JavacTask JAVAC_TASK = createTask();
    private static final Types TYPE_UTILS = JAVAC_TASK.getTypes();
    private static final Elements ELEMENT_UTILS = JAVAC_TASK.getElements();

    public static final TypeMirror INT = TYPE_UTILS.getPrimitiveType(TypeKind.INT);
    public static final TypeMirror LONG = TYPE_UTILS.getPrimitiveType(TypeKind.LONG);
    public static final TypeMirror INTEGER = lookup("java.lang.Integer").asType();
    public static final TypeMirror LONG_TYPE = lookup("java.lang.Long").asType();
    public static final TypeMirror STRING = lookup("java.lang.String").asType();
    public static final TypeMirror DAY_OF_WEEK = lookup("java.time.DayOfWeek").asType();
    public static final TypeMirror LOCAL_DATE_TIME =
            lookup("java.time.LocalDateTime").asType();
    public static final TypeMirror INSTANT = lookup("java.time.Instant").asType();
    public static final TypeMirror LIST_OF_INT = TYPE_UTILS.getDeclaredType(lookup("java.util.List"), INTEGER);
    public static final TypeMirror LIST_OF_STRING = TYPE_UTILS.getDeclaredType(lookup("java.util.List"), STRING);

    private static final List<TypeMirror> TYPE_POOL = List.of(
            INT, LONG, INTEGER, LONG_TYPE, STRING, DAY_OF_WEEK, LOCAL_DATE_TIME, INSTANT, LIST_OF_INT, LIST_OF_STRING);

    private TypeUniverse() {}

    public static Types types() {
        return TYPE_UTILS;
    }

    public static Elements elements() {
        return ELEMENT_UTILS;
    }

    public static List<TypeMirror> pool() {
        return TYPE_POOL;
    }

    private static JavacTask createTask() {
        final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Objects.requireNonNull(compiler, "no system Java compiler available; JDK required at runtime");
        return (JavacTask) compiler.getTask(null, null, null, null, null, null);
    }

    private static TypeElement lookup(final String qualifiedName) {
        return Objects.requireNonNull(
                ELEMENT_UTILS.getTypeElement(qualifiedName), () -> "type not found on classpath: " + qualifiedName);
    }
}
