import React, { useMemo, useState, useEffect } from 'react';
import ReactECharts from 'echarts-for-react';

const AuctionWidget = ({ data }) => {
  const [history, setHistory] = useState([]);
  const price = data?.instrumentLtp || 0;
  const symbol = data?.instrumentSymbol || '---';

  useEffect(() => {
    if (price > 0) {
      setHistory(prev => {
        const newHistory = [...prev, price];
        return newHistory.slice(-50); // Keep last 50 points
      });
    }
  }, [price, symbol]);

  const vah = data?.auctionProfile?.vah || 0;
  const val = data?.auctionProfile?.val || 0;
  const poc = data?.auctionProfile?.poc || 0;
  const totalVol = data?.totalVol || '0.0M';

  const chartOptions = useMemo(() => {
    // Determine dynamic markLines (Only show if > 0 to avoid force-scaling to 0 for Nifty)
    const lines = [];
    if (vah > 0) lines.push({ yAxis: vah, name: 'VAH', lineStyle: { color: '#F87171', type: 'dashed' } });
    if (poc > 0) lines.push({ yAxis: poc, name: 'POC', lineStyle: { color: '#FACC15', width: 2 } });
    if (val > 0) lines.push({ yAxis: val, name: 'VAL', lineStyle: { color: '#4ADE80', type: 'dashed' } });

    return {
      backgroundColor: 'transparent',
      animation: false,
      grid: { top: 15, bottom: 5, left: 45, right: 10, containLabel: false },
      xAxis: { type: 'category', show: false },
      yAxis: {
        type: 'value',
        scale: true,
        splitLine: { lineStyle: { color: '#1F2937' } },
        axisLabel: {
          color: '#8B949E',
          fontSize: 10,
          formatter: (value) => value.toFixed(0)
        },
        splitNumber: 4,
        min: (value) => (value.min * 0.999).toFixed(0), // Push min slightly lower than data
        max: (value) => (value.max * 1.001).toFixed(0)  // Push max slightly higher than data
      },
      series: [
        {
          name: 'Price',
          type: 'line',
          data: history,
          showSymbol: false,
          smooth: true,
          lineStyle: { color: '#2962FF', width: 2 },
          areaStyle: {
            color: {
              type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
              colorStops: [{ offset: 0, color: 'rgba(41, 98, 255, 0.2)' }, { offset: 1, color: 'rgba(41, 98, 255, 0)' }]
            }
          },
          markLine: {
            symbol: 'none',
            label: { position: 'end', fontSize: 8, color: '#9CA3AF', formatter: '{b}' },
            data: lines
          }
        }
      ]
    };
  }, [history, vah, val, poc]);

  return (
    <div className="bg-[#0D1117] p-4 rounded-lg shadow-lg h-full flex flex-col border border-gray-800">
      <div className="flex justify-between items-center mb-2">
        <h2 className="text-sm font-bold uppercase tracking-widest text-gray-400">Auction Profile</h2>
        <div className="flex items-center space-x-2">
          <span className="text-[10px] bg-cyan-500/10 text-cyan-400 px-2 py-0.5 rounded border border-cyan-500/20 font-bold tracking-tighter">
            {symbol}
          </span>
          <div className="w-1.5 h-1.5 rounded-full bg-green-500 animate-pulse"></div>
        </div>
      </div>

      <div className="flex-grow min-h-0">
        <ReactECharts
          option={chartOptions}
          style={{ height: '100%', width: '100%' }}
          theme="dark"
        />
      </div>

      <div className="flex justify-between items-center text-[10px] pt-3 border-t border-gray-800 mt-2 font-mono">
        <div className="bg-blue-500/10 text-blue-400 px-2 py-1 rounded border border-blue-500/20">
          VOL: <span className="font-bold">{totalVol}</span>
        </div>
        <div className="flex space-x-3">
          <div className="flex flex-col items-end">
            <span className="text-gray-500 uppercase text-[8px]">VAH</span>
            <span className="text-red-400 font-bold">{vah.toFixed(1)}</span>
          </div>
          <div className="flex flex-col items-end">
            <span className="text-gray-500 uppercase text-[8px]">POC</span>
            <span className="text-yellow-400 font-bold">{poc.toFixed(1)}</span>
          </div>
          <div className="flex flex-col items-end">
            <span className="text-gray-500 uppercase text-[8px]">VAL</span>
            <span className="text-green-400 font-bold">{val.toFixed(1)}</span>
          </div>
        </div>
      </div>
    </div>
  );
};

export default AuctionWidget;
