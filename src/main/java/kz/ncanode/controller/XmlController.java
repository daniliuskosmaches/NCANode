package kz.ncanode.controller;

import kz.ncanode.dto.certificate.CertificateRevocation;
import kz.ncanode.dto.request.SignerRequest;
import kz.ncanode.dto.request.XmlSignRequest;
import kz.ncanode.dto.request.XmlVerifyRequest;
import kz.ncanode.dto.response.VerificationResponse;
import kz.ncanode.dto.response.XmlSignResponse;
import kz.ncanode.service.XmlService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Base64;
import java.util.List;

@Tag(name = "XML", description = "Методы для работы с XML")
@RestController
@RequestMapping("xml")
@RequiredArgsConstructor
public class XmlController {
    private final XmlService xmlService;


    @PostMapping(value = "/sign-file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<XmlSignResponse> signXmlFile(
        @RequestPart("file") MultipartFile file,
        @RequestPart("password") String password,
        @RequestPart("xml") String xml
    ) throws IOException {

        return ResponseEntity.ok(
            xmlService.sign(XmlSignRequest.builder()
                .xml(xml)
                .signers(List.of(
                    SignerRequest.builder()
                        .key(Base64.getEncoder().encodeToString(file.getBytes()))
                        .password(password)
                        .build()
                ))
                .build())
        );
    }





    @PostMapping("/sign")
    public ResponseEntity<XmlSignResponse> sign(@Valid @RequestBody XmlSignRequest xmlSignRequest) {
        return ResponseEntity.ok(xmlService.sign(xmlSignRequest));
    }

    @PostMapping("/verify")
    public ResponseEntity<VerificationResponse> verify(@Valid @RequestBody XmlVerifyRequest xmlVerifyRequest) {
        return ResponseEntity.ok(xmlService.verify(xmlVerifyRequest.getXml(),
            xmlVerifyRequest.getRevocationCheck().contains(CertificateRevocation.OCSP),
            xmlVerifyRequest.getRevocationCheck().contains(CertificateRevocation.CRL)
        ));
    }
}
