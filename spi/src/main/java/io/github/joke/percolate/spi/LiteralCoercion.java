package io.github.joke.percolate.spi;

import com.palantir.javapoet.CodeBlock;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import org.jspecify.annotations.Nullable;

/**
 * Coerces a raw {@code @Map} string ({@code constant} or {@code defaultValue}) into a typed Java literal for the JDK
 * scalar types only: the 8 primitives, their 8 wrappers, and {@code String}. Every other target type — enums,
 * {@code BigDecimal}, {@code java.time}, arrays, collections, arbitrary declared types — fails coercion (returns an
 * empty {@link Optional}), so a strategy can take the success path (emit a step rendering the literal) and a late
 * diagnostic stage the failure path (report {@code "cannot coerce 'x' to T"}).
 *
 * <p>Coercion is strict and lossless: {@code char} accepts exactly one character; {@code boolean} accepts only
 * {@code "true"}/{@code "false"}; numeric coercions reject out-of-range values rather than truncating and render with
 * the correct literal suffix (e.g. {@code long → 42L}); and the raw string is never whitespace-trimmed.
 */
public final class LiteralCoercion {

    private static final int SINGLE_CHAR_LENGTH = 1;
    private static final int FIRST_PRINTABLE = 0x20;
    private static final int LAST_PRINTABLE = 0x7e;

    /** The canonical {@code boolean} source forms; any other text fails coercion. */
    private static final Set<String> BOOLEAN_LITERALS = Set.of("true", "false");

    /** Primitive coercers keyed by {@link TypeKind}; each renders a bare primitive literal. */
    private static final Map<TypeKind, Function<String, Optional<CodeBlock>>> PRIMITIVES = Map.of(
            TypeKind.BOOLEAN, LiteralCoercion::booleanLiteral,
            TypeKind.BYTE, LiteralCoercion::byteLiteral,
            TypeKind.SHORT, LiteralCoercion::shortLiteral,
            TypeKind.INT, LiteralCoercion::intLiteral,
            TypeKind.LONG, LiteralCoercion::longLiteral,
            TypeKind.CHAR, LiteralCoercion::charLiteral,
            TypeKind.FLOAT, LiteralCoercion::floatLiteral,
            TypeKind.DOUBLE, LiteralCoercion::doubleLiteral);

    /** Wrapper / {@code String} coercers keyed by fully-qualified name; wrappers box their primitive literal. */
    private static final Map<String, Function<String, Optional<CodeBlock>>> DECLARED_COERCERS = Map.of(
            "java.lang.String", raw -> Optional.of(CodeBlock.of("$S", raw)),
            "java.lang.Boolean", raw -> booleanLiteral(raw).map(box(Boolean.class)),
            "java.lang.Byte", raw -> byteLiteral(raw).map(box(Byte.class)),
            "java.lang.Short", raw -> shortLiteral(raw).map(box(Short.class)),
            "java.lang.Integer", raw -> intLiteral(raw).map(box(Integer.class)),
            "java.lang.Long", raw -> longLiteral(raw).map(box(Long.class)),
            "java.lang.Character", raw -> charLiteral(raw).map(box(Character.class)),
            "java.lang.Float", raw -> floatLiteral(raw).map(box(Float.class)),
            "java.lang.Double", raw -> doubleLiteral(raw).map(box(Double.class)));

    /** The escape bodies for the Java {@code char} literals that require one, keyed by the raw character. */
    private static final Map<Character, String> CHAR_ESCAPES = Map.of(
            '\'', "\\'",
            '\\', "\\\\",
            '\n', "\\n",
            '\r', "\\r",
            '\t', "\\t",
            '\b', "\\b",
            '\f', "\\f");

    private LiteralCoercion() {}

    /**
     * The coerced literal expression for {@code raw} at {@code targetType}, or empty when {@code raw} cannot be
     * losslessly coerced to {@code targetType} (out of scope, or strictness violated).
     */
    public static Optional<CodeBlock> coerce(final String raw, final TypeMirror targetType) {
        final var primitive = PRIMITIVES.get(targetType.getKind());
        if (primitive != null) {
            return primitive.apply(raw);
        }
        if (targetType.getKind() == TypeKind.DECLARED) {
            return declared(raw, (DeclaredType) targetType);
        }
        return Optional.empty();
    }

    private static Optional<CodeBlock> declared(final String raw, final DeclaredType type) {
        final var element = type.asElement();
        if (!(element instanceof TypeElement)) {
            return Optional.empty();
        }
        final var fqn = ((TypeElement) element).getQualifiedName().toString();
        final var coercer = DECLARED_COERCERS.get(fqn);
        return coercer == null ? Optional.empty() : coercer.apply(raw);
    }

    private static Optional<CodeBlock> booleanLiteral(final String raw) {
        if (BOOLEAN_LITERALS.contains(raw)) {
            return Optional.of(CodeBlock.of("$L", raw));
        }
        return Optional.empty();
    }

    private static Optional<CodeBlock> byteLiteral(final String raw) {
        return integral(raw, Byte.MIN_VALUE, Byte.MAX_VALUE, "(byte) ", "");
    }

    private static Optional<CodeBlock> shortLiteral(final String raw) {
        return integral(raw, Short.MIN_VALUE, Short.MAX_VALUE, "(short) ", "");
    }

    private static Optional<CodeBlock> intLiteral(final String raw) {
        return integral(raw, Integer.MIN_VALUE, Integer.MAX_VALUE, "", "");
    }

    private static Optional<CodeBlock> longLiteral(final String raw) {
        return integral(raw, Long.MIN_VALUE, Long.MAX_VALUE, "", "L");
    }

    private static Optional<CodeBlock> floatLiteral(final String raw) {
        return parseFloat(raw).filter(Float::isFinite).map(value -> CodeBlock.of("$L", value + "f"));
    }

    private static Optional<CodeBlock> doubleLiteral(final String raw) {
        return parseDouble(raw).filter(Double::isFinite).map(value -> CodeBlock.of("$L", value.toString()));
    }

    private static Optional<CodeBlock> charLiteral(final String raw) {
        if (raw.length() != SINGLE_CHAR_LENGTH) {
            return Optional.empty();
        }
        return Optional.of(CodeBlock.of("$L", charLiteralText(raw.charAt(0))));
    }

    private static Optional<CodeBlock> integral(
            final String raw, final long min, final long max, final String castPrefix, final String suffix) {
        final Long value = parseLong(raw);
        if (value == null || value < min || value > max) {
            return Optional.empty();
        }
        return Optional.of(CodeBlock.of("$L", castPrefix + value + suffix));
    }

    private static Optional<Float> parseFloat(final String raw) {
        try {
            return Optional.of(Float.parseFloat(raw));
        } catch (final NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    private static Optional<Double> parseDouble(final String raw) {
        try {
            return Optional.of(Double.parseDouble(raw));
        } catch (final NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    @Nullable
    private static Long parseLong(final String raw) {
        try {
            return Long.parseLong(raw);
        } catch (final NumberFormatException ignored) {
            return null;
        }
    }

    private static Function<CodeBlock, CodeBlock> box(final Class<?> wrapper) {
        return primitive -> CodeBlock.of("$T.valueOf($L)", wrapper, primitive);
    }

    /** A Java {@code char} literal for {@code c}, escaping the special and non-printable characters. */
    private static String charLiteralText(final char c) {
        return "'" + escape(c) + "'";
    }

    private static String escape(final char c) {
        final var known = CHAR_ESCAPES.get(c);
        if (known != null) {
            return known;
        }
        if (c < FIRST_PRINTABLE || c > LAST_PRINTABLE) {
            return String.format("\\u%04x", (int) c);
        }
        return String.valueOf(c);
    }
}
