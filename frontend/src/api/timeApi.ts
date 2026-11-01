import { apiRequest } from './client';

interface TimeStatusRaw {
  real_time: string;
  virtual_time: string;
  multiplier: number;
  is_paused: boolean;
}

export interface TimeStatusResponse {
  realTime: string;
  virtualTime: string;
  multiplier: number;
  isPaused: boolean;
  isAccelerated: boolean;
  activeThreads: number;
  lastEvent: string | null;
}

function mapTimeStatus(raw: TimeStatusRaw): TimeStatusResponse {
  return {
    realTime: raw.real_time,
    virtualTime: raw.virtual_time,
    multiplier: raw.multiplier,
    isPaused: raw.is_paused,
    isAccelerated: raw.multiplier > 1,
    activeThreads: 0,
    lastEvent: null
  };
}

export const timeApi = {
  async getTimeStatus(): Promise<TimeStatusResponse> {
    const raw = await apiRequest<TimeStatusRaw>('/time');
    return mapTimeStatus(raw);
  },

  async setMultiplier(multiplier: number): Promise<{ multiplier: number }> {
    const raw = await apiRequest<TimeStatusRaw>('/time/accelerate', {
      method: 'POST',
      body: JSON.stringify({ multiplier })
    });
    return { multiplier: raw.multiplier };
  },

  async pause(): Promise<{ isPaused: boolean }> {
    const raw = await apiRequest<TimeStatusRaw>('/time/pause', {
      method: 'POST'
    });
    return { isPaused: raw.is_paused };
  },

  async resume(): Promise<{ isPaused: boolean }> {
    const raw = await apiRequest<TimeStatusRaw>('/time/resume', {
      method: 'POST'
    });
    return { isPaused: raw.is_paused };
  },

  async reset(): Promise<{ success: boolean }> {
    await apiRequest<TimeStatusRaw>('/time/reset', {
      method: 'POST'
    });
    return { success: true };
  }
};
