import { useEffect, useMemo, useRef, useState } from "react";

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || "http://localhost:8080";
const TOKEN_STORAGE_KEY = "documind-token";

const starterMessages = [
  {
    role: "assistant",
    text: "Upload a PDF, then ask a question about it. I will answer from your indexed document chunks.",
    sources: []
  }
];

function App() {
  const fileInputRef = useRef(null);
  const [authMode, setAuthMode] = useState("login");
  const [authForm, setAuthForm] = useState({ username: "", password: "" });
  const [authLoading, setAuthLoading] = useState(false);
  const [token, setToken] = useState(() => localStorage.getItem(TOKEN_STORAGE_KEY) || "");
  const [user, setUser] = useState(null);
  const [selectedFile, setSelectedFile] = useState(null);
  const [uploadState, setUploadState] = useState({
    status: "No document uploaded yet",
    filename: "",
    chunksCreated: 0
  });
  const [uploading, setUploading] = useState(false);
  const [question, setQuestion] = useState("");
  const [asking, setAsking] = useState(false);
  const [documents, setDocuments] = useState([]);
  const [conversations, setConversations] = useState([]);
  const [currentConversationId, setCurrentConversationId] = useState("");
  const [selectedDocumentScope, setSelectedDocumentScope] = useState("");
  const [loadingDocuments, setLoadingDocuments] = useState(false);
  const [deletingFilename, setDeletingFilename] = useState("");
  const [loadingConversationId, setLoadingConversationId] = useState("");
  const [metrics, setMetrics] = useState(null);
  const [messages, setMessages] = useState(starterMessages);
  const [lastSources, setLastSources] = useState([]);
  const [error, setError] = useState("");

  const canAsk = useMemo(() => question.trim().length > 0 && !asking, [question, asking]);

  useEffect(() => {
    if (!token) {
      setUser(null);
      return;
    }

    initializeAuthenticatedApp();
  }, [token]);

  async function initializeAuthenticatedApp() {
    try {
      await loadProfile();
      await Promise.all([
        loadDocuments(),
        loadMetrics(),
        loadConversations()
      ]);
    } catch (initializationError) {
      signOut();
      setError(initializationError.message || "Could not restore session.");
    }
  }

  async function apiFetch(path, options = {}) {
    const headers = new Headers(options.headers || {});

    if (token) {
      headers.set("Authorization", `Bearer ${token}`);
    }

    if (options.body && !headers.has("Content-Type") && !(options.body instanceof FormData)) {
      headers.set("Content-Type", "application/json");
    }

    return fetch(`${API_BASE_URL}${path}`, {
      ...options,
      headers
    });
  }

  async function loadProfile() {
    const response = await apiFetch("/auth/me");

    if (!response.ok) {
      throw new Error(`Profile failed with status ${response.status}`);
    }

    const data = await response.json();
    setUser(data);
  }

  async function submitAuth(event) {
    event.preventDefault();
    setError("");
    setAuthLoading(true);

    try {
      const response = await fetch(`${API_BASE_URL}/auth/${authMode}`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json"
        },
        body: JSON.stringify(authForm)
      });

      if (!response.ok) {
        const fallbackMessage = authMode === "login" ? "Login failed." : "Signup failed.";
        throw new Error(fallbackMessage);
      }

      const data = await response.json();
      localStorage.setItem(TOKEN_STORAGE_KEY, data.token);
      setToken(data.token);
      setUser({
        userId: data.userId,
        username: data.username
      });
      setAuthForm({ username: "", password: "" });
      setMessages(starterMessages);
      setCurrentConversationId("");
      setLastSources([]);
    } catch (authError) {
      setError(authError.message || "Authentication failed.");
    } finally {
      setAuthLoading(false);
    }
  }

  function signOut() {
    localStorage.removeItem(TOKEN_STORAGE_KEY);
    setToken("");
    setUser(null);
    setDocuments([]);
    setConversations([]);
    setMetrics(null);
    setMessages(starterMessages);
    setCurrentConversationId("");
    setLastSources([]);
    setSelectedDocumentScope("");
  }

  async function loadDocuments() {
    setLoadingDocuments(true);

    try {
      const response = await apiFetch("/documents");

      if (!response.ok) {
        throw new Error(`Document list failed with status ${response.status}`);
      }

      const data = await response.json();
      setDocuments(data);
      setSelectedDocumentScope((currentScope) => {
        if (!currentScope) {
          return currentScope;
        }

        return data.some((document) => document.filename === currentScope)
          ? currentScope
          : "";
      });
    } catch (listError) {
      setError(listError.message || "Could not load indexed documents.");
    } finally {
      setLoadingDocuments(false);
    }
  }

  async function loadConversations() {
    try {
      const response = await apiFetch("/ai/conversations");

      if (!response.ok) {
        throw new Error(`Conversations failed with status ${response.status}`);
      }

      const data = await response.json();
      setConversations(data);
    } catch (conversationError) {
      setError(conversationError.message || "Could not load conversations.");
    }
  }

  async function loadConversation(conversationId) {
    setLoadingConversationId(conversationId);
    setError("");

    try {
      const response = await apiFetch(`/ai/conversations/${conversationId}`);

      if (!response.ok) {
        throw new Error(`Conversation load failed with status ${response.status}`);
      }

      const data = await response.json();
      setCurrentConversationId(data.conversationId);
      setSelectedDocumentScope(data.activeFilename || "");
      setMessages(
        data.messages.length > 0
          ? data.messages.map((message) => ({
              role: message.role,
              text: message.text,
              sources: message.sources || [],
              scope: message.scope,
              durationMs: message.durationMs,
              model: message.model
            }))
          : starterMessages
      );

      const latestAssistant = [...data.messages]
        .reverse()
        .find((message) => message.role === "assistant" && (message.sources || []).length > 0);

      setLastSources(latestAssistant ? latestAssistant.sources : []);
    } catch (conversationError) {
      setError(conversationError.message || "Could not open conversation.");
    } finally {
      setLoadingConversationId("");
    }
  }

  async function loadMetrics() {
    try {
      const response = await apiFetch("/ai/metrics");

      if (!response.ok) {
        throw new Error(`Metrics failed with status ${response.status}`);
      }

      const data = await response.json();
      setMetrics(data);
    } catch (metricsError) {
      setError(metricsError.message || "Could not load chat metrics.");
    }
  }

  function startNewConversation() {
    setCurrentConversationId("");
    setMessages(starterMessages);
    setLastSources([]);
    setQuestion("");
  }

  async function uploadDocument(event) {
    event.preventDefault();

    if (!selectedFile) {
      setError("Choose a PDF before uploading.");
      return;
    }

    setError("");
    setUploading(true);

    const formData = new FormData();
    formData.append("file", selectedFile);

    try {
      const response = await apiFetch("/documents/upload", {
        method: "POST",
        body: formData
      });

      if (!response.ok) {
        throw new Error(`Upload failed with status ${response.status}`);
      }

      const data = await response.json();
      setUploadState(data);
      setSelectedDocumentScope(data.filename);
      await loadDocuments();
      setMessages((current) => [
        ...current,
        {
          role: "system",
          text: `${data.filename} uploaded. ${data.chunksCreated} chunks are ready for retrieval.`,
          sources: []
        }
      ]);
    } catch (uploadError) {
      setError(uploadError.message || "Upload failed.");
    } finally {
      setUploading(false);
    }
  }

  async function deleteDocument(filename) {
    setError("");
    setDeletingFilename(filename);

    try {
      const response = await apiFetch(
        `/documents/${encodeURIComponent(filename)}`,
        { method: "DELETE" }
      );

      if (!response.ok) {
        throw new Error(`Delete failed with status ${response.status}`);
      }

      const data = await response.json();
      await loadDocuments();
      setSelectedDocumentScope((currentScope) => currentScope === filename ? "" : currentScope);
      setLastSources((current) => current.filter((source) => source.filename !== filename));
      setMessages((current) => [
        ...current,
        {
          role: "system",
          text: `${data.filename} removed. ${data.chunksDeleted} chunks deleted from the index.`,
          sources: []
        }
      ]);
    } catch (deleteError) {
      setError(deleteError.message || "Could not delete document.");
    } finally {
      setDeletingFilename("");
    }
  }

  async function askQuestion(event) {
    event.preventDefault();

    const trimmedQuestion = question.trim();

    if (!trimmedQuestion) {
      return;
    }

    setQuestion("");
    setError("");
    setAsking(true);
    setMessages((current) => [
      ...current,
      { role: "user", text: trimmedQuestion, sources: [] }
    ]);

    try {
      const response = await apiFetch("/ai/conversations/ask", {
        method: "POST",
        body: JSON.stringify({
          conversationId: currentConversationId || null,
          message: trimmedQuestion,
          filename: selectedDocumentScope || null
        })
      });

      if (!response.ok) {
        throw new Error(`Ask failed with status ${response.status}`);
      }

      const data = await response.json();
      setCurrentConversationId(data.conversationId);
      setLastSources(data.sources || []);
      await Promise.all([loadMetrics(), loadConversations()]);
      setMessages((current) => [
        ...current,
        {
          role: "assistant",
          text: data.answer,
          sources: data.sources || [],
          scope: data.scope,
          durationMs: data.durationMs,
          model: data.model
        }
      ]);
    } catch (askError) {
      setError(askError.message || "Question failed.");
      setMessages((current) => [
        ...current,
        {
          role: "assistant",
          text: "I could not complete that request. Check the backend logs and try again.",
          sources: []
        }
      ]);
    } finally {
      setAsking(false);
    }
  }

  if (!token || !user) {
    return (
      <main className="auth-shell">
        <section className="auth-panel">
          <div className="brand-block">
            <p className="eyebrow">DocuMind AI</p>
            <h1>Private Document Workspace</h1>
          </div>

          <div className="auth-toggle">
            <button
              className={`toggle-button ${authMode === "login" ? "active" : ""}`}
              type="button"
              onClick={() => setAuthMode("login")}
            >
              Login
            </button>
            <button
              className={`toggle-button ${authMode === "signup" ? "active" : ""}`}
              type="button"
              onClick={() => setAuthMode("signup")}
            >
              Signup
            </button>
          </div>

          {error && <div className="error-banner">{error}</div>}

          <form className="auth-form" onSubmit={submitAuth}>
            <input
              type="text"
              value={authForm.username}
              placeholder="Username"
              onChange={(event) =>
                setAuthForm((current) => ({ ...current, username: event.target.value }))
              }
            />
            <input
              type="password"
              value={authForm.password}
              placeholder="Password"
              onChange={(event) =>
                setAuthForm((current) => ({ ...current, password: event.target.value }))
              }
            />
            <button className="primary-button" type="submit" disabled={authLoading}>
              {authLoading ? "Please wait..." : authMode === "login" ? "Login" : "Create account"}
            </button>
          </form>
        </section>
      </main>
    );
  }

  return (
    <main className="app-shell">
      <section className="workspace">
        <aside className="sidebar" aria-label="Document controls">
          <div className="brand-block">
            <p className="eyebrow">DocuMind AI</p>
            <h1>Document Q&A</h1>
            <div className="user-strip">
              <span>{user.username}</span>
              <button className="icon-button" type="button" onClick={signOut}>
                Sign out
              </button>
            </div>
          </div>

          <section className="conversations-panel" aria-label="Saved conversations">
            <div className="panel-heading">
              <h2>Conversations</h2>
              <button className="icon-button" type="button" onClick={startNewConversation}>
                New chat
              </button>
            </div>

            <div className="conversation-list">
              {conversations.length === 0 ? (
                <p className="muted">Saved chats will appear here.</p>
              ) : (
                conversations.map((conversation) => (
                  <button
                    className={`conversation-item ${currentConversationId === conversation.conversationId ? "active" : ""}`}
                    type="button"
                    key={conversation.conversationId}
                    onClick={() => loadConversation(conversation.conversationId)}
                    disabled={loadingConversationId === conversation.conversationId}
                  >
                    <strong>{conversation.title}</strong>
                    <span>{conversation.activeFilename || "All documents"}</span>
                    <span>{conversation.messageCount} messages</span>
                  </button>
                ))
              )}
            </div>
          </section>

          <form className="upload-panel" onSubmit={uploadDocument}>
            <label className="file-drop">
              <input
                ref={fileInputRef}
                type="file"
                accept="application/pdf"
                onChange={(event) => setSelectedFile(event.target.files?.[0] || null)}
              />
              <span className="file-label">
                {selectedFile ? selectedFile.name : "Choose PDF"}
              </span>
              <span className="file-help">PDF documents up to backend upload limit</span>
            </label>

            <button className="primary-button" type="submit" disabled={uploading}>
              {uploading ? "Uploading..." : "Upload document"}
            </button>
          </form>

          <div className="status-panel">
            <span className="status-label">Index status</span>
            <strong>{uploadState.status}</strong>
            {uploadState.filename && <span>{uploadState.filename}</span>}
            <span>{uploadState.chunksCreated || 0} chunks indexed</span>
          </div>

          <section className="documents-panel" aria-label="Indexed documents">
            <div className="panel-heading">
              <h2>Documents</h2>
              <button className="icon-button" type="button" onClick={loadDocuments} disabled={loadingDocuments}>
                {loadingDocuments ? "..." : "Refresh"}
              </button>
            </div>

            <div className="document-list">
              {documents.length === 0 ? (
                <p className="muted">No indexed documents yet.</p>
              ) : (
                documents.map((document) => (
                  <article className="document-item" key={document.filename}>
                    <div>
                      <strong>{document.filename}</strong>
                      <span>{document.chunks} chunks</span>
                    </div>
                    <button
                      className="danger-button"
                      type="button"
                      onClick={() => deleteDocument(document.filename)}
                      disabled={deletingFilename === document.filename}
                    >
                      {deletingFilename === document.filename ? "Removing" : "Delete"}
                    </button>
                  </article>
                ))
              )}
            </div>
          </section>

          <section className="metrics-panel" aria-label="RAG performance">
            <div className="panel-heading">
              <h2>Performance</h2>
              <button className="icon-button" type="button" onClick={loadMetrics}>
                Refresh
              </button>
            </div>

            {metrics ? (
              <div className="metrics-grid">
                <div className="metric-card">
                  <span className="metric-label">Model</span>
                  <strong>{metrics.model}</strong>
                </div>
                <div className="metric-card">
                  <span className="metric-label">Avg time</span>
                  <strong>{(metrics.averageDurationMs / 1000).toFixed(1)}s</strong>
                </div>
                <div className="metric-card">
                  <span className="metric-label">Last time</span>
                  <strong>{(metrics.lastDurationMs / 1000).toFixed(1)}s</strong>
                </div>
                <div className="metric-card">
                  <span className="metric-label">Samples</span>
                  <strong>{metrics.samples}</strong>
                </div>
                <div className="metric-card">
                  <span className="metric-label">Top K</span>
                  <strong>{metrics.topK}</strong>
                </div>
                <div className="metric-card">
                  <span className="metric-label">Threshold</span>
                  <strong>{metrics.similarityThreshold}</strong>
                </div>
                <div className="metric-card">
                  <span className="metric-label">Chunk size</span>
                  <strong>{metrics.chunkSize}</strong>
                </div>
                <div className="metric-card">
                  <span className="metric-label">Overlap</span>
                  <strong>{metrics.chunkOverlap}</strong>
                </div>
              </div>
            ) : (
              <p className="muted">Metrics will appear after the backend responds.</p>
            )}
          </section>

          <section className="sources-panel" aria-label="Retrieved sources">
            <div className="panel-heading">
              <h2>Sources</h2>
              <span>{lastSources.length}</span>
            </div>

            <div className="source-list">
              {lastSources.length === 0 ? (
                <p className="muted">Matched chunks will appear after a question.</p>
              ) : (
                lastSources.map((source, index) => (
                  <article className="source-item" key={`${source.filename}-${source.chunkIndex}-${index}`}>
                    <div>
                      <strong>{source.filename}</strong>
                      <span>Chunk {source.chunkIndex}</span>
                    </div>
                    <p>{source.preview}</p>
                  </article>
                ))
              )}
            </div>
          </section>
        </aside>

        <section className="chat-panel" aria-label="Chat">
          <div className="chat-header">
            <div>
              <p className="eyebrow">RAG workspace</p>
              <h2>Ask your uploaded documents</h2>
            </div>
            <div className="chat-tools">
              <label className="scope-select">
                <span>Search scope</span>
                <select
                  value={selectedDocumentScope}
                  onChange={(event) => setSelectedDocumentScope(event.target.value)}
                >
                  <option value="">All documents</option>
                  {documents.map((document) => (
                    <option value={document.filename} key={document.filename}>
                      {document.filename}
                    </option>
                  ))}
                </select>
              </label>
              <span className="api-pill">API {API_BASE_URL}</span>
            </div>
          </div>

          {error && <div className="error-banner">{error}</div>}

          <div className="messages" aria-live="polite">
            {messages.map((message, index) => (
              <article className={`message ${message.role}`} key={`${message.role}-${index}`}>
                <span className="message-role">
                  {message.role === "user" ? "You" : message.role === "system" ? "System" : "DocuMind"}
                </span>
                <p>{message.text}</p>
                {message.durationMs !== undefined && (
                  <span className="message-meta">
                    {message.scope} | {message.model} | {(message.durationMs / 1000).toFixed(1)}s
                  </span>
                )}
              </article>
            ))}
            {asking && (
              <article className="message assistant">
                <span className="message-role">DocuMind</span>
                <p>Retrieving context and generating an answer...</p>
              </article>
            )}
          </div>

          <form className="ask-form" onSubmit={askQuestion}>
            <textarea
              value={question}
              onChange={(event) => setQuestion(event.target.value)}
              placeholder="Ask a question about your uploaded PDF"
              rows="3"
            />
            <button className="primary-button send-button" type="submit" disabled={!canAsk}>
              {asking ? "Thinking..." : "Ask"}
            </button>
          </form>
        </section>
      </section>
    </main>
  );
}

export default App;
