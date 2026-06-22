import { useEffect } from 'react'
import type { Document } from '../App'
import './DocumentList.css'

const API_BASE = 'http://localhost:8080/api'

interface DocumentListProps {
  documents: Document[]
  selectedId: number | null
  onSelect: (id: number) => void
  onDocumentsLoaded: (docs: Document[]) => void
}

function DocumentList({ documents, selectedId, onSelect, onDocumentsLoaded }: DocumentListProps) {
  useEffect(() => {
    fetch(`${API_BASE}/documents`)
      .then(res => res.json())
      .then(onDocumentsLoaded)
      .catch(() => {})
  }, [])

  const statusLabel = (status: Document['status']) => {
    switch (status) {
      case 'READY': return 'Ready'
      case 'PROCESSING': return 'Processing...'
      case 'FAILED': return 'Failed'
    }
  }

  return (
    <div className="document-list">
      <h2>Documents</h2>
      {documents.length === 0 ? (
        <p className="empty-message">No documents uploaded yet.</p>
      ) : (
        <ul>
          {documents.map(doc => (
            <li
              key={doc.id}
              className={`document-item ${selectedId === doc.id ? 'selected' : ''}`}
              onClick={() => doc.status === 'READY' && onSelect(doc.id)}
            >
              <span className="document-name">{doc.name}</span>
              <span className={`document-status status-${doc.status.toLowerCase()}`}>
                {statusLabel(doc.status)}
              </span>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

export default DocumentList
