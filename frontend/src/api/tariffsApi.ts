import { apiRequest } from './client';
import { Tariff } from '../types';

interface TariffRaw {
  package_id: number;
  name: string;
  total_traffic: number;
  total_minutes: number;
}

function mapTariff(raw: TariffRaw): Tariff {
  return {
    id: raw.package_id,
    name: raw.name,
    traffic: raw.total_traffic,
    minutes: raw.total_minutes
  };
}

export const tariffsApi = {
  async getTariffs(): Promise<Tariff[]> {
    try {
      const raw = await apiRequest<TariffRaw[]>('/tariffs', {}, true);
      return (raw || []).map(mapTariff);
    } catch (e) {
      console.warn('[API] Failed to fetch tariffs, returning empty list:', e);
      return [];
    }
  },

  async createTariff(data: Omit<Tariff, 'id'>): Promise<Tariff> {
    const raw = await apiRequest<TariffRaw>('/tariffs', {
      method: 'POST',
      body: JSON.stringify({
        name: data.name,
        total_traffic: data.traffic,
        total_minutes: data.minutes
      })
    }, true);
    return mapTariff(raw);
  },

  async deleteTariff(id: number): Promise<void> {
    await apiRequest<void>(`/tariffs/${id}`, {
      method: 'DELETE'
    }, true);
  }
};
