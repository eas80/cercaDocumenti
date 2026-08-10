import { useEffect, useState } from 'react';
import Modal from './Modal';
import type { DocumentSummary } from '../api/types';
import { fetchOtherUsers } from '../api/authApi';
import { shareDocument } from '../api/documentsApi';

interface ShareModalProps {
  document: DocumentSummary;
  currentUsername: string;
  onClose: () => void;
  onShared: (updated: DocumentSummary) => void;
}

export default function ShareModal({ document, currentUsername, onClose, onShared }: ShareModalProps) {
  const [otherUsers, setOtherUsers] = useState<string[]>([]);
  const [selected, setSelected] = useState<Set<string>>(new Set(document.sharedWith));
  const [loadingUsers, setLoadingUsers] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetchOtherUsers()
      .then((users) => setOtherUsers(users.filter((u) => u !== currentUsername)))
      .catch((err) => setError(err instanceof Error ? err.message : 'Errore nel caricamento degli utenti.'))
      .finally(() => setLoadingUsers(false));
  }, [currentUsername]);

  function toggle(username: string) {
    setSelected((previous) => {
      const next = new Set(previous);
      if (next.has(username)) {
        next.delete(username);
      } else {
        next.add(username);
      }
      return next;
    });
  }

  async function handleSave() {
    setSubmitting(true);
    setError(null);
    try {
      const updated = await shareDocument(document.id, Array.from(selected));
      onShared(updated);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Errore imprevisto.');
      setSubmitting(false);
    }
  }

  return (
    <Modal title={`Condividi "${document.name}"`} onClose={onClose}>
      {loadingUsers && <p className="description-text">Caricamento utenti…</p>}

      {!loadingUsers && otherUsers.length === 0 && (
        <p className="description-text">
          <em>Non ci sono altri utenti con cui condividere.</em>
        </p>
      )}

      {!loadingUsers && otherUsers.length > 0 && (
        <ul className="share-user-list">
          {otherUsers.map((username) => (
            <li key={username} className="share-user-item">
              <label>
                <input type="checkbox" checked={selected.has(username)} onChange={() => toggle(username)} />
                {username}
              </label>
            </li>
          ))}
        </ul>
      )}

      {error && <p className="form-error">{error}</p>}

      <div className="form-actions">
        <button type="button" className="btn btn-ghost" onClick={onClose} disabled={submitting}>
          Annulla
        </button>
        <button type="button" className="btn btn-primary" onClick={handleSave} disabled={submitting || loadingUsers}>
          {submitting ? 'Salvataggio…' : 'Salva'}
        </button>
      </div>
    </Modal>
  );
}
