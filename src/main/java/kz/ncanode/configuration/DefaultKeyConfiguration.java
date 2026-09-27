package kz.ncanode.configuration;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Getter
public class DefaultKeyConfiguration {

    @Value("${app.nca.default-key-path:/app/keys/cert.p12}")
    private String defaultKeyPath;

    @Value("${app.nca.default-key-password:}")
    private String defaultKeyPassword;
}
