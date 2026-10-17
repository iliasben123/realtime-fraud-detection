import { useMemo } from "react";
import { useSearchParams } from "react-router-dom";
import { describeError } from "@/api/client";
import { useTransactions } from "@/api/hooks";
import { TRANSACTION_TYPES, type TransactionsQuery } from "@/api/types";
import { Card, Empty, ErrorBox, Field, Pagination, RiskScore, Spinner, fmt } from "@/components/ui";

const PAGE_SIZE = 25;

export function TransactionsPage() {
  const [params, setParams] = useSearchParams({ suspicious: "true" });

  const query = useMemo<TransactionsQuery>(() => {
    const q: TransactionsQuery = { page: Number(params.get("page") ?? 0), size: PAGE_SIZE, sortBy: "riskScore", direction: "DESC" };
    const suspicious = params.get("suspicious");
    const fraud = params.get("fraud");
    const type = params.get("type");
    const account = params.get("account");
    const minAmount = params.get("minAmount");
    if (suspicious) q.suspicious = suspicious === "true";
    if (fraud) q.fraud = fraud === "true";
    if (type) q.type = type as TransactionsQuery["type"];
    if (account) q.account = account;
    if (minAmount) q.minAmount = Number(minAmount);
    return q;
  }, [params]);

  const { data, isLoading, isError, error } = useTransactions(query);

  function update(key: string, value: string) {
    const next = new URLSearchParams(params);
    if (value) next.set(key, value);
    else next.delete(key);
    if (key !== "page") next.delete("page");
    setParams(next);
  }

  return (
    <div className="space-y-4">
      <div>
        <h1 className="text-xl font-semibold">Transactions analysées</h1>
        <p className="text-sm text-slate-500">Scores des règles et du modèle MLlib, label du dataset pour contrôle.</p>
      </div>

      <Card>
        <div className="grid grid-cols-2 gap-3 md:grid-cols-5">
          <Field label="Suspectes">
            <select className="input" value={params.get("suspicious") ?? ""} onChange={(e) => update("suspicious", e.target.value)}>
              <option value="">Toutes</option>
              <option value="true">Suspectes</option>
              <option value="false">Non suspectes</option>
            </select>
          </Field>
          <Field label="Label dataset">
            <select className="input" value={params.get("fraud") ?? ""} onChange={(e) => update("fraud", e.target.value)}>
              <option value="">Tous</option>
              <option value="true">Fraude</option>
              <option value="false">Légitime</option>
            </select>
          </Field>
          <Field label="Type">
            <select className="input" value={params.get("type") ?? ""} onChange={(e) => update("type", e.target.value)}>
              <option value="">Tous</option>
              {TRANSACTION_TYPES.map((t) => (
                <option key={t} value={t}>
                  {t}
                </option>
              ))}
            </select>
          </Field>
          <Field label="Compte">
            <input className="input" value={params.get("account") ?? ""} onChange={(e) => update("account", e.target.value)} />
          </Field>
          <Field label="Montant minimum">
            <input className="input" type="number" min={0} value={params.get("minAmount") ?? ""} onChange={(e) => update("minAmount", e.target.value)} />
          </Field>
        </div>
      </Card>

      <Card>
        {isLoading && <Spinner />}
        {isError && <ErrorBox message={describeError(error)} />}
        {data && data.content?.length === 0 && <Empty />}
        {data && data.content && data.content.length > 0 && (
          <>
            <div className="overflow-x-auto">
              <table className="table">
                <thead>
                  <tr>
                    <th>Risque</th>
                    <th className="text-right">Score ML</th>
                    <th>Type</th>
                    <th className="text-right">Montant</th>
                    <th>Émetteur</th>
                    <th>Destinataire</th>
                    <th className="text-right">Solde émetteur après</th>
                    <th>Label</th>
                    <th>Horodatage</th>
                  </tr>
                </thead>
                <tbody>
                  {data.content.map((t) => (
                    <tr key={t.id} className={t.fraud ? "bg-rose-50/40" : undefined}>
                      <td>
                        <RiskScore value={t.riskScore} />
                      </td>
                      <td className="text-right tabular-nums">{fmt.score(t.mlScore)}</td>
                      <td className="font-mono text-xs">{t.type}</td>
                      <td className="text-right tabular-nums">{fmt.money(t.amount)}</td>
                      <td className="font-mono text-xs">{t.nameOrig}</td>
                      <td className="font-mono text-xs">{t.nameDest}</td>
                      <td className="text-right tabular-nums">{fmt.money(t.newBalanceOrig)}</td>
                      <td>{t.fraud ? <span className="font-medium text-rose-700">fraude</span> : <span className="text-slate-500">légitime</span>}</td>
                      <td className="whitespace-nowrap text-xs text-slate-500">{fmt.date(t.eventTime)}</td>
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
