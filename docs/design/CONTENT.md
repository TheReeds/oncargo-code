# LOGYX — Guía de contenido (voz, tono y microcopy)

Aplica a web, Android y cualquier mensaje de la API que llegue a una pantalla. Español peruano, claro y sin jerga.

## Voz y tono
- **Profesional y cercano**: como un buen coordinador de logística. Directo, sin pomposidad ni diminutivos.
- **Tratamiento — decisión:** usamos **"tú"** en la app móvil (transportistas y conductores: lenguaje corto, usado en el camino, a veces con una mano) y en toda la web. En la web de negocio evitamos además formas marcadas: preferimos frases **neutras/impersonales** ("Publica tu solicitud", "Revisa las ofertas") y nunca "usted" ni "vos". Justificación: "tú" es el registro natural en Perú entre empresas pequeñas, reduce palabras y suena humano; "usted" solo aparece en documentos legales (política de privacidad, términos).
- El tono sube de seriedad con el riesgo: éxito = breve y positivo; error = empático y con salida; acción destructiva = explícito sobre la consecuencia.
- Sin signos de exclamación salvo en celebraciones puntuales (primer envío entregado). Sin emojis en la UI.

## Botones y enlaces: verbo + objeto
| Bien | Mal |
|---|---|
| Publicar solicitud | Enviar / Aceptar / OK |
| Enviar oferta | Ofertar ya |
| Aceptar oferta | Confirmar |
| Cancelar solicitud | Sí |
| Asignar conductor | Guardar |
| Ver detalle · Ver envío | Click aquí |
- El botón de cancelar un diálogo se llama **"Volver"** (no "Cancelar", que es ambiguo cuando la acción es cancelar algo).
- Estado en curso: gerundio + objeto con puntos suspensivos ("Publicando…", "Enviando oferta…"). Usa `loading` del Button.
- Un solo botón primario por vista. Dinero/oportunidad ("Enviar oferta") usa variante `money`.

## Mensajes de error: qué pasó + qué hacer
Estructura: **hecho** + **siguiente paso**. Sin códigos técnicos, sin culpar al usuario.
- "No pudimos publicar la solicitud. Revisa tu conexión e inténtalo de nuevo."
- "El monto es menor al piso de la subasta (S/ 1,200.00). Sube tu oferta para continuar."
- "Ingresa la dirección de recojo." (validación de campo: imperativo, corto, bajo el campo)
- Credenciales: "Correo o contraseña incorrectos. Revísalos e inténtalo de nuevo." (no revelar cuál falló)
- Sesión: "Tu sesión venció. Ingresa de nuevo para continuar."
- Los mensajes de la API (`error.message`) ya vienen en español: muéstralos tal cual. Errores de campo → inline bajo el campo (`Field error`); errores de la acción completa → `toast.error` o `Alert` si el usuario debe actuar sobre la página.

## Éxito y confirmaciones (toasts)
Pasado simple, sujeto implícito, sin "correctamente": "Oferta enviada", "Solicitud publicada", "Solicitud cancelada", "Oferta aceptada", "Conductor asignado", "Vehículo agregado". Segunda línea opcional con el siguiente paso ("La PYME verá tu oferta de inmediato.").

## Estados vacíos
Título (qué falta) + una frase (por qué importa / qué ocurrirá) + acción siguiente.
- "Aún no tienes solicitudes" · "Publica tu primera carga y recibe ofertas de transportistas en minutos." · [Publicar solicitud]
- "No hay cargas con estos filtros" · "Prueba con otra ciudad o amplía el rango de fechas." · [Limpiar filtros]
- "Todavía no has enviado ofertas" · "Revisa el marketplace y oferta por las cargas compatibles con tu flota."

## Confirmaciones destructivas
Usa `confirm({ tone: 'destructive' })`. Título = pregunta con el objeto ("¿Cancelar esta solicitud?"); descripción = consecuencia concreta e irreversibilidad ("Se cerrará la subasta y los transportistas dejarán de ver tu carga. No se puede deshacer."); botón de confirmar repite el verbo+objeto ("Cancelar solicitud"); botón secundario "Volver". Las acciones reversibles no piden confirmación.
Aceptar una oferta es compromiso económico: confirmar con monto y transportista ("¿Aceptar la oferta de S/ 1,850.00?").

## Formatos (siempre con `lib/format.ts` o el equivalente móvil)
| Dato | Formato | Ejemplo |
|---|---|---|
| Dinero | `S/` + espacio, miles con coma, 2 decimales | S/ 1,850.00 |
| Fecha y hora | día + mes corto minúscula sin punto + ` · ` + 24 h, zona America/Lima | 28 nov · 08:00 |
| Fecha sola | día + mes corto | 28 nov |
| Peso | toneladas, 1 decimal máx. (cargas) · kg en formularios | 2.5 t · 3,200 kg |
| Distancia | miles con coma + `km` | 1,010 km |
| Rutas | `Origen → Destino` | Lima → Juliaca |
| Cuenta regresiva | `h min` / `m:ss min` | 2 h 05 min · 4:30 min |
| Estados | etiqueta corta capitalizada (StatusPill) | Por recoger, En tránsito |
Cifras técnicas (placas, km, toneladas, IDs, montos en tabla) en tipografía mono con números tabulares.

## Glosario (usa estos términos, solo estos)
| Término | Significado | No uses |
|---|---|---|
| **Carga** | Lo que se transporta (tipo, peso, descripción) | mercadería, paquete, pedido |
| **Solicitud** | Lo que publica la PYME: ruta + carga + fecha para recibir ofertas | pedido, orden, requerimiento, "flete publicado" |
| **Oferta** | Precio que propone un transportista por una solicitud | cotización (reservado a la estimación del sistema), propuesta, puja |
| **Subasta** | Ventana de tiempo en que los transportistas ofertan (inversa: gana el menor precio razonable) | licitación, remate |
| **Envío** | Lo que existe tras aceptar una oferta: se ejecuta y se rastrea | viaje (salvo en lenguaje del conductor: "viaje activo"), despacho |
| **Parada** | Punto de recojo o entrega dentro de un envío | escala, punto |
| **Transportista** | Empresa que oferta y presta el servicio | camionero, fletero, proveedor |
| **Conductor** | Persona que maneja y ejecuta el envío | chofer, driver, piloto |
| **PYME** | Empresa que publica solicitudes (en UI: "empresa" o "tu empresa" cuando sea natural) | cliente, shipper, generador de carga |
| **Precio sugerido / Piso** | Referencia calculada / mínimo permitido en la subasta | tarifa, precio base |
| **Cotización estimada** | Desglose en vivo del sistema al crear la solicitud | presupuesto |
Anglicismos prohibidos en UI: *marketplace* (en la web decimos "Cargas disponibles" para el rol transportista; "Marketplace" solo en documentos técnicos), *tracking* ("Seguimiento"), *bid* ("oferta"), *dashboard* ("Panel"), *feedback*, *submit*.

## Mayúsculas y puntuación
- Mayúscula solo en la primera palabra de títulos, botones, etiquetas y menús (estilo oración). Nombres propios y siglas (RUC, PYME) como corresponde.
- Sin punto final en botones, etiquetas, títulos y toasts de una línea; con punto en frases completas de ayuda y errores.
- Signos de apertura obligatorios (¿ ¡). Comillas latinas o ninguna.
- Etiquetas de campo: sustantivo ("Peso", "Fecha de recojo"), sin dos puntos. Ayuda bajo el campo, en `ink-500`.
- Placeholders: ejemplo realista del Perú ("Av. Argentina 1234, Callao"), nunca repiten la etiqueta.
