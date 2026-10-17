import { useState, type FormEvent } from "react";
import { Navigate, useLocation, useNavigate } from "react-router-dom";
import { describeError } from "@/api/client";
import { useAuth } from "@/auth/AuthContext";
import { ErrorBox, Field } from "@/components/ui";

export function LoginPage() {
  const { user, login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [username, setUsername] = useState("analyst");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  if (user) return <Navigate to="/alerts" replace />;

  const from = (location.state as { from?: { pathname: string } } | null)?.from?.pathname ?? "/alerts";

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await login(username, password);
      navigate(from, { replace: true });
    } catch (err) {
      setError(describeError(err, "Identifiants invalides"));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center px-4">
      <form onSubmit={submit} className="card w-full max-w-sm space-y-4 p-6">
        <div>
          <h1 className="text-xl font-semibold text-brand-700">Fraud Console</h1>
          <p className="mt-1 text-sm text-slate-500">Plateforme de détection de fraude en temps réel</p>
        </div>
        <Field label="Identifiant">
          <input className="input" value={username} onChange={(e) => setUsername(e.target.value)} autoFocus autoComplete="username" />
        </Field>
        <Field label="Mot de passe">
          <input className="input" type="password" value={password} onChange={(e) => setPassword(e.target.value)} autoComplete="current-password" />
        </Field>
        {error && <ErrorBox message={error} />}
        <button className="btn-primary w-full" type="submit" disabled={busy || !username || !password}>
          {busy ? "Connexion…" : "Se connecter"}
        </button>
        <p className="text-xs text-slate-500">
          Comptes de démo : <code>analyst / analyst123</code> (analyste), <code>supervisor / supervisor123</code> (superviseur,
          peut bloquer).
        </p>
      </form>
    </div>
  );
}
