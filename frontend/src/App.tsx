import { Navigate, Route, Routes } from "react-router-dom";
import { RequireAuth } from "@/auth/AuthContext";
import { Layout } from "@/components/Layout";
import { AlertDetailPage } from "@/pages/AlertDetailPage";
import { AlertsPage } from "@/pages/AlertsPage";
import { LoginPage } from "@/pages/LoginPage";
import { QualityPage } from "@/pages/QualityPage";
import { TransactionsPage } from "@/pages/TransactionsPage";

export function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route
        element={
          <RequireAuth>
            <Layout />
          </RequireAuth>
        }
      >
        <Route path="/alerts" element={<AlertsPage />} />
        <Route path="/alerts/:alertId" element={<AlertDetailPage />} />
        <Route path="/transactions" element={<TransactionsPage />} />
        <Route path="/quality" element={<QualityPage />} />
        <Route path="*" element={<Navigate to="/alerts" replace />} />
      </Route>
    </Routes>
  );
}
