import { useState } from 'react';
import type { DocumentSummary } from '../api/types';
import { deleteDocument, downloadDocument } from '../api/documentsApi';
import { formatBytes, formatDateTime } from '../utils/format';
import ConfirmModal from './ConfirmModal';

interface ResultsTableProps {
  documents: DocumentSummary[];
  loading: boolean;
  currentUsername: string;
  onShowDescription: (document: DocumentSummary) => void;
  onEdit: (document: DocumentSummary) => void;
  onShare: (document: DocumentSummary) => void;
  onDeleted: () => void;
}

function isOwnedBy(document: DocumentSummary, username: string): boolean {
  return document.owner === null || document.owner === username;
}

export default function ResultsTable({
  documents,
  loading,
  currentUsername,
  onShowDescription,
  onEdit,
  onShare,
  onDeleted,
}: ResultsTableProps) {
  const [downloadingId, setDownloadingId] = useState<string | null>(null);
  const [downloadError, setDownloadError] = useState<string | null>(null);
  const [pendingDelete, setPendingDelete] = useState<DocumentSummary | null>(null);

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

  async function handleConfirmDelete() {
    if (!pendingDelete) return;
    await deleteDocument(pendingDelete.id);
    setPendingDelete(null);
    onDeleted();
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
              <th className="download-header">
                <span className="sr-only">Download</span>
              </th>
              <th>Nome</th>
              <th>Proprietario</th>
              <th>Descrizione</th>
              <th>Tipo</th>
              <th>Dimensione</th>
              <th>Ultima modifica</th>
              <th className="actions-header">Azioni</th>
            </tr>
          </thead>
          <tbody>
            {documents.map((doc) => {
              const owned = isOwnedBy(doc, currentUsername);
              return (
                <tr key={doc.id}>
                  <td className="download-cell">
                    <button
                      type="button"
                      className="btn-icon"
                      aria-label={`Scarica ${doc.name}`}
                      title="Scarica"
                      onClick={() => handleDownload(doc)}
                      disabled={downloadingId === doc.id}
                    >
                      {downloadingId === doc.id ? '…' : '⬇'}
                    </button>
                  </td>
                  <td className="cell-name">{doc.name}</td>
                  <td>
                    <span className="badge">{owned ? 'Tuo' : `Di ${doc.owner}`}</span>
                    {owned && doc.sharedWith.length > 0 && (
                      <div className="shared-with-note">Condiviso con {doc.sharedWith.join(', ')}</div>
                    )}
                  </td>
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
                    {owned && (
                      <button type="button" className="btn btn-small" onClick={() => onShare(doc)}>
                        Condividi
                      </button>
                    )}
                    <button type="button" className="btn btn-small btn-danger" onClick={() => setPendingDelete(doc)}>
                      Elimina
                    </button>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>

      {pendingDelete && (
        <ConfirmModal
          title="Eliminare il documento?"
          message={`"${pendingDelete.name}" verrà eliminato definitivamente. L'operazione non è reversibile.`}
          confirmLabel="Elimina"
          onConfirm={handleConfirmDelete}
          onCancel={() => setPendingDelete(null)}
        />
      )}
    </>
  );
}
