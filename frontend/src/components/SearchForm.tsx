import type { FormEvent } from 'react';
import type { SearchParams } from '../api/types';
import { emptySearchParams } from '../api/types';

interface SearchFormProps {
  value: SearchParams;
  onChange: (value: SearchParams) => void;
  onSearch: () => void;
  loading: boolean;
}

export default function SearchForm({ value, onChange, onSearch, loading }: SearchFormProps) {
  function handleSubmit(event: FormEvent) {
    event.preventDefault();
    onSearch();
  }

  function update<K extends keyof SearchParams>(key: K, fieldValue: SearchParams[K]) {
    onChange({ ...value, [key]: fieldValue });
  }

  function handleReset() {
    onChange(emptySearchParams);
  }

  return (
    <form className="search-form" onSubmit={handleSubmit}>
      <div className="field">
        <label htmlFor="search-name">Nome documento</label>
        <input
          id="search-name"
          type="text"
          value={value.nameLike}
          onChange={(e) => update('nameLike', e.target.value)}
          placeholder="es. fattura"
        />
      </div>

      <div className="field">
        <label htmlFor="search-description">Descrizione</label>
        <input
          id="search-description"
          type="text"
          value={value.descriptionLike}
          onChange={(e) => update('descriptionLike', e.target.value)}
          placeholder="es. gennaio"
        />
      </div>

      <div className="field">
        <label htmlFor="search-date-from">Data da</label>
        <input
          id="search-date-from"
          type="date"
          value={value.dateFrom}
          onChange={(e) => update('dateFrom', e.target.value)}
        />
      </div>

      <div className="field">
        <label htmlFor="search-date-to">Data a</label>
        <input
          id="search-date-to"
          type="date"
          value={value.dateTo}
          onChange={(e) => update('dateTo', e.target.value)}
        />
      </div>

      <div className="search-form-actions">
        <button type="button" className="btn btn-ghost" onClick={handleReset} disabled={loading}>
          Azzera
        </button>
        <button type="submit" className="btn btn-primary" disabled={loading}>
          {loading ? 'Ricerca…' : 'Cerca'}
        </button>
      </div>
    </form>
  );
}
