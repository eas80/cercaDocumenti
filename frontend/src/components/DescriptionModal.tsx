import Modal from './Modal';
import type { DocumentSummary } from '../api/types';
import { formatDateTime } from '../utils/format';

interface DescriptionModalProps {
  document: DocumentSummary;
  onClose: () => void;
}

export default function DescriptionModal({ document, onClose }: DescriptionModalProps) {
  return (
    <Modal title={document.name} onClose={onClose}>
      <p className="description-text">
        {document.description ? document.description : <em>Nessuna descrizione presente.</em>}
      </p>
      <dl className="description-meta">
        <dt>Ultima modifica</dt>
        <dd>{formatDateTime(document.lastModifiedDate)}</dd>
      </dl>
    </Modal>
  );
}
