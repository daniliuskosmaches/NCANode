package kz.ncanode.service;


import kz.ncanode.configuration.DefaultKeyConfiguration;
import kz.ncanode.dto.request.SignerRequest;
import kz.ncanode.exception.ClientException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class KeyFallBackService {
    private final DefaultKeyConfiguration defaultKeyConfiguration;

    public SignerRequest prepareSigner(SignerRequest signer) {
        if (signer == null) {
            signer = SignerRequest.builder().build();
        }

        // Если ключ не передан в JSON — берем подмонтированный файл
        if (signer.getKey() == null || signer.getKey().isBlank()) {
            signer.setKey(loadKeyFromDisk(defaultKeyConfiguration.getDefaultKeyPath()));
        }

        if (signer.getPassword() == null || signer.getPassword().isBlank()) {
            signer.setPassword(defaultKeyConfiguration.getDefaultKeyPassword());
        }

        if (signer.getPassword() == null || signer.getPassword().isBlank()) {
            throw new ClientException("Пароль дефолтного ЭЦП не задан. Укажите NCANODE_KEY_PASSWORD.");
        }

        return signer;
    }

    public List<SignerRequest> prepareSigners(List<SignerRequest> signers) {
        if (signers == null || signers.isEmpty()) {
            return List.of(prepareSigner(null));
        }

        List<SignerRequest> prepared = signers.stream()
            .filter(Objects::nonNull)
            .map(this::prepareSigner)
            .toList();

        if (prepared.isEmpty()) {
            return List.of(prepareSigner(null));
        }

        return prepared;
    }

    private String loadKeyFromDisk(String path) {
        if (path == null || path.isBlank()) {
            throw new ClientException("Путь к дефолтному ЭЦП не задан. Укажите NCANODE_KEY_PATH.");
        }

        Path keyPath = Path.of(path);
        if (!Files.isRegularFile(keyPath)) {
            throw new ClientException("Дефолтный ЭЦП не найден по пути: " + path);
        }

        try {
            byte[] bytes = Files.readAllBytes(keyPath);
            return Base64.getEncoder().encodeToString(bytes);
        } catch (IOException e) {
            throw new ClientException("Не удалось прочитать дефолтный ЭЦП по пути: " + path, e);
        }
    }
}
