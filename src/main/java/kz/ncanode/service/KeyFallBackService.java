package kz.ncanode.service;


import kz.ncanode.configuration.DefaultKeyConfiguration;
import kz.ncanode.dto.request.SignerRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.List;

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

        return signer;
    }

    public List<SignerRequest> prepareSigners(List<SignerRequest> signers ) {
        if (signers == null || signers.isEmpty()) {
            return List.of(prepareSigner(null));
        }
        return signers.stream().map(this::prepareSigner).toList();


    }

    private String loadKeyFromDisk(String path) {
        try {
            byte[] bytes = Files.readAllBytes(Paths.get(path));
            return Base64.getEncoder().encodeToString(bytes);
        } catch (IOException e) {
            throw new RuntimeException("Не удалось прочитать дефолтный ЭЦП по пути: " + path, e);
        }
    }
}
