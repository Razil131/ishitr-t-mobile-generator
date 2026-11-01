import { apiRequest } from './client';
import { User, FamilyGroup } from '../types';

interface UserRaw {
  user_id: number;
  phone_number: string;
  name?: string;
  group_id?: number;
  group_name?: string;
  role?: string;
  status?: string;
}

interface GroupRaw {
  group_id: number;
  owner_id: number;
  owner_name?: string;
  status: string;
  created_at: string;
  members_count: number;
  name?: string;
}

function mapUser(raw: UserRaw): User {
  return {
    id: raw.user_id,
    phone: raw.phone_number || '',
    name: raw.name || `User #${raw.user_id}`,
    groupId: raw.group_id ?? 0,
    groupName: raw.group_name || '',
    role: (raw.role?.toLowerCase() as 'owner' | 'member') || 'member',
    status: (raw.status?.toLowerCase() as 'active' | 'inactive') || 'active'
  };
}

function mapGroup(raw: GroupRaw): FamilyGroup {
  const statusMap: Record<string, 'Активна' | 'Неактивна'> = {
    ACTIVE: 'Активна',
    DISBANDED: 'Неактивна'
  };
  return {
    id: raw.group_id,
    name: raw.name || `Group #${raw.group_id}`,
    ownerId: raw.owner_id,
    ownerName: raw.owner_name || `Owner #${raw.owner_id}`,
    status: statusMap[raw.status] || 'Активна',
    membersCount: raw.members_count,
    maxMembers: 4,
    created: raw.created_at ? new Date(raw.created_at).toLocaleDateString('ru-RU') : ''
  };
}

export const usersApi = {
  async getUsers(): Promise<User[]> {
    try {
      const raw = await apiRequest<UserRaw[]>('/users', {}, true);
      return (raw || []).map(mapUser);
    } catch (e) {
      console.warn('[API] Failed to fetch users, returning empty list:', e);
      return [];
    }
  },

  async getFamilyGroups(): Promise<FamilyGroup[]> {
    try {
      const raw = await apiRequest<GroupRaw[]>('/groups', {}, true);
      return (raw || []).map(mapGroup);
    } catch (e) {
      console.warn('[API] Failed to fetch groups, returning empty list:', e);
      return [];
    }
  }
};
