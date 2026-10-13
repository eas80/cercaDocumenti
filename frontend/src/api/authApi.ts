import { API_ROOT } from './apiRoot';
import { clearSession, setSession } from '../auth/authStore';
import { authorizedFetch, errorMessage } from './http';

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

export async function changePassword(currentPassword: string, newPassword: string): Promise<void> {
  const response = await authorizedFetch(`${API_ROOT}/api/auth/password`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ currentPassword, newPassword }),
  });
  if (response.status === 403) {
    throw new Error('La password attuale non è corretta.');
  }
  if (!response.ok) throw new Error(await errorMessage(response));
}

export async function fetchOtherUsers(): Promise<string[]> {
  const response = await authorizedFetch(`${API_ROOT}/api/auth/users`);
  if (!response.ok) {
    throw new Error(`Impossibile recuperare l'elenco utenti (HTTP ${response.status})`);
  }
  return response.json();
}
