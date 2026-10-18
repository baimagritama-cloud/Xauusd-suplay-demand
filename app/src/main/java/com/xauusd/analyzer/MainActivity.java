package com.xauusd.analyzer;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.graphics.*;
import android.view.*;
import java.util.*;
import java.text.SimpleDateFormat;

public class MainActivity extends Activity {
    public static volatile String livePrice="Menghubungkan...", liveSpread="--", liveTime="Menunggu tick", liveState="CONNECTING";
    public static volatile String engineStatus="Menunggu data OHLC Biquote";
    private BiquoteStream stream;
    private BiquoteCandleRepository candles;
    private Dashboard dashboard;
    private final Handler handler=new Handler();
    private final Map<String,List<SwingAnalysisEngine.Candle>> data=new HashMap<>();
    private final Map<String,SwingAnalysisEngine.Result> analyses=new HashMap<>();
    private final Map<String,Boolean> freshness=new HashMap<>();
    private final String[] frames={"M1","M5","M15","M30","H1","H4","H8","H12","D1","W1"};
    private final int[] colors={0xffb7d8f0,0xffff62c8,0xffc94dff,0xff1687ff,0xff08d5e7,0xff00d982,0xffffd21c,0xffff891a,0xffff2451,0xffffb81d};
    private int selected=4;
    private boolean page2=false;
    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        getWindow().getDecorView().setSystemUiVisibility(5894|1024|512);
        dashboard=new Dashboard(); setContentView(dashboard);
        candles=new BiquoteCandleRepository();
        stream=new BiquoteStream(new BiquoteStream.Listener(){
            public void onTick(String price,String spread,String time,String state){livePrice=price;liveSpread=spread;liveTime=time;liveState=state;runOnUiThread(()->dashboard.invalidate());}
            public void onStatus(String state){liveState=state;runOnUiThread(()->dashboard.invalidate());}
        });
        stream.connect(); refreshCandles();
    }
    private double price(){try{return Double.parseDouble(livePrice);}catch(Exception e){return Double.NaN;}}
    private void refreshCandles(){
        if(candles==null)return;
        for(String tf:frames) candles.load(tf,(frame,bars,fresh,message)->{
            synchronized(data){
                data.put(frame,bars); freshness.put(frame,fresh);
                SwingAnalysisEngine.Result r=SwingAnalysisEngine.analyze(bars,price(),fresh);
                analyses.put(frame,r);
                engineStatus=message;
            }
            runOnUiThread(()->{if(dashboard!=null)dashboard.invalidate();});
        });
        handler.postDelayed(()->{if(!isFinishing())refreshCandles();},60000);
    }
    @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);if(stream!=null)stream.close();if(candles!=null)candles.close();super.onDestroy();}

    private class Dashboard extends View {
        Canvas c; Paint p=new Paint(3); float sx,sy,W,H;
        final int bg=0xff030c19,panel=0xff051526,panel2=0xff081c30,grid=0xff18384e,white=0xffe4eff9,muted=0xff8ea6c0,cyan=0xff22d3e6,red=0xffff4962,blue=0xff2b89ff,green=0xff14e097,yellow=0xffffd31f;
        Dashboard(){super(MainActivity.this);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
        void fill(int color){p.setColor(color);p.setStyle(Paint.Style.FILL);p.setStrokeWidth(1);}
        void rect(float x,float y,float w,float h,int color,float radius){fill(color);c.drawRoundRect(x,y,x+w,y+h,radius,radius,p);p.setStyle(Paint.Style.STROKE);p.setColor(0xff25415c);p.setStrokeWidth(1);c.drawRoundRect(x,y,x+w,y+h,radius,radius,p);p.setStyle(Paint.Style.FILL);}
        void line(float x,float y,float x2,float y2,int color,float width){p.setColor(color);p.setStrokeWidth(width);p.setStyle(Paint.Style.STROKE);c.drawLine(x,y,x2,y2,p);p.setStyle(Paint.Style.FILL);}
        void text(String s,float x,float y,float size,int color,boolean bold){fill(color);p.setTextSize(size);p.setTypeface(bold?Typeface.DEFAULT_BOLD:Typeface.DEFAULT);c.drawText(s,x,y,p);}
        void center(String s,float x,float y,float size,int color,boolean bold){fill(color);p.setTextSize(size);p.setTypeface(bold?Typeface.DEFAULT_BOLD:Typeface.DEFAULT);c.drawText(s,x-p.measureText(s)/2,y,p);}
        @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);c=canvas;W=getWidth();H=getHeight();sx=W/1536f;sy=H/768f;c.save();c.scale(sx,sy);fill(bg);c.drawRect(0,0,1536,768,p);header();for(int i=0;i<10;i++){float x=8+i*77.2f;rect(x,82,72,36,i==selected?colors[i]:panel2,7);center(frames[i],x+36,105,14,i==selected?bg:colors[i],true);}if(page2)drawDetail();else drawChart();rect(8,742,1520,20,0xff241722,4);text("DATA BERSUMBER DARI BIQUOTE • Analisis hanya memakai candle tertutup dan data segar. Sinyal WAIT jika belum terkonfirmasi.",17,756,11,0xffffb3be,true);c.restore();}
        void header(){rect(8,7,780,68,panel,9);text("◆",20,51,42,0xffffc629,true);text("XAUUSD Analyzer",82,34,26,white,true);text("MULTI-TIMEFRAME • HH/HL/LH/LL • SUPPLY & DEMAND",83,57,12,muted,false);rect(800,7,250,68,panel,9);text("HARGA SEKARANG",814,26,11,muted,true);text(livePrice,814,53,25,green,true);rect(1060,7,125,68,panel,9);text("SPREAD",1073,26,11,muted,true);text(liveSpread,1073,51,22,white,true);rect(1195,7,333,68,panel,9);text("FEED: "+liveState,1208,29,14,liveState.equals("LIVE")?green:yellow,true);text("Waktu quote: "+liveTime,1208,51,13,white,false);}
        void drawChart(){
            rect(8,127,980,604,panel,9);rect(998,127,530,604,panel,9);
            List<SwingAnalysisEngine.Candle> bars; SwingAnalysisEngine.Result result;
            synchronized(data){bars=data.get(selectedTf());result=analyses.get(selectedTf());}
            text("CHART CANDLE TERTUTUP • "+frames[selected]+" • 300 BAR MAKSIMUM",21,150,14,white,true);
            if(bars==null||bars.size()<2){center("Menunggu data OHLC Biquote yang valid...",490,414,19,yellow,true);text(engineStatus,22,179,13,muted,false);return;}
            drawCandles(bars,24,166,934,525);
            text("Bar: "+bars.size()+"   Status: "+(Boolean.TRUE.equals(freshness.get(selectedTf()))?"FRESH":"STALE / WAIT"),22,718,12,Boolean.TRUE.equals(freshness.get(selectedTf()))?green:red,true);
            drawLiveNarrative(result);
            rect(1439,690,72,31,panel2,7);center("DETAIL →",1475,710,11,cyan,true);
        }
        void drawLiveNarrative(SwingAnalysisEngine.Result selectedResult){
            text("PERJALANAN HARGA LIVE",1014,153,16,cyan,true);
            text("Timeframe chart: "+selectedTf()+"  |  Struktur: "+(selectedResult==null?"MENUNGGU":selectedResult.structure),1014,179,11,white,true);
            text("Harga: "+livePrice+"  |  Feed: "+liveState+"  |  Sinyal: "+(selectedResult==null?"WAIT":selectedResult.signal),1014,201,10.5f,"LIVE".equals(liveState)?green:yellow,true);
            line(1014,214,1510,214,grid,1);
            double px=price();
            boolean fresh=Boolean.TRUE.equals(freshness.get(selectedTf()))&&"LIVE".equals(liveState);
            if(!Double.isFinite(px)||!fresh){
                drawWrapped("Data harga belum terverifikasi live. Zona dan struktur yang terlihat hanya konteks historis; kesimpulan arah, retest, dan skenario perjalanan harga ditahan sampai quote serta candle kembali segar.",1014,239,74,11,white,17,7);
                text("STATUS: WAIT • probabilitas tidak dihitung",1014,407,11,red,true);
                text("Yang perlu dikonfirmasi",1014,442,12,yellow,true);
                drawWrapped("1. Quote Biquote berubah dan berstatus LIVE. 2. Candle timeframe terpilih selesai ditutup. 3. Harga benar-benar menyentuh atau menembus zona yang dihitung dari OHLC.",1014,464,74,11,muted,18,6);
                text("Skenario berikutnya akan tampil setelah data valid.",1014,604,10.5f,cyan,true);
                text("Detail timeframe, swing, dan riwayat ada di Halaman 2.",1014,625,10,muted,false);
                return;
            }
            SwingAnalysisEngine.Zone nearest=null;String nearestTf="";double dist=Double.MAX_VALUE;
            for(int tf=0;tf<frames.length;tf++){
                SwingAnalysisEngine.Result ar=analyses.get(frames[tf]);if(ar==null)continue;
                for(SwingAnalysisEngine.Zone z:ar.zones){
                    double d=px<z.low?z.low-px:(px>z.high?px-z.high:0);
                    if(d<dist){dist=d;nearest=z;nearestTf=frames[tf];}
                    if(d==0)break;
                }
                if(dist==0)break;
            }
            List<SwingAnalysisEngine.Candle> bars;
            synchronized(data){bars=data.get(selectedTf());}
            String title=nearest==null?"belum ada zona valid":(nearest.supply?"SUPPLY ":"DEMAND ")+nearestTf+" "+fmt(nearest.low)+"–"+fmt(nearest.high);
            boolean touching=nearest!=null&&nearest.contains(px);
            text((touching?"ZONA SEDANG DIUJI: ":"ZONA TERDEKAT: ")+title,1014,239,11,touching?yellow:cyan,true);
            String retest=nearest==null?"Belum ada zona Supply/Demand kandidat yang valid.":retestStatus(nearest,bars);
            int statusColor=retest.toLowerCase(Locale.US).contains("gagal")?red:(retest.toLowerCase(Locale.US).contains("bertahan")?green:yellow);
            text("BREAKOUT / RETEST • "+selectedTf(),1014,268,12,statusColor,true);
            drawWrapped(retest,1014,290,74,10.5f,white,17,5);
            text("ALUR YANG MUNGKIN TERJADI",1014,393,12,cyan,true);
            String scenario;
            if(nearest==null)scenario="Belum ada level yang layak dijadikan acuan. Tunggu zona terkonfirmasi dari candle tertutup.";
            else if(touching&&nearest.supply)scenario="Jika candle close bearish menjauh dari Supply, penolakan turun dapat dipertimbangkan menuju Demand terdekat yang relevan. Jika candle close menembus batas atas, skenario penolakan batal dan breakout perlu diuji.";
            else if(touching)scenario="Jika candle close bullish menjauh dari Demand, pantulan naik dapat dipertimbangkan menuju Supply terdekat yang relevan. Jika candle close menembus batas bawah, skenario pantulan batal dan breakdown perlu diuji.";
            else scenario="Harga belum berada di zona. Pantau apakah harga mendekat, menembus dengan candle close, atau kembali retest. Jangan menganggap penolakan sudah terjadi sebelum ada reaksi.";
            drawWrapped(scenario,1014,415,74,10.5f,white,17,6);
            text("BUKTI DAN PEMBATALAN",1014,541,12,yellow,true);
            String evidence="Bukti: candle close, bentuk reaksi, dan struktur swing "+selectedTf()+". Pembatalan: close menembus sisi berlawanan zona atau struktur baru menyangkal skenario.";
            drawWrapped(evidence,1014,563,74,10.5f,muted,17,4);
            text("Tanpa backtest, tidak ada persentase peluang yang diklaim.",1014,652,9.5f,yellow,true);
        }
        void drawWrapped(String s,float x,float y,int maxChars,float size,int color,float lineHeight,int maxLines){
            String[] lines=wrapText(s,maxChars);
            for(int i=0;i<Math.min(lines.length,maxLines);i++)text(lines[i],x,y+i*lineHeight,size,color,false);
        }
        String retestStatus(SwingAnalysisEngine.Zone z,List<SwingAnalysisEngine.Candle> bars){
            if(z==null||bars==null||bars.size()<4)return "Data candle belum cukup untuk menilai retest.";
            int from=Math.max(1,bars.size()-12);
            int breakout=-1; boolean above=false;
            for(int i=from;i<bars.size()-1;i++){
                SwingAnalysisEngine.Candle b=bars.get(i);
                if(b.close>z.high){breakout=i;above=true;}
                else if(b.close<z.low){breakout=i;above=false;}
            }
            if(breakout<0)return "Belum ada candle close yang mengonfirmasi breakout zona ini.";
            boolean retested=false; SwingAnalysisEngine.Candle reaction=null;
            for(int i=breakout+1;i<bars.size();i++){
                SwingAnalysisEngine.Candle b=bars.get(i);
                boolean overlaps=b.high>=z.low&&b.low<=z.high;
                if(overlaps){retested=true;reaction=b;}
            }
            if(!retested)return above
                    ?"Breakout ke atas terdeteksi; menunggu harga kembali menguji zona dari atas."
                    :"Breakout ke bawah terdeteksi; menunggu harga kembali menguji zona dari bawah.";
            SwingAnalysisEngine.Candle last=bars.get(bars.size()-1);
            if(above){
                if(last.close>z.high&&last.close>last.open)
                    return "Retest dari atas teramati; candle terakhir bullish dan bertahan di atas zona. Skenario lanjut naik masih perlu candle berikutnya.";
                if(last.close<z.low)
                    return "Retest gagal: candle close kembali di bawah zona. Breakout ke atas berpotensi gagal; tunggu struktur baru.";
                return "Zona sedang diuji setelah breakout atas, tetapi candle close belum mengonfirmasi pantulan.";
            }else{
                if(last.close<z.low&&last.close<last.open)
                    return "Retest dari bawah teramati; candle terakhir bearish dan bertahan di bawah zona. Skenario lanjut turun masih perlu candle berikutnya.";
                if(last.close>z.high)
                    return "Retest gagal: candle close kembali di atas zona. Breakdown ke bawah berpotensi gagal; tunggu struktur baru.";
                return "Zona sedang diuji setelah breakout bawah, tetapi candle close belum mengonfirmasi penolakan.";
            }
        }
        void drawPriceJourney(SwingAnalysisEngine.Result selectedResult){
            text("PERJALANAN HARGA LIVE + RETEST",1014,549,13,yellow,true);
            double px=price();
            boolean fresh=Boolean.TRUE.equals(freshness.get(selectedTf())) && "LIVE".equals(liveState);
            if(!Double.isFinite(px)){
                text("Menunggu quote Biquote; status WAIT.",1014,573,11,red,true);
                text("Belum dapat mengidentifikasi sentuhan atau retest.",1014,592,11,white,false);
                text("Tidak ada persentase skenario tanpa data valid.",1014,611,11,muted,false);
                return;
            }
            if(!fresh){
                text("Harga referensi "+fmt(px)+"; feed "+liveState+".",1014,572,11,red,true);
                text("Retest hanya dievaluasi dari candle historis tertutup.",1014,591,11,white,false);
                text("Bukan pembacaan live; probabilitas dinonaktifkan.",1014,610,11,yellow,true);
                text("Saat feed segar, panel cek breakout, retest,",1014,629,11,muted,false);
                text("reaksi candle, dan kemungkinan retest gagal.",1014,648,11,muted,false);
                return;
            }
            SwingAnalysisEngine.Zone zone=null; String zoneTf=""; double distance=Double.MAX_VALUE;
            for(int tf=0;tf<frames.length;tf++){
                SwingAnalysisEngine.Result ar=analyses.get(frames[tf]); if(ar==null)continue;
                for(SwingAnalysisEngine.Zone z:ar.zones){
                    double d=px<z.low?z.low-px:(px>z.high?px-z.high:0);
                    if(d<distance){distance=d;zone=z;zoneTf=frames[tf];}
                    if(d==0)break;
                }
                if(distance==0)break;
            }
            List<SwingAnalysisEngine.Candle> bars;
            synchronized(data){bars=data.get(selectedTf());}
            SwingAnalysisEngine.Candle last=(bars==null||bars.isEmpty())?null:bars.get(bars.size()-1);
            boolean inZone=zone!=null&&zone.contains(px);
            String zoneTitle=zone==null?"zona kandidat belum tersedia":(zone.supply?"SUPPLY ":"DEMAND ")+zoneTf+" "+fmt(zone.low)+"-"+fmt(zone.high);
            text("Harga "+fmt(px)+" • "+(inZone?"MENYENTUH ":"zona terdekat ")+zoneTitle,1014,572,10.5f,white,true);
            if(zone==null||bars==null||bars.size()<4){
                text("Belum cukup data untuk menilai breakout/retest.",1014,592,11,muted,false);
                text("Tunggu candle close; status tetap WAIT.",1014,611,11,yellow,true);
                return;
            }
            String retest=retestStatus(zone,bars);
            // Render a detailed, line-wrapped summary that distinguishes touch, breakout and retest.
            int col=retest.contains("gagal")?red:(retest.contains("bertahan")?green:yellow);
            text("RETEST • "+selectedTf()+" candle tertutup:",1014,592,11,cyan,true);
            String[] lines=wrapText(retest,76);
            int yy=610;
            for(int i=0;i<Math.min(3,lines.length);i++){text(lines[i],1014,yy,10.5f,i==0?col:white,false);yy+=17;}
            if(inZone){
                text("Harga ada di zona; sentuhan saja bukan konfirmasi.",1014,yy+1,10.5f,white,false);
                text("Validasi berikutnya: candle close + struktur.",1014,yy+18,10.5f,muted,false);
            }else{
                text("Belum menyentuh zona: jangan anggap retest terjadi.",1014,yy+1,10.5f,muted,false);
            }
            text("Skenario bersifat kondisional; tidak ada % tanpa backtest.",1014,678,9.5f,yellow,true);
        }
        String[] wrapText(String s,int maxChars){
            ArrayList<String> out=new ArrayList<>(); StringBuilder line=new StringBuilder();
            for(String word:s.split(" ")){
                if(line.length()>0&&line.length()+1+word.length()>maxChars){out.add(line.toString());line.setLength(0);}
                if(line.length()>0)line.append(' ');
                line.append(word);
            }
            if(line.length()>0)out.add(line.toString());
            return out.toArray(new String[0]);
        }
        String selectedTf(){return frames[selected];}
        String fmt(double v){return Double.isFinite(v)?String.format(Locale.US,"%.2f",v):"--";}
        void drawCandles(List<SwingAnalysisEngine.Candle> bars,float x,float y,float w,float h){
            int start=Math.max(0,bars.size()-100);
            double lo=Double.MAX_VALUE,hi=-Double.MAX_VALUE;
            for(int i=start;i<bars.size();i++){
                SwingAnalysisEngine.Candle b=bars.get(i);
                lo=Math.min(lo,b.low);hi=Math.max(hi,b.high);
            }
            if(!(hi>lo))hi=lo+1;
            // Reserve a dedicated label rail. Candles and zones never extend beneath it.
            float railW=218f;
            float plotW=w-railW;
            float railX=x+plotW;
            for(int i=0;i<=6;i++){
                float yy=y+i*h/6;
                line(x,yy,x+plotW,yy,grid,1);
                text(fmt(hi-i*(hi-lo)/6),x+w+3,yy+4,10,muted,false);
            }
            int n=bars.size()-start;
            float cw=plotW/n;
            double current=price();

            // Draw supply/demand fills FIRST so they remain behind every candle.
            ArrayList<String> zoneLabels=new ArrayList<>();
            ArrayList<Float> zonePriceY=new ArrayList<>();
            ArrayList<Integer> zoneLabelColors=new ArrayList<>();
            ArrayList<Integer> zoneTypes=new ArrayList<>();
            for(int tf=0;tf<frames.length;tf++){
                SwingAnalysisEngine.Result zoneResult=analyses.get(frames[tf]);
                if(zoneResult==null)continue;
                for(SwingAnalysisEngine.Zone z:zoneResult.zones){
                    float z1=y+(float)((hi-z.high)/(hi-lo))*h;
                    float z2=y+(float)((hi-z.low)/(hi-lo))*h;
                    if(z2<y||z1>y+h)continue;
                    fill(z.supply?red:blue);p.setAlpha(12);
                    c.drawRect(x,Math.max(y,z1),x+plotW,Math.min(y+h,z2),p);
                    p.setAlpha(255);
                    String tag=frames[tf]+(z.supply?" S ":" D ")+fmt(z.low)+"-"+fmt(z.high);
                    zoneLabels.add(tag);
                    zonePriceY.add(Math.max(y+7,Math.min(y+h-2,(z1+z2)/2)));
                    zoneLabelColors.add(colors[tf]);
                    zoneTypes.add(z.supply?red:blue);
                }
            }

            // Candles are rendered after zone fills and use only the plot area.
            for(int i=0;i<n;i++){
                SwingAnalysisEngine.Candle b=bars.get(start+i);
                float xx=x+i*cw+cw/2;
                float yh=y+(float)((hi-b.high)/(hi-lo))*h;
                float yl=y+(float)((hi-b.low)/(hi-lo))*h;
                float yo=y+(float)((hi-b.open)/(hi-lo))*h;
                float yc=y+(float)((hi-b.close)/(hi-lo))*h;
                int col=b.close>=b.open?green:red;
                line(xx,yh,xx,yl,col,1.5f);fill(col);
                float bw=Math.max(2,cw*.62f);
                c.drawRect(xx-bw/2,Math.min(yo,yc),xx+bw/2,Math.max(Math.min(yo,yc)+2,Math.max(yo,yc)),p);
            }

            // Timeframe labels are drawn only in the reserved rail, outside the candle area.
            int labelCount=zoneLabels.size();
            int labelStart=Math.max(0,labelCount-12);
            ArrayList<Integer> order=new ArrayList<>();
            for(int i=labelStart;i<labelCount;i++)order.add(i);
            Collections.sort(order,(a,b)->Float.compare(zonePriceY.get(a),zonePriceY.get(b)));
            fill(0xe8051526);c.drawRect(railX,y,x+w,y+h,p);
            line(railX,y,railX,y+h,grid,1);
            int slots=Math.max(1,(int)(h/18f));
            int drawn=Math.min(order.size(),slots);
            for(int j=0;j<drawn;j++){
                int idx=order.get(j);
                float target=zonePriceY.get(idx);
                float labelY=y+10+j*((h-20f)/Math.max(1,drawn-1));
                String tag=zoneLabels.get(idx);
                p.setTextSize(8.5f);p.setTypeface(Typeface.DEFAULT_BOLD);
                float tagW=Math.min(railW-8,p.measureText(tag)+8);
                float labelX=x+w-tagW-3;
                fill(0xf003101e);c.drawRoundRect(labelX,labelY-10,x+w-3,labelY+3,3,3,p);
                text(tag,labelX+4,labelY,8.5f,zoneLabelColors.get(idx),true);
                line(railX,target,railX+5,labelY-3,zoneTypes.get(idx),0.8f);
            }

            // Live-price movement: white dashed line and white price label.
            // The line stops at the plot boundary; its label sits in the dedicated rail.
            if(Double.isFinite(current)&&current>=lo&&current<=hi){
                float liveY=y+(float)((hi-current)/(hi-lo))*h;
                p.setColor(white);p.setStrokeWidth(1.6f);p.setStyle(Paint.Style.STROKE);
                p.setPathEffect(new DashPathEffect(new float[]{7f,4f},0));
                c.drawLine(x,liveY,x+plotW,liveY,p);
                p.setPathEffect(null);p.setStyle(Paint.Style.FILL);
                String liveLabel=fmt(current);
                float tagW=58f,tagH=20f,tagX=x+w-tagW-2f;
                fill(0xfff4f7fb);c.drawRoundRect(tagX,liveY-tagH/2,tagX+tagW,liveY+tagH/2,4,4,p);
                text(liveLabel,tagX+5,liveY+4,11,0xff061322,true);
            }

            // Confirmed swing markers remain on top of candles.
            SwingAnalysisEngine.Result r=analyses.get(selectedTf());
            if(r!=null){
                for(SwingAnalysisEngine.Swing s:r.swings){
                    if(s.index<start||s.index>=bars.size())continue;
                    float xx=x+(s.index-start+.5f)*cw;
                    float yy=y+(float)((hi-s.price)/(hi-lo))*h;
                    center(s.high?"H":"L",xx,yy+(s.high?-5:13),9,s.high?red:green,true);
                }
            }
        }
        void drawDetail(){
            rect(8,127,745,604,panel,9);rect(763,127,765,604,panel,9);
            text("RINGKASAN MULTI-TIMEFRAME",22,153,16,cyan,true);
            float row=181;for(int i=0;i<10;i++){SwingAnalysisEngine.Result r=analyses.get(frames[i]);boolean fresh=Boolean.TRUE.equals(freshness.get(frames[i]));text(frames[i],23,row+i*49,14,colors[i],true);text("Struktur: "+(r==null?"MENUNGGU":r.structure),83,row+i*49,13,white,true);text("Signal: "+(r==null?"WAIT":r.signal),315,row+i*49,13,yellow,true);text("High "+fmt(r==null?Double.NaN:r.lastConfirmedHigh)+" / Low "+fmt(r==null?Double.NaN:r.lastConfirmedLow),414,row+i*49,12,muted,false);text(fresh?"FRESH":"WAIT",675,row+i*49,11,fresh?green:red,true);line(20,row+10+i*49,740,row+10+i*49,grid,1);}
            text("PENJELASAN ANALISIS",779,153,16,cyan,true);
            SwingAnalysisEngine.Result r=analyses.get(selectedTf());text("Timeframe terpilih: "+selectedTf(),781,184,14,white,true);
            text("Struktur: "+(r==null?"MENUNGGU":r.structure),781,210,14,white,true);
            text("Status data: "+(Boolean.TRUE.equals(freshness.get(selectedTf()))?"FRESH":"STALE / DATA BELUM TERSEDIA"),781,236,13,Boolean.TRUE.equals(freshness.get(selectedTf()))?green:red,true);
            text("Kesimpulan: "+(r==null?"Belum ada data valid.":r.reason),781,270,12,muted,false);
            text("ATURAN PEMBACAAN",781,312,14,yellow,true);
            text("1. Swing hanya dikonfirmasi setelah dua candle di kanan tertutup.",781,339,12,white,false);
            text("2. Supply/Demand adalah kandidat dari displacement, bukan order institusi pasti.",781,363,12,white,false);
            text("3. Breakout perlu candle close melewati swing/level.",781,387,12,white,false);
            text("4. Retest perlu reaksi baru; tidak diasumsikan terjadi otomatis.",781,411,12,white,false);
            text("5. Jika feed stale, candle tidak cukup, atau arah ambigu: WAIT.",781,435,12,white,false);
            text("6. Aplikasi hanya membaca data; tidak mengirim order ke broker.",781,459,12,green,true);
            text("RIWAYAT BAR TERAKHIR",781,499,14,cyan,true);
            List<SwingAnalysisEngine.Candle> bars=data.get(selectedTf());if(bars!=null){for(int i=0;i<Math.min(6,bars.size());i++){SwingAnalysisEngine.Candle b=bars.get(bars.size()-1-i);text(new SimpleDateFormat("MM-dd HH:mm",Locale.US).format(new Date(b.time)),781,526+i*27,11,muted,false);text("O "+fmt(b.open)+" H "+fmt(b.high)+" L "+fmt(b.low)+" C "+fmt(b.close),900,526+i*27,11,white,false);}}
            rect(1427,690,84,31,panel2,7);center("← CHART",1469,710,11,cyan,true);
        }
        @Override public boolean onTouchEvent(android.view.MotionEvent e){if(e.getAction()==MotionEvent.ACTION_UP){float x=e.getX()/sx,y=e.getY()/sy;if(y>=82&&y<120&&x<790){selected=Math.max(0,Math.min(9,(int)(x/77.2f)));}else if(y>680&&x>1380){page2=!page2;}else if(y<75&&x>1400){page2=!page2;}invalidate();return true;}return true;}
    }
}
