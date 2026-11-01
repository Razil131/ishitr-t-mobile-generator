import React, { useState } from 'react';
import { useAdmin } from '../context/AdminContext';

function formatTimeOnly(d: Date): string {
  const pad = (n: number) => n.toString().padStart(2, '0');
  return `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
}

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

export const TimeManagementView: React.FC = () => {
  const {
    systemInfo,
    updateMultiplier,
    resetTime,
    pauseTime,
    resumeTime,
    showToast
  } = useAdmin();

  const [inputMultiplier, setInputMultiplier] = useState<string>(
    systemInfo.multiplier > 1 ? systemInfo.multiplier.toString() : '1000'
  );
  const [error, setError] = useState<string | null>(null);

  const handleAccelerate = async () => {
    const val = parseInt(inputMultiplier, 10);
    if (!val || val < 1) {
      setError('Введите корректное значение (минимум 1)');
      return;
    }
    setError(null);
    await updateMultiplier(val);
  };

  const handleReset = async () => {
    await resetTime();
    setInputMultiplier('1000');
    setError(null);
  };

  const handlePause = async () => {
    if (systemInfo.isPaused) {
      showToast('Генерация уже находится на паузе', 'info');
      return;
    }
    await pauseTime();
  };

  const handleResume = async () => {
    if (!systemInfo.isPaused) {
      showToast('Генерация уже активна', 'info');
      return;
    }
    await resumeTime();
  };

  return (
    <div className="page-container">
      {/* Top Hero Card */}
      <div className="time-hero-card">
        <div className="time-hero-title">ВИРТУАЛЬНОЕ ВРЕМЯ</div>
        <div className="time-hero-display">
          {formatTimeOnly(systemInfo.virtualTime)}
        </div>
        <div className="time-hero-multiplier">
          ×{systemInfo.multiplier}
        </div>
        <div className={`time-hero-status ${systemInfo.isPaused ? 'paused' : 'active'}`}>
          <span className={`status-dot ${systemInfo.isPaused ? 'red' : 'green'}`} />
          <span>{systemInfo.isPaused ? 'Генерация на паузе' : 'Генерация активна'}</span>
        </div>
      </div>

      {/* Middle 2-Column Grid */}
      <div className="time-actions-grid">
        {/* Left Card: Acceleration Settings */}
        <div className="admin-card">
          <h3 className="card-title">Настройки ускорения</h3>

          <div className="form-group">
            <label className="form-label">Множитель времени</label>
            <input
              type="number"
              className="input-control"
              value={inputMultiplier}
              onChange={e => {
                setInputMultiplier(e.target.value);
                if (error) setError(null);
              }}
              min="1"
              placeholder="1000"
            />
            {error ? (
              <div className="form-error">{error}</div>
            ) : (
              <div className="form-hint">
                Минимальное значение: 1, Рекомендуемые: 10, 100, 1000
              </div>
            )}
          </div>

          <button
            className="btn btn-primary btn-full btn-lg"
            onClick={handleAccelerate}
          >
            Ускорить время
          </button>
        </div>

        {/* Right Card: Actions */}
        <div className="admin-card">
          <h3 className="card-title">Действия</h3>

          <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
            <button
              className="btn btn-secondary btn-full btn-lg"
              onClick={handleReset}
            >
              Сбросить в реальное время
            </button>

            <button
              className="btn btn-secondary btn-full btn-lg"
              onClick={handlePause}
            >
              Пауза
            </button>

            <button
              className="btn btn-outline-green btn-full btn-lg"
              onClick={handleResume}
            >
              Возобновить
            </button>
          </div>
        </div>
      </div>

      {/* Bottom Card: System Information */}
      <div className="admin-card">
        <h3 className="card-title">Информация о системе</h3>

        <div className="key-value-list">
          <div className="key-value-row">
            <span className="label">Реальное время</span>
            <span className="val">{formatFullDateTime(systemInfo.realTime)}</span>
          </div>

          <div className="key-value-row">
            <span className="label">Виртуальное время</span>
            <span className="val yellow">{formatFullDateTime(systemInfo.virtualTime)}</span>
          </div>

          <div className="key-value-row">
            <span className="label">Множитель</span>
            <span className="val">×{systemInfo.multiplier}</span>
          </div>

          <div className="key-value-row">
            <span className="label">Статус генерации</span>
            <span className={`val ${systemInfo.isPaused ? 'red' : 'green'}`}>
              {systemInfo.isPaused ? 'На паузе' : 'Активна'}
            </span>
          </div>

          <div className="key-value-row">
            <span className="label">Активных потоков</span>
            <span className="val">{systemInfo.activeThreads}</span>
          </div>

          <div className="key-value-row">
            <span className="label">Последнее событие</span>
            <span className="val">{systemInfo.lastEvent || '—'}</span>
          </div>
        </div>
      </div>
    </div>
  );
};
