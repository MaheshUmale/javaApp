import React, { useMemo, useState, useEffect } from 'react';
import ReactECharts from 'echarts-for-react';

const TradePanel = ({ data }) => {
  const [ohlcHistory, setOhlcHistory] = useState([]);
  const ohlc = data?.ohlc;
  const symbol = data?.instrumentSymbol || '---';

  useEffect(() => {
    if (ohlc && ohlc.close > 0) {
      setOhlcHistory(prev => {
        // Use a unique key for each bar if timestamp is available, 
        // but for demo we just append. To avoid duplicates if same bar update comes:
        const lastBar = prev[prev.length - 1];
        if (lastBar && lastBar[0] === ohlc.open && lastBar[1] === ohlc.close) return prev;

        const newBar = [ohlc.open, ohlc.close, ohlc.low, ohlc.high];
        const next = [...prev, newBar];
        return next.slice(-30); // Keep last 30 bars
      });
    }
  }, [ohlc, symbol]);

  const thetaGcr = data?.theta_gcr || 1.22;
  const thetaColor = thetaGcr > 1 ? 'text-green-400' : 'text-red-400';

  const chartOptions = useMemo(() => {
    return {
      backgroundColor: 'transparent',
      animation: false,
      grid: { top: 10, bottom: 5, left: 45, right: 10, containLabel: false },
      xAxis: { type: 'category', show: false },
      yAxis: {
        type: 'value',
        scale: true,
        splitLine: { lineStyle: { color: '#1F2937' } },
        axisLabel: { color: '#8B949E', fontSize: 10 },
        splitNumber: 4
      },
      series: [
        {
          type: 'candlestick',
          data: ohlcHistory,
          itemStyle: {
            color: '#26A69A',
            color0: '#EF5350',
            borderColor: '#26A69A',
            borderColor0: '#EF5350'
          }
        }
      ]
    };
  }, [ohlcHistory]);

  const handleTrade = async (side) => {
    if (!data?.optionKey || data.optionKey === '---') {
      alert("No active option selected!");
      return;
    }

    const payload = {
      instrumentKey: data.optionKey,
      quantity: 50, // Fixed default quantity for paper trading
      side: side,
      orderType: 'LIMIT',
      price: data.optionLtp
    };

    try {
      const response = await fetch('http://localhost:7070/api/order/place', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
      });
      const result = await response.json();
      if (result.success) {
        console.log(`[PAPER] ${side} order placed for ${symbol}`);
      } else {
        alert("Order failed: " + result.error);
      }
    } catch (err) {
      console.error("Manual trade error:", err);
    }
  };

  return (
    <div className="bg-[#0D1117] p-4 rounded-lg shadow-lg h-full flex flex-col border border-gray-800">
      <div className="flex justify-between items-center mb-1">
        <h2 className="text-sm font-bold uppercase tracking-widest text-gray-400">Trade Panel</h2>
        <div className="flex items-center space-x-2">
          <span className="text-[10px] text-gray-500 font-mono">Spot: {data?.indexSpot?.toFixed(1)} | LTP: {data?.optionLtp?.toFixed(1)}</span>
          <span className="text-[10px] bg-cyan-500/10 text-cyan-400 px-2 py-0.5 rounded border border-cyan-500/20 font-bold">@ {symbol}</span>
        </div>
      </div>

      <div className="flex-grow my-2 min-h-0">
        <ReactECharts
          option={chartOptions}
          style={{ height: '100%', width: '100%' }}
          theme="dark"
        />
      </div>

      <div className="flex flex-col space-y-4 pt-2 border-t border-gray-800 mt-2">
        <div className="flex justify-between items-center">
          <div className="flex items-center space-x-2">
            <span className="text-[10px] text-gray-500 uppercase tracking-wider">Theta-GCR:</span>
            <span className={`font-mono font-bold text-sm ${thetaColor}`}>{thetaGcr.toFixed(2)}</span>
          </div>
          <div className="flex items-center space-x-1">
            <div className="w-1.5 h-1.5 rounded-full bg-green-500"></div>
            <span className="text-[10px] text-green-500 uppercase font-black tracking-widest">Active</span>
          </div>
        </div>

        <div className="grid grid-cols-2 gap-3">
          <button
            onClick={() => handleTrade('BUY')}
            className="bg-green-600 hover:bg-green-500 text-white font-black py-3 rounded-lg text-sm uppercase tracking-widest transition-colors"
          >
            BUY
          </button>
          <button
            onClick={() => handleTrade('SELL')}
            className="bg-red-600 hover:bg-red-500 text-white font-black py-3 rounded-lg text-sm uppercase tracking-widest transition-colors"
          >
            SELL
          </button>
        </div>
      </div>
    </div>
  );
};

export default TradePanel;
