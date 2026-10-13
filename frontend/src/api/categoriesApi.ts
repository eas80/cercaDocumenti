import type { Category } from './types';
import { API_ROOT } from './apiRoot';
import { authorizedFetch, errorMessage } from './http';

const BASE_URL = `${API_ROOT}/api/categories`;

export async function fetchCategories(): Promise<Category[]> {
  const response = await authorizedFetch(BASE_URL);
  if (!response.ok) throw new Error(await errorMessage(response));
  return response.json();
}

export async function createCategory(name: string): Promise<Category> {
  const response = await authorizedFetch(BASE_URL, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name }),
  });
  if (!response.ok) throw new Error(await errorMessage(response));
  return response.json();
}

export async function renameCategory(id: string, name: string): Promise<Category> {
  const response = await authorizedFetch(`${BASE_URL}/${encodeURIComponent(id)}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name }),
  });
  if (!response.ok) throw new Error(await errorMessage(response));
  return response.json();
}

export async function deleteCategory(id: string): Promise<void> {
  const response = await authorizedFetch(`${BASE_URL}/${encodeURIComponent(id)}`, { method: 'DELETE' });
  if (!response.ok) throw new Error(await errorMessage(response));
}
