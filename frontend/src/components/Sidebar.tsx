import React from 'react';
import { useAdmin } from '../context/AdminContext';
import { NavigationTab } from '../types';

interface NavItemConfig {
  id: NavigationTab;
  label: string;
}

const NAV_ITEMS: NavItemConfig[] = [
  { id: 'dashboard', label: 'Дашборд' },
  { id: 'time', label: 'Управление временем' },
  { id: 'events', label: 'Эмуляция событий' },
  { id: 'tariffs', label: 'Справочники' }
];

export const Sidebar: React.FC = () => {
  const { currentTab, setCurrentTab } = useAdmin();

  return (
    <aside className="app-sidebar">
      {/* Brand logo */}
      <div className="sidebar-logo-area">
        <div className="tinkoff-logo-box">T</div>
        <span className="sidebar-logo-text">Админ-панель</span>
      </div>

      {/* Navigation menu */}
      <nav className="sidebar-nav">
        {NAV_ITEMS.map(item => {
          const isActive = currentTab === item.id;
          return (
            <button
              key={item.id}
              className={`nav-item ${isActive ? 'active' : ''}`}
              onClick={() => setCurrentTab(item.id)}
            >
              <span>{item.label}</span>
            </button>
          );
        })}
      </nav>

      {/* Version footer */}
      <div className="sidebar-footer">
        Версия 1.0.0
      </div>
    </aside>
  );
};
