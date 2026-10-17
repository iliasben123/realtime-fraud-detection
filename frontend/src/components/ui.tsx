import type { ReactNode } from "react";
import type { AlertSeverity, AlertStatus } from "@/api/types";
import { SEVERITY_LABELS, STATUS_LABELS } from "@/api/types";

// ----------------------------------------------------------------------------- formatage

const money = new Intl.NumberFormat("fr-FR", { style: "currency", currency: "EUR", maximumFractionDigits: 2 });
const dateTime = new Intl.DateTimeFormat("fr-FR", { dateStyle: "short", timeStyle: "medium" });

export const fmt = {
  money: (v: number | undefined | null) => (v == null ? "–" : money.format(v)),
  date: (v: string | undefined | null) => (v ? dateTime.format(new Date(v)) : "–"),
  pct: (v: number | undefined | null, digits = 1) => (v == null ? "–" : `${(v * 100).toFixed(digits)} %`),
  score: (v: number | undefined | null) => (v == null ? "–" : v.toFixed(0)),
  int: (v: number | undefined | null) => (v == null ? "–" : new Intl.NumberFormat("fr-FR").format(v)),
  shortId: (v: string | undefined) => (v ? v.slice(0, 8) : "–"),
};

// ----------------------------------------------------------------------------- badges

const severityClass: Record<AlertSeverity, string> = {
  LOW: "bg-slate-100 text-slate-700",
  MEDIUM: "bg-amber-100 text-amber-800",
  HIGH: "bg-orange-100 text-orange-800",
  CRITICAL: "bg-rose-100 text-rose-800",
};

const statusClass: Record<AlertStatus, string> = {
  OPEN: "bg-blue-100 text-blue-800",
  UNDER_REVIEW: "bg-violet-100 text-violet-800",
  CONFIRMED_FRAUD: "bg-rose-100 text-rose-800",
  FALSE_POSITIVE: "bg-emerald-100 text-emerald-800",
  BLOCKED: "bg-slate-800 text-white",
};

export function SeverityBadge({ value }: { value?: AlertSeverity }) {
  if (!value) return null;
  return <span className={`rounded-full px-2 py-0.5 text-xs font-semibold ${severityClass[value]}`}>{SEVERITY_LABELS[value]}</span>;
}

export function StatusBadge({ value }: { value?: AlertStatus }) {
  if (!value) return null;
  return <span className={`rounded-full px-2 py-0.5 text-xs font-semibold ${statusClass[value]}`}>{STATUS_LABELS[value]}</span>;
}

export function RiskScore({ value }: { value?: number | null }) {
  if (value == null) return <span className="text-slate-400">–</span>;
  const tone = value >= 85 ? "bg-rose-500" : value >= 60 ? "bg-orange-500" : value >= 40 ? "bg-amber-500" : "bg-slate-400";
  return (
    <span className="inline-flex items-center gap-2">
      <span className="h-2 w-16 overflow-hidden rounded bg-slate-200">
        <span className={`block h-full ${tone}`} style={{ width: `${Math.min(100, value)}%` }} />
      </span>
      <span className="tabular-nums font-medium">{value.toFixed(0)}</span>
    </span>
  );
}

export function RuleChip({ name }: { name?: string }) {
  const ml = name === "ML_HIGH_RISK";
  return (
    <span className={`rounded px-1.5 py-0.5 font-mono text-xs ${ml ? "bg-brand-100 text-brand-700" : "bg-slate-100 text-slate-700"}`}>
      {name ?? "–"}
    </span>
  );
}

// ----------------------------------------------------------------------------- blocs

export function Card({ title, children, actions }: { title?: string; children: ReactNode; actions?: ReactNode }) {
  return (
    <section className="card">
      {(title || actions) && (
        <header className="flex items-center justify-between border-b border-slate-200 px-4 py-3">
          <h2 className="text-sm font-semibold text-slate-700">{title}</h2>
          {actions}
        </header>
      )}
      <div className="p-4">{children}</div>
    </section>
  );
}

export function Kpi({ label, value, hint }: { label: string; value: ReactNode; hint?: string }) {
  return (
    <div className="card p-4">
      <div className="text-xs uppercase tracking-wide text-slate-500">{label}</div>
      <div className="mt-1 text-2xl font-semibold tabular-nums">{value}</div>
      {hint && <div className="mt-1 text-xs text-slate-500">{hint}</div>}
    </div>
  );
}

export function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <label className="block text-sm">
      <span className="mb-1 block text-xs font-medium text-slate-600">{label}</span>
      {children}
    </label>
  );
}

export function Spinner({ label = "Chargement…" }: { label?: string }) {
  return <div className="py-8 text-center text-sm text-slate-500">{label}</div>;
}

export function ErrorBox({ message }: { message: string }) {
  return <div className="rounded-md border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-800">{message}</div>;
}

export function Empty({ message = "Aucun résultat" }: { message?: string }) {
  return <div className="py-8 text-center text-sm text-slate-500">{message}</div>;
}

export function Pagination({
  page,
  totalPages,
  totalElements,
  onChange,
}: {
  page: number;
  totalPages: number;
  totalElements: number;
  onChange: (page: number) => void;
}) {
  return (
    <div className="flex items-center justify-between px-1 pt-3 text-sm text-slate-600">
      <span>
        {fmt.int(totalElements)} résultat{totalElements > 1 ? "s" : ""} · page {page + 1} / {Math.max(1, totalPages)}
      </span>
      <div className="flex gap-2">
        <button className="btn-secondary" disabled={page <= 0} onClick={() => onChange(page - 1)}>
          Précédent
        </button>
        <button className="btn-secondary" disabled={page + 1 >= totalPages} onClick={() => onChange(page + 1)}>
          Suivant
        </button>
      </div>
    </div>
  );
}

export function DescriptionList({ items }: { items: Array<[string, ReactNode]> }) {
  return (
    <dl className="grid grid-cols-1 gap-x-6 gap-y-2 text-sm sm:grid-cols-2">
      {items.map(([k, v]) => (
        <div key={k} className="flex justify-between gap-4 border-b border-slate-100 py-1">
          <dt className="text-slate-500">{k}</dt>
          <dd className="text-right font-medium">{v}</dd>
        </div>
      ))}
    </dl>
  );
}
