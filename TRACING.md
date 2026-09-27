### Трассировка (OpenTelemetry) - Опционально

NCANode отдаёт трассы по OTLP — стандартному протоколу OpenTelemetry. Принять их может что угодно,
что умеет OTLP: Jaeger и Grafana Tempo напрямую, OpenTelemetry Collector (а из него — куда угодно),
Elastic APM, SigNoz, Uptrace, Datadog, Honeycomb и прочие. Отдельный коллектор не обязателен.
По умолчанию трассировка **выключена**: ничего никуда не отправляется.

Например, с Jaeger:

```bash
# Jaeger: UI на 16686, приём OTLP на 4318 (HTTP) и 4317 (gRPC)
docker run -d --name jaeger -p 16686:16686 -p 4317:4317 -p 4318:4318 jaegertracing/jaeger:2.20.0

# NCANode с трассировкой
NCANODE_TRACING_ENABLED=true java -jar NCANode.jar
```

Трассы появятся в http://localhost:16686 под сервисом `NCANode`. Для другого бэкенда меняется
только `NCANODE_TRACING_ENDPOINT` — в коде ничего Jaeger-специфичного нет. Если NCANode сам в
контейнере, эндпоинт должен указывать на контейнер приёмника (общая docker-сеть либо
`http://host.docker.internal:4318/v1/traces`).

| Переменная | По умолчанию | Назначение |
|---|---|---|
| `NCANODE_TRACING_ENABLED` | `false` | включить экспорт трасс |
| `NCANODE_TRACING_ENDPOINT` | `http://localhost:4318/v1/traces` | OTLP/HTTP-эндпоинт приёмника (gRPC — порт 4317) |
| `NCANODE_TRACING_SAMPLING` | `1.0` | доля трассируемых запросов (`0.1` = 10%) |
| `NCANODE_TRACING_SERVICE_NAME` | `NCANode` | имя сервиса в трассах |
| `NCANODE_TRACING_TIMEOUT` | `10s` | таймаут отправки трасс |
| `NCANODE_TRACING_EXCLUDED_PATHS` | `/actuator/**,/swagger-ui/**,/v3/api-docs/**,/favicon.ico` | пути без трассировки |

В трассе видна вся работа по запросу: HTTP-эндпоинт → операция подписи/проверки (`cms sign`,
`xml verify`, …) → проверка сертификата (`ca build chain`, `ocsp verify`) → обращения к CRL/OCSP/TSP/УЦ
отдельными CLIENT-span-ами. `traceId`/`spanId` попадают в строки лога. Те же измерения собираются
как метрики Micrometer независимо от трассировки, но эндпоинт метрик надо открыть явно:
`MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,metrics`.