import React from 'react';
import { useWebSocketBuffer } from '../hooks/useWebSocketBuffer';
import AuctionWidget from './AuctionWidget';
import HeavyweightWidget from './HeavyweightWidget';
import OptionChain from './OptionChain';
import TradePanel from './TradePanel';
import SentimentWidget from './SentimentWidget';
import ActiveTradesWidget from './ActiveTradesWidget';

const Dashboard = ({ data, stockData, optionData }) => {
  return (
    <div className="grid grid-cols-3 gap-6 flex-grow pb-4">
      <div className="h-[400px]"><AuctionWidget data={stockData} /></div>
      <div className="h-[400px]"><OptionChain data={data} /></div>
      <div className="h-[400px]"><TradePanel data={optionData} /></div>
      <div className="h-[380px]"><HeavyweightWidget data={data} /></div>
      <div className="h-[380px]"><SentimentWidget data={data} /></div>
      <div className="h-[380px]"><ActiveTradesWidget data={data} /></div>
    </div>
  );
};

export default Dashboard;
