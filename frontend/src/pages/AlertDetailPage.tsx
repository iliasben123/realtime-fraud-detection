import { useState } from "react";
import { Link, useParams } from "react-router-dom";
import { describeError } from "@/api/client";
import { useAlert, useAlertHistory, useChangeStatus, useTransaction } from "@/api/hooks";
import { STATUS_LABELS, type AlertStatus } from "@/api/types";
import { useAuth } from "@/auth/AuthContext";
import { Card, DescriptionList, ErrorBox, RiskScore, RuleChip, SeverityBadge, Spinner, StatusBadge, fmt } from "@/components/ui";

const ACTION_STYLE: Record<AlertStatus, string> = {
  OPEN: "btn-secondary",
  UNDER_REVIEW: "btn-secondary",
  CONFIRMED_FRAUD: "btn-primary",
  FALSE_POSITIVE: "btn-secondary",
  BLOCKED: "btn-danger",
};

export function AlertDetailPage() {
  const { alertId = "" } = useParams();
  const { user } = useAuth();
  const detail = useAlert(alertId);
  const history = useAlertHistory(alertId);
  const transaction = useTransaction(detail.data?.transaction?.id);
  const change = useChangeStatus(alertId);
  const [comment, setComment] = useState("");

  if (detail.isLoading) return <Spinner />;
  if (detail.isError || !detail.data) return <ErrorBox message={describeError(detail.error, "Alerte introuvable")} />;

  const { alert, transaction: tx } = detail.data;
  const transitions = (alert?.allowedTransitions ?? []) as AlertStatus[];
  const otherAlerts = (transaction.data?.alerts ?? []).filter((a) => a.id !== alert?.id);

  async function apply(status: AlertStatus) {
    await change.mutateAsync({ status, comment: comment.trim() || undefined });
    setComment("");
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <div>
          <Link to="/alerts" className="text-sm text-brand-600 hover:underline">
            ← File d'alertes
          </Link>
          <h1 className="mt-1 flex items-center gap-3 text-xl font-semibold">
            <RuleChip name={alert?.ruleName} />
            <SeverityBadge value={alert?.severity} />
            <StatusBadge value={alert?.status} />
          </h1>
          <p className="mt-1 text-sm text-slate-600">{alert?.description}</p>
        </div>
        <div className="text-right">
          <div className="text-xs uppercase tracking-wide text-slate-500">Score de risque</div>
          <RiskScore value={tx?.riskScore} />
        </div>
      </div>

      <div className="grid gap-4 lg:grid-cols-3">
        <div className="space-y-4 lg:col-span-2">
          <Card title="Transaction">
            <DescriptionList
              items={[
                ["Identifiant", <span className="font-mono text-xs">{tx?.id}</span>],
                ["Type", tx?.type ?? "–"],
                ["Montant", fmt.money(tx?.amount)],
                ["Horodatage", fmt.date(tx?.eventTime)],
                ["Émetteur", <span className="font-mono text-xs">{tx?.nameOrig}</span>],
                ["Solde émetteur avant → après", `${fmt.money(tx?.oldBalanceOrig)} → ${fmt.money(tx?.newBalanceOrig)}`],
                ["Destinataire", <span className="font-mono text-xs">{tx?.nameDest}</span>],
                ["Solde destinataire avant → après", `${fmt.money(tx?.oldBalanceDest)} → ${fmt.money(tx?.newBalanceDest)}`],
                ["Score ML", tx?.mlScore != null ? `${tx.mlScore.toFixed(1)} (modèle ${tx.modelVersion})` : "non scoré"],
                ["Label dataset (vérité terrain)", tx?.fraud ? <span className="text-rose-700">fraude</span> : "légitime"],
                ["Signalée par la source", tx?.flaggedFraud ? "oui" : "non"],
              ]}
            />
          </Card>

          <Card title={`Autres alertes sur cette transaction (${otherAlerts.length})`}>
            {otherAlerts.length === 0 ? (
              <p className="text-sm text-slate-500">Aucune autre règle déclenchée.</p>
            ) : (
              <ul className="space-y-2 text-sm">
                {otherAlerts.map((a) => (
                  <li key={a.id} className="flex items-center justify-between">
                    <span className="flex items-center gap-2">
                      <RuleChip name={a.ruleName} />
                      <SeverityBadge value={a.severity} />
                      <StatusBadge value={a.status} />
                    </span>
                    <Link to={`/alerts/${a.id}`} className="text-brand-600 hover:underline">
                      Ouvrir
                    </Link>
                  </li>
                ))}
              </ul>
            )}
          </Card>

          <Card title="Historique des décisions">
            {history.isLoading && <Spinner />}
            {history.data && history.data.length === 0 && <p className="text-sm text-slate-500">Aucune décision pour l'instant.</p>}
            {history.data && history.data.length > 0 && (
              <ol className="space-y-3 text-sm">
                {history.data.map((h) => (
                  <li key={h.id} className="flex gap-3">
                    <span className="w-36 shrink-0 text-xs text-slate-500">{fmt.date(h.changedAt)}</span>
                    <span className="flex-1">
                      <span className="font-medium">{h.changedBy ?? "système"}</span>{" "}
                      {h.previousStatus ? `${STATUS_LABELS[h.previousStatus]} → ` : ""}
                      <span className="font-medium">{h.newStatus ? STATUS_LABELS[h.newStatus] : ""}</span>
                      {h.comment && <span className="block text-slate-600">« {h.comment} »</span>}
                    </span>
                  </li>
                ))}
              </ol>
            )}
          </Card>
        </div>

        <div className="space-y-4">
          <Card title="Décision">
            {transitions.length === 0 ? (
              <p className="text-sm text-slate-500">État terminal : plus aucune transition possible.</p>
            ) : (
              <div className="space-y-3">
                <textarea
                  className="input"
                  rows={3}
                  placeholder="Commentaire (optionnel)"
                  value={comment}
                  onChange={(e) => setComment(e.target.value)}
                />
                <div className="flex flex-col gap-2">
                  {transitions.map((status) => {
                    const needsSupervisor = status === "BLOCKED" && !user?.canBlock;
                    return (
                      <button
                        key={status}
                        className={ACTION_STYLE[status]}
                        disabled={change.isPending || needsSupervisor}
                        title={needsSupervisor ? "Réservé aux superviseurs" : undefined}
                        onClick={() => apply(status)}
                      >
                        {STATUS_LABELS[status]}
                        {needsSupervisor && <span className="text-xs opacity-70">(superviseur)</span>}
                      </button>
                    );
                  })}
                </div>
                {change.isError && <ErrorBox message={describeError(change.error)} />}
                {change.isSuccess && <p className="text-sm text-emerald-700">Statut mis à jour.</p>}
              </div>
            )}
          </Card>

          <Card title="Alerte">
            <DescriptionList
              items={[
                ["Identifiant", <span className="font-mono text-xs">{fmt.shortId(alert?.id)}…</span>],
                ["Créée", fmt.date(alert?.createdAt)],
                ["Mise à jour", fmt.date(alert?.updatedAt)],
                ["Relecteur", alert?.reviewedBy ?? "–"],
                ["Commentaire", alert?.reviewComment ?? "–"],
                ["Score de la règle", fmt.score(alert?.riskScore)],
              ]}
            />
          </Card>
        </div>
      </div>
    </div>
  );
}
