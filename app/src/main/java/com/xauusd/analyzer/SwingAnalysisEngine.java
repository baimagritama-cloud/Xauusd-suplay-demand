package com.xauusd.analyzer;

import java.util.*;

/**
 * Deterministic price-action engine. Input must be real, time-ordered OHLC candles.
 * It deliberately returns WAIT when there is not enough confirmed data.
 */
public final class SwingAnalysisEngine {
    public static final class Candle {
        public final long time;
        public final double open, high, low, close;
        public Candle(long time, double open, double high, double low, double close) {
            this.time=time; this.open=open; this.high=high; this.low=low; this.close=close;
        }
    }
    public static final class Swing {
        public final int index;
        public final double price;
        public final boolean high;
        Swing(int index, double price, boolean high) { this.index=index; this.price=price; this.high=high; }
    }
    public static final class Zone {
        public final double low, high;
        public final boolean supply;
        public final int sourceIndex;
        Zone(double low, double high, boolean supply, int sourceIndex) {
            this.low=Math.min(low,high); this.high=Math.max(low,high); this.supply=supply; this.sourceIndex=sourceIndex;
        }
        public boolean contains(double price) { return price>=low && price<=high; }
    }
    public static final class Result {
        public final List<Swing> swings;
        public final List<Zone> zones;
        public final String structure, signal, reason;
        public final double lastConfirmedHigh, lastConfirmedLow;
        Result(List<Swing> swings, List<Zone> zones, String structure, String signal, String reason,
               double lastConfirmedHigh, double lastConfirmedLow) {
            this.swings=Collections.unmodifiableList(swings);
            this.zones=Collections.unmodifiableList(zones);
            this.structure=structure; this.signal=signal; this.reason=reason;
            this.lastConfirmedHigh=lastConfirmedHigh; this.lastConfirmedLow=lastConfirmedLow;
        }
    }

    private SwingAnalysisEngine() {}

    public static Result analyze(List<Candle> input, double currentPrice, boolean dataFresh) {
        if (input == null || input.size()<12) {
            return new Result(new ArrayList<>(), new ArrayList<>(), "DATA BELUM CUKUP", "WAIT",
                    "Menunggu minimal 12 candle OHLC valid.",
                    Double.NaN, Double.NaN);
        }
        ArrayList<Swing> swings=new ArrayList<>();
        // A swing is confirmed only after two candles on each side have closed.
        for (int i=2;i<input.size()-2;i++) {
            Candle c=input.get(i); boolean isHigh=true,isLow=true;
            for(int j=i-2;j<=i+2;j++) if(j!=i) {
                Candle n=input.get(j);
                if(n.high>=c.high) isHigh=false;
                if(n.low<=c.low) isLow=false;
            }
            if(isHigh) swings.add(new Swing(i,c.high,true));
            if(isLow) swings.add(new Swing(i,c.low,false));
        }
        ArrayList<Zone> zones=new ArrayList<>();
        int from=Math.max(0,input.size()-80);
        for(int i=from;i<input.size()-1;i++) {
            Candle c=input.get(i), next=input.get(i+1);
            double range=Math.max(c.high-c.low,0.00001);
            double body=Math.abs(c.close-c.open);
            // Candidate supply/demand base is accepted only when followed by displacement.
            if(c.close>c.open && next.close<c.low && Math.abs(next.close-next.open)>range*0.35)
                zones.add(new Zone(c.low,c.high,true,i));
            if(c.close<c.open && next.close>c.high && Math.abs(next.close-next.open)>range*0.35)
                zones.add(new Zone(c.low,c.high,false,i));
        }
        double lastHigh=Double.NaN,lastLow=Double.NaN;
        ArrayList<Swing> highs=new ArrayList<>(), lows=new ArrayList<>();
        for(Swing s:swings) if(s.high) highs.add(s); else lows.add(s);
        if(!highs.isEmpty()) lastHigh=highs.get(highs.size()-1).price;
        if(!lows.isEmpty()) lastLow=lows.get(lows.size()-1).price;
        String structure="RANGE";
        if(highs.size()>=2 && lows.size()>=2) {
            double ph=highs.get(highs.size()-2).price, pl=lows.get(lows.size()-2).price;
            if(lastHigh>ph && lastLow>pl) structure="HH / HL";
            else if(lastHigh<ph && lastLow<pl) structure="LH / LL";
        }
        String signal="WAIT", reason="Belum ada breakout terkonfirmasi; tunggu candle close dan retest.";
        // Historical OHLC can still describe structure while the market is closed, but never
        // promote stale candles or a missing quote into a live actionable signal.
        if (!dataFresh || !Double.isFinite(currentPrice)) {
            reason = !dataFresh
                    ? "Struktur historis dihitung dari candle tersedia; data tidak segar, sinyal live dinonaktifkan."
                    : "Struktur historis tersedia; harga live belum tersedia, sinyal dinonaktifkan.";
        } else if(Double.isFinite(lastHigh) && currentPrice>lastHigh) {
            signal="WAIT"; reason="Harga melewati swing high, tetapi perlu candle close dan retest untuk konfirmasi.";
        } else if(Double.isFinite(lastLow) && currentPrice<lastLow) {
            signal="WAIT"; reason="Harga melewati swing low, tetapi perlu candle close dan retest untuk konfirmasi.";
        } else {
            for(Zone z:zones) if(z.contains(currentPrice)) {
                reason=(z.supply?"Harga berada dalam kandidat Supply":"Harga berada dalam kandidat Demand")
                        +"; tunggu reaksi candle dan konfirmasi struktur.";
                break;
            }
        }
        return new Result(swings,zones,structure,signal,reason,lastHigh,lastLow);
    }
}
