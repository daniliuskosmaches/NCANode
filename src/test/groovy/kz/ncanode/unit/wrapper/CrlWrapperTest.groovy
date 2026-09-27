package kz.ncanode.unit.wrapper

import kz.ncanode.common.WithTestData
import kz.ncanode.wrapper.CrlWrapper
import kz.ncanode.wrapper.KalkanWrapper
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.util.ResourceUtils
import spock.lang.Specification
import spock.lang.Unroll

import javax.security.auth.x500.X500Principal
import java.security.cert.CertificateFactory
import java.security.cert.X509CRL
import java.security.cert.X509CRLEntry
import java.security.cert.X509Certificate

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class CrlWrapperTest extends Specification implements WithTestData {

    @Autowired
    KalkanWrapper kalkanWrapper

    def CRL_NAMES = ['nca_gost2022_test.crl', 'nca_gost_test.crl', 'nca_rsa_test.crl']

    X509CRL crl(String name) {
        (X509CRL) CertificateFactory.getInstance("X.509").generateCRL(new FileInputStream(ResourceUtils.getFile("classpath:crl/${name}")))
    }

    X509Certificate keyCertificate(String key, String password) {
        kalkanWrapper.read(key, null, password).certificate.x509Certificate
    }

    X509Certificate certificate(X500Principal issuer, BigInteger serial) {
        Mock(X509Certificate) {
            getIssuerX500Principal() >> issuer
            getSerialNumber() >> serial
        }
    }

    X509CRLEntry entry(BigInteger serial, Date revocationDate, X500Principal certificateIssuer = null) {
        Mock(X509CRLEntry) {
            getSerialNumber() >> serial
            getRevocationDate() >> revocationDate
            getCertificateIssuer() >> certificateIssuer
        }
    }

    @Unroll("#caseName")
    def "test index gives the same answer as X509CRL"() {
        given:
        def x509Crl = crl(crlName)
        def cert = keyCertificate(key, password)

        when:
        def entry = CrlWrapper.fromX509Crl(x509Crl).orElseThrow().getRevokedEntry(cert)
        def expected = x509Crl.getRevokedCertificate(cert)

        then:
        noExceptionThrown()
        entry.isPresent() == x509Crl.isRevoked(cert)
        entry.map { it.revocationDate() }.orElse(null) == expected?.revocationDate
        entry.map { it.revocationReason() }.orElse(null) == expected?.revocationReason

        where:
        [keyCase, crlName] << [[
            ['revoked auth 2004 key',     KEY_INDIVIDUAL_AUTH_REVOKED_2004, KEY_INDIVIDUAL_VALID_SIGN_2004_PASSWORD],
            ['revoked sign 2004 key',     KEY_INDIVIDUAL_SIGN_REVOKED_2004, KEY_INDIVIDUAL_VALID_SIGN_2004_PASSWORD],
            ['revoked ceo sign 2004 key', KEY_CEO_SIGN_REVOKED_2004,        KEY_INDIVIDUAL_VALID_SIGN_2004_PASSWORD],
            ['active sign 2004 key',      KEY_INDIVIDUAL_VALID_SIGN_2004,   KEY_INDIVIDUAL_VALID_SIGN_2004_PASSWORD],
            ['revoked ceo 2015 key',      KEY_CEO_REVOKED_2015,             KEY_INDIVIDUAL_VALID_2015_PASSWORD],
            ['active 2015 key',           KEY_INDIVIDUAL_VALID_2015,        KEY_INDIVIDUAL_VALID_2015_PASSWORD],
        ], ['nca_gost2022_test.crl', 'nca_gost_test.crl', 'nca_rsa_test.crl']].combinations()
        caseName = "${keyCase[0]} in ${crlName}"
        key = keyCase[1]
        password = keyCase[2]
    }

    @Unroll("#caseName")
    def "test revoked key is found by index in one of the CRLs"() {
        given:
        def cert = keyCertificate(key, password)

        expect:
        CRL_NAMES.any { CrlWrapper.fromX509Crl(crl(it)).orElseThrow().getRevokedEntry(cert).isPresent() }

        where:
        caseName                    | key                              | password
        'revoked auth 2004 key'     | KEY_INDIVIDUAL_AUTH_REVOKED_2004 | KEY_INDIVIDUAL_VALID_SIGN_2004_PASSWORD
        'revoked sign 2004 key'     | KEY_INDIVIDUAL_SIGN_REVOKED_2004 | KEY_INDIVIDUAL_VALID_SIGN_2004_PASSWORD
        'revoked ceo sign 2004 key' | KEY_CEO_SIGN_REVOKED_2004        | KEY_INDIVIDUAL_VALID_SIGN_2004_PASSWORD
        'revoked ceo 2015 key'      | KEY_CEO_REVOKED_2015             | KEY_INDIVIDUAL_VALID_2015_PASSWORD
    }

    def "check revoked serial of another issuer is not revoked"() {
        given:
        def x509Crl = crl('nca_gost2022_test.crl')
        def revokedSerial = x509Crl.revokedCertificates.first().serialNumber
        def own = certificate(x509Crl.issuerX500Principal, revokedSerial)
        def foreign = certificate(new X500Principal("CN=Foreign CA"), revokedSerial)
        def index = CrlWrapper.fromX509Crl(x509Crl).orElseThrow()

        expect:
        x509Crl.isRevoked(own)
        index.getRevokedEntry(own).isPresent()

        and:
        !x509Crl.isRevoked(foreign)
        !index.getRevokedEntry(foreign).isPresent()
    }

    def "test entry with certificateIssuer is looked up by that issuer"() {
        given:
        def crlIssuer = new X500Principal("CN=CRL Issuer")
        def certIssuer = new X500Principal("CN=Certificate Issuer")
        def x509Crl = Mock(X509CRL) {
            getIssuerX500Principal() >> crlIssuer
            getRevokedCertificates() >> ([entry(BigInteger.ONE, new Date(1000), certIssuer)] as Set)
        }
        def index = CrlWrapper.fromX509Crl(x509Crl).orElseThrow()

        expect:
        index.getRevokedEntry(certificate(certIssuer, BigInteger.ONE)).get().revocationDate() == new Date(1000)
        !index.getRevokedEntry(certificate(crlIssuer, BigInteger.ONE)).isPresent()
    }

    def "check index is not built when a certificate is revoked twice"() {
        given:
        def issuer = new X500Principal("CN=Test CA")
        def x509Crl = Mock(X509CRL) {
            getIssuerX500Principal() >> issuer
            getRevokedCertificates() >> ([entry(BigInteger.TEN, new Date(1000)), entry(BigInteger.TEN, new Date(2000))] as Set)
        }

        expect:
        !CrlWrapper.fromX509Crl(x509Crl).isPresent()
    }

    def "test empty CRL revokes nothing"() {
        given:
        def issuer = new X500Principal("CN=Test CA")
        def x509Crl = Mock(X509CRL) {
            getIssuerX500Principal() >> issuer
            getRevokedCertificates() >> null
        }

        expect:
        !CrlWrapper.fromX509Crl(x509Crl).orElseThrow().getRevokedEntry(certificate(issuer, BigInteger.ONE)).isPresent()
    }
}
