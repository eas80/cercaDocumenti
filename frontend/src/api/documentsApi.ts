import type { DocumentSummary, SearchParams } from './types';
import { API_ROOT } from './apiRoot';
import { getToken, setToken } from '../auth/authStore';

const BASE_URL = `${API_ROOT}/api/documents`;

/** Attaches the bearer token (if any) and logs out automatically on a 401. */
async function authorizedFetch(url: string, init: RequestInit = {}): Promise<Response> {
  const token = getToken();
  const headers = new Headers(init.headers);
  if (token) headers.set('Authorization', `Bearer ${token}`);

  const response = await fetch(url, { ...init, headers });
  if (response.status === 401) {
    setToken(null);
  }
  return response;
}

async function errorMessage(response: Response): Promise<string> {
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

export async function searchDocuments(params: SearchParams): Promise<DocumentSummary[]> {
  const query = new URLSearchParams();
  if (params.nameLike.trim()) query.set('nameLike', params.nameLike.trim());
  if (params.descriptionLike.trim()) query.set('descriptionLike', params.descriptionLike.trim());
  if (params.dateFrom) query.set('dateFrom', params.dateFrom);
  if (params.dateTo) query.set('dateTo', params.dateTo);

  const response = await authorizedFetch(`${BASE_URL}?${query.toString()}`);
  if (!response.ok) throw new Error(await errorMessage(response));
  return response.json();
}

export async function createDocument(name: string, description: string, file: File): Promise<DocumentSummary> {
  const formData = new FormData();
  formData.set('name', name);
  formData.set('description', description);
  formData.set('file', file);

  const response = await authorizedFetch(BASE_URL, { method: 'PUT', body: formData });
  if (!response.ok) throw new Error(await errorMessage(response));
  return response.json();
}

export async function updateDocument(
  id: string,
  name: string,
  description: string,
  file: File | null,
): Promise<DocumentSummary> {
  const formData = new FormData();
  formData.set('name', name);
  formData.set('description', description);
  if (file) formData.set('file', file);

  const response = await authorizedFetch(`${BASE_URL}/${encodeURIComponent(id)}`, { method: 'POST', body: formData });
  if (!response.ok) throw new Error(await errorMessage(response));
  return response.json();
}

export async function deleteDocument(id: string): Promise<void> {
  const response = await authorizedFetch(`${BASE_URL}/${encodeURIComponent(id)}`, { method: 'DELETE' });
  if (!response.ok) throw new Error(await errorMessage(response));
}

export async function downloadDocument(id: string, fallbackFilename: string): Promise<void> {
  const response = await authorizedFetch(`${BASE_URL}/${encodeURIComponent(id)}`);
  if (!response.ok) throw new Error(await errorMessage(response));

  const blob = await response.blob();
  const filename = extractFilename(response.headers.get('Content-Disposition')) ?? fallbackFilename;

  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

function extractFilename(disposition: string | null): string | null {
  if (!disposition) return null;
  const utf8Match = disposition.match(/filename\*=UTF-8''([^;]+)/i);
  if (utf8Match) return decodeURIComponent(utf8Match[1]);
  const asciiMatch = disposition.match(/filename="([^"]+)"/i);
  return asciiMatch ? asciiMatch[1] : null;
}
