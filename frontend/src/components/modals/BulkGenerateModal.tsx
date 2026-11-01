import React, { useState } from 'react';
import { useAdmin } from '../../context/AdminContext';

interface Props {
  isOpen: boolean;
  onClose: () => void;
}

export const BulkGenerateModal: React.FC<Props> = ({
  isOpen,
  onClose
}) => {
  const { bulkGenerate, showToast } = useAdmin();
  const [count, setCount] = useState<string>('10');
  const [resourceType, setResourceType] = useState<'TRAFFIC' | 'MINUTES'>('TRAFFIC');

  if (!isOpen) return null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const parsedCount = parseInt(count, 10);
    if (!parsedCount || parsedCount < 1) {
      showToast('Введите количество событий (минимум 1)', 'error');
      return;
    }

    await bulkGenerate({
      count: parsedCount,
      resourceType
    });
    onClose();
  };

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal-dialog" onClick={e => e.stopPropagation()}>
        <h3 className="modal-title">📦 Массовая генерация</h3>

        <form onSubmit={handleSubmit}>
          <div className="form-group">
            <label className="form-label">Количество событий на пользователя</label>
            <input
              type="number"
              min="1"
              className="input-control"
              value={count}
              onChange={e => setCount(e.target.value)}
              placeholder="10"
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
              Сгенерировать
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
