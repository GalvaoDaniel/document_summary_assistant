import { useState } from 'react'
import FileUpload from './components/FileUpload'
import DocumentList from './components/DocumentList'
import SummaryView from './components/SummaryView'
import './App.css'

export interface Document {
  id: number
  name: string
  status: 'PROCESSING' | 'READY' | 'FAILED'
  uploadedAt: string
}

function App() {
  const [documents, setDocuments] = useState<Document[]>([])
  const [selectedDocumentId, setSelectedDocumentId] = useState<number | null>(null)

  const handleUploadComplete = (doc: Document) => {
    setDocuments(prev => [doc, ...prev])
  }

  const handleDocumentsLoaded = (docs: Document[]) => {
    setDocuments(docs)
  }

  return (
    <div className="app">
      <h1>Document Summary Assistant</h1>
      <div className="app-layout">
        <div className="sidebar">
          <FileUpload onUploadComplete={handleUploadComplete} />
          <DocumentList
            documents={documents}
            selectedId={selectedDocumentId}
            onSelect={setSelectedDocumentId}
            onDocumentsLoaded={handleDocumentsLoaded}
          />
        </div>
        <div className="main-content">
          <SummaryView documentId={selectedDocumentId} />
        </div>
      </div>
    </div>
  )
}

export default App
