import React from 'react';
import { useAdmin } from '../context/AdminContext';

const TAB_TITLES: Record<string, string> = {
  dashboard: 'Дашборд',
  time: 'Управление временем',
  events: 'Эмуляция событий',
  tariffs: 'Справочники'
};

function formatFullDateTime(d: Date): string {
  const pad = (n: number) => n.toString().padStart(2, '0');
  const day = pad(d.getDate());
  const month = pad(d.getMonth() + 1);
  const year = d.getFullYear();
  const hours = pad(d.getHours());
  const minutes = pad(d.getMinutes());
  const seconds = pad(d.getSeconds());
  return `${day}.${month}.${year} ${hours}:${minutes}:${seconds}`;
}

export const Header: React.FC = () => {
  const { currentTab, systemInfo } = useAdmin();

  const title = TAB_TITLES[currentTab] || 'Админ-панель';
  const realStr = formatFullDateTime(systemInfo.realTime);
  const virtualStr = formatFullDateTime(systemInfo.virtualTime);

  return (
    <header className="app-header">
      <h1 className="header-page-title">{title}</h1>

      <div className="header-badges">
        {/* Real Time Badge */}
        <div className="badge-pill real">
          <span>Реальное: {realStr}</span>
        </div>

        {/* Virtual Time Badge */}
        <div className={`badge-pill virtual ${systemInfo.isPaused ? 'paused' : ''}`}>
          <span>Виртуальное: {virtualStr}</span>
        </div>

        {/* Global Status Badge */}
        <div className={`badge-pill ${systemInfo.isPaused ? 'status-paused' : 'status-active'}`}>
          <span className={`status-dot ${systemInfo.isPaused ? 'red' : 'green'}`} />
          <span>{systemInfo.isPaused ? 'На паузе' : 'Активна'}</span>
        </div>
      </div>
    </header>
  );
};
