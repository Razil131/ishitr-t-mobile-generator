import React, { useState, useMemo } from 'react';
import { useAdmin } from '../context/AdminContext';
import { InstantDeductModal } from '../components/modals/InstantDeductModal';
import { AnomalyModal } from '../components/modals/AnomalyModal';
import { DelayedEventModal } from '../components/modals/DelayedEventModal';
import { BulkGenerateModal } from '../components/modals/BulkGenerateModal';

type TargetMode = 'user' | 'group';
type BgMode = 'simple' | 'realistic';
type ActiveModal = 'instantDeduct' | 'anomaly' | 'delayed' | 'bulk' | null;

export const EventEmulationView: React.FC = () => {
  const {
    users,
    groups,
    activeGenerations,
    startBackgroundGen,
    stopGeneration,
    showToast
  } = useAdmin();

  const [targetMode, setTargetMode] = useState<TargetMode>('user');
  const [selectedGroupId, setSelectedGroupId] = useState<number | null>(groups[0]?.id || 1);
  const [selectedUserId, setSelectedUserId] = useState<number | null>(users[0]?.id || 1);

  // Background gen settings
  const [bgMode, setBgMode] = useState<BgMode>('simple');
  const [intervalSec, setIntervalSec] = useState<number>(5);
  const [profile, setProfile] = useState<'STANDARD' | 'TRIP'>('STANDARD');

  // Modals state
  const [activeModal, setActiveModal] = useState<ActiveModal>(null);

  // Filter users by selected group
  const filteredUsers = useMemo(() => {
    if (!selectedGroupId) return users;
    return users.filter(u => u.groupId === selectedGroupId);
  }, [users, selectedGroupId]);

  // Keep selected user valid
  const handleGroupChange = (groupId: number) => {
    setSelectedGroupId(groupId);
    const usersInGroup = users.filter(u => u.groupId === groupId);
    if (usersInGroup.length > 0) {
      setSelectedUserId(usersInGroup[0].id);
    } else {
      setSelectedUserId(null);
    }
  };

  const selectedUser = users.find(u => u.id === selectedUserId);
  const selectedGroup = groups.find(g => g.id === selectedGroupId);

  const getTargetLabel = (): string => {
    if (targetMode === 'user' && selectedUser) {
      return `${selectedUser.name} (${selectedUser.phone})`;
    }
    if (targetMode === 'group' && selectedGroup) {
      return selectedGroup.name;
    }
    return 'Не выбрано';
  };

  const handleStartBgGen = async () => {
    if (targetMode === 'user' && !selectedUserId) {
      showToast('Выберите пользователя', 'error');
      return;
    }
    if (targetMode === 'group' && !selectedGroupId) {
      showToast('Выберите группу', 'error');
      return;
    }

    const targetId = targetMode === 'user' ? (selectedUserId as number) : (selectedGroupId as number);
    await startBackgroundGen(
      {
        targetMode,
        targetId,
        bgMode,
        intervalSec,
        profile
      },
      getTargetLabel()
    );
  };

  return (
    <div className="page-container">
      {/* 1. Target Selection Card */}
      <div className="admin-card">
        <h3 className="card-title">Выбор цели</h3>

        <div className="toggle-tab-group">
          <button
            className={`toggle-tab-btn ${targetMode === 'user' ? 'active' : ''}`}
            onClick={() => setTargetMode('user')}
          >
            Для пользователя
          </button>
          <button
            className={`toggle-tab-btn ${targetMode === 'group' ? 'active' : ''}`}
            onClick={() => setTargetMode('group')}
          >
            Для группы
          </button>
        </div>

        {targetMode === 'user' ? (
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '20px' }}>
            <div className="form-group" style={{ marginBottom: 0 }}>
              <label className="form-label">Группа</label>
              <select
                className="input-control"
                value={selectedGroupId || ''}
                onChange={e => handleGroupChange(Number(e.target.value))}
              >
                {groups.map(g => (
                  <option key={g.id} value={g.id}>
                    {g.name} ({g.membersCount} уч.)
                  </option>
                ))}
              </select>
            </div>

            <div className="form-group" style={{ marginBottom: 0 }}>
              <label className="form-label">Пользователь</label>
              <select
                className="input-control"
                value={selectedUserId || ''}
                onChange={e => setSelectedUserId(Number(e.target.value))}
              >
                {filteredUsers.map(u => (
                  <option key={u.id} value={u.id}>
                    {u.name} ({u.phone})
                  </option>
                ))}
              </select>
            </div>
          </div>
        ) : (
          <div className="form-group" style={{ marginBottom: 0 }}>
            <label className="form-label">Группа</label>
            <select
              className="input-control"
              value={selectedGroupId || ''}
              onChange={e => setSelectedGroupId(Number(e.target.value))}
            >
              {groups.map(g => (
                <option key={g.id} value={g.id}>
                  {g.name} ({g.membersCount} уч.)
                </option>
              ))}
            </select>
          </div>
        )}
      </div>

      {/* 2. Instant Actions Grid */}
      <div className="card-heading">Мгновенные действия</div>
      <div className="instant-actions-grid">
        {/* Action 1 */}
        <div className="action-box" onClick={() => setActiveModal('instantDeduct')}>
          <div>
            <div className="action-box-title">Списать ГБ/мин</div>
            <div className="action-box-desc">Мгновенное списание ресурса</div>
          </div>
          <div className="action-box-link">Настроить →</div>
        </div>

        {/* Action 2 */}
        <div className="action-box" onClick={() => setActiveModal('anomaly')}>
          <div>
            <div className="action-box-title">Запустить аномалию</div>
            <div className="action-box-desc">Резкое потребление за 6 часов</div>
          </div>
          <div className="action-box-link">Настроить →</div>
        </div>

        {/* Action 3 */}
        <div className="action-box" onClick={() => setActiveModal('delayed')}>
          <div>
            <div className="action-box-title">Запоздалое событие</div>
            <div className="action-box-desc">Событие с прошедшим временем</div>
          </div>
          <div className="action-box-link">Настроить →</div>
        </div>

        {/* Action 4 */}
        <div className="action-box" onClick={() => setActiveModal('bulk')}>
          <div>
            <div className="action-box-title">Массовая генерация</div>
            <div className="action-box-desc">N событий для всех пользователей</div>
          </div>
          <div className="action-box-link">Настроить →</div>
        </div>
      </div>

      {/* 3. Background Generation Card */}
      <div className="admin-card">
        <h3 className="card-title">Фоновая генерация</h3>

        <div className="toggle-tab-group">
          <button
            className={`toggle-tab-btn ${bgMode === 'simple' ? 'active' : ''}`}
            onClick={() => setBgMode('simple')}
          >
            Простой (интервал)
          </button>
          <button
            className={`toggle-tab-btn ${bgMode === 'realistic' ? 'active' : ''}`}
            onClick={() => setBgMode('realistic')}
          >
            Реалистичный (профиль)
          </button>
        </div>

        {bgMode === 'simple' ? (
          <div className="form-group" style={{ maxWidth: '400px' }}>
            <label className="form-label">Интервал (секунды)</label>
            <input
              type="number"
              className="input-control"
              value={intervalSec}
              onChange={e => setIntervalSec(Math.max(1, parseInt(e.target.value, 10) || 1))}
              min="1"
            />
          </div>
        ) : (
          <div className="form-group" style={{ maxWidth: '400px' }}>
            <label className="form-label">Профиль</label>
            <select
              className="input-control"
              value={profile}
              onChange={e => setProfile(e.target.value as 'STANDARD' | 'TRIP')}
            >
              <option value="STANDARD">Standard — обычное использование</option>
              <option value="TRIP">Trip — симуляция поездки</option>
            </select>
          </div>
        )}

        <button
          className="btn btn-primary"
          style={{ marginTop: '8px' }}
          onClick={handleStartBgGen}
        >
          ▶ Старт фоновой генерации
        </button>
      </div>

      {/* 4. Active Generations Section */}
      <div className="section-header">
        <h2 className="section-title">Активные генерации</h2>
      </div>
      <div className="table-wrapper">
        <table className="admin-table">
          <thead>
            <tr>
              <th>Цель</th>
              <th>Тип</th>
              <th>Режим</th>
              <th>Статус</th>
              <th>Запущено</th>
              <th>Действия</th>
            </tr>
          </thead>
          <tbody>
            {activeGenerations.length === 0 ? (
              <tr>
                <td colSpan={6} style={{ textAlign: 'center', padding: '36px', color: 'var(--text-secondary)' }}>
                  Нет активных генераций
                </td>
              </tr>
            ) : (
              activeGenerations.map(gen => (
                <tr key={gen.id}>
                  <td>{gen.target}</td>
                  <td>{gen.type}</td>
                  <td>{gen.modeLabel}</td>
                  <td>
                    <span className="status-indicator active">
                      <span className="status-dot green" />
                      <span>{gen.status}</span>
                    </span>
                  </td>
                  <td>{gen.startedAt}</td>
                  <td>
                    <button
                      className="btn-danger-pill"
                      onClick={() => stopGeneration(gen.id)}
                    >
                      Стоп
                    </button>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      {/* Modal Dialogs */}
      <InstantDeductModal
        isOpen={activeModal === 'instantDeduct'}
        onClose={() => setActiveModal(null)}
        selectedUserId={selectedUserId}
      />

      <AnomalyModal
        isOpen={activeModal === 'anomaly'}
        onClose={() => setActiveModal(null)}
        selectedUserId={selectedUserId}
        targetLabel={getTargetLabel()}
      />

      <DelayedEventModal
        isOpen={activeModal === 'delayed'}
        onClose={() => setActiveModal(null)}
        selectedUserId={selectedUserId}
      />

      <BulkGenerateModal
        isOpen={activeModal === 'bulk'}
        onClose={() => setActiveModal(null)}
      />
    </div>
  );
};
