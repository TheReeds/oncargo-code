package pe.logyx.documents.application;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import pe.logyx.documents.application.port.out.DocumentRepository;
import pe.logyx.documents.application.port.out.DocumentStorage;
import pe.logyx.documents.domain.DocumentUploadPolicy;
import pe.logyx.documents.domain.FileFormat;
import pe.logyx.documents.domain.model.Document;
import pe.logyx.shared.audit.AuditService;
import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shared.tenancy.TenantContext;
import pe.logyx.shipment.application.ShipmentQueries;
import pe.logyx.shipment.application.ShipmentSummary;
import pe.logyx.shipment.domain.model.Shipment;
import pe.logyx.shipment.domain.model.ShipmentStop;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.UUID;

/** Subida de documentos y evidencia de entrega (RF37, RF46, RF47). */
@ApplicationScoped
public class DocumentService {

    @Inject DocumentRepository documents;
    @Inject DocumentStorage storage;
    @Inject ShipmentQueries shipments;
    @Inject AuditService audit;
    @Inject TenantContext tenant;

    @ConfigProperty(name = "logyx.documents.max-size-bytes", defaultValue = "10485760")
    long maxBytes;

    @Transactional
    public DocumentSummary upload(UploadDocumentCommand cmd) {
        // detail() aplica la visibilidad: partes del envío y, si es conductor, solo su envío (404 si no)
        ShipmentSummary sum = shipments.detail(cmd.shipmentId());
        Shipment s = sum.shipment();
        UUID me = tenant.orgId();
        if (!s.isParty(me)) throw BusinessException.notFound("Envío");
        if (s.status() == Shipment.Status.CANCELLED) {
            throw BusinessException.conflict("LOGYX-DOC-006", "El envío está cancelado");
        }
        DocumentUploadPolicy.StopInfo stop = null;
        if (cmd.stopId() != null) {
            ShipmentStop st = sum.stop(cmd.stopId());
            stop = new DocumentUploadPolicy.StopInfo(st.type() == ShipmentStop.Type.DELIVERY,
                    st.status() != ShipmentStop.Status.PENDING);
        }
        DocumentUploadPolicy.ensureCanAttach(cmd.type(), me.equals(s.carrierOrgId()), stop);
        FileFormat format = DocumentUploadPolicy.ensureValidFile(cmd.type(), cmd.sizeBytes(), maxBytes, header(cmd));

        UUID id = UUID.randomUUID();
        String key = Document.storageKeyFor(s.id(), id, format.extension);
        String sha256 = store(key, cmd);
        try {
            Document d = new Document(id, s.id(), cmd.stopId(), s.requesterOrgId(), s.carrierOrgId(), cmd.type(),
                    cleanName(cmd.fileName(), format), format.contentType, cmd.sizeBytes(), sha256, key,
                    tenant.profileId(), me, OffsetDateTime.now());
            documents.insert(d);
            audit.record(tenant.profileId(), me, "DOCUMENT_UPLOADED", "document", d.id(),
                    d.type() + " envio=" + s.id());
            return new DocumentSummary(d);
        } catch (RuntimeException e) {
            storage.delete(key);   // sin fila no debe quedar archivo huérfano
            throw e;
        }
    }

    private static byte[] header(UploadDocumentCommand cmd) {
        try (InputStream in = cmd.content().open()) {
            return in.readNBytes(FileFormat.HEADER_LENGTH);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String store(String key, UploadDocumentCommand cmd) {
        try (InputStream raw = cmd.content().open();
             DigestInputStream in = new DigestInputStream(raw, MessageDigest.getInstance("SHA-256"))) {
            storage.put(key, in);
            return HexFormat.of().formatHex(in.getMessageDigest().digest());
        } catch (IOException e) {
            Log.error("No se pudo guardar el documento " + key, e);
            throw new BusinessException(500, "LOGYX-DOC-500", "No se pudo guardar el archivo");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Nombre para mostrar: sin rutas ni caracteres de control, máx. 120, con la extensión del formato real. */
    static String cleanName(String original, FileFormat format) {
        String n = original == null ? "" : original.replace('\\', '/');
        n = n.substring(n.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}\"]", "").trim();
        int dot = n.lastIndexOf('.');
        String base = dot > 0 ? n.substring(0, dot) : n;
        if (base.isBlank()) base = "documento";
        if (base.length() > 120) base = base.substring(0, 120);
        return base + "." + format.extension;
    }
}
