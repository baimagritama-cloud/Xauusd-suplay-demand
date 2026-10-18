package com.xauusd.analyzer;

import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import org.json.JSONObject;
import okhttp3.*;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Read-only Biquote quote client. Uses the documented REST endpoint with allowStale=false.
 * Polling every 5 seconds avoids falsely labelling a disconnected raw SignalR socket as live.
 */
public final class BiquoteStream {
 public interface Listener {
  void onTick(String price,String spread,String time,String state);
  void onStatus(String state);
 }
 private final Listener listener;
 private final OkHttpClient http=new OkHttpClient.Builder()
   .connectTimeout(10,TimeUnit.SECONDS).readTimeout(10,TimeUnit.SECONDS)
   .callTimeout(12,TimeUnit.SECONDS).retryOnConnectionFailure(true).build();
 private final HandlerThread thread=new HandlerThread("biquote-poll");
 private Handler handler;
 private volatile boolean closed=true;
 private Call activeCall;
 private int failures=0;

 public BiquoteStream(Listener l){listener=l;thread.start();handler=new Handler(thread.getLooper());}
 public void connect(){closed=false;handler.removeCallbacksAndMessages(null);poll();}
 private void poll(){
  if(closed)return;
  listener.onStatus("CHECKING FEED");
  Request req=new Request.Builder().url("https://biquote.io/api/XAUUSD?allowStale=false")
    .header("Accept","application/json").build();
  activeCall=http.newCall(req);
  activeCall.enqueue(new okhttp3.Callback(){
   @Override public void onFailure(Call call,IOException e){
    if(closed)return; failures++;
    listener.onStatus(failures>=3?"FEED UNAVAILABLE":"RECONNECTING");
    schedule(failures>=3?15000:5000);
   }
   @Override public void onResponse(Call call,Response response)throws IOException{
    try(Response res=response){
     if(closed)return;
     if(!res.isSuccessful()||res.body()==null){
      failures++; listener.onStatus(response.code()==404?"STALE / NO QUOTE":"HTTP "+response.code());
      schedule(failures>=3?15000:5000); return;
     }
     try{
      JSONObject tick=new JSONObject(res.body().string());
      double mid=tick.optDouble("mid",Double.NaN),bid=tick.optDouble("bid",Double.NaN),ask=tick.optDouble("ask",Double.NaN);
      if(!Double.isFinite(mid)||mid<=0){
       failures++;listener.onStatus("INVALID QUOTE");schedule(5000);return;
      }
      boolean stale=tick.optBoolean("stale",true)||tick.optLong("quoteAgeSeconds",Long.MAX_VALUE)>300;
      String market=tick.optString("marketState","unknown");
      String state=stale?"STALE":("closed".equalsIgnoreCase(market)?"MARKET CLOSED":("open".equalsIgnoreCase(market)?"LIVE":"MARKET "+market.toUpperCase(Locale.US)));
      String spreadValue=tick.has("spread")?String.format(Locale.US,"%.2f",tick.optDouble("spread",Double.NaN)):
        ((!Double.isFinite(bid)||!Double.isFinite(ask))?"--":String.format(Locale.US,"%.2f",ask-bid));
      String stamp=tick.optString("timestamp","");
      String time="--";
      try{
       SimpleDateFormat in=new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss",Locale.US);
       in.setTimeZone(TimeZone.getTimeZone("UTC"));
       Date d=in.parse(stamp.substring(0,19));
       SimpleDateFormat out=new SimpleDateFormat("HH:mm:ss",Locale.US);
       out.setTimeZone(TimeZone.getDefault());time=out.format(d);
      }catch(Exception ignored){ }
      failures=0;
      listener.onTick(String.format(Locale.US,"%.2f",mid),spreadValue,time,state);
      schedule(5000);
     }catch(Exception e){Log.w("BiquoteStream","Invalid quote payload",e);failures++;listener.onStatus("INVALID FEED DATA");schedule(10000);}
    }
   }
  });
 }
 private void schedule(long delay){if(!closed)handler.postDelayed(this::poll,delay);}
 public void close(){closed=true;handler.removeCallbacksAndMessages(null);if(activeCall!=null)activeCall.cancel();http.dispatcher().executorService().shutdown();http.connectionPool().evictAll();thread.quitSafely();}
}
