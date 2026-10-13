import type { Category, DocumentSummary } from '../api/types';

interface FolderGridProps {
  categories: Category[];
  documents: DocumentSummary[];
  /** null = the "uncategorized" folder. */
  onOpen: (categoryId: string | null) => void;
  onRename: (category: Category) => void;
  onDelete: (category: Category) => void;
}

function countLabel(count: number): string {
  return count === 1 ? '1 documento' : `${count} documenti`;
}

export default function FolderGrid({ categories, documents, onOpen, onRename, onDelete }: FolderGridProps) {
  const countByCategory = new Map<string | null, number>();
  for (const document of documents) {
    countByCategory.set(document.categoryId, (countByCategory.get(document.categoryId) ?? 0) + 1);
  }

  return (
    <div className="folder-grid">
      {categories.map((category) => (
        <div key={category.id} className="folder-card">
          <button type="button" className="folder-open" onClick={() => onOpen(category.id)}>
            <span className="folder-icon" aria-hidden="true">
              📁
            </span>
            <span className="folder-name">{category.name}</span>
            <span className="folder-count">{countLabel(countByCategory.get(category.id) ?? 0)}</span>
          </button>
          <div className="folder-actions">
            <button
              type="button"
              className="btn-icon"
              aria-label={`Rinomina ${category.name}`}
              title="Rinomina"
              onClick={() => onRename(category)}
            >
              ✎
            </button>
            <button
              type="button"
              className="btn-icon"
              aria-label={`Elimina ${category.name}`}
              title="Elimina"
              onClick={() => onDelete(category)}
            >
              🗑
            </button>
          </div>
        </div>
      ))}

      <div className="folder-card folder-card-uncategorized">
        <button type="button" className="folder-open" onClick={() => onOpen(null)}>
          <span className="folder-icon" aria-hidden="true">
            🗂
          </span>
          <span className="folder-name">Non categorizzato</span>
          <span className="folder-count">{countLabel(countByCategory.get(null) ?? 0)}</span>
        </button>
      </div>
    </div>
  );
}
