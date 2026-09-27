package kz.ncanode.unit.wrapper

import io.micrometer.observation.tck.TestObservationRegistry
import io.micrometer.observation.transport.Kind
import io.micrometer.observation.transport.SenderContext
import kz.ncanode.wrapper.TracedHttpRequestExecutor
import org.apache.http.*
import org.apache.http.message.*
import org.apache.http.protocol.*
import spock.lang.Specification
import spock.lang.Unroll

import static io.micrometer.observation.tck.TestObservationRegistryAssert.assertThat

class TracedHttpRequestExecutorTest extends Specification {

    TestObservationRegistry registry = TestObservationRegistry.create()

    HttpContext contextFor(HttpHost target) {
        def context = new BasicHttpContext()
        context.setAttribute(HttpCoreContext.HTTP_TARGET_HOST, target)

        return context
    }

    /**
     * Подменяет обмен по сокету: execute() отдаёт результат doSendRequest, если он не null
     */
    TracedHttpRequestExecutor executor(Throwable failure = null) {
        new TracedHttpRequestExecutor(registry) {
            @Override
            protected HttpResponse doSendRequest(HttpRequest request, HttpClientConnection conn, HttpContext context) {
                if (failure != null) {
                    throw failure
                }

                return new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK")
            }
        }
    }

    def "test observation for successful exchange"() {
        given:
        def request = new BasicHttpRequest("GET", "/crl/nca_gost2022_test.crl")

        when:
        def response = executor().execute(request, Mock(HttpClientConnection), contextFor(new HttpHost("test.pki.gov.kz", -1, "http")))

        then:
        noExceptionThrown()
        response.statusLine.statusCode == 200

        and:
        assertThat(registry)
            .hasObservationWithNameEqualTo(TracedHttpRequestExecutor.OBSERVATION_NAME)
            .that()
            .hasContextualNameEqualTo("GET test.pki.gov.kz")
            .hasLowCardinalityKeyValue("http.request.method", "GET")
            .hasLowCardinalityKeyValue("http.response.status_code", "200")
    }

    def "test observation is a CLIENT sender carrying the request"() {
        given:
        def request = new BasicHttpRequest("POST", "/")

        when:
        executor().execute(request, Mock(HttpClientConnection), contextFor(new HttpHost("ocsp.pki.gov.kz", -1, "http")))

        then:
        assertThat(registry).hasHandledContextsThatSatisfy({ contexts ->
            def context = contexts.find { it.name == TracedHttpRequestExecutor.OBSERVATION_NAME } as SenderContext

            assert context.kind == Kind.CLIENT
            assert context.remoteServiceName == "ocsp.pki.gov.kz"
            assert context.carrier.is(request)

            // заголовок распространения трассировки кладётся в сам запрос
            context.setter.set(request, "traceparent", "00-trace-span-01")
            assert request.getFirstHeader("traceparent").value == "00-trace-span-01"
        })
    }

    def "check error recorded and rethrown when exchange fails"() {
        given:
        def failure = new IOException("connection refused")

        when:
        executor(failure).execute(new BasicHttpRequest("POST", "/"), Mock(HttpClientConnection), contextFor(new HttpHost("tsp.pki.gov.kz", -1, "http")))

        then:
        def caught = thrown(IOException)
        caught.is(failure)

        and:
        assertThat(registry)
            .hasObservationWithNameEqualTo(TracedHttpRequestExecutor.OBSERVATION_NAME)
            .that()
            .hasError(failure)
    }

    @Unroll("#caseName")
    def "test url and host recorded in observation"() {
        when:
        executor().execute(new BasicHttpRequest("GET", uri), Mock(HttpClientConnection), contextFor(target))

        then:
        assertThat(registry)
            .hasObservationWithNameEqualTo(TracedHttpRequestExecutor.OBSERVATION_NAME)
            .that()
            .hasLowCardinalityKeyValue("server.address", expectedHost)
            .hasHighCardinalityKeyValue("url.full", expectedUrl)

        where:
        caseName                     | uri                             | target                                        || expectedHost     | expectedUrl
        'relative uri'               | '/nca.crl'                      | new HttpHost("crl.pki.gov.kz", -1, "http")    || 'crl.pki.gov.kz' | 'http://crl.pki.gov.kz/nca.crl'
        'absolute uri through proxy' | 'http://crl.pki.gov.kz/nca.crl' | new HttpHost("proxy.internal", 3128, "http")  || 'proxy.internal' | 'http://crl.pki.gov.kz/nca.crl'
        'explicit port'              | '/'                             | new HttpHost("tsp.local", 8080, "http")       || 'tsp.local'      | 'http://tsp.local:8080/'
        'no target host in context'  | '/cert/nca_gost.crt'            | null                                          || 'unknown'        | '/cert/nca_gost.crt'
    }
}
