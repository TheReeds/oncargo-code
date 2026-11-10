package pe.logyx.documents.application;

import pe.logyx.documents.domain.model.Document;

/** Documento tal como se lista (sin la clave interna de almacenamiento fuera del módulo). */
public record DocumentSummary(Document document) {}
