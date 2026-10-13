import { useState } from 'react';
import type { FormEvent } from 'react';
import Modal from './Modal';
import { changePassword } from '../api/authApi';

const MIN_PASSWORD_LENGTH = 8;

interface ChangePasswordModalProps {
  onClose: () => void;
}

export default function ChangePasswordModal({ onClose }: ChangePasswordModalProps) {
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState(false);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (newPassword.length < MIN_PASSWORD_LENGTH) {
      setError(`La nuova password deve avere almeno ${MIN_PASSWORD_LENGTH} caratteri.`);
      return;
    }
    if (newPassword !== confirmPassword) {
      setError('Le due nuove password non coincidono.');
      return;
    }

    setSubmitting(true);
    setError(null);
    try {
      await changePassword(currentPassword, newPassword);
      setDone(true);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Errore imprevisto.');
    } finally {
      setSubmitting(false);
    }
  }

  if (done) {
    return (
      <Modal title="Cambia password" onClose={onClose}>
        <p className="description-text">Password aggiornata. Usala dal prossimo accesso.</p>
        <div className="form-actions">
          <button type="button" className="btn btn-primary" onClick={onClose}>
            Chiudi
          </button>
        </div>
      </Modal>
    );
  }

  return (
    <Modal title="Cambia password" onClose={onClose}>
      <form className="document-form" onSubmit={handleSubmit}>
        <div className="field">
          <label htmlFor="password-current">Password attuale</label>
          <input
            id="password-current"
            type="password"
            autoComplete="current-password"
            value={currentPassword}
            onChange={(e) => setCurrentPassword(e.target.value)}
            autoFocus
          />
        </div>

        <div className="field">
          <label htmlFor="password-new">Nuova password</label>
          <input
            id="password-new"
            type="password"
            autoComplete="new-password"
            value={newPassword}
            onChange={(e) => setNewPassword(e.target.value)}
          />
          <p className="field-hint">Almeno {MIN_PASSWORD_LENGTH} caratteri.</p>
        </div>

        <div className="field">
          <label htmlFor="password-confirm">Ripeti nuova password</label>
          <input
            id="password-confirm"
            type="password"
            autoComplete="new-password"
            value={confirmPassword}
            onChange={(e) => setConfirmPassword(e.target.value)}
          />
        </div>

        {error && <p className="form-error">{error}</p>}

        <div className="form-actions">
          <button type="button" className="btn btn-ghost" onClick={onClose} disabled={submitting}>
            Annulla
          </button>
          <button type="submit" className="btn btn-primary" disabled={submitting}>
            {submitting ? 'Salvataggio…' : 'Salva'}
          </button>
        </div>
      </form>
    </Modal>
  );
}
