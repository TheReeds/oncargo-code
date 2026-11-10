import { auth } from '@/features/auth/actions';
import { requests } from '@/features/requests/actions';
import { marketplace } from '@/features/marketplace/actions';
import { fleet } from '@/features/fleet/actions';
import { shipments } from '@/features/shipments/actions';

export const server = { auth, requests, marketplace, fleet, shipments };
