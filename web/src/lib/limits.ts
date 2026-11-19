/**
 * Longitudes máximas de los textos libres. Espejan los `@Size` de los DTOs del backend
 * (`backend/src/main/java/**\/infrastructure/web/*Dtos.java`): si cambian allá, cámbialas aquí (F37).
 */
export const LIMITS = {
  legalName: 200,
  fullName: 150,
  originAddress: 200,
  destinationAddress: 200,
  cargoDescription: 300,
  bidNotes: 300,
  tripNotes: 300,
  bookingCargoDescription: 300,
  bookingAddress: 200,
  bookingRejectReason: 300,
  requestCancelReason: 500,
  customerReference: 40,
  contactName: 120,
  contactPhone: 20,
  stopInstructions: 300,
  placeLabel: 80,
  placeHours: 120,
  shipmentReason: 500,
  counterOfferMessage: 500,
  chatMessage: 1000,
  reviewComment: 1000,
  incidentText: 2000,
  smartLoadNotes: 300,
  operatorNotes: 300,
  parameterReason: 300,
  organizationReason: 500,
  organizationNote: 500,
} as const;
