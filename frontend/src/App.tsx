import React from 'react';
import { AdminProvider, useAdmin } from './context/AdminContext';
import { Sidebar } from './components/Sidebar';
import { Header } from './components/Header';
import { ToastContainer } from './components/ToastContainer';
import { DashboardView } from './views/DashboardView';
import { TimeManagementView } from './views/TimeManagementView';
import { EventEmulationView } from './views/EventEmulationView';
import { TariffsView } from './views/TariffsView';

const MainContent: React.FC = () => {
  const { currentTab } = useAdmin();

  return (
    <div className="app-layout">
      {/* 16:9 Desktop Left Sidebar */}
      <Sidebar />

      {/* Main Content Area */}
      <main className="app-main">
        <Header />

        {currentTab === 'dashboard' && <DashboardView />}
        {currentTab === 'time' && <TimeManagementView />}
        {currentTab === 'events' && <EventEmulationView />}
        {currentTab === 'tariffs' && <TariffsView />}
      </main>

      {/* Notification Toast Stack */}
      <ToastContainer />
    </div>
  );
};

export const App: React.FC = () => {
  return (
    <AdminProvider>
      <MainContent />
    </AdminProvider>
  );
};

export default App;
