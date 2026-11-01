import React, { createContext, useContext, useState, useEffect, useRef, useCallback } from 'react';
import {
  NavigationTab,
  User,
  FamilyGroup,
  Tariff,
  SystemInfo,
  ActiveGeneration,
  InstantDeductPayload,
  AnomalyPayload,
  DelayedEventPayload,
  BulkGeneratePayload,
  BackgroundGenPayload,
  ToastMessage
} from '../types';
import {
  usersApi,
  tariffsApi,
  timeApi,
  eventsApi
} from '../api';

interface AdminContextType {
  currentTab: NavigationTab;
  setCurrentTab: (tab: NavigationTab) => void;
  systemInfo: SystemInfo;
  users: User[];
  groups: FamilyGroup[];
  tariffs: Tariff[];
  activeGenerations: ActiveGeneration[];
  toasts: ToastMessage[];
  showToast: (message: string, type?: 'success' | 'error' | 'info') => void;
  removeToast: (id: string) => void;
  // Time controls
  updateMultiplier: (multiplier: number) => Promise<void>;
  resetTime: () => Promise<void>;
  pauseTime: () => Promise<void>;
  resumeTime: () => Promise<void>;
  togglePause: () => Promise<void>;
  // Tariffs
  createTariff: (tariff: Omit<Tariff, 'id'>) => Promise<boolean>;
  deleteTariff: (id: number) => Promise<void>;
  refreshTariffs: () => Promise<void>;
  // Users & Groups
  refreshUsers: () => Promise<void>;
  refreshGroups: () => Promise<void>;
  // Events
  instantDeduct: (payload: InstantDeductPayload) => Promise<void>;
  startAnomaly: (payload: AnomalyPayload, targetLabel: string) => Promise<void>;
  sendDelayedEvent: (payload: DelayedEventPayload) => Promise<void>;
  bulkGenerate: (payload: BulkGeneratePayload) => Promise<void>;
  startBackgroundGen: (payload: BackgroundGenPayload, targetLabel: string) => Promise<void>;
  stopGeneration: (id: number) => Promise<void>;
}

const AdminContext = createContext<AdminContextType | null>(null);

export const AdminProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [currentTab, setCurrentTab] = useState<NavigationTab>('dashboard');
  
  // Real-time and Virtual-time states
  const [systemInfo, setSystemInfo] = useState<SystemInfo>({
    realTime: new Date(),
    virtualTime: new Date(),
    multiplier: 1,
    isPaused: false,
    isAccelerated: false,
    activeThreads: 0,
    lastEvent: null
  });

  const [users, setUsers] = useState<User[]>([]);
  const [groups, setGroups] = useState<FamilyGroup[]>([]);
  const [tariffs, setTariffs] = useState<Tariff[]>([]);
  const [activeGenerations, setActiveGenerations] = useState<ActiveGeneration[]>([]);
  const [toasts, setToasts] = useState<ToastMessage[]>([]);

  const lastRealTimeRef = useRef<number>(Date.now());
  const systemInfoRef = useRef(systemInfo);
  systemInfoRef.current = systemInfo;

  // Toast notification helper
  const showToast = useCallback((message: string, type: 'success' | 'error' | 'info' = 'success') => {
    const id = Math.random().toString(36).substring(2, 9);
    setToasts(prev => [...prev, { id, message, type }]);
    setTimeout(() => {
      setToasts(prev => prev.filter(t => t.id !== id));
    }, 3200);
  }, []);

  const removeToast = useCallback((id: string) => {
    setToasts(prev => prev.filter(t => t.id !== id));
  }, []);

  // Initial data loading
  useEffect(() => {
    usersApi.getUsers().then(setUsers);
    usersApi.getFamilyGroups().then(setGroups);
    tariffsApi.getTariffs().then(setTariffs);
    eventsApi.getActiveGenerations().then(setActiveGenerations);
    // Poll active generations every 3 seconds
    const genInterval = setInterval(() => {
      eventsApi.getActiveGenerations().then(setActiveGenerations).catch(() => {});
    }, 3000);

    // Load real time status from backend on startup
    timeApi.getTimeStatus().then(status => {
      setSystemInfo(prev => ({
        ...prev,
        realTime: new Date(status.realTime),
        virtualTime: new Date(status.virtualTime),
        multiplier: status.multiplier,
        isPaused: status.isPaused,
        isAccelerated: status.isAccelerated
      }));
    }).catch(e => console.warn('[API] Failed to load initial time status:', e));
    return () => clearInterval(genInterval);
  }, []);

  // Real-time timer ticker
  useEffect(() => {
    lastRealTimeRef.current = Date.now();
    const interval = setInterval(() => {
      const now = Date.now();
      const delta = now - lastRealTimeRef.current;
      lastRealTimeRef.current = now;

      setSystemInfo(prev => {
        let nextVirtual: Date;
        if (prev.isPaused) {
          nextVirtual = prev.virtualTime;
        } else if (prev.isAccelerated) {
          nextVirtual = new Date(prev.virtualTime.getTime() + delta * prev.multiplier);
        } else {
          nextVirtual = new Date(now);
        }

        return {
          ...prev,
          realTime: new Date(now),
          virtualTime: nextVirtual,
          activeThreads: activeGenerations.length
        };
      });
    }, 1000);

    return () => clearInterval(interval);
  }, [activeGenerations.length]);

  // Time control actions
  const updateMultiplier = async (multiplier: number) => {
    try {
      await timeApi.setMultiplier(multiplier);
      setSystemInfo(prev => ({
        ...prev,
        multiplier,
        isAccelerated: true,
        isPaused: false
      }));
      showToast(`Время ускорено в ${multiplier} раз`, 'success');
    } catch {
      showToast('Ошибка при ускорении времени', 'error');
    }
  };

  const resetTime = async () => {
    try {
      await timeApi.reset();
      const now = new Date();
      setSystemInfo(prev => ({
        ...prev,
        realTime: now,
        virtualTime: now,
        multiplier: 1,
        isAccelerated: false,
        isPaused: false
      }));
      showToast('Время сброшено до реального', 'success');
    } catch {
      showToast('Ошибка при сбросе времени', 'error');
    }
  };

  const pauseTime = async () => {
    try {
      await timeApi.pause();
      setSystemInfo(prev => ({ ...prev, isPaused: true }));
      showToast('Генерация приостановлена', 'info');
    } catch {
      showToast('Ошибка при остановке времени', 'error');
    }
  };

  const resumeTime = async () => {
    try {
      await timeApi.resume();
      setSystemInfo(prev => ({ ...prev, isPaused: false }));
      showToast('Генерация возобновлена', 'success');
    } catch {
      showToast('Ошибка при возобновлении времени', 'error');
    }
  };

  const togglePause = async () => {
    if (systemInfo.isPaused) {
      await resumeTime();
    } else {
      await pauseTime();
    }
  };

  // Tariff actions
  const createTariff = async (tariffData: Omit<Tariff, 'id'>): Promise<boolean> => {
    try {
      const created = await tariffsApi.createTariff(tariffData);
      setTariffs(prev => [...prev, created]);
      showToast(`Тариф "${created.name}" успешно создан`, 'success');
      return true;
    } catch {
      showToast('Не удалось создать тариф', 'error');
      return false;
    }
  };

  const deleteTariff = async (id: number) => {
    try {
      await tariffsApi.deleteTariff(id);
      setTariffs(prev => prev.filter(t => t.id !== id));
      showToast('Тариф удалён', 'info');
    } catch (e) {
      const message = e instanceof Error ? e.message : 'Ошибка при удалении тарифа';
      showToast(message, 'error');
    }
  };

  const refreshTariffs = async () => {
    const list = await tariffsApi.getTariffs();
    setTariffs(list);
    showToast('Данные тарифов обновлены', 'success');
  };

  const refreshUsers = async () => {
    const list = await usersApi.getUsers();
    setUsers(list);
    showToast('Данные пользователей обновлены', 'success');
  };

  const refreshGroups = async () => {
    const list = await usersApi.getFamilyGroups();
    setGroups(list);
    showToast('Данные групп обновлены', 'success');
  };

  // Event emulation actions
  const instantDeduct = async (payload: InstantDeductPayload) => {
    try {
      await eventsApi.instantDeduct(payload);
      const user = users.find(u => u.id === payload.userId);
      const unit = payload.resourceType === 'TRAFFIC' ? 'ГБ' : 'мин';
      const timeStr = systemInfo.virtualTime.toLocaleTimeString('ru-RU');
      setSystemInfo(prev => ({ ...prev, lastEvent: `${timeStr} — Списание ${payload.amount} ${unit}` }));
      showToast(`Событие опубликовано: списание ${payload.amount} ${unit} для ${user?.name || 'пользователя'}`, 'success');
    } catch {
      showToast('Ошибка при отправке события', 'error');
    }
  };

  const startAnomaly = async (payload: AnomalyPayload, targetLabel: string) => {
    try {
      const newGen = await eventsApi.startAnomaly(payload, targetLabel);
      setActiveGenerations(prev => [...prev, newGen]);
      const timeStr = systemInfo.virtualTime.toLocaleTimeString('ru-RU');
      setSystemInfo(prev => ({ ...prev, lastEvent: `${timeStr} — Аномалия ${payload.amount} ГБ` }));
      showToast(`Аномалия запущена: ${payload.amount} ГБ за ${payload.durationHours} виртуальных часов`, 'success');
    } catch {
      showToast('Ошибка при запуске аномалии', 'error');
    }
  };

  const sendDelayedEvent = async (payload: DelayedEventPayload) => {
    try {
      await eventsApi.sendDelayedEvent(payload);
      const eventTime = new Date(systemInfo.virtualTime.getTime() - payload.hoursAgo * 3600000);
      const timeStr = eventTime.toLocaleTimeString('ru-RU');
      setSystemInfo(prev => ({ ...prev, lastEvent: `${timeStr} (запоздалое) — ${payload.amount} ГБ` }));
      showToast(`Запоздалое событие опубликовано (время: ${timeStr})`, 'success');
    } catch {
      showToast('Ошибка при отправке запоздалого события', 'error');
    }
  };

  const bulkGenerate = async (payload: BulkGeneratePayload) => {
    try {
      const res = await eventsApi.bulkGenerate(payload);
      const timeStr = systemInfo.virtualTime.toLocaleTimeString('ru-RU');
      setSystemInfo(prev => ({ ...prev, lastEvent: `${timeStr} — Массовая генерация (${res.generatedCount} соб.)` }));
      showToast(`Сгенерировано ${payload.count} событий для ${users.length} пользователей`, 'success');
    } catch {
      showToast('Ошибка при массовой генерации', 'error');
    }
  };

  const startBackgroundGen = async (payload: BackgroundGenPayload, targetLabel: string) => {
    try {
      const newGen = await eventsApi.startBackgroundGeneration(payload, targetLabel);
      setActiveGenerations(prev => [...prev, newGen]);
      showToast(`Фоновая генерация запущена (${newGen.modeLabel})`, 'success');
    } catch {
      showToast('Ошибка при запуске фоновой генерации', 'error');
    }
  };

  const stopGeneration = async (id: number) => {
    try {
      const targetGen = activeGenerations.find(g => g.id === id);
      if (targetGen?.type === 'Аномалия') {
        await eventsApi.stopAnomaly(id);
      } else {
        await eventsApi.stopGeneration(id);
      }
      setActiveGenerations(prev => prev.filter(g => g.id !== id));
      showToast(`Генерация остановлена: ${targetGen?.target || id}`, 'success');
    } catch {
      showToast('Ошибка при остановке генерации', 'error');
    }
  };

  return (
    <AdminContext.Provider
      value={{
        currentTab,
        setCurrentTab,
        systemInfo,
        users,
        groups,
        tariffs,
        activeGenerations,
        toasts,
        showToast,
        removeToast,
        updateMultiplier,
        resetTime,
        pauseTime,
        resumeTime,
        togglePause,
        createTariff,
        deleteTariff,
        refreshTariffs,
        refreshUsers,
        refreshGroups,
        instantDeduct,
        startAnomaly,
        sendDelayedEvent,
        bulkGenerate,
        startBackgroundGen,
        stopGeneration
      }}
    >
      {children}
    </AdminContext.Provider>
  );
};

export const useAdmin = () => {
  const context = useContext(AdminContext);
  if (!context) {
    throw new Error('useAdmin must be used within an AdminProvider');
  }
  return context;
};
