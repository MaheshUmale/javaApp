import React from 'react';

const ActiveTradesWidget = ({ data }) => {

  const trades = data?.activeTrades || [];

  const getPnlColor = (pnl) => pnl >= 0 ? 'text-green-400' : 'text-red-400';

  return (
    <div className="bg-[#0D1117] p-4 rounded-lg shadow-lg h-full border border-gray-800 flex flex-col">
      <div className="flex justify-between items-center mb-4">
        <h2 className="text-sm font-bold uppercase tracking-widest text-gray-400">Active Trades & Strategy</h2>
        <div className="bg-gray-800 text-[10px] px-2 py-0.5 rounded text-gray-400 font-mono">
          REAL-TIME P&L
        </div>
      </div>

      <div className="flex-grow overflow-auto">
        <table className="w-full text-xs text-left font-mono border-separate border-spacing-y-1">
          <thead className="text-[10px] text-gray-500 uppercase tracking-wider">
            <tr>
              <th className="pb-2 px-1">Symbol</th>
              <th className="pb-2">Entry / LTP</th>
              <th className="pb-2">Qty</th>
              <th className="pb-2">P/L</th>
              <th className="pb-2 text-right px-1">Exit Reason</th>
            </tr>
          </thead>
          <tbody>
            {trades.length === 0 ? (
              <tr>
                <td colSpan="5" className="py-8 text-center text-gray-500 italic text-[10px] bg-gray-900/10 rounded-lg">
                  Waiting for trade opportunity generation...
                </td>
              </tr>
            ) : (
              trades.map((trade) => (
                <tr key={trade.symbol} className="bg-gray-900/30 hover:bg-gray-800/30 transition-colors">
                  <td className="py-2.5 px-2 font-black text-gray-300 rounded-l-lg border-l border-y border-gray-800/50">{trade.symbol}</td>
                  <td className="py-2.5 border-y border-gray-800/50">{trade.entry.toFixed(1)} / {trade.ltp.toFixed(1)}</td>
                  <td className="py-2.5 border-y border-gray-800/50 text-cyan-400 font-bold">{trade.qty}</td>
                  <td className={`py-2.5 border-y border-gray-800/50 font-black ${getPnlColor(trade.pnl)}`}>
                    {trade.pnl > 0 ? '+' : ''}{trade.pnl.toLocaleString()}
                  </td>
                  <td className="py-2.5 px-2 text-right text-yellow-500/80 rounded-r-lg border-r border-y border-gray-800/50 text-[10px] uppercase font-bold italic">
                    {trade.reason}
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      <div className="mt-4 bg-gradient-to-r from-cyan-500/10 to-transparent p-3 rounded-lg border border-cyan-500/20 flex items-center justify-between">
        <div className="flex items-center space-x-3">
          <div className="w-2 h-2 rounded-full bg-cyan-400 animate-pulse shadow-[0_0_8px_rgba(34,211,238,0.5)]"></div>
          <span className="text-gray-500 text-[10px] uppercase font-black tracking-widest">Active Strategy</span>
        </div>
        <span className="font-black text-sm text-cyan-400 tracking-tighter uppercase transition-all duration-500">
          {data?.auctionState || 'ROTATION'} REJECTION
        </span>
      </div>
    </div>
  );
};

export default ActiveTradesWidget;
