import type { components } from './schema';
import { apiClient } from './client';

export type Schemas = components['schemas'];
export type RequestView = Schemas['RequestView'];
export type BidView = Schemas['BidView'];
export type ShipmentView = Schemas['ShipmentView'];
export type StopView = Schemas['StopView'];
export type VehicleView = Schemas['VehicleView'];
export type DriverView = Schemas['DriverView'];
export type Quote = Schemas['Quote'];
export type CityItem = Schemas['CityItem'];
export type CargoItem = Schemas['CargoItem'];
export type UserInfo = Schemas['UserInfo'];

/** Cliente de la API con el token de la sesión actual (solo servidor). */
export const apiFor = (token: string) => apiClient(token);
