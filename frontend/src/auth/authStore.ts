const TOKEN_KEY = 'documentstore.token';
const USERNAME_KEY = 'documentstore.username';

export interface Session {
  token: string | null;
  username: string | null;
}

type Listener = (session: Session) => void;

let session: Session = {
  token: localStorage.getItem(TOKEN_KEY),
  username: localStorage.getItem(USERNAME_KEY),
};
const listeners = new Set<Listener>();

export function getSession(): Session {
  return session;
}

export function getToken(): string | null {
  return session.token;
}

export function setSession(token: string | null, username: string | null): void {
  session = { token, username };
  if (token) {
    localStorage.setItem(TOKEN_KEY, token);
  } else {
    localStorage.removeItem(TOKEN_KEY);
  }
  if (username) {
    localStorage.setItem(USERNAME_KEY, username);
  } else {
    localStorage.removeItem(USERNAME_KEY);
  }
  listeners.forEach((listener) => listener(session));
}

export function clearSession(): void {
  setSession(null, null);
}

export function subscribe(listener: Listener): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}
