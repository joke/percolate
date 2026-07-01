package io.github.joke.percolate.test

import com.google.testing.compile.Compilation
import com.google.testing.compile.Compiler
import com.google.testing.compile.JavaFileObjects
import io.github.joke.percolate.processor.PercolateProcessor

import javax.tools.JavaFileObject

/**
 * World-2 compile harness: runs the real {@link PercolateProcessor} over supplied sources and returns the
 * {@link Compilation}. Strategy-agnostic — it references no strategy module. Whichever strategies are on the
 * running test classpath are what the processor discovers via ServiceLoader — the real builtins in a strategy
 * module for the feature-e2e layer. Every e2e suite compiles through here rather than re-implementing the
 * {@code Compiler.javac().withProcessors(...)} boilerplate.
 */
final class PercolateCompiler {

    private PercolateCompiler() {}

    static Compilation compile(final JavaFileObject... sources) {
        compileWith([], sources)
    }

    static Compilation compileWith(final List<String> options, final JavaFileObject... sources) {
        Compiler.javac().withProcessors(new PercolateProcessor()).withOptions(options).compile(sources)
    }

    static Compilation compileAll(final List<JavaFileObject> sources) {
        compile(sources as JavaFileObject[])
    }

    static JavaFileObject source(final String qualifiedName, final String... lines) {
        JavaFileObjects.forSourceLines(qualifiedName, lines)
    }
}
