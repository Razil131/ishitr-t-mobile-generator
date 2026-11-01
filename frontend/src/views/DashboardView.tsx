import React from 'react';
import { useAdmin } from '../context/AdminContext';

function formatTimeOnly(d: Date): string {
  const pad = (n: number) => n.toString().padStart(2, '0');
  return `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
}

function formatDateRu(d: Date): string {
  return d.toLocaleDateString('ru-RU', {
    day: 'numeric',
    month: 'long',
    year: 'numeric'
  });
}

export const DashboardView: React.FC = () => {
  const {
    systemInfo,
    users,
    groups,
    tariffs,
    deleteTariff,
    refreshUsers,
    refreshGroups,
    refreshTariffs,
    setCurrentTab
  } = useAdmin();

  return (
    <div className="page-container">
      {/* 3 Top Metric Cards in Desktop 16:9 Grid */}
      <div className="dashboard-metrics-grid">
        {/* Card 1: Virtual Time */}
        <div
          className="metric-card"
          style={{ cursor: 'pointer' }}
          onClick={() => setCurrentTab('time')}
          title="Нажмите, чтобы перейти к управлению временем"
        >
          <div>
            <div className="metric-label">ВИРТУАЛЬНОЕ ВРЕМЯ</div>
            <div className="metric-val yellow">
              {formatTimeOnly(systemInfo.virtualTime)}
            </div>
          </div>
          <div className="metric-sub">
            <span>Множитель: <strong>×{systemInfo.multiplier}</strong></span>
          </div>
        </div>

        {/* Card 2: Real Time */}
        <div className="metric-card">
          <div>
            <div className="metric-label">РЕАЛЬНОЕ ВРЕМЯ</div>
            <div className="metric-val white">
              {formatTimeOnly(systemInfo.realTime)}
            </div>
          </div>
          <div className="metric-sub">
            <span>{formatDateRu(systemInfo.realTime)}</span>
          </div>
        </div>

        {/* Card 3: Status */}
        <div className="metric-card">
          <div>
            <div className="metric-label">СТАТУС ГЕНЕРАЦИИ</div>
            <div className={`metric-val ${systemInfo.isPaused ? 'red' : 'green'}`}>
              {systemInfo.isPaused ? 'На паузе' : 'Активна'}
            </div>
          </div>
          <div className="metric-sub">
            <span>Фоновых потоков: {systemInfo.activeThreads}</span>
          </div>
        </div>
      </div>

      {/* Users Section */}
      <div className="section-header">
        <h2 className="section-title">Пользователи</h2>
        <button className="btn-refresh" onClick={refreshUsers}>
          Обновить
        </button>
      </div>
      <div className="table-wrapper">
        <table className="admin-table">
          <thead>
            <tr>
              <th>ID</th>
              <th>ТЕЛЕФОН</th>
              <th>ИМЯ</th>
              <th>ГРУППА</th>
              <th>РОЛЬ</th>
              <th>СТАТУС</th>
            </tr>
          </thead>
          <tbody>
            {users.map(u => (
              <tr key={u.id}>
                <td>{u.id}</td>
                <td>{u.phone}</td>
                <td>{u.name}</td>
                <td>{u.groupName}</td>
                <td>
                  <span className={`role-badge ${u.role}`}>
                    {u.role === 'owner' ? 'Владелец' : 'Участник'}
                  </span>
                </td>
                <td>
                  <span className="status-indicator active">
                    <span className="status-dot green" />
                    <span>Активен</span>
                  </span>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* Family Groups Section */}
      <div className="section-header">
        <h2 className="section-title">Семейные группы</h2>
        <button className="btn-refresh" onClick={refreshGroups}>
          Обновить
        </button>
      </div>
      <div className="table-wrapper">
        <table className="admin-table">
          <thead>
            <tr>
              <th>ID</th>
              <th>НАЗВАНИЕ</th>
              <th>ВЛАДЕЛЕЦ</th>
              <th>УЧАСТНИКИ</th>
              <th>СТАТУС</th>
              <th>СОЗДАНА</th>
            </tr>
          </thead>
          <tbody>
            {groups.map(g => (
              <tr key={g.id}>
                <td>{g.id}</td>
                <td>{g.name}</td>
                <td>{g.ownerName}</td>
                <td>{g.membersCount} из {g.maxMembers}</td>
                <td>
                  <span className="status-indicator active">
                    <span className="status-dot green" />
                    <span>Активна</span>
                  </span>
                </td>
                <td>{g.created}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* Tariffs Section */}
      <div className="section-header">
        <h2 className="section-title">Тарифы</h2>
        <button className="btn-refresh" onClick={refreshTariffs}>
          Обновить
        </button>
      </div>
      <div className="table-wrapper">
        <table className="admin-table">
          <thead>
            <tr>
              <th>ID</th>
              <th>НАЗВАНИЕ</th>
              <th>ТРАФИК (ГБ)</th>
              <th>МИНУТЫ</th>
              <th>ДЕЙСТВИЯ</th>
            </tr>
          </thead>
          <tbody>
            {tariffs.map(t => (
              <tr key={t.id}>
                <td>{t.id}</td>
                <td>{t.name}</td>
                <td>{t.traffic}</td>
                <td>{t.minutes}</td>
                <td>
                  <button
                    className="btn-danger-pill"
                    onClick={() => deleteTariff(t.id)}
                  >
                    Удалить
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
};
