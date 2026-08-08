import { useEffect, useState } from 'react';
import { getToken, subscribe } from '../auth/authStore';

export function useAuthToken(): string | null {
  const [token, setTokenState] = useState(getToken());

  useEffect(() => subscribe(setTokenState), []);

  return token;
}
