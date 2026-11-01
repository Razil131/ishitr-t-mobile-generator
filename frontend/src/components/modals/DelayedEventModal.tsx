import React, { useState } from 'react';
import { useAdmin } from '../../context/AdminContext';

interface Props {
  isOpen: boolean;
  onClose: () => void;
  selectedUserId: number | null;
}

export const DelayedEventModal: React.FC<Props> = ({
  isOpen,
  onClose,
  selectedUserId
}) => {
  const { sendDelayedEvent, showToast } = useAdmin();
  const [amount, setAmount] = useState<string>('1.5');
  const [hoursAgo, setHoursAgo] = useState<string>('3');

  if (!isOpen) return null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedUserId) {
      showToast('Пожалуйста, выберите пользователя в блоке "Выбор цели"', 'error');
      return;
    }
    const parsedAmount = parseFloat(amount);
    const parsedHours = parseInt(hoursAgo, 10);

    if (!parsedAmount || parsedAmount <= 0) {
      showToast('Введите корректный объём', 'error');
      return;
    }
    if (!parsedHours || parsedHours < 1) {
      showToast('Укажите количество часов назад (минимум 1)', 'error');
      return;
    }

    await sendDelayedEvent({
      userId: selectedUserId,
      amount: parsedAmount,
      hoursAgo: parsedHours
    });
    onClose();
  };

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal-dialog" onClick={e => e.stopPropagation()}>
        <h3 className="modal-title">⏪ Запоздалое событие</h3>

        <form onSubmit={handleSubmit}>
          <div className="form-group">
            <label className="form-label">Объём (ГБ)</label>
            <input
              type="number"
              step="0.01"
              min="0.01"
              className="input-control"
              value={amount}
              onChange={e => setAmount(e.target.value)}
              placeholder="1.5"
              autoFocus
            />
          </div>

          <div className="form-group">
            <label className="form-label">Часов назад</label>
            <input
              type="number"
              min="1"
              className="input-control"
              value={hoursAgo}
              onChange={e => setHoursAgo(e.target.value)}
              placeholder="3"
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
              className="btn btn-primary"
              style={{ flex: 1 }}
            >
              Отправить
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
