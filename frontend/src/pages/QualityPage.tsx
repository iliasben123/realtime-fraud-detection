import { Bar, BarChart, CartesianGrid, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { describeError } from "@/api/client";
import { useModelStats, useOverview, useRuleStats } from "@/api/hooks";
import { Card, Empty, ErrorBox, Kpi, RuleChip, Spinner, fmt } from "@/components/ui";

export function QualityPage() {
  const overview = useOverview();
  const rules = useRuleStats();
  const models = useModelStats();

  const chartData = (rules.data ?? []).map((r) => ({
    rule: r.ruleName,
    "Alertes émises": r.alerts ?? 0,
    "dont fraudes labellisées": r.onLabeledFraud ?? 0,
  }));

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-xl font-semibold">Qualité de détection</h1>
        <p className="text-sm text-slate-500">
          Deux lectures : contre le label du dataset (vérité terrain PaySim) et contre les décisions des analystes, seule mesure
          disponible en production.
        </p>
      </div>

      {overview.isError && <ErrorBox message={describeError(overview.error)} />}
      {overview.data && (
        <div className="grid grid-cols-2 gap-4 md:grid-cols-5">
          <Kpi label="Transactions" value={fmt.int(overview.data.totalTransactions)} />
          <Kpi label="Suspectes" value={fmt.int(overview.data.suspiciousTransactions)} hint="règles ou modèle" />
          <Kpi label="Fraudes labellisées" value={fmt.int(overview.data.labeledFraudTransactions)} />
          <Kpi label="Précision globale" value={fmt.pct(overview.data.precision)} hint="suspectes réellement frauduleuses" />
          <Kpi label="Rappel global" value={fmt.pct(overview.data.recall)} hint="fraudes détectées" />
        </div>
      )}

      <Card title="Par règle : volume d'alertes et part fondée (label)">
        {rules.isLoading && <Spinner />}
        {rules.isError && <ErrorBox message={describeError(rules.error)} />}
        {rules.data && rules.data.length === 0 && <Empty message="Aucune alerte émise pour l'instant." />}
        {rules.data && rules.data.length > 0 && (
          <div className="grid gap-6 lg:grid-cols-2">
            <div className="h-72">
              <ResponsiveContainer>
                <BarChart data={chartData} margin={{ left: 8, right: 8 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
                  <XAxis dataKey="rule" tick={{ fontSize: 10 }} interval={0} angle={-20} textAnchor="end" height={60} />
                  <YAxis tick={{ fontSize: 11 }} scale="sqrt" />
                  <Tooltip />
                  <Legend wrapperStyle={{ fontSize: 12 }} />
                  <Bar dataKey="Alertes émises" fill="#94a3b8" radius={[3, 3, 0, 0]} />
                  <Bar dataKey="dont fraudes labellisées" fill="#3b5bdb" radius={[3, 3, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
              <p className="mt-1 text-center text-xs text-slate-400">échelle racine carrée</p>
            </div>
            <div className="overflow-x-auto">
              <table className="table">
                <thead>
                  <tr>
                    <th>Règle</th>
                    <th className="text-right">Alertes</th>
                    <th className="text-right">Précision (label)</th>
                    <th className="text-right">Confirmées</th>
                    <th className="text-right">Faux positifs</th>
                    <th className="text-right">Précision (analystes)</th>
                    <th className="text-right">Revues</th>
                  </tr>
                </thead>
                <tbody>
                  {rules.data.map((r) => (
                    <tr key={r.ruleName}>
                      <td>
                        <RuleChip name={r.ruleName} />
                      </td>
                      <td className="text-right tabular-nums">{fmt.int(r.alerts)}</td>
                      <td className="text-right tabular-nums">{fmt.pct(r.precisionVsLabel)}</td>
                      <td className="text-right tabular-nums">{fmt.int(r.confirmed)}</td>
                      <td className="text-right tabular-nums">{fmt.int(r.falsePositive)}</td>
                      <td className="text-right tabular-nums">{(r.confirmed ?? 0) + (r.falsePositive ?? 0) > 0 ? fmt.pct(r.precisionVsAnalysts) : "–"}</td>
                      <td className="text-right tabular-nums">{fmt.pct(r.reviewRate, 0)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}
      </Card>

      <Card title="Par version du modèle MLlib">
        {models.isLoading && <Spinner />}
        {models.isError && <ErrorBox message={describeError(models.error)} />}
        {models.data && models.data.length === 0 && <Empty message="Aucune transaction scorée par un modèle." />}
        {models.data && models.data.length > 0 && (
          <div className="overflow-x-auto">
            <table className="table">
              <thead>
                <tr>
                  <th>Version</th>
                  <th className="text-right">Scorées</th>
                  <th className="text-right">Fraudes (label)</th>
                  <th className="text-right">Alertes ML</th>
                  <th className="text-right">Précision (label)</th>
                  <th className="text-right">Rappel (label)</th>
                  <th className="text-right">Confirmées</th>
                  <th className="text-right">Faux positifs</th>
                  <th className="text-right">Précision (analystes)</th>
                  <th className="text-right">Score ML moyen</th>
                  <th>Période</th>
                </tr>
              </thead>
              <tbody>
                {models.data.map((m) => (
                  <tr key={m.modelVersion}>
                    <td className="font-mono text-xs">{m.modelVersion}</td>
                    <td className="text-right tabular-nums">{fmt.int(m.scoredTransactions)}</td>
                    <td className="text-right tabular-nums">{fmt.int(m.labeledFraud)}</td>
                    <td className="text-right tabular-nums">{fmt.int(m.mlAlerts)}</td>
                    <td className="text-right tabular-nums">{fmt.pct(m.precisionVsLabel)}</td>
                    <td className="text-right tabular-nums">{fmt.pct(m.recallVsLabel)}</td>
                    <td className="text-right tabular-nums">{fmt.int(m.mlAlertsConfirmed)}</td>
                    <td className="text-right tabular-nums">{fmt.int(m.mlAlertsFalsePositive)}</td>
                    <td className="text-right tabular-nums">
                      {(m.mlAlertsConfirmed ?? 0) + (m.mlAlertsFalsePositive ?? 0) > 0 ? fmt.pct(m.precisionVsAnalysts) : "–"}
                    </td>
                    <td className="text-right tabular-nums">{m.avgMlScore?.toFixed(1) ?? "–"}</td>
                    <td className="whitespace-nowrap text-xs text-slate-500">
                      {fmt.date(m.firstScoredAt)} → {fmt.date(m.lastScoredAt)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>
    </div>
  );
}
