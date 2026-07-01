package io.github.joke.percolate.processor

import io.github.joke.percolate.processor.internal.stages.Stage
import io.github.joke.percolate.processor.internal.stages.discover.DiscoverAbstractMethodsStage
import io.github.joke.percolate.processor.internal.stages.discover.DiscoverCallableMethodsStage
import io.github.joke.percolate.processor.internal.stages.discover.DiscoverMappingsStage
import io.github.joke.percolate.processor.internal.stages.dump.DumpFullGraphStage
import io.github.joke.percolate.processor.internal.stages.dump.DumpPlanStage
import io.github.joke.percolate.processor.internal.stages.dump.DumpTransformsStage
import io.github.joke.percolate.processor.internal.stages.expand.ExpandStage
import io.github.joke.percolate.processor.internal.stages.generate.GenerateStage
import io.github.joke.percolate.processor.internal.stages.validate.RealisationDiagnosticsStage
import io.github.joke.percolate.processor.internal.stages.validate.ValidateConstantDefaultLegalityStage
import io.github.joke.percolate.processor.internal.stages.validate.ValidateMappingShapeStage
import io.github.joke.percolate.processor.internal.stages.validate.ValidateNoDuplicateTargetsStage
import io.github.joke.percolate.processor.internal.stages.validate.ValidateSourceParametersStage
import io.github.joke.percolate.processor.nullability.JspecifyNullabilityResolver
import io.github.joke.percolate.processor.nullability.NullabilityResolver
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Tag

import javax.annotation.processing.Filer
import javax.annotation.processing.Messager
import javax.annotation.processing.ProcessingEnvironment
import javax.lang.model.util.Elements
import javax.lang.model.util.Types

/**
 * {@link ProcessorModule} seam, unit-tested directly: the Dagger bindings are exercised as plain methods. The
 * environment-facing providers delegate to the {@link ProcessingEnvironment}; the nullability binding merges the
 * configured custom annotations onto the jspecify defaults; and the stage-list providers thread the stages in the
 * documented pipeline order.
 */
@Tag('unit')
class ProcessorModuleSpec extends Specification {

    ProcessingEnvironment env = Mock()
    @Subject
    ProcessorModule module = new ProcessorModule(env)

    def 'elements delegates to the processing environment'() {
        Elements elements = Mock()

        when:
        def result = module.elements()

        then:
        1 * env.elementUtils >> elements
        0 * _

        expect:
        result.is(elements)
    }

    def 'types delegates to the processing environment'() {
        Types types = Mock()

        when:
        def result = module.types()

        then:
        1 * env.typeUtils >> types
        0 * _

        expect:
        result.is(types)
    }

    def 'messager delegates to the processing environment'() {
        Messager messager = Mock()

        when:
        def result = module.messager()

        then:
        1 * env.messager >> messager
        0 * _

        expect:
        result.is(messager)
    }

    def 'filer delegates to the processing environment'() {
        Filer filer = Mock()

        when:
        def result = module.filer()

        then:
        1 * env.filer >> filer
        0 * _

        expect:
        result.is(filer)
    }

    def 'processorOptions are parsed from the environment options'() {
        when:
        def result = module.processorOptions()

        then:
        1 * env.options >> ['percolate.docTags': 'true']
        0 * _

        expect:
        result.docTags
    }

    def 'nullabilityAnnotations returns the jspecify defaults when no custom annotations are configured'() {
        given:
        def options = new ProcessorOptions(false, [] as Set, false, false, false)

        expect:
        module.nullabilityAnnotations(options).nullableFqns == ['org.jspecify.annotations.Nullable'] as Set
    }

    def 'nullabilityAnnotations merges the custom nullable FQNs onto the defaults, keeping the marked/unmarked sets'() {
        given:
        def options = new ProcessorOptions(false, ['com.example.Nullable'] as Set, false, false, false)
        def annotations = module.nullabilityAnnotations(options)

        expect:
        annotations.nullableFqns == ['org.jspecify.annotations.Nullable', 'com.example.Nullable'] as Set
        annotations.markedFqns == ['org.jspecify.annotations.NullMarked'] as Set
        annotations.unmarkedFqns == ['org.jspecify.annotations.NullUnmarked'] as Set
    }

    def 'nullabilityResolver returns the injected jspecify resolver'() {
        given:
        JspecifyNullabilityResolver resolver = Mock()

        expect:
        module.nullabilityResolver(resolver).is(resolver)
    }

    def 'expandStage assembles an ExpandStage from the injected collaborators'() {
        expect:
        module.expandStage([], [], Mock(Types), Mock(Elements), Mock(NullabilityResolver)) instanceof ExpandStage
    }

    def 'assembleExpansionPipeline constructs an ExpandStage'() {
        expect:
        ProcessorModule.assembleExpansionPipeline([], [], Mock(Types), Mock(Elements), Mock(NullabilityResolver)) instanceof ExpandStage
    }

    def 'discoverStages lists abstract-methods, mappings, then callable-methods in order'() {
        given:
        DiscoverAbstractMethodsStage abstractMethods = Mock()
        DiscoverMappingsStage mappings = Mock()
        DiscoverCallableMethodsStage callableMethods = Mock()

        expect:
        ProcessorModule.discoverStages(abstractMethods, mappings, callableMethods) == [abstractMethods, mappings, callableMethods]
    }

    def 'stages threads the discover stages, then validation, expansion, realisation, dumps and generation in order'() {
        given:
        Stage discoverA = Mock()
        Stage discoverB = Mock()
        ValidateNoDuplicateTargetsStage noDuplicateTargets = Mock()
        ValidateMappingShapeStage mappingShape = Mock()
        ValidateSourceParametersStage sourceParameters = Mock()
        ExpandStage expand = Mock()
        DumpFullGraphStage dumpFullGraph = Mock()
        DumpTransformsStage dumpTransforms = Mock()
        DumpPlanStage dumpPlan = Mock()
        ValidateConstantDefaultLegalityStage constantDefaultLegality = Mock()
        RealisationDiagnosticsStage realisation = Mock()
        GenerateStage generate = Mock()

        when:
        def stages = ProcessorModule.stages(
                [discoverA, discoverB], noDuplicateTargets, mappingShape, sourceParameters, expand,
                dumpFullGraph, dumpTransforms, dumpPlan, constantDefaultLegality, realisation, generate)

        then:
        stages == [
                discoverA, discoverB,
                noDuplicateTargets, mappingShape, sourceParameters,
                expand,
                constantDefaultLegality, realisation,
                dumpFullGraph, dumpTransforms, dumpPlan,
                generate
        ]
    }
}
