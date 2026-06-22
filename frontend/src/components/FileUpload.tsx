import { useState, useRef } from 'react'
import type { Document } from '../App'
import './FileUpload.css'

const API_BASE = 'http://localhost:8080/api'

interface FileUploadProps {
  onUploadComplete: (doc: Document) => void
}

function FileUpload({ onUploadComplete }: FileUploadProps) {
  const [uploading, setUploading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const fileInputRef = useRef<HTMLInputElement>(null)

  const handleUpload = async () => {
    const file = fileInputRef.current?.files?.[0]
    if (!file) return

    setUploading(true)
    setError(null)

    const formData = new FormData()
    formData.append('file', file)

    try {
      const response = await fetch(`${API_BASE}/documents`, {
        method: 'POST',
        body: formData,
      })

      if (!response.ok) {
        throw new Error(`Upload failed: ${response.statusText}`)
      }

      const doc: Document = await response.json()
      onUploadComplete(doc)

      if (fileInputRef.current) {
        fileInputRef.current.value = ''
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Upload failed')
    } finally {
      setUploading(false)
    }
  }

  return (
    <div className="file-upload">
      <h2>Upload Document</h2>
      <div className="upload-controls">
        <input
          ref={fileInputRef}
          type="file"
          accept=".pdf,.txt"
          disabled={uploading}
        />
        <button
          onClick={handleUpload}
          disabled={uploading}
          className="upload-button"
        >
          {uploading ? 'Uploading...' : 'Upload'}
        </button>
      </div>
      {error && <p className="upload-error">{error}</p>}
    </div>
  )
}

export default FileUpload
