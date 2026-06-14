package io.github.joke.percolate.processor.graph

import spock.lang.Specification
import spock.lang.Tag

@Tag('unit')
class CostSpec extends Specification {

    def 'totality dominates weight in the lexicographic order'() {
        expect: 'fewer partials wins despite a higher weight; equal partials fall to weight'
        Cost.finite(0, 100.0) < Cost.finite(1, 1.0)
        Cost.finite(1, 1.0) < Cost.finite(1, 2.0)
    }

    def 'INFINITE is greater than every finite cost and equal to itself'() {
        expect:
        Cost.finite(9, 9.0) < Cost.INFINITE
        Cost.ZERO < Cost.INFINITE
        (Cost.INFINITE <=> Cost.INFINITE) == 0
    }

    def 'reachability is finiteness'() {
        expect:
        Cost.ZERO.reachable
        Cost.finite(3, 4.0).reachable
        !Cost.INFINITE.reachable
    }

    def 'plus adds componentwise and is absorbed by INFINITE'() {
        expect:
        Cost.finite(1, 2.0) + Cost.finite(2, 3.0) == Cost.finite(3, 5.0)
        Cost.finite(1, 2.0) + Cost.INFINITE == Cost.INFINITE
        Cost.INFINITE + Cost.ZERO == Cost.INFINITE
        Cost.ZERO + Cost.finite(1, 1.0) == Cost.finite(1, 1.0)
    }

    def 'min picks the cheaper cost'() {
        expect:
        Cost.finite(0, 5.0).min(Cost.finite(1, 1.0)) == Cost.finite(0, 5.0)
        Cost.finite(2, 2.0).min(Cost.INFINITE) == Cost.finite(2, 2.0)
    }
}
