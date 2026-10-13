import { useEffect, useState } from 'react';
import Header from './components/Header';
import SearchForm from './components/SearchForm';
import ResultsTable from './components/ResultsTable';
import FolderGrid from './components/FolderGrid';
import DescriptionModal from './components/DescriptionModal';
import DocumentFormModal from './components/DocumentFormModal';
import CategoryFormModal from './components/CategoryFormModal';
import ChangePasswordModal from './components/ChangePasswordModal';
import ConfirmModal from './components/ConfirmModal';
import ShareModal from './components/ShareModal';
import LoginPage from './components/LoginPage';
import { searchDocuments } from './api/documentsApi';
import { deleteCategory, fetchCategories } from './api/categoriesApi';
import { logout } from './api/authApi';
import { useSession } from './hooks/useSession';
import type { Category, DocumentSummary, SearchParams } from './api/types';
import { emptySearchParams } from './api/types';

type ModalState =
  | { type: 'create' }
  | { type: 'edit'; document: DocumentSummary }
  | { type: 'description'; document: DocumentSummary }
  | { type: 'share'; document: DocumentSummary }
  | { type: 'createCategory' }
  | { type: 'renameCategory'; category: Category }
  | { type: 'deleteCategory'; category: Category }
  | { type: 'password' }
  | null;

function App() {
  const { token, username } = useSession();
  const [searchParams, setSearchParams] = useState<SearchParams>(emptySearchParams);
  const [categories, setCategories] = useState<Category[]>([]);
  const [documents, setDocuments] = useState<DocumentSummary[]>([]);
  // undefined = the folder list; null = the "uncategorized" folder; a string = that category's folder.
  const [openFolder, setOpenFolder] = useState<string | null | undefined>(undefined);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [modal, setModal] = useState<ModalState>(null);

  async function load(params: SearchParams) {
    setLoading(true);
    setError(null);
    try {
      const [loadedCategories, results] = await Promise.all([fetchCategories(), searchDocuments(params)]);
      setCategories(loadedCategories);
      setDocuments(results);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Errore imprevisto durante il caricamento.');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    if (token) {
      setOpenFolder(undefined);
      setSearchParams(emptySearchParams);
      load(emptySearchParams);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token]);

  function reload() {
    setModal(null);
    load(searchParams);
  }

  // The folder list counts every document, so the search filters only live inside a folder.
  function closeFolder() {
    setOpenFolder(undefined);
    setSearchParams(emptySearchParams);
    load(emptySearchParams);
  }

  async function handleDeleteCategory(category: Category) {
    await deleteCategory(category.id);
    reload();
  }

  if (!token || !username) {
    return <LoginPage />;
  }

  const openCategory = categories.find((category) => category.id === openFolder);
  // A category folder that no longer exists (deleted meanwhile) falls back to the folder list.
  const inFolder = openFolder === null || openCategory !== undefined;

  return (
    <div className="app-shell">
      <Header username={username} onChangePassword={() => setModal({ type: 'password' })} onLogout={logout} />

      <main className="app-main">
        {!inFolder && (
          <section className="card">
            <div className="toolbar">
              <h2>Categorie</h2>
              <div className="toolbar-actions">
                <button type="button" className="btn" onClick={() => setModal({ type: 'createCategory' })}>
                  + Nuova categoria
                </button>
                <button type="button" className="btn btn-primary" onClick={() => setModal({ type: 'create' })}>
                  + Nuovo documento
                </button>
              </div>
            </div>

            {error && <p className="form-error">{error}</p>}

            {loading ? (
              <div className="empty-state">Caricamento in corso…</div>
            ) : (
              <FolderGrid
                categories={categories}
                documents={documents}
                onOpen={setOpenFolder}
                onRename={(category) => setModal({ type: 'renameCategory', category })}
                onDelete={(category) => setModal({ type: 'deleteCategory', category })}
              />
            )}
          </section>
        )}

        {inFolder && (
          <>
            <nav className="folder-nav" aria-label="Cartella corrente">
              <button type="button" className="btn" onClick={closeFolder}>
                ← Tutte le categorie
              </button>
              <span className="folder-nav-current">
                <span aria-hidden="true">{openCategory ? '📁' : '🗂'}</span>{' '}
                {openCategory ? openCategory.name : 'Non categorizzato'}
              </span>
            </nav>

            <section className="card">
              <SearchForm
                value={searchParams}
                onChange={setSearchParams}
                onSearch={() => load(searchParams)}
                loading={loading}
              />
            </section>

            <section className="card">
              <div className="toolbar">
                <h2>Documenti</h2>
                <button type="button" className="btn btn-primary" onClick={() => setModal({ type: 'create' })}>
                  + Nuovo documento
                </button>
              </div>

              {error && <p className="form-error">{error}</p>}

              <ResultsTable
                documents={documents.filter((document) => document.categoryId === openFolder)}
                loading={loading}
                currentUsername={username}
                onShowDescription={(document) => setModal({ type: 'description', document })}
                onEdit={(document) => setModal({ type: 'edit', document })}
                onShare={(document) => setModal({ type: 'share', document })}
                onDeleted={() => load(searchParams)}
              />
            </section>
          </>
        )}
      </main>

      {modal?.type === 'description' && (
        <DescriptionModal document={modal.document} onClose={() => setModal(null)} />
      )}

      {modal?.type === 'create' && (
        <DocumentFormModal
          mode="create"
          categories={categories}
          defaultCategoryId={inFolder ? openFolder : null}
          onClose={() => setModal(null)}
          onSaved={reload}
        />
      )}

      {modal?.type === 'edit' && (
        <DocumentFormModal
          mode="edit"
          document={modal.document}
          categories={categories}
          onClose={() => setModal(null)}
          onSaved={reload}
        />
      )}

      {modal?.type === 'share' && (
        <ShareModal
          document={modal.document}
          currentUsername={username}
          onClose={() => setModal(null)}
          onShared={reload}
        />
      )}

      {modal?.type === 'createCategory' && <CategoryFormModal onClose={() => setModal(null)} onSaved={reload} />}

      {modal?.type === 'renameCategory' && (
        <CategoryFormModal category={modal.category} onClose={() => setModal(null)} onSaved={reload} />
      )}

      {modal?.type === 'deleteCategory' && (
        <ConfirmModal
          title="Eliminare la categoria?"
          message={`La categoria "${modal.category.name}" verrà eliminata. I documenti al suo interno non vengono cancellati: passano in "Non categorizzato".`}
          confirmLabel="Elimina"
          onConfirm={() => handleDeleteCategory(modal.category)}
          onCancel={() => setModal(null)}
        />
      )}

      {modal?.type === 'password' && <ChangePasswordModal onClose={() => setModal(null)} />}
    </div>
  );
}

export default App;
