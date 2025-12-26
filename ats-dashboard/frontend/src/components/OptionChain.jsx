import React from 'react';

const OptionChain = ({ data }) => {
  const isCallFocus = data?.auctionState === 'INITIATIVE_BUY';
  const isPutFocus = data?.auctionState === 'INITIATIVE_SELL';

  // Group options by strike price
  const strikes = (data?.option_window || []).reduce((acc, option) => {
    acc[option.strike] = acc[option.strike] || {};
    acc[option.strike][option.type] = option;
    return acc;
  }, {});

  const sortedStrikes = Object.keys(strikes).sort((a, b) => a - b);

  const getRowStyle = (strike) => {
    const isATM = Math.abs(strike - data?.spot) < 50; // Simple ATM logic
    let style = 'border-b border-gray-800';
    if (isATM) {
      if (isCallFocus) style += ' signal-buy';
      if (isPutFocus) style += ' signal-sell';
    }
    return style;
  };


  return (
    <div className="bg-[#0D1117] p-4 rounded-lg shadow-lg h-full border border-gray-800 flex flex-col">
      <div className="flex justify-between items-center mb-4">
        <h2 className="text-sm font-bold uppercase tracking-widest text-gray-400">Dynamic Option Chain (ATM &gt; 2)</h2>
        <div className="bg-gray-800 text-[10px] px-2 py-0.5 rounded text-gray-400 font-mono">
          NIFTY EXP: 28-DEC
        </div>
      </div>

      <div className="flex-grow overflow-auto">
        {sortedStrikes.length === 0 ? (
          <div className="text-center text-gray-500 py-8 italic text-xs">Waiting for market data...</div>
        ) : (
          <table className="w-full text-xs text-center font-mono border-separate border-spacing-y-1">
            <thead className="text-[10px] text-gray-500 uppercase tracking-wider">
              <tr>
                <th className="pb-2 text-left px-2">Call Chg (%)</th>
                <th className="pb-2">Call OI</th>
                <th className="pb-2 bg-gray-900/50 rounded-t-lg">Strike</th>
                <th className="pb-2">Put OI</th>
                <th className="pb-2 text-right px-2">Put Chg (%)</th>
              </tr>
            </thead>
            <tbody>
              {sortedStrikes.map((strike) => {
                const call = strikes[strike].CE;
                const put = strikes[strike].PE;
                const isATM = Math.abs(strike - data?.spot) < 30;

                return (
                  <tr key={strike} className={`group transition-colors ${isATM ? 'bg-cyan-500/5' : 'hover:bg-gray-800/30'}`}>
                    {/* CALLS */}
                    <td className={`py-2 text-left px-2 font-bold ${call?.oi_chg > 0 ? 'text-green-400' : 'text-red-400'}`}>
                      {call?.oi_chg ? `${call.oi_chg > 0 ? '+' : ''}${call.oi_chg.toFixed(1)}%` : '-'}
                    </td>
                    <td className="py-2 text-gray-400">
                      {call?.oi ? `${(call.oi / 1000).toFixed(1)}k` : '-'}
                    </td>

                    {/* STRIKE */}
                    <td className={`py-2 bg-gray-900/80 font-bold text-sm border-x border-gray-800 ${isATM ? 'text-cyan-400' : 'text-gray-300'}`}>
                      {strike}
                    </td>

                    {/* PUTS */}
                    <td className="py-2 text-gray-400">
                      {put?.oi ? `${(put.oi / 1000).toFixed(1)}k` : '-'}
                    </td>
                    <td className={`py-2 text-right px-2 font-bold ${put?.oi_chg > 0 ? 'text-green-400' : 'text-red-400'}`}>
                      <div className="flex items-center justify-end space-x-2">
                        <span>{put?.oi_chg ? `${put.oi_chg > 0 ? '+' : ''}${put.oi_chg.toFixed(1)}%` : '-'}</span>
                        {put?.sentiment && (
                          <span className={`text-[8px] px-1 rounded ${put.sentiment.includes('BULLISH') ? 'bg-green-500/20 text-green-500' : 'bg-red-500/20 text-red-500'}`}>
                            {put.sentiment}
                          </span>
                        )}
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        )}
      </div>

      <div className="flex justify-between items-center mt-4 pt-3 border-t border-gray-800 text-[10px] font-mono">
        <div className="text-gray-500">
          PCR: <span className="text-white font-bold">{data?.pcr?.toFixed(2) || '0.00'}</span>
        </div>
        <div className="flex items-center space-x-2">
          <div className={`w-1.5 h-1.5 rounded-full ${data?.pcr > 1 ? 'bg-green-500' : 'bg-red-500'}`}></div>
          <span className="text-gray-400 uppercase">{data?.pcr > 1 ? 'Bullish Sentiment' : 'Bearish Sentiment'}</span>
        </div>
      </div>
    </div>
  );
};

export default OptionChain;
