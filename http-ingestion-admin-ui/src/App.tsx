import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import AppShell from './components/AppShell';
import ConnectorListPage from './pages/ConnectorListPage';
import ConnectorWizardPage from './pages/ConnectorWizardPage';
import ConnectorDetailPage from './pages/ConnectorDetailPage';
import StatsPage from './pages/StatsPage';

export default function App() {
  return (
    <BrowserRouter>
      <AppShell>
        <Routes>
          <Route path="/" element={<Navigate to="/connectors" replace />} />
          <Route path="/connectors" element={<ConnectorListPage />} />
          <Route path="/connectors/new" element={<ConnectorWizardPage />} />
          <Route path="/connectors/:id" element={<ConnectorDetailPage />} />
          <Route path="/connectors/:id/edit" element={<ConnectorWizardPage />} />
          <Route path="/stats" element={<StatsPage />} />
        </Routes>
      </AppShell>
    </BrowserRouter>
  );
}
