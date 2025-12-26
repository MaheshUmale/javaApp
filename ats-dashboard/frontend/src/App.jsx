import React, { useState, useEffect } from 'react';
import { useWebSocketBuffer } from './hooks/useWebSocketBuffer';
import Header from './components/Header';
import Dashboard from './components/Dashboard';
import './index.css';

const App = () => {
  const buffer = useWebSocketBuffer('ws://localhost:7070/data');
  const [appData, setAppData] = useState({
    global: {
      indexSpot: 0, indexFuture: 0, indexBasis: 0, timestamp: 0,
      wssLatency: 0, questDbLag: 0, activeTrades: [], option_window: [],
      heavyweights: [], theta_gcr: 1.22
    },
    stockFocus: null,
    optionFocus: null
  });

  useEffect(() => {
    if (!buffer || buffer.length === 0) return;

    setAppData(prev => {
      const lastMsg = buffer[buffer.length - 1];

      // Separate focal fields from shared/global fields
      const {
        stockSymbol, stockLtp, optionSymbol, optionLtp,
        auctionProfile, ohlc, totalVol,
        ...sharedFields
      } = lastMsg;

      const next = {
        // Global contains ONLY shared data (no focal contamination)
        global: { ...prev.global, ...sharedFields },

        // Stock focus for Auction Widget
        stockFocus: {
          instrumentSymbol: stockSymbol,
          instrumentLtp: stockLtp,
          auctionProfile: auctionProfile,
          totalVol: totalVol
        },

        // Option focus for Trade Panel
        optionFocus: {
          instrumentSymbol: optionSymbol,
          instrumentLtp: optionLtp,
          ohlc: ohlc
        }
      };

      return next;
    });
  }, [buffer]);

  // Now stockData and optionData get clean, segregated data
  const stockData = appData.stockFocus ? { ...appData.global, ...appData.stockFocus } : appData.global;
  const optionData = appData.optionFocus ? { ...appData.global, ...appData.optionFocus } : appData.global;

  return (
    <div className="bg-[#0A0E17] min-h-screen p-4 text-white font-sans flex flex-col">
      <Header data={appData.global} />
      <main className="flex-grow mt-4">
        <Dashboard
          data={appData.global}
          stockData={stockData}
          optionData={optionData}
        />
      </main>
    </div>
  );
};

export default App;
