import { useState } from 'react';
import type { FormEvent } from 'react';
import Modal from './Modal';
import type { Category } from '../api/types';
import { createCategory, renameCategory } from '../api/categoriesApi';

interface CategoryFormModalProps {
  /** Present when renaming an existing category, absent when creating one. */
  category?: Category;
  onClose: () => void;
  onSaved: (category: Category) => void;
}

export default function CategoryFormModal({ category, onClose, onSaved }: CategoryFormModalProps) {
  const [name, setName] = useState(category?.name ?? '');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (!name.trim()) {
      setError('Il nome è obbligatorio.');
      return;
    }

    setSubmitting(true);
    setError(null);
    try {
      const saved = category ? await renameCategory(category.id, name.trim()) : await createCategory(name.trim());
      onSaved(saved);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Errore imprevisto.');
      setSubmitting(false);
    }
  }

  return (
    <Modal title={category ? 'Rinomina categoria' : 'Nuova categoria'} onClose={onClose}>
      <form className="document-form" onSubmit={handleSubmit}>
        <div className="field">
          <label htmlFor="category-name">Nome categoria</label>
          <input
            id="category-name"
            type="text"
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="es. Fatture"
            maxLength={60}
            autoFocus
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
