package io.github.joke.percolate.spi.builtins.enumconversion

import com.google.testing.compile.Compilation
import com.google.testing.compile.JavaFileObjects
import io.github.joke.percolate.test.PercolateCompiler
import spock.lang.Specification
import spock.lang.Tag

import javax.tools.JavaFileObject

/**
 * Compile-time coverage safety, uniform across both switch tiers (design D6 of change
 * {@code decouple-engine-from-strategy-semantics}): an uncovered source constant is refused by the strategy's
 * bound before grounding ever competes, so it fails the build identically on {@code CLASSIC} and {@code ARROW}
 * targets — the modern tier's own switch-expression exhaustiveness check is retained only as a second line of
 * defence, never reached once the bound is in place.
 */
@Tag('integration')
class EnumConversionCoverageSpec extends Specification {

    def 'an uncovered source constant fails the compile via a positioned diagnostic naming it on a Java 17 (arrow) target'() {
        when:
        Compilation compilation = compile(['--release', '17'], MY_STATUS_UNCOVERED)
        def error = compilation.errors().find { it.getMessage(null).contains('CANCELLED') }

        then:
        error != null
        error.lineNumber > 0
    }

    def 'an uncovered source constant fails the compile via a positioned diagnostic naming it on a Java 11 (classic) target'() {
        when:
        Compilation compilation = compile(['--release', '11'], MY_STATUS_UNCOVERED)
        def error = compilation.errors().find { it.getMessage(null).contains('CANCELLED') }

        then:
        error != null
        error.lineNumber > 0
    }

    def 'a non-enum source fails the compile via a positioned diagnostic naming the bound, rather than a processor crash'() {
        when:
        Compilation compilation = PercolateCompiler.compile(ORDER_STATUS, TAG_STATUS_MAPPER)
        def error = compilation.errors().find { it.getMessage(null).contains('enum conversion requires an enum source') }

        then:
        error != null
        error.lineNumber > 0
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

    private static final JavaFileObject TAG_STATUS_MAPPER = JavaFileObjects.forSourceLines(
            'examples.enumcoverage.TagStatusMapper',
            'package examples.enumcoverage;',
            'import io.github.joke.percolate.Mapper;',
            '@Mapper',
            'public interface TagStatusMapper {',
            '    OrderStatus map(String tag);',
            '}')

    private static Compilation compile(final List<String> options, final JavaFileObject myStatus) {
        PercolateCompiler.compileWith(options, myStatus, ORDER_STATUS, MAPPER)
    }
}
