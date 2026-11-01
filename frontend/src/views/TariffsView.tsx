import React, { useState } from 'react';
import { useAdmin } from '../context/AdminContext';

export const TariffsView: React.FC = () => {
  const {
    tariffs,
    createTariff,
    deleteTariff,
    refreshTariffs,
    showToast
  } = useAdmin();

  const [name, setName] = useState('');
  const [traffic, setTraffic] = useState('');
  const [minutes, setMinutes] = useState('');

  const [errors, setErrors] = useState<{
    name?: string;
    traffic?: string;
    minutes?: string;
  }>({});

  // Compute statistics dynamically
  const totalTariffsCount = tariffs.length;
  const totalTraffic = tariffs.reduce((sum, t) => sum + t.traffic, 0);
  const totalMinutes = tariffs.reduce((sum, t) => sum + t.minutes, 0);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const newErrors: typeof errors = {};

    const trimmedName = name.trim();
    if (!trimmedName) {
      newErrors.name = 'Название тарифа обязательно';
    } else if (tariffs.some(t => t.name.toLowerCase() === trimmedName.toLowerCase())) {
      newErrors.name = 'Тариф с таким названием уже существует';
    }

    const parsedTraffic = parseFloat(traffic);
    if (isNaN(parsedTraffic) || parsedTraffic < 0) {
      newErrors.traffic = 'Введите корректное значение трафика';
    }

    const parsedMinutes = parseInt(minutes, 10);
    if (isNaN(parsedMinutes) || parsedMinutes < 0) {
      newErrors.minutes = 'Введите корректное значение минут';
    }

    if (Object.keys(newErrors).length > 0) {
      setErrors(newErrors);
      showToast('Пожалуйста, заполните все обязательные поля корректно', 'error');
      return;
    }

    setErrors({});
    const success = await createTariff({
      name: trimmedName,
      traffic: parsedTraffic,
      minutes: parsedMinutes
    });

    if (success) {
      setName('');
      setTraffic('');
      setMinutes('');
    }
  };

  return (
    <div className="page-container">
      {/* Top 2-Column Grid */}
      <div className="tariffs-top-grid">
        {/* Left Card: Creation Form */}
        <div className="admin-card">
          <h3 className="card-title">Создание тарифа</h3>

          <form onSubmit={handleSubmit}>
            <div className="form-group">
              <label className="form-label">
                Название тарифа <span className="required">*</span>
              </label>
              <input
                type="text"
                className="input-control"
                placeholder="Например: Семейный"
                value={name}
                onChange={e => {
                  setName(e.target.value);
                  if (errors.name) setErrors(prev => ({ ...prev, name: undefined }));
                }}
              />
              {errors.name && <div className="form-error">{errors.name}</div>}
            </div>

            <div className="form-group">
              <label className="form-label">
                Общий трафик (ГБ) <span className="required">*</span>
              </label>
              <input
                type="number"
                step="0.01"
                min="0"
                className="input-control"
                placeholder="50"
                value={traffic}
                onChange={e => {
                  setTraffic(e.target.value);
                  if (errors.traffic) setErrors(prev => ({ ...prev, traffic: undefined }));
                }}
              />
              {errors.traffic && <div className="form-error">{errors.traffic}</div>}
            </div>

            <div className="form-group">
              <label className="form-label">
                Общие минуты <span className="required">*</span>
              </label>
              <input
                type="number"
                min="0"
                className="input-control"
                placeholder="500"
                value={minutes}
                onChange={e => {
                  setMinutes(e.target.value);
                  if (errors.minutes) setErrors(prev => ({ ...prev, minutes: undefined }));
                }}
              />
              {errors.minutes && <div className="form-error">{errors.minutes}</div>}
            </div>

            <button
              type="submit"
              className="btn btn-primary btn-full btn-lg"
              style={{ marginTop: '8px' }}
            >
              Создать тариф
            </button>
          </form>
        </div>

        {/* Right Card: Tariff Statistics */}
        <div className="admin-card">
          <h3 className="card-title">Статистика тарифов</h3>

          <div style={{ display: 'flex', flexDirection: 'column' }}>
            <div className="tariff-stat-item">
              <span className="tariff-stat-label">Всего тарифов</span>
              <span className="tariff-stat-num">{totalTariffsCount}</span>
            </div>

            <div className="tariff-stat-item">
              <span className="tariff-stat-label">Общий трафик</span>
              <span className="tariff-stat-num">{totalTraffic} ГБ</span>
            </div>

            <div className="tariff-stat-item">
              <span className="tariff-stat-label">Общие минуты</span>
              <span className="tariff-stat-num">{totalMinutes}</span>
            </div>
          </div>
        </div>
      </div>

      {/* Bottom Section: Created Tariffs Table */}
      <div className="section-header">
        <h2 className="section-title">Созданные тарифы</h2>
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
            {tariffs.length === 0 ? (
              <tr>
                <td colSpan={5} style={{ textAlign: 'center', padding: '36px', color: 'var(--text-secondary)' }}>
                  Нет данных. Создайте первый тариф выше.
                </td>
              </tr>
            ) : (
              tariffs.map(t => (
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
              ))
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
};
