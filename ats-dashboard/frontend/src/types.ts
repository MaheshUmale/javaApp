
export interface StockData {
  symbol: string;
  price: number;
  change: number;
  changePercent: number;
  qtp: number;
  weightedDelta: number;
}

export interface OptionChainData {
  callOI: string;
  callChgPercent: number;
  strike: string;
  putOI: string;
  putChgPercent: number;
  sentiment: 'BULLISH' | 'BEARISH' | 'UNWINDING' | 'NEUTRAL';
}

export interface TradePosition {
  symbol: string;
  entry: string;
  ltp: string;
  qty: number;
  pnl: number;
  exitReason?: string;
}

export interface Alert {
  id: string;
  type: 'success' | 'warning' | 'error';
  message: string;
}
