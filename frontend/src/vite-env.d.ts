/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** Base URL of the backend API (e.g. https://documentstore-backend.onrender.com).
   *  Leave unset in local dev: the Vite dev server proxies /api to localhost:8080. */
  readonly VITE_API_BASE_URL?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
