package kz.ncanode.unit.configuration

import io.micrometer.observation.Observation
import kz.ncanode.configuration.TracingConfiguration
import org.springframework.http.server.observation.ServerRequestObservationContext
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import spock.lang.Specification
import spock.lang.Unroll

class TracingConfigurationTest extends Specification {

    def DEFAULT_PATTERNS = ['/actuator/**', '/swagger-ui/**', '/v3/api-docs/**', '/favicon.ico']

    def predicate(List<String> patterns) {
        def configuration = new TracingConfiguration()
        configuration.setExcludedPaths(patterns)

        return configuration.excludedPathsObservationPredicate()
    }

    def request(String uri) {
        new ServerRequestObservationContext(new MockHttpServletRequest("POST", uri), new MockHttpServletResponse())
    }

    @Unroll("#caseName")
    def "test server request observation is filtered by path"() {
        expect:
        predicate(DEFAULT_PATTERNS).test("http.server.requests", request(uri)) == observed

        where:
        caseName            | uri                                 || observed
        'health endpoint'   | '/actuator/health'                  || false
        'metrics endpoint'  | '/actuator/metrics/jvm.memory.used' || false
        'swagger ui'        | '/swagger-ui/index.html'            || false
        'api docs'          | '/v3/api-docs/swagger-config'       || false
        'favicon'           | '/favicon.ico'                      || false
        'cms sign'          | '/cms/sign'                         || true
        'xml verify'        | '/xml/verify'                       || true
        'home page'         | '/'                                 || true
    }

    def "check observations outside of a server request are not filtered"() {
        expect: "исходящие вызовы и методы с Observed отсеивать нечем и незачем"
        predicate(DEFAULT_PATTERNS).test("ncanode.http.client", new Observation.Context())

        and: "с пустым списком паттернов наблюдается всё"
        predicate([]).test("http.server.requests", request('/actuator/health'))
    }
}
