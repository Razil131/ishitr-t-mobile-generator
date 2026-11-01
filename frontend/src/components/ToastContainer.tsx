import React from 'react';
import { useAdmin } from '../context/AdminContext';

export const ToastContainer: React.FC = () => {
  const { toasts, removeToast } = useAdmin();

  if (toasts.length === 0) return null;

  return (
    <div className="toast-stack">
      {toasts.map(toast => (
        <div
          key={toast.id}
          className={`toast-item ${toast.type}`}
          onClick={() => removeToast(toast.id)}
        >
          <span>{toast.message}</span>
        </div>
      ))}
    </div>
  );
};
