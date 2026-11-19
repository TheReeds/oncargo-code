import { defineAction } from 'astro:actions';
import { z } from 'astro/zod';
import { requireToken, run } from '@/lib/actions';
import {
  archivePayoutAccount, collectCommission, confirmSettlement, createPayoutAccount, disputeSettlement, getSettlement, listCommissionCollections,
  listPayoutAccounts, listSettlements, makePayoutPrimary, registerInvoice, registerPayment, resolveSettlement, settlementSummary,
} from './server';

const id = z.object({ id: z.uuid() });
const date = z.string().regex(/^\d{4}-\d{2}-\d{2}$/, 'Elige la fecha.');
const method = z.enum(['TRANSFER', 'DEPOSIT', 'YAPE', 'PLIN', 'CASH', 'OTHER']);
const status = z.enum(['PENDING_INVOICE', 'INVOICED', 'PAID', 'CONFIRMED', 'DISPUTED']);
// Los topes van como literales (ver la trampa de `lib/limits.ts` en web/AGENTS.md).
const docId = z.uuid().optional();

export const settlements = {
  /** Cobro de un envío; `null` si aún no se entregó. */
  get: defineAction({ accept: 'json', input: id, handler: ({ id }, ctx) => run(async () => (await getSettlement(requireToken(ctx), id)) ?? null) }),
  summary: defineAction({ accept: 'json', input: z.object({}), handler: (_i, ctx) => run(() => settlementSummary(requireToken(ctx))) }),
  list: defineAction({
    accept: 'json',
    input: z.object({ status: z.string().max(80).optional(), overdue: z.boolean().optional(), carrierOrgId: z.uuid().optional(), commissionPending: z.boolean().optional(), page: z.number().int().min(0).optional(), size: z.number().int().min(1).max(100).optional() }),
    handler: (input, ctx) => run(() => listSettlements(requireToken(ctx), input)),
  }),
  invoice: defineAction({
    accept: 'json',
    input: z.object({ id: z.uuid(), invoiceNumber: z.string().trim().min(1, 'Ingresa el número del comprobante.').max(40, 'El número admite hasta 40 caracteres.'), issuedOn: date, documentId: docId }),
    handler: ({ id, ...body }, ctx) => run(() => registerInvoice(requireToken(ctx), id, body)),
  }),
  payment: defineAction({
    accept: 'json',
    input: z.object({ id: z.uuid(), method, operationNumber: z.string().trim().max(40, 'El número admite hasta 40 caracteres.').optional(), paidOn: date, amount: z.number().positive('Ingresa el monto pagado.'), documentId: docId }),
    handler: ({ id, ...body }, ctx) => run(() => registerPayment(requireToken(ctx), id, { ...body, operationNumber: body.operationNumber || undefined })),
  }),
  confirm: defineAction({ accept: 'json', input: id, handler: ({ id }, ctx) => run(() => confirmSettlement(requireToken(ctx), id)) }),
  dispute: defineAction({
    accept: 'json',
    input: z.object({ id: z.uuid(), reason: z.string().trim().min(1, 'Cuéntanos el motivo.').max(500, 'El motivo admite hasta 500 caracteres.') }),
    handler: ({ id, reason }, ctx) => run(() => disputeSettlement(requireToken(ctx), id, reason)),
  }),
  resolve: defineAction({
    accept: 'json',
    input: z.object({ id: z.uuid(), status: status.optional(), note: z.string().trim().max(500, 'La nota admite hasta 500 caracteres.').optional() }),
    handler: ({ id, ...body }, ctx) => run(() => resolveSettlement(requireToken(ctx), id, { status: body.status, note: body.note || undefined })),
  }),

  collectCommission: defineAction({
    accept: 'json',
    input: z.object({ carrierOrgId: z.uuid(), shipmentIds: z.array(z.uuid()).min(1, 'Elige al menos un envío.').max(200, 'Elige hasta 200 envíos por cobro.'), method, operationNumber: z.string().trim().min(1, 'Ingresa el número de operación.').max(40), paidOn: date, note: z.string().trim().max(300).optional() }),
    handler: (input, ctx) => run(() => collectCommission(requireToken(ctx), { ...input, note: input.note || undefined })),
  }),
  commissionCollections: defineAction({
    accept: 'json',
    input: z.object({ carrierOrgId: z.uuid().optional(), page: z.number().int().min(0).optional(), size: z.number().int().min(1).max(100).optional() }),
    handler: (input, ctx) => run(() => listCommissionCollections(requireToken(ctx), input)),
  }),

  payoutAccounts: defineAction({ accept: 'json', input: z.object({}), handler: (_i, ctx) => run(() => listPayoutAccounts(requireToken(ctx))) }),
  addPayoutAccount: defineAction({
    accept: 'json',
    input: z.object({
      kind: z.enum(['BANK', 'YAPE', 'PLIN']), bankName: z.string().trim().max(80, 'El banco admite hasta 80 caracteres.').optional(),
      holderName: z.string().trim().min(1, 'Ingresa el nombre del titular.').max(120, 'El titular admite hasta 120 caracteres.'),
      number: z.string().trim().min(1, 'Ingresa el número.').max(30), primary: z.boolean().optional(),
    }),
    handler: (input, ctx) => run(() => createPayoutAccount(requireToken(ctx), { ...input, bankName: input.kind === 'BANK' ? input.bankName || undefined : undefined })),
  }),
  primaryPayoutAccount: defineAction({ accept: 'json', input: id, handler: ({ id }, ctx) => run(() => makePayoutPrimary(requireToken(ctx), id)) }),
  archivePayoutAccount: defineAction({ accept: 'json', input: id, handler: ({ id }, ctx) => run(() => archivePayoutAccount(requireToken(ctx), id)) }),
};
