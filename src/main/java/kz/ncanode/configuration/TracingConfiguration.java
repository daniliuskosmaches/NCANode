package kz.ncanode.configuration;

import io.micrometer.observation.ObservationPredicate;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.aop.ObservedAspect;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.util.AntPathMatcher;

import java.util.*;

/**
 * Конфигурация трассировки. Экспортом трасс управляют стандартные настройки Spring Boot,
 * здесь только то, что специфично для NCANode.
 */
@Configuration
@ConfigurationProperties(prefix = "ncanode.tracing")
@Getter
@Setter
public class TracingConfiguration {
    private List<String> excludedPaths = new ArrayList<>();

    /**
     * Включает обработку аннотации Observed на сервисах
     *
     * @param observationRegistry Реестр наблюдений
     * @return Аспект, без которого аннотация ничего не делает
     */
    @Bean
    public ObservedAspect observedAspect(ObservationRegistry observationRegistry) {
        return new ObservedAspect(observationRegistry);
    }

    /**
     * Отсеивает служебные запросы. Docker-healthcheck ходит на /actuator/health каждые 20 секунд,
     * Swagger тянет статику — в трассах от этого один шум.
     *
     * @return Условие, по которому создаётся серверное наблюдение
     */
    @Bean
    public ObservationPredicate excludedPathsObservationPredicate() {
        AntPathMatcher matcher = new AntPathMatcher();
        List<String> patterns = List.copyOf(excludedPaths);

        return (name, context) -> {
            if (!(context instanceof ServerRequestObservationContext serverContext)) {
                return true;
            }

            String path = serverContext.getCarrier().getRequestURI();

            return patterns.stream().noneMatch(pattern -> matcher.match(pattern, path));
        };
    }
}
