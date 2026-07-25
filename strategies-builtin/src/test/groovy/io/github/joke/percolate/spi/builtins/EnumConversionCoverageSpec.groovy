package io.github.joke.percolate.spi.builtins

import com.google.testing.compile.Compilation
import com.google.testing.compile.JavaFileObjects
import io.github.joke.percolate.test.PercolateCompiler
import spock.lang.Specification
import spock.lang.Tag

import javax.tools.JavaFileObject

/**
 * Compile-time coverage safety across both switch tiers (design D5): an uncovered source constant fails the build
 * either way, but through a different mechanism — javac's own exhaustiveness check on the {@code ARROW} tier
 * (Java 14+, no percolate diagnostic at all), and percolate's own coverage check on the {@code CLASSIC} tier
 * (Java 11, where statement-switch totality is unchecked by the compiler).
 */
@Tag('integration')
class EnumConversionCoverageSpec extends Specification {

    def 'an uncovered source constant fails the compile via javac exhaustiveness on a Java 17 (arrow) target'() {
        when:
        Compilation compilation = compile(['--release', '17'], MY_STATUS_UNCOVERED)

        then: "javac's own exhaustiveness check rejects it — not percolate's own coverage diagnostic"
        !compilation.errors().empty
        !compilation.errors().any { it.getMessage(null).contains('no @MapEnum or same-name match') }
    }

    def 'an uncovered source constant fails the compile via a percolate diagnostic naming it on a Java 11 (classic) target'() {
        when:
        Compilation compilation = compile(['--release', '11'], MY_STATUS_UNCOVERED)

        then:
        compilation.errors().any { it.getMessage(null).contains('CANCELLED') }
    }

    def 'a fully-covered conversion compiles cleanly on the arrow tier'() {
        expect:
        compile(['--release', '17'], MY_STATUS_COVERED).errors().empty
    }

    def 'a fully-covered conversion compiles cleanly on the classic tier'() {
        expect:
        compile(['--release', '11'], MY_STATUS_COVERED).errors().empty
    }

    // ---- harness -------------------------------------------------------------------------------------------

    private static final JavaFileObject MY_STATUS_UNCOVERED = JavaFileObjects.forSourceLines(
            'examples.enumcoverage.MyStatus',
            'package examples.enumcoverage;',
            'public enum MyStatus { NEW, COMPLETED, CANCELLED }')

    private static final JavaFileObject MY_STATUS_COVERED = JavaFileObjects.forSourceLines(
            'examples.enumcoverage.MyStatus',
            'package examples.enumcoverage;',
            'public enum MyStatus { NEW, COMPLETED }')

    private static final JavaFileObject ORDER_STATUS = JavaFileObjects.forSourceLines(
            'examples.enumcoverage.OrderStatus',
            'package examples.enumcoverage;',
            'public enum OrderStatus { CREATED, FULFILLED }')

    private static final JavaFileObject MAPPER = JavaFileObjects.forSourceLines(
            'examples.enumcoverage.StatusMapper',
            'package examples.enumcoverage;',
            'import io.github.joke.percolate.Mapper;',
            'import io.github.joke.percolate.MapEnum;',
            '@Mapper',
            'public interface StatusMapper {',
            '    @MapEnum(source = "NEW", target = "CREATED")',
            '    @MapEnum(source = "COMPLETED", target = "FULFILLED")',
            '    OrderStatus toStatus(MyStatus s);',
            '}')

    private static Compilation compile(final List<String> options, final JavaFileObject myStatus) {
        PercolateCompiler.compileWith(options, myStatus, ORDER_STATUS, MAPPER)
    }
}
