package pe.logyx.marketplace.domain.model;

/**
 * Estado de una oferta de respaldo (roadmap 2.9): AVAILABLE guardada al adjudicar; REQUESTED la PYME pidió al
 * transportista que confirme; CONFIRMED / DECLINED su respuesta; USED se adjudicó a esta oferta.
 */
public enum BackupState { AVAILABLE, REQUESTED, CONFIRMED, DECLINED, USED }
