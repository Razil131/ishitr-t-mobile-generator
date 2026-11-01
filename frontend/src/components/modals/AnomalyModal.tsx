import React, { useState } from 'react';
import { useAdmin } from '../../context/AdminContext';

interface Props {
  isOpen: boolean;
  onClose: () => void;
  selectedUserId: number | null;
  targetLabel: string;
}

export const AnomalyModal: React.FC<Props> = ({
  isOpen,
  onClose,
  selectedUserId,
  targetLabel
}) => {
  const { startAnomaly, showToast } = useAdmin();
  const [amount, setAmount] = useState<string>('15');
  const [durationHours, setDurationHours] = useState<string>('6');

  if (!isOpen) return null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedUserId) {
      showToast('Пожалуйста, выберите пользователя в блоке "Выбор цели"', 'error');
      return;
    }
    const parsedAmount = parseFloat(amount);
    const parsedHours = parseInt(durationHours, 10);

    if (!parsedAmount || parsedAmount <= 0) {
      showToast('Введите корректный объём аномалии', 'error');
      return;
    }
    if (!parsedHours || parsedHours < 1) {
      showToast('Длительность должна быть не менее 1 часа', 'error');
      return;
    }

    await startAnomaly(
      {
        userId: selectedUserId,
        amount: parsedAmount,
        durationHours: parsedHours
      },
      targetLabel
    );
    onClose();
  };

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal-dialog" onClick={e => e.stopPropagation()}>
        <h3 className="modal-title">🔥 Запуск аномалии</h3>

        <form onSubmit={handleSubmit}>
          <div className="form-group">
            <label className="form-label">Объём аномалии (ГБ)</label>
            <input
              type="number"
              step="0.01"
              min="0.01"
              className="input-control"
              value={amount}
              onChange={e => setAmount(e.target.value)}
              placeholder="15"
              autoFocus
            />
          </div>

          <div className="form-group">
            <label className="form-label">Длительность (виртуальных часов)</label>
            <input
              type="number"
              min="1"
              className="input-control"
              value={durationHours}
              onChange={e => setDurationHours(e.target.value)}
              placeholder="6"
            />
          </div>

          <div className="modal-actions">
            <button
              type="button"
              className="btn btn-secondary"
              style={{ flex: 1 }}
              onClick={onClose}
            >
              Отмена
            </button>
            <button
              type="submit"
              className="btn btn-danger-pill"
              style={{ flex: 1, padding: '12px', fontSize: '14px', borderRadius: '8px' }}
            >
              Запустить
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
