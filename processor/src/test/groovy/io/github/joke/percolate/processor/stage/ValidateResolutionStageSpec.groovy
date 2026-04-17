package io.github.joke.percolate.processor.stage

import io.github.joke.percolate.processor.match.AssignmentOrigin
import io.github.joke.percolate.processor.match.MappingAssignment
import io.github.joke.percolate.processor.match.MethodMatching
import io.github.joke.percolate.processor.match.ResolutionFailure
import io.github.joke.percolate.processor.match.ResolvedAssignment
import io.github.joke.percolate.processor.model.MappingMethodModel
import io.github.joke.percolate.processor.model.WriteAccessor
import io.github.joke.percolate.processor.spi.TargetPropertyDiscovery
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.TypeElement
import javax.lang.model.type.TypeMirror
import javax.lang.model.util.Elements
import javax.lang.model.util.Types

@Tag('unit')
class ValidateResolutionStageSpec extends Specification {

    final Types    types    = Stub(Types)
    final Elements elements = Stub(Elements)

    // -------------------------------------------------------------------------
    // 10.3 — source access failure (segment not found on context type)
    // -------------------------------------------------------------------------

    def 'source access failure produces unknownSourceProperty diagnostic'() {
        given:
        final stringType   = typeMirror('java.lang.String')
        final orderType    = typeMirror('test.Order')
        final method       = Stub(ExecutableElement)
        final model        = model(method, orderType, stringType)
        final assignment   = MappingAssignment.of(['missing'], 'name', [:], null, AssignmentOrigin.EXPLICIT_MAP)
        final failure      = new ResolutionFailure('missing', ['id', 'status'] as Set)
        final ra           = new ResolvedAssignment(assignment, null, failure)
        final mapperType   = mapperType()
        final discovery    = discoveryWith(stringType, 'name', stringType)
        final stage        = new ValidateResolutionStage(types, elements, [discovery], [])

        when:
        final result = stage.execute(mapperType, [(matching(method, model, [assignment])): [ra]])

        then:
        !result.isSuccess()
        result.errors().size() == 1
        result.errors()[0].element.is(method)
        result.errors()[0].message.contains('missing')
        result.errors()[0].message.contains('source property')
    }

    // -------------------------------------------------------------------------
    // 10.4 — unknown target property (target slot not found via discovery)
    // -------------------------------------------------------------------------

    def 'unknown target property produces unknownTargetProperty diagnostic'() {
        given:
        final stringType  = typeMirror('java.lang.String')
        final orderType   = typeMirror('test.Order')
        final method      = Stub(ExecutableElement)
        final model       = model(method, orderType, stringType)
        final assignment  = MappingAssignment.of(['name'], 'bogusTarget', [:], null, AssignmentOrigin.EXPLICIT_MAP)
        final ra          = new ResolvedAssignment(assignment, null, null)
        final mapperType  = mapperType()
        // discovery returns only 'name', not 'bogusTarget'
        final discovery   = discoveryWith(stringType, 'name', stringType)
        final stage       = new ValidateResolutionStage(types, elements, [discovery], [])

        when:
        final result = stage.execute(mapperType, [(matching(method, model, [assignment])): [ra]])

        then:
        !result.isSuccess()
        final err = result.errors().find { it.message.contains('bogusTarget') }
        err != null
        err.message.contains('target property')
    }

    // -------------------------------------------------------------------------
    // 10.2 — type gap (target slot found but no conversion path)
    // -------------------------------------------------------------------------

    def 'null path with existing target slot produces unresolvedTransform diagnostic'() {
        given:
        final intType    = typeMirror('int')
        final stringType = typeMirror('java.lang.String')
        final orderType  = typeMirror('test.Order')
        final method     = Stub(ExecutableElement)
        final model      = model(method, orderType, stringType)
        final assignment = MappingAssignment.of(['count'], 'name', [:], null, AssignmentOrigin.EXPLICIT_MAP)
        final ra         = new ResolvedAssignment(assignment, null, null)
        final mapperType = mapperType()
        // discovery sees 'name' with String type — source 'count' is int, no conversion → type gap
        final discovery  = discoveryWith(stringType, 'name', stringType)
        final stage      = new ValidateResolutionStage(types, elements, [discovery], [])

        when:
        final result = stage.execute(mapperType, [(matching(method, model, [assignment])): [ra]])

        then:
        !result.isSuccess()
        final err = result.errors().find { it.message.contains('Cannot map') }
        err != null
        err.element.is(method)
        err.message.contains('count')
        err.message.contains('name')
    }

    // -------------------------------------------------------------------------
    // 10.2 — type gap with using= attribute propagated into diagnostic
    // -------------------------------------------------------------------------

    def 'type gap with using= includes using in diagnostic message'() {
        given:
        final stringType = typeMirror('java.lang.String')
        final orderType  = typeMirror('test.Order')
        final method     = Stub(ExecutableElement)
        final model      = model(method, orderType, stringType)
        final assignment = MappingAssignment.of(['code'], 'name', [:], 'toName', AssignmentOrigin.EXPLICIT_MAP)
        final ra         = new ResolvedAssignment(assignment, null, null)
        final mapperType = mapperType()
        final discovery  = discoveryWith(stringType, 'name', stringType)
        final stage      = new ValidateResolutionStage(types, elements, [discovery], [])

        when:
        final result = stage.execute(mapperType, [(matching(method, model, [assignment])): [ra]])

        then:
        !result.isSuccess()
        result.errors().any { it.message.contains('toName') }
    }

    // -------------------------------------------------------------------------
    // 10.4 — unmapped target property (no assignment at all)
    // -------------------------------------------------------------------------

    def 'target property with no assignment produces unmappedTargetProperty diagnostic'() {
        given:
        final stringType = typeMirror('java.lang.String')
        final orderType  = typeMirror('test.Order')
        final method     = Stub(ExecutableElement)
        final model      = model(method, orderType, stringType)
        // assignment covers 'name' but not 'status'
        final assignment = MappingAssignment.of(['name'], 'name', [:], null, AssignmentOrigin.AUTO_MAPPED)
        final path       = Stub(org.jgrapht.GraphPath)
        final ra         = new ResolvedAssignment(assignment, path, null)
        final mapperType = mapperType()
        // discovery returns both 'name' and 'status'
        final nameAccessor   = writeAccessor('name', stringType)
        final statusAccessor = writeAccessor('status', stringType)
        final discovery  = Stub(TargetPropertyDiscovery) {
            priority() >> 10
            discover(stringType, elements, types) >> [nameAccessor, statusAccessor]
        }
        final stage = new ValidateResolutionStage(types, elements, [discovery], [])

        when:
        final result = stage.execute(mapperType, [(matching(method, model, [assignment])): [ra]])

        then:
        !result.isSuccess()
        result.errors().size() == 1
        result.errors()[0].message.contains('status')
        result.errors()[0].message.contains('Unmapped target')
    }

    // -------------------------------------------------------------------------
    // Happy path — all assignments resolved, all targets covered
    // -------------------------------------------------------------------------

    def 'all assignments resolved and all targets covered returns success'() {
        given:
        final stringType = typeMirror('java.lang.String')
        final orderType  = typeMirror('test.Order')
        final method     = Stub(ExecutableElement)
        final model      = model(method, orderType, stringType)
        final assignment = MappingAssignment.of(['name'], 'name', [:], null, AssignmentOrigin.AUTO_MAPPED)
        final path       = Stub(org.jgrapht.GraphPath)
        final ra         = new ResolvedAssignment(assignment, path, null)
        final mapperType = mapperType()
        final discovery  = discoveryWith(stringType, 'name', stringType)
        final stage      = new ValidateResolutionStage(types, elements, [discovery], [])

        when:
        final result = stage.execute(mapperType, [(matching(method, model, [assignment])): [ra]])

        then:
        result.isSuccess()
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private MethodMatching matching(
            final ExecutableElement method,
            final MappingMethodModel model,
            final List<MappingAssignment> assignments) {
        new MethodMatching(method, model, assignments)
    }

    private MappingMethodModel model(
            final ExecutableElement method, final TypeMirror sourceType, final TypeMirror targetType) {
        Stub(MappingMethodModel) {
            getMethod() >> method
            getSourceType() >> sourceType
            getTargetType() >> targetType
        }
    }

    private TypeElement mapperType() {
        Stub(TypeElement) { toString() >> 'test.MyMapper' }
    }

    private WriteAccessor writeAccessor(final String name, final TypeMirror type) {
        Stub(WriteAccessor) {
            getName() >> name
            getType() >> type
        }
    }

    private TargetPropertyDiscovery discoveryWith(
            final TypeMirror targetType, final String propName, final TypeMirror propType) {
        final accessor = writeAccessor(propName, propType)
        Stub(TargetPropertyDiscovery) {
            priority() >> 10
            discover(targetType, elements, types) >> [accessor]
        }
    }

    private TypeMirror typeMirror(final String name) {
        Stub(TypeMirror) { toString() >> name }
    }
}
