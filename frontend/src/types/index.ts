export type NavigationTab = 'dashboard' | 'time' | 'events' | 'tariffs';

export interface User {
  id: number;
  phone: string;
  name: string;
  groupId: number;
  groupName: string;
  role: 'owner' | 'member';
  status: 'active' | 'inactive';
}

export interface FamilyGroup {
  id: number;
  name: string;
  ownerId: number;
  ownerName: string;
  status: 'Активна' | 'Неактивна' | 'active';
  membersCount: number;
  maxMembers: number;
  created: string;
}

export interface Tariff {
  id: number;
  name: string;
  traffic: number; // in GB
  minutes: number;
}

export interface SystemInfo {
  realTime: Date;
  virtualTime: Date;
  multiplier: number;
  isPaused: boolean;
  isAccelerated: boolean;
  activeThreads: number;
  lastEvent: string | null;
}

export interface ActiveGeneration {
  id: number;
  target: string;
  targetType: 'user' | 'group';
  type: string;
  mode: string;
  modeLabel: string;
  status: string;
  startedAt: string;
}

export interface InstantDeductPayload {
  userId: number;
  amount: number;
  resourceType: 'TRAFFIC' | 'MINUTES';
}

export interface AnomalyPayload {
  userId: number;
  amount: number;
  durationHours: number;
}

export interface DelayedEventPayload {
  userId: number;
  amount: number;
  hoursAgo: number;
}

export interface BulkGeneratePayload {
  count: number;
  resourceType: 'TRAFFIC' | 'MINUTES';
}

export interface BackgroundGenPayload {
  targetMode: 'user' | 'group';
  targetId: number;
  bgMode: 'simple' | 'realistic';
  intervalSec?: number;
  profile?: 'STANDARD' | 'TRIP';
}

export interface ToastMessage {
  id: string;
  message: string;
  type: 'success' | 'error' | 'info';
}
