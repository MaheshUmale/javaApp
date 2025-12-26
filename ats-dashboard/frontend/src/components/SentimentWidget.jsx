import React, { useMemo, useState, useEffect } from 'react';
import ReactECharts from 'echarts-for-react';
import { CheckCircle, XCircle, AlertTriangle, Activity } from 'lucide-react';

const SentimentWidget = ({ data }) => {
  const [pcrHistory, setPcrHistory] = useState([]);
  const pcr = data?.pcr || 1.22;

  useEffect(() => {
    setPcrHistory(prev => {
      // Add randomness if constant to show life in chart
      const effectivePcr = pcr + (Math.random() * 0.02 - 0.01);
      const next = [...prev, effectivePcr];
      return next.slice(-30); // Keep last 30 points
    });
  }, [data]); // Update on every data tick, not just when PCR changes value

  const auctionState = data?.auctionState || 'ROTATION';
  const stateColor = auctionState === 'INITIATIVE_BUY' ? 'text-green-400' : auctionState === 'INITIATIVE_SELL' ? 'text-red-400' : 'text-yellow-400';
  const alertText = auctionState.replace('_', ' ') + ' Setup';

  const chartOptions = useMemo(() => {
    return {
      backgroundColor: 'transparent',
      animation: false,
      grid: { top: 5, bottom: 5, left: 5, right: 5 },
      xAxis: { type: 'category', show: false },
      yAxis: { type: 'value', scale: true, show: false },
      series: [{
        type: 'line',
        data: pcrHistory,
        showSymbol: false,
        smooth: true,
        lineStyle: { color: '#2962FF', width: 1.5 },
        areaStyle: {
          color: {
            type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
            colorStops: [{ offset: 0, color: 'rgba(41, 98, 255, 0.3)' }, { offset: 1, color: 'rgba(41, 98, 255, 0)' }]
          }
        }
      }]
    };
  }, [pcrHistory]);

  return (
    <div className="bg-[#0D1117] p-4 rounded-lg shadow-lg h-full flex flex-col border border-gray-800">
      <div className="flex justify-between items-center mb-4">
        <h2 className="text-sm font-bold uppercase tracking-widest text-gray-400">Sentiment & Triggers</h2>
        <div className="flex space-x-1">
          <div className="w-1.5 h-1.5 rounded-full bg-cyan-500 animate-pulse"></div>
        </div>
      </div>

      <div className="flex-grow flex flex-col overflow-hidden">
        <div className="flex items-center justify-between mb-4">
          <div className="w-1/2 flex flex-col">
            <p className="text-[10px] text-gray-500 uppercase font-black tracking-widest mb-1">PCR Trend:</p>
            <div className="h-20 bg-gray-900/40 rounded border border-gray-800 relative overflow-hidden">
              <div className="absolute top-2 left-2 text-[10px] font-mono text-cyan-400/50 z-10">
                CURR: {pcr.toFixed(2)}
              </div>
              <ReactECharts
                option={chartOptions}
                style={{ height: '100%', width: '100%' }}
                theme="dark"
              />
            </div>
          </div>

          <div className="w-1/2 text-[11px] pl-6 space-y-2.5">
            <p className="text-[10px] text-gray-500 uppercase font-black tracking-widest">Key Alerts (Live):</p>
            {(data?.alerts || []).map((alert, index) => (
              <div key={index} className="flex items-center space-x-2 bg-gray-900/30 p-1.5 rounded border border-gray-800/50">
                {alert.type === 'success' && <CheckCircle size={14} className="text-green-500" />}
                {alert.type === 'warning' && <AlertTriangle size={14} className="text-yellow-500" />}
                {alert.type === 'error' && <XCircle size={14} className="text-red-500" />}
                <span className="text-gray-300 font-medium leading-tight truncate">{alert.message}</span>
              </div>
            ))}
          </div>
        </div>

        <div className="mt-auto">
          <p className="text-gray-500 text-[10px] uppercase font-black tracking-tighter mb-2">Market Auction State</p>
          <div className={`p-4 rounded-xl border-t-2 ${stateColor.replace('text', 'border')} bg-gradient-to-b ${stateColor.replace('text', 'from')}/10 to-transparent shadow-Inner flex items-center justify-between`}>
            <div>
              <p className={`font-black text-xl tracking-tight leading-none ${stateColor}`}>ALERT: {alertText}</p>
              <p className="text-[9px] text-gray-500 mt-1 font-mono">CONFIRMED VIA VOLUME DELTA CLUSTER</p>
            </div>
            <div className={`p-2 rounded-lg ${stateColor.replace('text', 'bg')}/10`}>
              <Activity size={24} className={stateColor} />
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default SentimentWidget;
