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
            text("ANALISIS "+frames[selected],1014,151,16,cyan,true);
            text("Struktur: "+(result==null?"MENUNGGU":result.structure),1014,180,14,white,true);
            text("Sinyal: "+(result==null?"WAIT":result.signal),1014,205,14,yellow,true);
            text(result==null?engineStatus:result.reason,1014,232,12,muted,false);
            line(1014,248,1510,248,grid,1);
            text("LEVEL SWING TERKONFIRMASI",1014,271,13,white,true);
            text("Swing high: "+fmt(result==null?Double.NaN:result.lastConfirmedHigh),1014,298,14,red,true);
            text("Swing low : "+fmt(result==null?Double.NaN:result.lastConfirmedLow),1014,324,14,green,true);
            text("ZONA SUPPLY / DEMAND KANDIDAT",1014,357,13,white,true);
            if(result!=null&&!result.zones.isEmpty()){
                int shown=0;for(int i=result.zones.size()-1;i>=0&&shown<5;i--){SwingAnalysisEngine.Zone z=result.zones.get(i);int col=z.supply?red:blue;text((z.supply?"SUPPLY ":"DEMAND ")+fmt(z.low)+" - "+fmt(z.high),1014,384+shown*25,12,col,true);shown++;}
            }else text("Belum ada zona valid terdeteksi.",1014,384,12,muted,false);
            line(1014,525,1510,525,grid,1);
            drawPriceJourney(result);
            text("Ketuk DETAIL untuk riwayat dan konteks multi-timeframe.",1014,682,11,cyan,true);
            rect(1439,690,72,31,panel2,7);center("DETAIL →",1475,710,11,cyan,true);
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
            int start=Math.max(0,bars.size()-100);double lo=Double.MAX_VALUE,hi=-Double.MAX_VALUE;
            for(int i=start;i<bars.size();i++){SwingAnalysisEngine.Candle b=bars.get(i);lo=Math.min(lo,b.low);hi=Math.max(hi,b.high);}
            if(!(hi>lo)){hi=lo+1;}
            for(int i=0;i<=6;i++){float yy=y+i*h/6;line(x,yy,x+w,yy,grid,1);text(fmt(hi-i*(hi-lo)/6),x+w+3,yy+4,10,muted,false);}
            int n=bars.size()-start;float cw=w/n;
            for(int i=0;i<n;i++){SwingAnalysisEngine.Candle b=bars.get(start+i);float xx=x+i*cw+cw/2;float yh=y+(float)((hi-b.high)/(hi-lo))*h;float yl=y+(float)((hi-b.low)/(hi-lo))*h;float yo=y+(float)((hi-b.open)/(hi-lo))*h;float yc=y+(float)((hi-b.close)/(hi-lo))*h;int col=b.close>=b.open?green:red;line(xx,yh,xx,yl,col,1.5f);fill(col);float bw=Math.max(2,cw*.62f);c.drawRect(xx-bw/2,Math.min(yo,yc),xx+bw/2,Math.max(Math.min(yo,yc)+2,Math.max(yo,yc)),p);}
            double current=price();if(Double.isFinite(current)&&current>=lo&&current<=hi){float yy=y+(float)((hi-current)/(hi-lo))*h;line(x,yy,x+w,yy,yellow,1.5f);text("Harga "+fmt(current),x+8,yy-5,11,yellow,true);}
            // Multi-timeframe zones: draw subtle fill across the chart, then place readable
            // compact labels in a dedicated right-side label rail. Avoid stacking all labels at
            // the same price by assigning each label a separated row while preserving price in text.
            ArrayList<String> zoneLabels=new ArrayList<>();
            ArrayList<Float> zonePriceY=new ArrayList<>();
            ArrayList<Integer> zoneLabelColors=new ArrayList<>();
            ArrayList<Integer> zoneTypes=new ArrayList<>();
            ArrayList<Integer> zoneTf=new ArrayList<>();
            for(int tf=0;tf<frames.length;tf++){
                SwingAnalysisEngine.Result zoneResult=analyses.get(frames[tf]);
                if(zoneResult==null)continue;
                for(SwingAnalysisEngine.Zone z:zoneResult.zones){
                    float z1=y+(float)((hi-z.high)/(hi-lo))*h;
                    float z2=y+(float)((hi-z.low)/(hi-lo))*h;
                    if(z2<y||z1>y+h)continue;
                    fill(z.supply?red:blue);p.setAlpha(12);c.drawRect(x,Math.max(y,z1),x+w,Math.min(y+h,z2),p);p.setAlpha(255);
                    String tag=frames[tf]+(z.supply?" S ":" D ")+fmt(z.low)+"-"+fmt(z.high);
                    zoneLabels.add(tag);
                    zonePriceY.add(Math.max(y+7,Math.min(y+h-2,(z1+z2)/2)));
                    zoneLabelColors.add(colors[tf]);
                    zoneTypes.add(z.supply?red:blue);
                    zoneTf.add(tf);
                }
            }
            // Price labels must not cover candles: reserve a narrow right-side rail inside the chart
            // and mask the rail background before drawing the text. Draw the scale on the rail edge.
            int labelCount=zoneLabels.size();
            int labelStart=Math.max(0,labelCount-12);
            ArrayList<Integer> order=new ArrayList<>();
            for(int i=labelStart;i<labelCount;i++)order.add(i);
            Collections.sort(order,(a,b)->Float.compare(zonePriceY.get(a),zonePriceY.get(b)));
            float railW=218f;
            float railX=x+w-railW;
            fill(0xe8051526);c.drawRect(railX,y, x+w,y+h,p);
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
                // Connector is confined to the label rail, never drawn across candles.
                line(railX,target,railX+5,labelY-3,zoneTypes.get(idx),0.8f);
            }
            // Confirmed swing points belong to the selected chart timeframe.
            SwingAnalysisEngine.Result r=analyses.get(selectedTf());if(r!=null){for(SwingAnalysisEngine.Swing s:r.swings){if(s.index<start||s.index>=bars.size())continue;float xx=x+(s.index-start+.5f)*cw;float yy=y+(float)((hi-s.price)/(hi-lo))*h;center(s.high?"H":"L",xx,yy+(s.high?-5:13),9,s.high?red:green,true);}}
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
