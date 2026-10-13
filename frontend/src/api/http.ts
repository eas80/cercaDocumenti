import { clearSession, getToken } from '../auth/authStore';

/** Attaches the bearer token (if any) and logs out automatically on a 401. */
export async function authorizedFetch(url: string, init: RequestInit = {}): Promise<Response> {
  const token = getToken();
  const headers = new Headers(init.headers);
  if (token) headers.set('Authorization', `Bearer ${token}`);

  const response = await fetch(url, { ...init, headers });
  if (response.status === 401) {
    clearSession();
  }
  return response;
}

export async function errorMessage(response: Response): Promise<string> {
  try {
    const body = await response.json();
    if (body && typeof body.message === 'string') {
      return body.message;
    }
  } catch {
    // response body wasn't JSON, fall through to the generic message below
  }
  return `Richiesta fallita (HTTP ${response.status})`;
}
