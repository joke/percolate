package io.github.joke.percolate.spi;

import io.github.joke.percolate.javapoet.CodeBlock;
import lombok.experimental.UtilityClass;

/**
 * Brackets a generated code region with AsciiDoc include-tag comments, so a documentation build can single-source
 * the real generated output by tag ({@code include::generated.java[tag=<name>]}). A <b>pure codegen transform</b>:
 * it names no processor option and reads no state — the caller owns the policy decision of whether tagging is on.
 */
@UtilityClass
public class DocTags {

    /**
     * Wrap {@code region} between {@code // tag::<name>[]} and {@code // end::<name>[]} comment lines. The directive
     * lines are stripped by the AsciiDoc include, so the bracketed region is reproduced verbatim. The {@code region}
     * is emitted unchanged between the markers — tagging is additive and never rewrites the wrapped code.
     */
    public CodeBlock wrap(final CodeBlock region, final String name) {
        return CodeBlock.builder()
                .add("// tag::$L[]\n", name)
                .add(region)
                .add("// end::$L[]\n", name)
                .build();
    }
}
