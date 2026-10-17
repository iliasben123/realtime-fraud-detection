import type { components, paths } from "./schema";

type S = components["schemas"];

export type AlertResponse = S["AlertResponse"];
export type AlertDetailResponse = S["AlertDetailResponse"];
export type TransactionResponse = S["TransactionResponse"];
export type TransactionDetailResponse = S["TransactionDetailResponse"];
export type StatusHistoryResponse = S["StatusHistoryResponse"];
export type StatisticsResponse = S["StatisticsResponse"];
export type ModelVersionStatisticsResponse = S["ModelVersionStatisticsResponse"];
export type RuleFeedbackResponse = S["RuleFeedbackResponse"];
export type LoginResponse = S["LoginResponse"];
export type MeResponse = S["MeResponse"];

export type AlertStatus = NonNullable<AlertResponse["status"]>;
export type AlertSeverity = NonNullable<AlertResponse["severity"]>;
export type TransactionType = NonNullable<TransactionResponse["type"]>;

export type AlertsQuery = NonNullable<paths["/api/v1/alerts"]["get"]["parameters"]["query"]>;
export type TransactionsQuery = NonNullable<paths["/api/v1/transactions"]["get"]["parameters"]["query"]>;

export const ALERT_STATUSES: AlertStatus[] = ["OPEN", "UNDER_REVIEW", "CONFIRMED_FRAUD", "FALSE_POSITIVE", "BLOCKED"];
export const SEVERITIES: AlertSeverity[] = ["LOW", "MEDIUM", "HIGH", "CRITICAL"];
export const TRANSACTION_TYPES: TransactionType[] = ["PAYMENT", "TRANSFER", "CASH_OUT", "CASH_IN", "DEBIT"];

export const STATUS_LABELS: Record<AlertStatus, string> = {
  OPEN: "Ouverte",
  UNDER_REVIEW: "En revue",
  CONFIRMED_FRAUD: "Fraude confirmée",
  FALSE_POSITIVE: "Faux positif",
  BLOCKED: "Bloquée",
};

export const SEVERITY_LABELS: Record<AlertSeverity, string> = {
  LOW: "Faible",
  MEDIUM: "Moyenne",
  HIGH: "Haute",
  CRITICAL: "Critique",
};
