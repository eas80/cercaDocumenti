import { useEffect, useState } from 'react';
import Header from './components/Header';
import SearchForm from './components/SearchForm';
import ResultsTable from './components/ResultsTable';
import DescriptionModal from './components/DescriptionModal';
import DocumentFormModal from './components/DocumentFormModal';
import ShareModal from './components/ShareModal';
import LoginPage from './components/LoginPage';
import { searchDocuments } from './api/documentsApi';
import { logout } from './api/authApi';
import { useSession } from './hooks/useSession';
import type { DocumentSummary, SearchParams } from './api/types';
import { emptySearchParams } from './api/types';

type ModalState =
  | { type: 'create' }
  | { type: 'edit'; document: DocumentSummary }
  | { type: 'description'; document: DocumentSummary }
  | { type: 'share'; document: DocumentSummary }
  | null;

function App() {
  const { token, username } = useSession();
  const [searchParams, setSearchParams] = useState<SearchParams>(emptySearchParams);
  const [documents, setDocuments] = useState<DocumentSummary[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [modal, setModal] = useState<ModalState>(null);

  async function runSearch() {
    setLoading(true);
    setError(null);
    try {
      const results = await searchDocuments(searchParams);
      setDocuments(results);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Errore imprevisto durante la ricerca.');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    if (token) {
      runSearch();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token]);

  function handleSaved() {
    setModal(null);
    runSearch();
  }

  function handleShared() {
    setModal(null);
    runSearch();
  }

  if (!token || !username) {
    return <LoginPage />;
  }

  return (
    <div className="app-shell">
      <Header username={username} onLogout={logout} />

      <main className="app-main">
        <section className="card">
          <SearchForm value={searchParams} onChange={setSearchParams} onSearch={runSearch} loading={loading} />
        </section>

        <section className="card">
          <div className="toolbar">
            <h2>Risultati</h2>
            <button type="button" className="btn btn-primary" onClick={() => setModal({ type: 'create' })}>
              + Nuovo documento
            </button>
          </div>

          {error && <p className="form-error">{error}</p>}

          <ResultsTable
            documents={documents}
            loading={loading}
            currentUsername={username}
            onShowDescription={(document) => setModal({ type: 'description', document })}
            onEdit={(document) => setModal({ type: 'edit', document })}
            onShare={(document) => setModal({ type: 'share', document })}
            onDeleted={runSearch}
          />
        </section>
      </main>

      {modal?.type === 'description' && (
        <DescriptionModal document={modal.document} onClose={() => setModal(null)} />
      )}

      {modal?.type === 'create' && (
        <DocumentFormModal mode="create" onClose={() => setModal(null)} onSaved={handleSaved} />
      )}

      {modal?.type === 'edit' && (
        <DocumentFormModal
          mode="edit"
          document={modal.document}
          onClose={() => setModal(null)}
          onSaved={handleSaved}
        />
      )}

      {modal?.type === 'share' && (
        <ShareModal
          document={modal.document}
          currentUsername={username}
          onClose={() => setModal(null)}
          onShared={handleShared}
        />
      )}
    </div>
  );
}

export default App;
