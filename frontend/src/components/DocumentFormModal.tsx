import { useState } from 'react';
import type { FormEvent } from 'react';
import Modal from './Modal';
import type { DocumentSummary } from '../api/types';
import { createDocument, updateDocument } from '../api/documentsApi';
import { formatBytes } from '../utils/format';

interface DocumentFormModalProps {
  mode: 'create' | 'edit';
  document?: DocumentSummary;
  onClose: () => void;
  onSaved: (document: DocumentSummary) => void;
}

export default function DocumentFormModal({ mode, document, onClose, onSaved }: DocumentFormModalProps) {
  const [name, setName] = useState(document?.name ?? '');
  const [description, setDescription] = useState(document?.description ?? '');
  const [file, setFile] = useState<File | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const isEdit = mode === 'edit';

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (!name.trim()) {
      setError('Il nome è obbligatorio.');
      return;
    }
    if (!isEdit && !file) {
      setError('Seleziona un file da caricare.');
      return;
    }

    setSubmitting(true);
    setError(null);
    try {
      const saved = isEdit
        ? await updateDocument(document!.id, name.trim(), description.trim(), file)
        : await createDocument(name.trim(), description.trim(), file!);
      onSaved(saved);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Errore imprevisto.');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Modal title={isEdit ? 'Modifica documento' : 'Nuovo documento'} onClose={onClose}>
      <form className="document-form" onSubmit={handleSubmit}>
        <div className="field">
          <label htmlFor="doc-name">Nome documento</label>
          <input
            id="doc-name"
            type="text"
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="es. Fattura Gennaio"
            autoFocus
          />
        </div>

        <div className="field">
          <label htmlFor="doc-description">Descrizione</label>
          <textarea
            id="doc-description"
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            placeholder="Descrizione facoltativa"
            rows={4}
          />
        </div>

        <div className="field">
          <label htmlFor="doc-file">{isEdit ? 'Sostituisci file (opzionale)' : 'File'}</label>
          <input
            id="doc-file"
            type="file"
            onChange={(e) => setFile(e.target.files?.[0] ?? null)}
          />
          {isEdit && document && !file && (
            <p className="field-hint">
              File attuale: {document.contentType ?? 'tipo sconosciuto'} · {formatBytes(document.sizeBytes)}
            </p>
          )}
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
