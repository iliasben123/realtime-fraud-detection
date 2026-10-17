import createClient, { type Middleware } from "openapi-fetch";
import type { paths } from "./schema";

/**
 * Client HTTP typé, généré depuis le contrat OpenAPI de l'API (`npm run generate:api`).
 * Le jeton JWT est ajouté à chaque requête ; une réponse 401 invalide la session.
 */
const TOKEN_KEY = "fraud-console.token";

export const tokenStore = {
  get: (): string | null => localStorage.getItem(TOKEN_KEY),
  set: (token: string) => localStorage.setItem(TOKEN_KEY, token),
  clear: () => localStorage.removeItem(TOKEN_KEY),
};

type Listener = () => void;
const unauthorizedListeners = new Set<Listener>();
export const onUnauthorized = (listener: Listener): (() => void) => {
  unauthorizedListeners.add(listener);
  return () => {
    unauthorizedListeners.delete(listener);
  };
};

const auth: Middleware = {
  onRequest(request) {
    const token = tokenStore.get();
    if (token) request.headers.set("Authorization", `Bearer ${token}`);
    return request;
  },
  onResponse(response) {
    if (response.status === 401 && !response.url.includes("/auth/login")) {
      tokenStore.clear();
      unauthorizedListeners.forEach((l) => l());
    }
    return response;
  },
};

export const api = createClient<paths>({ baseUrl: "" });
api.use(auth);

/** Corps d'erreur RFC 9457 renvoyé par l'API. */
export interface ProblemDetail {
  title?: string;
  status?: number;
  detail?: string;
  errors?: Record<string, string>;
  allowedTransitions?: string[];
  [key: string]: unknown;
}

export function describeError(error: unknown, fallback = "Erreur inattendue"): string {
  if (error && typeof error === "object") {
    const p = error as ProblemDetail;
    if (p.detail) return p.detail;
    if (p.title) return p.title;
    if (p.errors) return Object.entries(p.errors).map(([k, v]) => `${k} : ${v}`).join(", ");
  }
  if (error instanceof Error) return error.message;
  return fallback;
}
