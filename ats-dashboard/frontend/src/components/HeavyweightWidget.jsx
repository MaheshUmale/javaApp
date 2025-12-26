import React, { useState, useEffect } from 'react';

const HeavyweightWidget = ({ data }) => {
  const [pulse, setPulse] = useState('');

  // The pulse animation indicates new data has been received.
  useEffect(() => {
    if (data) {
      setPulse('bg-gray-800'); // A subtle flash effect
      const timer = setTimeout(() => setPulse(''), 200);
      return () => clearTimeout(timer);
    }
  }, [data?.aggregateWeightedDelta]);

  const heavyweights = data?.heavyweights || [];

  const totalDelta = data?.aggregateWeightedDelta || 0;
  const indexChange = data?.indexChange || 0;

  // Divergence Detection
  // Bearish Divergence: Index UP (Price > Open or +ve Change) but Delta DOWN (-ve)
  const isBearishDivergence = indexChange > 0 && totalDelta < 0;
  // Bullish Divergence: Index DOWN (-ve Change) but Delta UP (+ve)
  const isBullishDivergence = indexChange < 0 && totalDelta > 0;

  const totalDeltaColor = totalDelta >= 0 ? 'text-green-400' : 'text-red-400';
  const totalDeltaSign = totalDelta >= 0 ? '+' : '';

  let borderClass = "border-gray-800";
  let alertMessage = null;

  if (isBearishDivergence) {
    borderClass = "border-red-500 animate-pulse ring-1 ring-red-500";
    alertMessage = <div className="mb-2 bg-red-900/50 text-red-200 text-[10px] font-bold px-2 py-1 rounded text-center animate-pulse">⚠️ BEARISH DIVERGENCE</div>;
  } else if (isBullishDivergence) {
    borderClass = "border-green-500 animate-pulse ring-1 ring-green-500";
    alertMessage = <div className="mb-2 bg-green-900/50 text-green-200 text-[10px] font-bold px-2 py-1 rounded text-center animate-pulse">🚀 BULLISH DIVERGENCE</div>;
  }

  return (
    <div className={`bg-[#0D1117] p-4 rounded-lg shadow-lg transition-colors duration-200 ${pulse} ${borderClass} flex flex-col`}>
      {alertMessage}
      <div className="flex justify-between items-center mb-4">
        <h2 className="text-sm font-bold uppercase tracking-widest text-gray-400">Heavyweights (Nifty 50)</h2>
        <div className="bg-gray-800 text-[10px] px-2 py-0.5 rounded text-gray-400 font-mono">
          TOP 10
        </div>
      </div>

      <div className="flex-grow overflow-auto">
        <table className="w-full text-xs text-left">
          <thead>
            <tr className="text-[10px] text-gray-500 uppercase tracking-wider border-b border-gray-800">
              <th className="pb-2">Stock</th>
              <th className="pb-2 text-right">Price / %</th>
              <th className="pb-2 text-right px-2">LTQ</th>
              <th className="pb-2 text-right">W. Delta</th>
            </tr>
          </thead>
          <tbody>
            {heavyweights.map((stock) => (
              <tr key={stock.name} className="border-b border-gray-900/50 hover:bg-gray-800/20 transition-colors font-mono">
                <td className="py-2.5">
                  <div className="flex items-center">
                    <span className="text-[9px] text-gray-600 w-4 font-bold">{stock.rank}</span>
                    <span className="font-sans font-black text-gray-300">{stock.name}</span>
                  </div>
                </td>
                <td className="py-2.5 text-right text-gray-400">{stock.priceChange}</td>
                <td className={`py-2.5 text-right px-2 ${stock.qtp >= 5000 ? 'text-cyan-400' : 'text-gray-500'}`}>
                  {stock.qtp?.toLocaleString()}
                </td>
                <td className={`py-2.5 text-right font-black ${stock.delta >= 0 ? 'text-green-400' : 'text-red-400'}`}>
                  {stock.delta > 0 ? '+' : ''}{stock.delta?.toLocaleString()}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div className="flex justify-between items-center mt-4 bg-gray-900/80 p-3 rounded-lg border border-gray-800">
        <div>
          <p className="text-gray-500 text-[9px] uppercase font-bold tracking-widest">Agg. W. Delta</p>
          <div className="flex items-baseline space-x-2">
            <p className={`font-mono font-black text-lg ${totalDeltaColor}`}>
              {totalDeltaSign}{totalDelta?.toLocaleString()}
            </p>
            <p className="text-[10px] text-gray-600 font-bold">(70% WEIGHT)</p>
          </div>
        </div>
        <div className={`w-12 h-1 rounded-full overflow-hidden bg-gray-800`}>
          <div
            className={`h-full ${totalDelta >= 0 ? 'bg-green-500' : 'bg-red-500'}`}
            style={{ width: `${Math.min(100, (Math.abs(totalDelta) / 50000) * 100)}%` }}
          ></div>
        </div>
      </div>
    </div>
  );
};

export default HeavyweightWidget;
