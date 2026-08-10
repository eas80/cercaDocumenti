import { useState } from 'react';
import type { FormEvent } from 'react';
import { login } from '../api/authApi';

// If the backend is on a free-tier host that sleeps when idle, a cold start
// can take up to a minute - past this point the user needs to know that's
// what's happening, not just stare at a spinner wondering if it's broken.
const SLOW_LOGIN_HINT_DELAY_MS = 4000;

export default function LoginPage() {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [slow, setSlow] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setSubmitting(true);
    setSlow(false);
    setError(null);
    const slowTimer = setTimeout(() => setSlow(true), SLOW_LOGIN_HINT_DELAY_MS);
    try {
      await login(username, password);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Errore imprevisto.');
    } finally {
      clearTimeout(slowTimer);
      setSubmitting(false);
      setSlow(false);
    }
  }

  return (
    <div className="login-shell">
      <form className="login-card" onSubmit={handleSubmit}>
        <div className="login-header">
          <span className="app-header-icon" aria-hidden="true">
            📄
          </span>
          <h1>Documenti</h1>
        </div>

        <div className="field">
          <label htmlFor="login-username">Nome utente</label>
          <input
            id="login-username"
            type="text"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            autoFocus
            autoComplete="username"
          />
        </div>

        <div className="field">
          <label htmlFor="login-password">Password</label>
          <input
            id="login-password"
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete="current-password"
          />
        </div>

        {error && <p className="form-error">{error}</p>}

        <button type="submit" className="btn btn-primary login-submit" disabled={submitting}>
          {submitting ? 'Accesso in corso…' : 'Accedi'}
        </button>

        {slow && (
          <p className="login-hint">
            Il server potrebbe essere in stand-by e impiegare fino a un minuto per riattivarsi. Attendere…
          </p>
        )}
      </form>
    </div>
  );
}
