import { useState } from 'react'
import { fetchEventSource } from '@microsoft/fetch-event-source'
import ReactMarkdown from 'react-markdown'
import './SummaryView.css'

const API_BASE = 'http://localhost:8080/api'

interface SummaryViewProps {
  documentId: number | null
}

function SummaryView({ documentId }: SummaryViewProps) {
  const [summary, setSummary] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const handleSummarize = async () => {
    if (!documentId) return

    setSummary('')
    setLoading(true)
    setError(null)

    try {
      await fetchEventSource(`${API_BASE}/documents/${documentId}/summary`, {
        method: 'POST',
        onmessage(event) {
          setSummary(prev => prev + event.data)
        },
        onerror(err) {
          throw err
        },
      })
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to generate summary')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="summary-view">
      <div className="summary-header">
        <h2>Summary</h2>
        <button
          onClick={handleSummarize}
          disabled={!documentId || loading}
          className="summarize-button"
        >
          {loading ? 'Generating...' : 'Generate Summary'}
        </button>
      </div>

      {error && <p className="summary-error">{error}</p>}

      {summary ? (
        <div className="summary-content">
          <ReactMarkdown>{summary}</ReactMarkdown>
        </div>
      ) : (
        <p className="summary-placeholder">
          {documentId
            ? 'Click "Generate Summary" to summarize this document.'
            : 'Select a document to get started.'}
        </p>
      )}
    </div>
  )
}

export default SummaryView
