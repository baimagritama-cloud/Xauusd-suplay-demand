package com.xauusd.analyzer;

import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import okhttp3.*;
import java.util.concurrent.TimeUnit;

public final class BiquoteStream {
 public interface Listener { void onTick(String price,String spread,String time,String state); void onStatus(String state); }
 private final Listener listener;
 private final OkHttpClient http=new OkHttpClient.Builder().pingInterval(20,TimeUnit.SECONDS).retryOnConnectionFailure(true).build();
 private WebSocket socket; private volatile boolean closed=false;
 private final android.os.HandlerThread thread=new android.os.HandlerThread("biquote-reconnect");
 private android.os.Handler handler; private int attempts=0;
 public BiquoteStream(Listener l){listener=l;thread.start();handler=new android.os.Handler(thread.getLooper());}
 public void connect(){closed=false;connectSocket();}
 private void connectSocket(){
  if(closed)return;
  listener.onStatus("CONNECTING");
  Request req=new Request.Builder().url("wss://biquote.io/hubs/tick").build();
  socket=http.newWebSocket(req,new WebSocketListener(){
   @Override public void onOpen(WebSocket ws,Response response){
    attempts=0;listener.onStatus("CONNECTED — menunggu tick");
    ws.send("{\"protocol\":\"json\",\"version\":1}\u001e");
    ws.send("{\"type\":1,\"invocationId\":\"1\",\"target\":\"Subscribe\",\"arguments\":[[\"XAUUSD\"]]}\u001e");
   }
   @Override public void onMessage(WebSocket ws,String text){
    try{
     for(String frame:text.split("\u001e")){
      if(frame.trim().isEmpty())continue;
      JSONObject obj=new JSONObject(frame);
      if(obj.optInt("type")==6)continue;
      JSONArray args=obj.optJSONArray("arguments");
      if(args==null||args.length()==0)continue;
      JSONObject tick=args.optJSONObject(0); if(tick==null)continue;
      double mid=tick.optDouble("mid",Double.NaN),bid=tick.optDouble("bid",Double.NaN),ask=tick.optDouble("ask",Double.NaN);
      if(Double.isNaN(mid)&&!Double.isNaN(bid)&&!Double.isNaN(ask))mid=(bid+ask)/2.0;
      if(Double.isNaN(mid))continue;
      double spr=tick.optDouble("spread",(!Double.isNaN(ask)&&!Double.isNaN(bid))?ask-bid:Double.NaN);
      String state=tick.optString("marketState","LIVE");
      if(tick.optBoolean("stale",false) || tick.optLong("quoteAgeSeconds",0)>300)state="STALE";
      if("open".equalsIgnoreCase(state)) state="LIVE";
      if("closed".equalsIgnoreCase(state)) state="MARKET CLOSED";
      String timestamp=tick.optString("timestamp","");
      if(timestamp.length()>=19)timestamp=timestamp.substring(11,19);
      else if(timestamp.isEmpty())timestamp=java.text.DateFormat.getTimeInstance().format(new java.util.Date());
      listener.onTick(String.format(java.util.Locale.US,"%.2f",mid),Double.isNaN(spr)?"--":String.format(java.util.Locale.US,"%.2f",spr),timestamp,state);
     }
    }catch(Exception e){Log.w("BiquoteStream","Tick parse failed",e);}
   }
   @Override public void onFailure(WebSocket ws,Throwable t,Response response){listener.onStatus("RECONNECTING");scheduleReconnect();}
   @Override public void onClosed(WebSocket ws,int code,String reason){if(!closed)scheduleReconnect();}
  });
 }
 private void scheduleReconnect(){if(closed)return;long delay=Math.min(30000L,1000L*(1L<<Math.min(attempts++,5)));handler.postDelayed(()->connectSocket(),delay);}
 public void close(){closed=true;if(socket!=null)socket.close(1000,"Activity closed");thread.quitSafely();http.dispatcher().executorService().shutdown();}
}
