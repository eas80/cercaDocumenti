import { useState } from 'react';
import Modal from './Modal';

interface ConfirmModalProps {
  title: string;
  message: string;
  confirmLabel: string;
  onConfirm: () => Promise<void>;
  onCancel: () => void;
}

export default function ConfirmModal({ title, message, confirmLabel, onConfirm, onCancel }: ConfirmModalProps) {
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleConfirm() {
    setSubmitting(true);
    setError(null);
    try {
      await onConfirm();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Errore imprevisto.');
      setSubmitting(false);
    }
  }

  return (
    <Modal title={title} onClose={onCancel}>
      <p className="description-text">{message}</p>

      {error && <p className="form-error">{error}</p>}

      <div className="form-actions">
        <button type="button" className="btn btn-ghost" onClick={onCancel} disabled={submitting}>
          Annulla
        </button>
        <button type="button" className="btn btn-danger" onClick={handleConfirm} disabled={submitting}>
          {submitting ? 'Attendere…' : confirmLabel}
        </button>
      </div>
    </Modal>
  );
}
