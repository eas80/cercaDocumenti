import { API_ROOT } from './apiRoot';
import { clearSession, getToken, setSession } from '../auth/authStore';

export async function login(username: string, password: string): Promise<void> {
  const response = await fetch(`${API_ROOT}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  });

  if (!response.ok) {
    throw new Error(response.status === 401 ? 'Nome utente o password non corretti.' : `Login fallito (HTTP ${response.status})`);
  }

  const body = await response.json();
  setSession(body.token, body.username);
}

export function logout(): void {
  clearSession();
}

export async function fetchOtherUsers(): Promise<string[]> {
  const response = await fetch(`${API_ROOT}/api/auth/users`, {
    headers: { Authorization: `Bearer ${getToken()}` },
  });
  if (!response.ok) {
    throw new Error(`Impossibile recuperare l'elenco utenti (HTTP ${response.status})`);
  }
  return response.json();
}
