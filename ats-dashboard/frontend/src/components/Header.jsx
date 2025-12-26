import React, { useRef, useEffect, useState } from 'react';
import { Aperture, Clock, Wifi, Database, Activity, LogOut } from 'lucide-react';

const Header = ({ data }) => {
  const prevSpot = useRef(null);
  const [spotColor, setSpotColor] = useState('text-gray-300');

  useEffect(() => {
    if (data && data.spot) {
      if (prevSpot.current !== null) {
        if (data.spot > prevSpot.current) {
          setSpotColor('text-green-400');
        } else if (data.spot < prevSpot.current) {
          setSpotColor('text-red-400');
        }
      }
      prevSpot.current = data.spot;
    }
  }, [data]);

  const spot = data?.indexSpot?.toFixed(2) || '0.00';
  const fut = data?.indexFuture?.toFixed(2) || '0.00';
  const basis = data?.indexBasis?.toFixed(2) || '0.00';

  // Format timestamp
  const formattedTime = data?.timestamp
    ? new Date(data.timestamp * 1000).toLocaleTimeString('en-IN', { hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: true, timeZone: 'Asia/Kolkata' })
    : '00:00:00 AM';

  const getLatencyColor = (latency) => {
    if (latency <= 50) return 'text-green-500';
    if (latency <= 100) return 'text-yellow-500';
    return 'text-red-500';
  };

  const getDisruptorColor = (fillRate) => {
    if (fillRate <= 50) return 'text-green-500';
    if (fillRate <= 80) return 'text-yellow-500';
    return 'text-red-500';
  }

  return (
    <div className="bg-[#0D1117] p-3 rounded-lg flex justify-between items-center text-sm font-sans border border-gray-800 shadow-xl mb-4">
      {/* Left Section */}
      <div className="flex items-center space-x-4">
        <div className="flex items-center space-x-2">
          <div className="bg-cyan-500/10 p-1.5 rounded-lg border border-cyan-500/20">
            <Aperture className="text-cyan-400" size={24} />
          </div>
          <span className="font-bold text-xl text-white tracking-tight">JULES-HF-ATS</span>
        </div>
        <div className="bg-green-500 text-white px-2.5 py-0.5 rounded-full text-[10px] font-black flex items-center shadow-[0_0_10px_rgba(34,197,94,0.3)]">
          <div className="w-1.5 h-1.5 bg-white rounded-full mr-1.5 animate-pulse"></div>
          LIVE
        </div>
        <div className="bg-gray-800 text-gray-300 px-3 py-1 rounded border border-gray-700 text-xs font-bold">
          NIFTY 50
        </div>
      </div>

      {/* Center Section */}
      <div className="flex items-center space-x-8">
        <div className="flex items-center space-x-3">
          <span className={`font-mono text-3xl font-bold transition-colors duration-200 tracking-tighter ${spotColor}`}>{spot}</span>
          <div className="flex flex-col text-[10px] leading-tight">
            <span className="text-gray-500">FUT: <span className="text-gray-300 font-mono font-bold tracking-tight">{fut} ({basis})</span></span>
          </div>
        </div>

        <div className="h-8 w-[1px] bg-gray-800"></div>

        <div className="flex items-center space-x-3 text-gray-400 bg-gray-900/50 px-3 py-1.5 rounded-md border border-gray-800">
          <Clock size={16} className="text-cyan-400" />
          <span className="font-mono text-xs font-bold text-gray-300">{formattedTime}</span>
        </div>
      </div>

      {/* Right Section */}
      <div className="flex items-center space-x-6">
        <div className="flex flex-col items-end space-y-1">
          <div className="flex items-center space-x-2 text-[10px]">
            <Wifi size={12} className={getLatencyColor(data?.wssLatency)} />
            <span className="text-gray-500 uppercase tracking-widest">WSS Latency:</span>
            <span className="font-mono text-gray-300 font-bold">{data?.wssLatency || 0}ms</span>
          </div>
          <div className="flex items-center space-x-2 text-[10px]">
            <Database size={12} className={getLatencyColor(data?.questDbLag)} />
            <span className="text-gray-500 uppercase tracking-widest">QuestDB Lag:</span>
            <span className="font-mono text-gray-300 font-bold">{data?.questDbLag || 0}ms</span>
          </div>
        </div>

        <div className="flex items-center space-x-4 bg-gray-900/50 p-1 rounded-lg border border-gray-800">
          <div className="flex items-center space-x-2 px-2">
            <div className="w-8 h-8 bg-gray-800 rounded-full flex items-center justify-center text-[10px] font-bold text-gray-400 border border-gray-700">
              {data?.username ? data.username.substring(0, 2).toUpperCase() : 'GS'}
            </div>
            <span className="text-xs font-bold text-gray-300 pr-2">{data?.username || 'Guest'}</span>
          </div>
          <button className="bg-gray-800 hover:bg-red-600/20 hover:text-red-500 p-2 rounded-md transition-all border border-gray-700">
            <LogOut size={16} />
          </button>
        </div>
      </div>
    </div>
  );
};

export default Header;
