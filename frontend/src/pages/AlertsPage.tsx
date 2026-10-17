import { useMemo } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { describeError } from "@/api/client";
import { useAlerts } from "@/api/hooks";
import { ALERT_STATUSES, SEVERITIES, SEVERITY_LABELS, STATUS_LABELS, type AlertsQuery } from "@/api/types";
import { Card, Empty, ErrorBox, Field, Pagination, RiskScore, RuleChip, SeverityBadge, Spinner, StatusBadge, fmt } from "@/components/ui";

const PAGE_SIZE = 20;

export function AlertsPage() {
  const [params, setParams] = useSearchParams({ status: "OPEN" });

  const query = useMemo<AlertsQuery>(() => {
    const q: AlertsQuery = { page: Number(params.get("page") ?? 0), size: PAGE_SIZE, sortBy: "riskScore", direction: "DESC" };
    const status = params.get("status");
    const severity = params.get("severity");
    const ruleName = params.get("ruleName");
    const account = params.get("account");
    const minRiskScore = params.get("minRiskScore");
    if (status) q.status = status as AlertsQuery["status"];
    if (severity) q.severity = severity as AlertsQuery["severity"];
    if (ruleName) q.ruleName = ruleName;
    if (account) q.account = account;
    if (minRiskScore) q.minRiskScore = Number(minRiskScore);
    return q;
  }, [params]);

  const { data, isLoading, isError, error, isFetching } = useAlerts(query);

  function update(key: string, value: string) {
    const next = new URLSearchParams(params);
    if (value) next.set(key, value);
    else next.delete(key);
    if (key !== "page") next.delete("page");
    setParams(next);
  }

  return (
    <div className="space-y-4">
      <div className="flex items-end justify-between">
        <div>
          <h1 className="text-xl font-semibold">File d'alertes</h1>
          <p className="text-sm text-slate-500">Triées par score de risque décroissant, puis par date.</p>
        </div>
        {isFetching && !isLoading && <span className="text-xs text-slate-400">actualisation…</span>}
      </div>

      <Card>
        <div className="grid grid-cols-2 gap-3 md:grid-cols-5">
          <Field label="Statut">
            <select className="input" value={params.get("status") ?? ""} onChange={(e) => update("status", e.target.value)}>
              <option value="">Tous</option>
              {ALERT_STATUSES.map((s) => (
                <option key={s} value={s}>
                  {STATUS_LABELS[s]}
                </option>
              ))}
            </select>
          </Field>
          <Field label="Sévérité">
            <select className="input" value={params.get("severity") ?? ""} onChange={(e) => update("severity", e.target.value)}>
              <option value="">Toutes</option>
              {SEVERITIES.map((s) => (
                <option key={s} value={s}>
                  {SEVERITY_LABELS[s]}
                </option>
              ))}
            </select>
          </Field>
          <Field label="Règle">
            <input className="input" placeholder="ex. ML_HIGH_RISK" value={params.get("ruleName") ?? ""} onChange={(e) => update("ruleName", e.target.value)} />
          </Field>
          <Field label="Compte (émetteur ou destinataire)">
            <input className="input" placeholder="C1234567890" value={params.get("account") ?? ""} onChange={(e) => update("account", e.target.value)} />
          </Field>
          <Field label="Score de risque minimum">
            <input className="input" type="number" min={0} max={100} value={params.get("minRiskScore") ?? ""} onChange={(e) => update("minRiskScore", e.target.value)} />
          </Field>
        </div>
      </Card>

      <Card>
        {isLoading && <Spinner />}
        {isError && <ErrorBox message={describeError(error)} />}
        {data && data.content?.length === 0 && <Empty message="Aucune alerte ne correspond aux filtres." />}
        {data && data.content && data.content.length > 0 && (
          <>
            <div className="overflow-x-auto">
              <table className="table">
                <thead>
                  <tr>
                    <th>Risque</th>
                    <th>Règle</th>
                    <th>Sévérité</th>
                    <th>Statut</th>
                    <th>Transaction</th>
                    <th className="text-right">Montant</th>
                    <th>Émetteur → destinataire</th>
                    <th className="text-right">Score ML</th>
                    <th>Créée</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {data.content.map(({ alert, transaction }) => (
                    <tr key={alert?.id} className="hover:bg-slate-50">
                      <td>
                        <RiskScore value={alert?.riskScore} />
                      </td>
                      <td>
                        <RuleChip name={alert?.ruleName} />
                      </td>
                      <td>
                        <SeverityBadge value={alert?.severity} />
                      </td>
                      <td>
                        <StatusBadge value={alert?.status} />
                      </td>
                      <td className="font-mono text-xs">{transaction?.type}</td>
                      <td className="text-right tabular-nums">{fmt.money(transaction?.amount)}</td>
                      <td className="font-mono text-xs">
                        {transaction?.nameOrig} → {transaction?.nameDest}
                      </td>
                      <td className="text-right tabular-nums">{fmt.score(transaction?.mlScore)}</td>
                      <td className="whitespace-nowrap text-xs text-slate-500">{fmt.date(alert?.createdAt)}</td>
                      <td>
                        <Link to={`/alerts/${alert?.id}`} className="text-brand-600 hover:underline">
                          Ouvrir
                        </Link>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <Pagination
              page={data.page ?? 0}
              totalPages={data.totalPages ?? 0}
              totalElements={data.totalElements ?? 0}
              onChange={(p) => update("page", String(p))}
            />
          </>
        )}
      </Card>
    </div>
  );
}
