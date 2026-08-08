// Unset in local dev: the Vite dev server proxy (vite.config.ts) forwards
// relative /api calls to the backend. Set in production builds (Render) to
// the deployed backend's full URL.
export const API_ROOT = import.meta.env.VITE_API_BASE_URL ?? '';
