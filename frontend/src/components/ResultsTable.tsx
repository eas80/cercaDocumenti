import { useState } from 'react';
import type { DocumentSummary } from '../api/types';
import { downloadDocument } from '../api/documentsApi';
import { formatBytes, formatDateTime } from '../utils/format';

interface ResultsTableProps {
  documents: DocumentSummary[];
  loading: boolean;
  onShowDescription: (document: DocumentSummary) => void;
  onEdit: (document: DocumentSummary) => void;
}

export default function ResultsTable({ documents, loading, onShowDescription, onEdit }: ResultsTableProps) {
  const [downloadingId, setDownloadingId] = useState<string | null>(null);
  const [downloadError, setDownloadError] = useState<string | null>(null);

  async function handleDownload(document: DocumentSummary) {
    setDownloadError(null);
    setDownloadingId(document.id);
    try {
      await downloadDocument(document.id, document.name);
    } catch (err) {
      setDownloadError(err instanceof Error ? err.message : 'Download non riuscito.');
    } finally {
      setDownloadingId(null);
    }
  }

  if (loading) {
    return <div className="empty-state">Caricamento in corso…</div>;
  }

  if (documents.length === 0) {
    return <div className="empty-state">Nessun documento trovato. Prova a modificare i filtri di ricerca.</div>;
  }

  return (
    <>
      {downloadError && <p className="form-error">{downloadError}</p>}
      <div className="table-wrapper">
        <table className="results-table">
          <thead>
            <tr>
              <th>Nome</th>
              <th>Descrizione</th>
              <th>Tipo</th>
              <th>Dimensione</th>
              <th>Ultima modifica</th>
              <th className="actions-header">Azioni</th>
            </tr>
          </thead>
          <tbody>
            {documents.map((doc) => (
              <tr key={doc.id}>
                <td className="cell-name">{doc.name}</td>
                <td className="cell-description">{doc.description ? doc.description : <em>—</em>}</td>
                <td>
                  <span className="badge">{doc.contentType ?? 'sconosciuto'}</span>
                </td>
                <td>{formatBytes(doc.sizeBytes)}</td>
                <td>{formatDateTime(doc.lastModifiedDate)}</td>
                <td className="actions-cell">
                  <button type="button" className="btn btn-small" onClick={() => onShowDescription(doc)}>
                    Descrizione
                  </button>
                  <button type="button" className="btn btn-small" onClick={() => onEdit(doc)}>
                    Modifica
                  </button>
                  <button
                    type="button"
                    className="btn btn-small btn-primary"
                    onClick={() => handleDownload(doc)}
                    disabled={downloadingId === doc.id}
                  >
                    {downloadingId === doc.id ? 'Scarico…' : 'Download'}
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </>
  );
}
