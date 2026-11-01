import React, { useState } from 'react';
import { useAdmin } from '../../context/AdminContext';

interface Props {
  isOpen: boolean;
  onClose: () => void;
  selectedUserId: number | null;
}

export const InstantDeductModal: React.FC<Props> = ({
  isOpen,
  onClose,
  selectedUserId
}) => {
  const { instantDeduct, showToast } = useAdmin();
  const [amount, setAmount] = useState<string>('5');
  const [resourceType, setResourceType] = useState<'TRAFFIC' | 'MINUTES'>('TRAFFIC');

  if (!isOpen) return null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedUserId) {
      showToast('Пожалуйста, выберите пользователя в блоке "Выбор цели"', 'error');
      return;
    }
    const parsedAmount = parseFloat(amount);
    if (!parsedAmount || parsedAmount <= 0) {
      showToast('Введите корректный объём больше 0', 'error');
      return;
    }

    await instantDeduct({
      userId: selectedUserId,
      amount: parsedAmount,
      resourceType
    });
    onClose();
  };

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal-dialog" onClick={e => e.stopPropagation()}>
        <h3 className="modal-title">💸 Мгновенное списание</h3>

        <form onSubmit={handleSubmit}>
          <div className="form-group">
            <label className="form-label">Объём</label>
            <input
              type="number"
              step="0.01"
              min="0.01"
              className="input-control"
              value={amount}
              onChange={e => setAmount(e.target.value)}
              placeholder="5"
              autoFocus
            />
          </div>

          <div className="form-group">
            <label className="form-label">Тип ресурса</label>
            <select
              className="input-control"
              value={resourceType}
              onChange={e => setResourceType(e.target.value as 'TRAFFIC' | 'MINUTES')}
            >
              <option value="TRAFFIC">Трафик (ГБ)</option>
              <option value="MINUTES">Минуты</option>
            </select>
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
              Списать
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
