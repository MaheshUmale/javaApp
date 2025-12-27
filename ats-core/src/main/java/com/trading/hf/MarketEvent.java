package com.trading.hf;

import com.lmax.disruptor.EventFactory;

public class MarketEvent {
    private String symbol;
    private double ltp;
    private long ltt;
    private long ltq;
    private double cp;
    private double tbq;
    private double tsq;
    private long vtt;
    private double oi;
    private double iv;
    private double atp;
    private long ts;
    private double theta;
    private double bestBidPrice;
    private double bestAskPrice;
    private double dayOpen;
    private double dayHigh;
    private double dayLow;
    private double dayClose;
    private double optionDelta;

    // Padding to prevent false sharing
    private long p1, p2, p3, p4, p5, p6, p7;

    public double getBestBidPrice() {
        return bestBidPrice;
    }

    public void setBestBidPrice(double bestBidPrice) {
        this.bestBidPrice = bestBidPrice;
    }

    public double getBestAskPrice() {
        return bestAskPrice;
    }

    public void setBestAskPrice(double bestAskPrice) {
        this.bestAskPrice = bestAskPrice;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public double getLtp() {
        return ltp;
    }

    public void setLtp(double ltp) {
        this.ltp = ltp;
    }

    public long getLtt() {
        return ltt;
    }

    public void setLtt(long ltt) {
        this.ltt = ltt;
    }

    public long getLtq() {
        return ltq;
    }

    public void setLtq(long ltq) {
        this.ltq = ltq;
    }

    public double getCp() {
        return cp;
    }

    public void setCp(double cp) {
        this.cp = cp;
    }

    public double getTbq() {
        return tbq;
    }

    public void setTbq(double tbq) {
        this.tbq = tbq;
    }

    public double getTsq() {
        return tsq;
    }

    public void setTsq(double tsq) {
        this.tsq = tsq;
    }

    public long getVtt() {
        return vtt;
    }

    public void setVtt(long vtt) {
        this.vtt = vtt;
    }

    public double getOi() {
        return oi;
    }

    public void setOi(double oi) {
        this.oi = oi;
    }

    public double getIv() {
        return iv;
    }

    public void setIv(double iv) {
        this.iv = iv;
    }

    public double getAtp() {
        return atp;
    }

    public void setAtp(double atp) {
        this.atp = atp;
    }

    public long getTs() {
        return ts;
    }

    public void setTs(long ts) {
        this.ts = ts;
    }

    public double getTheta() {
        return theta;
    }

    public void setTheta(double theta) {
        this.theta = theta;
    }

    public double getDayOpen() {
        return dayOpen;
    }

    public void setDayOpen(double dayOpen) {
        this.dayOpen = dayOpen;
    }

    public double getDayHigh() {
        return dayHigh;
    }

    public void setDayHigh(double dayHigh) {
        this.dayHigh = dayHigh;
    }

    public double getDayLow() {
        return dayLow;
    }

    public void setDayLow(double dayLow) {
        this.dayLow = dayLow;
    }

    public double getDayClose() {
        return dayClose;
    }

    public void setDayClose(double dayClose) {
        this.dayClose = dayClose;
    }

    public double getOptionDelta() {
        return optionDelta;
    }

    public void setOptionDelta(double optionDelta) {
        this.optionDelta = optionDelta;
    }

    public final static EventFactory<MarketEvent> EVENT_FACTORY = MarketEvent::new;
}
