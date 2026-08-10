import { useEffect, useState } from 'react';
import type { Session } from '../auth/authStore';
import { getSession, subscribe } from '../auth/authStore';

export function useSession(): Session {
  const [session, setSessionState] = useState(getSession());

  useEffect(() => subscribe(setSessionState), []);

  return session;
}
