import { useEffect, useState } from 'react';
import Header from './components/Header';
import SearchForm from './components/SearchForm';
import ResultsTable from './components/ResultsTable';
import DescriptionModal from './components/DescriptionModal';
import DocumentFormModal from './components/DocumentFormModal';
import { searchDocuments } from './api/documentsApi';
import type { DocumentSummary, SearchParams } from './api/types';
import { emptySearchParams } from './api/types';

type ModalState =
  | { type: 'create' }
  | { type: 'edit'; document: DocumentSummary }
  | { type: 'description'; document: DocumentSummary }
  | null;

function App() {
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
    runSearch();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  function handleSaved() {
    setModal(null);
    runSearch();
  }

  return (
    <div className="app-shell">
      <Header />

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
            onShowDescription={(document) => setModal({ type: 'description', document })}
            onEdit={(document) => setModal({ type: 'edit', document })}
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
    </div>
  );
}

export default App;
