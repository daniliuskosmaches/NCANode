package kz.ncanode.wrapper;

import javax.security.auth.x500.X500Principal;
import java.math.BigInteger;
import java.security.cert.CRLReason;
import java.security.cert.X509CRL;
import java.security.cert.X509CRLEntry;
import java.security.cert.X509Certificate;
import java.util.*;

/**
 * Компактный индекс отозванных сертификатов из CRL.
 * Разобранный X509CRL боевого GOST-CRL держит в памяти около 218 МБ и разбирается больше полусекунды,
 * индекс из отсортированных серийных номеров занимает около 34 МБ и ищет за доли микросекунды.
 */
public class CrlWrapper {
    private final static byte NO_REASON = -1;
    private final static CRLReason[] REASONS = CRLReason.values();

    private final Map<X500Principal, Revoked> revokedByIssuer;

    private CrlWrapper(Map<X500Principal, Revoked> revokedByIssuer) {
        this.revokedByIssuer = revokedByIssuer;
    }

    /**
     * Строит индекс из CRL
     *
     * @param crl CRL
     * @return Индекс, либо ничего, если один сертификат отозван в CRL несколько раз с разными данными:
     * какую из записей вернёт X509CRL, зависит от их порядка в файле, а он в индексе не сохраняется
     */
    public static Optional<CrlWrapper> fromX509Crl(X509CRL crl) {
        Map<X500Principal, List<X509CRLEntry>> entriesByIssuer = new HashMap<>();
        Set<? extends X509CRLEntry> entries = crl.getRevokedCertificates();

        if (entries != null) {
            for (X509CRLEntry entry : entries) {
                // как в X509CRLImpl: издатель из certificateIssuer записи, иначе издатель самого CRL
                X500Principal issuer = entry.getCertificateIssuer() != null ? entry.getCertificateIssuer() : crl.getIssuerX500Principal();
                entriesByIssuer.computeIfAbsent(issuer, key -> new ArrayList<>()).add(entry);
            }
        }

        Map<X500Principal, Revoked> revokedByIssuer = new HashMap<>();

        for (var issuerEntries : entriesByIssuer.entrySet()) {
            Optional<Revoked> revoked = Revoked.fromEntries(issuerEntries.getValue());

            if (revoked.isEmpty()) {
                return Optional.empty();
            }

            revokedByIssuer.put(issuerEntries.getKey(), revoked.get());
        }

        return Optional.of(new CrlWrapper(revokedByIssuer));
    }

    /**
     * Ищет сертификат среди отозванных
     *
     * @param cert Сертификат
     * @return Запись об отзыве, либо ничего
     */
    public Optional<RevokedEntry> getRevokedEntry(X509Certificate cert) {
        Revoked revoked = revokedByIssuer.get(cert.getIssuerX500Principal());

        if (revoked == null) {
            return Optional.empty();
        }

        return revoked.find(cert.getSerialNumber());
    }

    public record RevokedEntry(Date revocationDate, CRLReason revocationReason) {
    }

    private record Revoked(BigInteger[] serials, long[] revocationDates, byte[] revocationReasons) {

        static Optional<Revoked> fromEntries(List<X509CRLEntry> entries) {
            entries.sort(Comparator.comparing(X509CRLEntry::getSerialNumber));

            int size = entries.size();
            BigInteger[] serials = new BigInteger[size];
            long[] revocationDates = new long[size];
            byte[] revocationReasons = new byte[size];

            for (int i = 0; i < size; i++) {
                X509CRLEntry entry = entries.get(i);
                serials[i] = entry.getSerialNumber();

                if (i > 0 && serials[i].equals(serials[i - 1])) {
                    return Optional.empty();
                }

                revocationDates[i] = entry.getRevocationDate().getTime();
                CRLReason reason = entry.getRevocationReason();
                revocationReasons[i] = reason == null ? NO_REASON : (byte) reason.ordinal();
            }

            return Optional.of(new Revoked(serials, revocationDates, revocationReasons));
        }

        Optional<RevokedEntry> find(BigInteger serial) {
            int i = Arrays.binarySearch(serials, serial);

            if (i < 0) {
                return Optional.empty();
            }

            CRLReason reason = revocationReasons[i] == NO_REASON ? null : REASONS[revocationReasons[i]];

            return Optional.of(new RevokedEntry(new Date(revocationDates[i]), reason));
        }
    }
}
