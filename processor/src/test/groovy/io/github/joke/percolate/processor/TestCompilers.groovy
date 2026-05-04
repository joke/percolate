package io.github.joke.percolate.processor

import com.google.testing.compile.Compiler

import static com.google.testing.compile.Compiler.javac

final class TestCompilers {

    private TestCompilers() {}

    static Compiler compiler() {
        def url = Class.forName('io.github.joke.percolate.Mapper')
                .protectionDomain
                .codeSource
                .location
        def entry = new File(url.toURI())
        return javac().withClasspath([entry])
    }
}
