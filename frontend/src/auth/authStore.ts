const STORAGE_KEY = 'documentstore.token';

type Listener = (token: string | null) => void;

let token: string | null = localStorage.getItem(STORAGE_KEY);
const listeners = new Set<Listener>();

export function getToken(): string | null {
  return token;
}

export function setToken(next: string | null): void {
  token = next;
  if (next) {
    localStorage.setItem(STORAGE_KEY, next);
  } else {
    localStorage.removeItem(STORAGE_KEY);
  }
  listeners.forEach((listener) => listener(token));
}

export function subscribe(listener: Listener): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}
