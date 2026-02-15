package io.github.joke.objects.customize;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static io.github.joke.objects.customize.ToString.Style.STRING_JOINER;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.SOURCE;

/**
 * Customize the implementation of {@link Object#toString()}.
 */
@Target(TYPE)
@Retention(SOURCE)
public @interface ToString {

    /**
     * Style used for generating {@link Object#toString()} implementations.
     */
    Style style() default STRING_JOINER;

    enum Style {
        /**
         * Implement {@link Object#toString()} using {@link java.util.StringJoiner}.
         *
         * <pre>{@code
         * @Override
         * public String toString() {
         *   return new StringJoiner(", ", Person.class.getSimpleName() + "[", "]")
         *     .add("name='" + name + "'")
         *     .toString();
         * }
         * }</pre>
         */
        STRING_JOINER,

        /**
         * Implement {@link Object#toString()} using {@link org.apache.commons.lang3.builder.ToStringBuilder}.
         *
         * <pre>{@code
         * @Override
         * public String toString() {
         *   return new org.apache.commons.lang3.builder.ToStringBuilder(this)
         *     .append("name", name)
         *     .toString();
         * }
         * }</pre>
         */
        TO_STRING_BUILDER
    }
}
