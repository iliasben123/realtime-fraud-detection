import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "./client";
import type { AlertStatus, AlertsQuery, TransactionsQuery } from "./types";

/** Lève le corps d'erreur (ProblemDetail) pour que react-query l'expose dans `error`. */
function unwrap<T>(result: { data?: T; error?: unknown; response: Response }): T {
  if (result.error !== undefined) throw result.error;
  if (result.data === undefined) throw new Error(`Réponse vide (${result.response.status})`);
  return result.data;
}

export const keys = {
  alerts: (q: AlertsQuery) => ["alerts", q] as const,
  alert: (id: string) => ["alert", id] as const,
  history: (id: string) => ["alert", id, "history"] as const,
  transactions: (q: TransactionsQuery) => ["transactions", q] as const,
  transaction: (id: string) => ["transaction", id] as const,
  overview: ["stats", "overview"] as const,
  models: ["stats", "models"] as const,
  rules: ["stats", "rules"] as const,
};

export function useAlerts(query: AlertsQuery) {
  return useQuery({
    queryKey: keys.alerts(query),
    queryFn: async () => unwrap(await api.GET("/api/v1/alerts", { params: { query } })),
    placeholderData: (prev) => prev,
  });
}

export function useAlert(id: string) {
  return useQuery({
    queryKey: keys.alert(id),
    queryFn: async () => unwrap(await api.GET("/api/v1/alerts/{alertId}", { params: { path: { alertId: id } } })),
  });
}

export function useAlertHistory(id: string) {
  return useQuery({
    queryKey: keys.history(id),
    queryFn: async () =>
      unwrap(await api.GET("/api/v1/alerts/{alertId}/history", { params: { path: { alertId: id } } })),
  });
}

export function useChangeStatus(alertId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (body: { status: AlertStatus; comment?: string }) =>
      unwrap(
        await api.PATCH("/api/v1/alerts/{alertId}/status", {
          params: { path: { alertId } },
          body,
        }),
      ),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["alert", alertId] });
      qc.invalidateQueries({ queryKey: ["alerts"] });
      qc.invalidateQueries({ queryKey: ["stats"] });
    },
  });
}

export function useTransactions(query: TransactionsQuery) {
  return useQuery({
    queryKey: keys.transactions(query),
    queryFn: async () => unwrap(await api.GET("/api/v1/transactions", { params: { query } })),
    placeholderData: (prev) => prev,
  });
}

export function useTransaction(id: string | undefined) {
  return useQuery({
    queryKey: keys.transaction(id ?? ""),
    enabled: !!id,
    queryFn: async () =>
      unwrap(await api.GET("/api/v1/transactions/{transactionId}", { params: { path: { transactionId: id! } } })),
  });
}

export function useOverview() {
  return useQuery({
    queryKey: keys.overview,
    queryFn: async () => unwrap(await api.GET("/api/v1/stats/overview")),
    refetchInterval: 15_000,
  });
}

export function useModelStats() {
  return useQuery({
    queryKey: keys.models,
    queryFn: async () => unwrap(await api.GET("/api/v1/stats/models")),
  });
}

export function useRuleStats() {
  return useQuery({
    queryKey: keys.rules,
    queryFn: async () => unwrap(await api.GET("/api/v1/stats/rules")),
  });
}
