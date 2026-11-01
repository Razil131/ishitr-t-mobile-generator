import { apiRequest } from './client';
import {
  InstantDeductPayload,
  AnomalyPayload,
  DelayedEventPayload,
  BulkGeneratePayload,
  BackgroundGenPayload,
  ActiveGeneration
} from '../types';

interface TaskInfoRaw {
  user_id: number;
  resource_type: string;
  mode: string;
  profile_type: string;
  interval_seconds: number;
  task_type?: string;
}

interface GenerationStatusRaw {
  active_tasks: TaskInfoRaw[];
}

function mapTaskToGeneration(raw: TaskInfoRaw): ActiveGeneration {
  const isAnomaly = raw.task_type === 'ANOMALY';
  const isSimple = raw.mode === 'SIMPLE';
  const modeLabel = isAnomaly
    ? 'Аномалия'
    : isSimple
      ? `Простой (${raw.interval_seconds}с)`
      : raw.profile_type === 'TRIP' ? 'Поездка' : 'Стандарт';

  return {
    id: raw.user_id,
    target: `User #${raw.user_id}`,
    targetType: 'user',
    type: isAnomaly ? 'Аномалия' : 'Фоновая',
    mode: isAnomaly ? `${raw.resource_type} ANOMALY` : `${raw.resource_type} ${raw.mode}`,
    modeLabel,
    status: 'Активна',
    startedAt: ''
  };
}

export const eventsApi = {
  async instantDeduct(payload: InstantDeductPayload): Promise<{ success: boolean; eventId: string }> {
    const res = await apiRequest<{ event_id: string }>('/events/instant-deduct', {
      method: 'POST',
      body: JSON.stringify({
        user_id: payload.userId,
        resource_type: payload.resourceType || 'TRAFFIC',
        amount: payload.amount
      })
    });
    return { success: true, eventId: res.event_id };
  },

  async startAnomaly(payload: AnomalyPayload, targetLabel: string): Promise<ActiveGeneration> {
    await apiRequest<Record<string, unknown>>('/events/anomaly/user/start', {
      method: 'POST',
      body: JSON.stringify({
        user_id: payload.userId,
        resource_type: 'TRAFFIC'
      })
    });
    return {
      id: payload.userId, target: targetLabel, targetType: 'user',
      type: 'Аномалия', mode: `TRAFFIC ${payload.amount}GB`,
      modeLabel: `Аномалия ${payload.amount}ГБ`, status: 'Активна',
      startedAt: new Date().toLocaleTimeString('ru-RU')
    };
  },

  async sendDelayedEvent(payload: DelayedEventPayload): Promise<{ success: boolean; eventId: string }> {
    const res = await apiRequest<{ event_id: string }>('/events/delayed', {
      method: 'POST',
      body: JSON.stringify({
        user_id: payload.userId,
        resource_type: 'TRAFFIC',
        amount: payload.amount,
        hours_ago: payload.hoursAgo
      })
    });
    return { success: true, eventId: res.event_id };
  },

  async bulkGenerate(payload: BulkGeneratePayload): Promise<{ success: boolean; generatedCount: number }> {
    const res = await apiRequest<{
      total_active_users: number; started_count: number;
      ignored_count: number; message: string;
    }>('/events/background/all/start', {
      method: 'POST',
      body: JSON.stringify({
        resource_type: payload.resourceType, mode: 'SIMPLE', interval_seconds: 60
      })
    });
    return { success: true, generatedCount: res.started_count };
  },

  async startBackgroundGeneration(payload: BackgroundGenPayload, targetLabel: string): Promise<ActiveGeneration> {
    const body: Record<string, unknown> = {
      user_id: payload.targetId,
      resource_type: 'TRAFFIC',
      mode: payload.bgMode === 'simple' ? 'SIMPLE' : 'REALISTIC'
    };
    if (payload.bgMode === 'simple') {
      body.interval_seconds = payload.intervalSec || 5;
    } else {
      body.profile_type = payload.profile || 'STANDARD';
    }

    await apiRequest<Record<string, string>>('/events/background/user/start', {
      method: 'POST', body: JSON.stringify(body)
    });

    const modeLabel = payload.bgMode === 'simple'
      ? `Простой (${payload.intervalSec || 5}с)`
      : payload.profile === 'TRIP' ? 'Поездка' : 'Стандарт';

    return {
      id: payload.targetId, target: targetLabel, targetType: payload.targetMode,
      type: 'Фоновая', mode: payload.bgMode === 'simple' ? 'SIMPLE' : 'REALISTIC',
      modeLabel, status: 'Активна', startedAt: new Date().toLocaleTimeString('ru-RU')
    };
  },

  async stopGeneration(id: number): Promise<void> {
    await apiRequest<void>('/events/background/user/stop', {
      method: 'POST',
      body: JSON.stringify({ user_id: id, resource_type: 'TRAFFIC' })
    });
  },

  async stopAnomaly(userId: number): Promise<void> {
    await apiRequest<void>('/events/anomaly/user/stop', {
      method: 'POST',
      body: JSON.stringify({ user_id: userId })
    });
  },

  async getActiveGenerations(): Promise<ActiveGeneration[]> {
    const raw = await apiRequest<GenerationStatusRaw>('/events/background/status');
    return (raw.active_tasks || []).map(task => mapTaskToGeneration(task));
  }
};
