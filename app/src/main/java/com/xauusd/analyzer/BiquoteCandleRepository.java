package com.xauusd.analyzer;

import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import okhttp3.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Read-only Biquote OHLC loader. Bars are sorted oldest-first and the open bar is excluded. */
public final class BiquoteCandleRepository {
    public interface Callback {
        void onCandles(String timeframe, List<SwingAnalysisEngine.Candle> candles, boolean fresh, String message);
    }
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS).readTimeout(12, TimeUnit.SECONDS)
            .callTimeout(15, TimeUnit.SECONDS).build();
    private volatile boolean closed;
    private final Set<Call> calls = Collections.synchronizedSet(new HashSet<Call>());

    public void load(String timeframe, Callback callback) {
        if ("H8".equals(timeframe) || "H12".equals(timeframe) || "W1".equals(timeframe)) {
            final String base = "W1".equals(timeframe) ? "D1" : "H4";
            final int factor = "H8".equals(timeframe) ? 2 : ("H12".equals(timeframe) ? 3 : 5);
            load(base, (ignored, source, fresh, message) -> {
                ArrayList<SwingAnalysisEngine.Candle> aggregated = aggregate(source, factor);
                boolean ok = fresh && aggregated.size() >= 12;
                callback.onCandles(timeframe, aggregated, ok,
                        ok ? "OHLC " + timeframe + " diagregasi dari candle tertutup " + base
                           : "Data dasar " + base + " belum cukup/segar untuk " + timeframe);
            });
            return;
        }
        final String interval = intervalFor(timeframe);
        if (interval == null) {
            callback.onCandles(timeframe, Collections.<SwingAnalysisEngine.Candle>emptyList(), false,
                    "Timeframe belum didukung sumber OHLC.");
            return;
        }
        Request request = new Request.Builder()
                .url("https://biquote.io/api/XAUUSD/ohlc?interval=" + interval + "&limit=300")
                .header("Accept", "application/json").build();
        Call call = client.newCall(request);
        calls.add(call);
        call.enqueue(new CallbackAdapter(timeframe, callback, call));
    }

    private final class CallbackAdapter implements okhttp3.Callback {
        private final String tf; private final Callback callback; private final Call call;
        CallbackAdapter(String tf, Callback cb, Call call) { this.tf=tf; callback=cb; this.call=call; }
        @Override public void onFailure(Call c, IOException e) {
            calls.remove(call);
            if (!closed) callback.onCandles(tf, Collections.<SwingAnalysisEngine.Candle>emptyList(), false,
                    "Gagal mengambil candle Biquote: " + e.getMessage());
        }
        @Override public void onResponse(Call c, Response response) throws IOException {
            calls.remove(call);
            try (Response r=response) {
                if (!r.isSuccessful() || r.body()==null) {
                    callback.onCandles(tf, Collections.<SwingAnalysisEngine.Candle>emptyList(), false,
                            "HTTP " + r.code() + " dari Biquote OHLC.");
                    return;
                }
                JSONObject root = new JSONObject(r.body().string());
                JSONArray bars = root.optJSONArray("bars");
                if (bars == null) {
                    callback.onCandles(tf, Collections.<SwingAnalysisEngine.Candle>emptyList(), false,
                            "Respons Biquote tidak berisi bars.");
                    return;
                }
                ArrayList<SwingAnalysisEngine.Candle> result = new ArrayList<>();
                long now = System.currentTimeMillis();
                for (int i=0; i<bars.length(); i++) {
                    JSONObject b=bars.optJSONObject(i); if (b==null || b.optBoolean("isOpen",false)) continue;
                    double o=b.optDouble("open",Double.NaN), h=b.optDouble("high",Double.NaN),
                           l=b.optDouble("low",Double.NaN), cl=b.optDouble("close",Double.NaN);
                    if (!Double.isFinite(o)||!Double.isFinite(h)||!Double.isFinite(l)||!Double.isFinite(cl)
                            || h<Math.max(o,cl)||l>Math.min(o,cl)) continue;
                    long time=0;
                    try {
                        String stamp=b.optString("openTime");
                        java.text.SimpleDateFormat fmt=new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US);
                        fmt.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
                        time=fmt.parse(stamp.substring(0,20)).getTime();
                    } catch (Exception ignored) {
                        try { time=javax.xml.datatype.DatatypeFactory.newInstance().newXMLGregorianCalendar(b.optString("openTime")).toGregorianCalendar().getTimeInMillis(); }
                        catch (Exception ignoredAgain) {}
                    }
                    if(time>0) result.add(new SwingAnalysisEngine.Candle(time,o,h,l,cl));
                }
                Collections.sort(result, new Comparator<SwingAnalysisEngine.Candle>() {
                    @Override public int compare(SwingAnalysisEngine.Candle a, SwingAnalysisEngine.Candle b) {
                        return Long.compare(a.time,b.time);
                    }
                });
                boolean fresh=!result.isEmpty() && now-result.get(result.size()-1).time < freshnessLimit(tf);
                callback.onCandles(tf, result, fresh, fresh ? "OHLC Biquote terbarui" : "OHLC lama; sinyal dinonaktifkan");
            } catch (Exception e) {
                Log.w("BiquoteCandles","Invalid OHLC payload",e);
                callback.onCandles(tf, Collections.<SwingAnalysisEngine.Candle>emptyList(), false,
                        "Format candle Biquote tidak valid.");
            }
        }
    }
    private static ArrayList<SwingAnalysisEngine.Candle> aggregate(List<SwingAnalysisEngine.Candle> source, int factor) {
        ArrayList<SwingAnalysisEngine.Candle> out = new ArrayList<>();
        if (source == null || factor < 2) return out;
        int usable = source.size() - (source.size() % factor);
        for (int i = 0; i < usable; i += factor) {
            SwingAnalysisEngine.Candle first = source.get(i);
            double high = first.high, low = first.low;
            for (int j = i + 1; j < i + factor; j++) {
                SwingAnalysisEngine.Candle item = source.get(j);
                high = Math.max(high, item.high);
                low = Math.min(low, item.low);
            }
            SwingAnalysisEngine.Candle last = source.get(i + factor - 1);
            out.add(new SwingAnalysisEngine.Candle(first.time, first.open, high, low, last.close));
        }
        return out;
    }

    private static long freshnessLimit(String tf) {
        if ("M1".equals(tf)) return 3*60_000L;
        if ("M5".equals(tf)) return 12*60_000L;
        if ("M15".equals(tf)) return 35*60_000L;
        if ("M30".equals(tf)) return 70*60_000L;
        if ("H1".equals(tf)) return 130*60_000L;
        if ("H4".equals(tf)) return 9*60*60_000L;
        if ("H8".equals(tf)) return 18*60*60_000L;
        if ("H12".equals(tf)) return 27*60*60_000L;
        if ("D1".equals(tf)) return 36*60*60_000L;
        return 8*24*60*60_000L;
    }
    private static String intervalFor(String tf) {
        switch(tf) {
            case "M1": return "1m"; case "M5": return "5m"; case "M15": return "15m";
            case "M30": return "30m"; case "H1": return "1h"; case "H4": return "4h"; case "D1": return "1d";
            // API has no native H8/H12/W1; those must be aggregated from closed H4/D1 bars.
            default: return null;
        }
    }
    public void close() {
        closed=true;
        synchronized(calls) { for(Call c:calls) c.cancel(); calls.clear(); }
        client.dispatcher().executorService().shutdown();
        client.connectionPool().evictAll();
    }
}
