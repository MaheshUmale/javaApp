
import React from 'react';
import Header from './components/Header';
import Dashboard from './components/Dashboard';

const App: React.FC = () => {
  return (
    <div className="min-h-screen flex flex-col bg-[#0b0e14]">
      <Header />
      <main className="flex-1 p-4 overflow-auto">
        <Dashboard />
      </main>
    </div>
  );
};

export default App;
