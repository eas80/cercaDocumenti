import type { DocumentSummary, SearchParams } from './types';
import { API_ROOT } from './apiRoot';
import { clearSession, getToken } from '../auth/authStore';

const BASE_URL = `${API_ROOT}/api/documents`;

/** Attaches the bearer token (if any) and logs out automatically on a 401. */
async function authorizedFetch(url: string, init: RequestInit = {}): Promise<Response> {
  const token = getToken();
  const headers = new Headers(init.headers);
  if (token) headers.set('Authorization', `Bearer ${token}`);

  const response = await fetch(url, { ...init, headers });
  if (response.status === 401) {
    clearSession();
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

export async function shareDocument(id: string, usernames: string[]): Promise<DocumentSummary> {
  const response = await authorizedFetch(`${BASE_URL}/${encodeURIComponent(id)}/share`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ usernames }),
  });
  if (!response.ok) throw new Error(await errorMessage(response));
  return response.json();
}

// Present only inside the Android WebView wrapper (see android/), which
// injects this interface so downloads can be saved to the device's real
// Downloads folder - a plain browser blob download (below) never reaches
// disk the same way inside a WebView.
declare global {
  interface Window {
    AndroidDownloader?: {
      saveBase64File(filename: string, mimeType: string, base64Data: string): void;
    };
  }
}

export async function downloadDocument(id: string, fallbackFilename: string): Promise<void> {
  const response = await authorizedFetch(`${BASE_URL}/${encodeURIComponent(id)}`);
  if (!response.ok) throw new Error(await errorMessage(response));

  const blob = await response.blob();
  const filename = extractFilename(response.headers.get('Content-Disposition')) ?? fallbackFilename;

  if (window.AndroidDownloader) {
    const base64 = await blobToBase64(blob);
    window.AndroidDownloader.saveBase64File(filename, blob.type || 'application/octet-stream', base64);
    return;
  }

  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

function blobToBase64(blob: Blob): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onloadend = () => {
      const result = reader.result as string;
      // strips the "data:<mime>;base64," prefix FileReader adds
      resolve(result.substring(result.indexOf(',') + 1));
    };
    reader.onerror = () => reject(reader.error ?? new Error('Failed to read blob'));
    reader.readAsDataURL(blob);
  });
}

function extractFilename(disposition: string | null): string | null {
  if (!disposition) return null;
  const utf8Match = disposition.match(/filename\*=UTF-8''([^;]+)/i);
  if (utf8Match) return decodeURIComponent(utf8Match[1]);
  const asciiMatch = disposition.match(/filename="([^"]+)"/i);
  return asciiMatch ? asciiMatch[1] : null;
}
