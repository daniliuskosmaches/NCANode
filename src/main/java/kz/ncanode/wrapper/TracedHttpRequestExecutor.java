package kz.ncanode.wrapper;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.transport.Kind;
import io.micrometer.observation.transport.SenderContext;
import lombok.RequiredArgsConstructor;
import org.apache.http.*;
import org.apache.http.protocol.*;

import java.io.IOException;

/**
 * Оборачивает исходящие запросы Apache HttpClient в наблюдение Micrometer.
 * CRL, OCSP, TSP и загрузка сертификатов УЦ — самая долгая часть подписи и проверки,
 * а HttpClient 4.x Micrometer сам не инструментирует.
 */
@RequiredArgsConstructor
public class TracedHttpRequestExecutor extends HttpRequestExecutor {
    public final static String OBSERVATION_NAME = "ncanode.http.client";
    private final static String UNKNOWN_HOST = "unknown";

    private final ObservationRegistry observationRegistry;

    @Override
    public HttpResponse execute(HttpRequest request, HttpClientConnection conn, HttpContext context) throws IOException, HttpException {
        HttpHost target = HttpCoreContext.adapt(context).getTargetHost();
        String method = request.getRequestLine().getMethod();
        String host = target != null ? target.getHostName() : UNKNOWN_HOST;

        SenderContext<HttpRequest> senderContext = new SenderContext<>(HttpRequest::setHeader, Kind.CLIENT);
        senderContext.setCarrier(request);
        senderContext.setRemoteServiceName(host);

        Observation observation = Observation.createNotStarted(OBSERVATION_NAME, () -> senderContext, observationRegistry)
            .contextualName(method + " " + host)
            .lowCardinalityKeyValue("http.request.method", method)
            .lowCardinalityKeyValue("server.address", host)
            .highCardinalityKeyValue("url.full", fullUrl(target, request));

        observation.start();

        try (Observation.Scope ignored = observation.openScope()) {
            HttpResponse response = super.execute(request, conn, context);
            observation.lowCardinalityKeyValue("http.response.status_code", String.valueOf(response.getStatusLine().getStatusCode()));

            return response;
        } catch (IOException | HttpException | RuntimeException e) {
            observation.error(e);
            throw e;
        } finally {
            observation.stop();
        }
    }

    /**
     * Собирает полный URL запроса
     *
     * @param target Хост из контекста обмена, либо ничего
     * @param request Запрос
     * @return Полный URL, либо путь, если хост неизвестен
     */
    private static String fullUrl(HttpHost target, HttpRequest request) {
        String uri = request.getRequestLine().getUri();

        // при работе через прокси в request-line уже лежит абсолютный URI
        if (target == null || uri.startsWith("http://") || uri.startsWith("https://")) {
            return uri;
        }

        return target.toURI() + uri;
    }
}
