import { auth } from '@/features/auth/actions';
import { password } from '@/features/auth/password-actions';
import { requests } from '@/features/requests/actions';
import { marketplace } from '@/features/marketplace/actions';
import { fleet } from '@/features/fleet/actions';
import { shipments } from '@/features/shipments/actions';

import { negotiation } from '@/features/negotiation/actions';
import { reputation } from '@/features/reputation/actions';
import { documents } from '@/features/documents/actions';
import { incidents } from '@/features/incidents/actions';
import { notifications } from '@/features/notifications/actions';
import { trips } from '@/features/trips/actions';
import { operator } from '@/features/operator/actions';
import { routing } from '@/features/routing/actions';
import { organization } from '@/features/organization/actions';

export const server = { auth, password, requests, marketplace, fleet, shipments, negotiation, reputation, documents, incidents, notifications, operator, trips, routing, organization };
