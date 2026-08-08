import { API_ROOT } from './apiRoot';
import { setToken } from '../auth/authStore';

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
  setToken(body.token);
}

export function logout(): void {
  setToken(null);
}
