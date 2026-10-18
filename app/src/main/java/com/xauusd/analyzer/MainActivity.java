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
    private final int[] colors={0xffffd700,0xff00ffff,0xffdc1eff,0xff00ff5a,0xffff3737,0xffffd200,0xff00b8ff,0xffff8a00,0xffbd55ff,0xffffffff};
    private int selected=4;
    private int pageIndex=0; // 0=Chart, 1=Keterangan, 2=Ringkasan Multi-Timeframe
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
        Canvas c; Paint p=new Paint(3); float sx,sy,W,H,offX,offY;
        final int bg=0xff02070c,panel=0xff142331,panel2=0xff0b1925,grid=0xff142837,white=0xffffffff,muted=0xffd0dce5,cyan=0xff00ffff,red=0xffff3737,blue=0xff00b8ff,green=0xff00ff5a,yellow=0xffffd200,gold=0xffffd700,magenta=0xffdc1eff;
        Dashboard(){super(MainActivity.this);setLayerType(View.LAYER_TYPE_HARDWARE,null);setFocusable(true);}
        void fill(int color){p.setColor(color);p.setStyle(Paint.Style.FILL);p.setStrokeWidth(1);}
        void rect(float x,float y,float w,float h,int color,float radius){fill(color);p.setAntiAlias(true);c.drawRoundRect(x,y,x+w,y+h,radius,radius,p);p.setStyle(Paint.Style.STROKE);p.setColor(0xff00ffff);p.setStrokeWidth(1f);p.setAntiAlias(true);c.drawRoundRect(x,y,x+w,y+h,radius,radius,p);p.setStyle(Paint.Style.FILL);}
        void line(float x,float y,float x2,float y2,int color,float width){p.setColor(color);p.setAntiAlias(true);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);p.setStrokeWidth(width);p.setStyle(Paint.Style.STROKE);c.drawLine(x,y,x2,y2,p);p.setStyle(Paint.Style.FILL);}
        void text(String s,float x,float y,float size,int color,boolean bold){fill(color);p.setTextSize(size);p.setTypeface(bold?Typeface.create("sans-serif",Typeface.BOLD):Typeface.create("sans-serif",Typeface.NORMAL));p.setSubpixelText(true);p.setAntiAlias(true);p.setFilterBitmap(false);c.drawText(s,x,y,p);}
        void center(String s,float x,float y,float size,int color,boolean bold){fill(color);p.setTextSize(size);p.setTypeface(bold?Typeface.create("sans-serif",Typeface.BOLD):Typeface.create("sans-serif",Typeface.NORMAL));p.setSubpixelText(true);p.setAntiAlias(true);c.drawText(s,x-p.measureText(s)/2,y,p);}
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);c=canvas;W=getWidth();H=getHeight();c.save();
            fill(bg);c.drawRect(0,0,W,H,p);
            // Draw the dashboard as vector geometry with UNIFORM scaling. This preserves
            // crisp text/lines and prevents distortion on ultrawide displays or casting.
            if(H>W){
                sx=sy=Math.min(W/768f,H/1536f);offX=(W-768f*sx)/2f;offY=(H-1536f*sy)/2f;
                c.translate(offX,offY);c.scale(sx,sy);drawPortrait();
            }else{
                // Fit the dashboard to the actual external display instead of enforcing
                // a minimum scale that can crop content on unusual resolutions.
                // Keep independent layout scales so all available pixels are used; text is
                // still vector-rendered by Canvas rather than enlarged from a bitmap.
                sx=W/1536f;sy=H/768f;offX=0f;offY=0f;
                c.scale(sx,sy);fill(bg);c.drawRect(0,0,1536,768,p);header();
                for(int i=0;i<10;i++){float x=8+i*77.2f;rect(x,82,72,36,i==selected?colors[i]:panel2,7);center(frames[i],x+36,105,14,i==selected?bg:colors[i],true);}
                if(pageIndex==0)drawChart();else if(pageIndex==1)drawExplanation();else drawDetail();
                if(pageIndex>0){rect(8,742,1520,20,0xff142331,4);text("DATA BERSUMBER DARI BIQUOTE • Analisis hanya memakai candle tertutup dan data segar. Sinyal WAIT jika belum terkonfirmasi.",17,756,11,red,true);}
            }c.restore();
        }
        void drawPortrait(){
            fill(bg);c.drawRect(0,0,768,1536,p);
            rect(12,12,744,82,panel,12);text("◆",25,64,37,gold,true);text("XAUUSD Analyzer",75,43,24,white,true);
            text("HARGA "+livePrice+"  •  SPREAD "+liveSpread,76,70,13,green,true);
            text("FEED: "+liveState,535,43,13,liveState.equals("LIVE")?green:yellow,true);
            text("Quote: "+liveTime,535,68,10,muted,false);
            for(int i=0;i<10;i++){int col=i%5,row=i/5;float x=12+col*150,y=105+row*43;rect(x,y,142,36,i==selected?colors[i]:panel2,7);center(frames[i],x+71,y+23,13,i==selected?bg:colors[i],true);}
            float top=204;
            rect(12,top,744,42,panel,8);
            text((pageIndex==0?"CHART XAUUSD • "+frames[selected]:pageIndex==1?"HALAMAN 2 • PERJALANAN HARGA":"HALAMAN 3 • RINGKASAN MULTI-TIMEFRAME"),24,top+26,15,cyan,true);
            if(pageIndex==0){
                float cx=24,cy=top+55,cw=620,ch=870;
                rect(cx,cy,cw,ch,panel,6);
                List<SwingAnalysisEngine.Candle> bars=data.get(frames[selected]);
                if(bars!=null&&!bars.isEmpty()){
                    int start=Math.max(0,bars.size()-300),n=bars.size()-start;double lo=Double.MAX_VALUE,hi=-Double.MAX_VALUE;
                    for(int i=start;i<bars.size();i++){SwingAnalysisEngine.Candle b=bars.get(i);lo=Math.min(lo,b.low);hi=Math.max(hi,b.high);}
                    double pad=(hi-lo)*.05;lo-=pad;hi+=pad;
                    for(int g=0;g<=6;g++){float yy=cy+ch*g/6;line(cx,yy,cx+cw,yy,grid,1);text(fmt(hi-(hi-lo)*g/6),cx+cw+8,yy+4,10,muted,false);}
                    float slot=cw/n;
                    for(int i=0;i<n;i++){SwingAnalysisEngine.Candle b=bars.get(start+i);float xx=cx+i*slot+slot/2;float yH=cy+(float)((hi-b.high)/(hi-lo))*ch,yL=cy+(float)((hi-b.low)/(hi-lo))*ch,yO=cy+(float)((hi-b.open)/(hi-lo))*ch,yC=cy+(float)((hi-b.close)/(hi-lo))*ch;int col=b.close>=b.open?green:red;line(xx,yH,xx,yL,col,Math.max(1,slot*.45f));fill(col);c.drawRect(xx-Math.max(1,slot*.32f),Math.min(yO,yC),xx+Math.max(1,slot*.32f),Math.max(yO,yC)+1.5f,p);}
                }
                text("BAR: "+(data.get(frames[selected])==null?0:data.get(frames[selected]).size())+" • "+(Boolean.TRUE.equals(freshness.get(frames[selected]))?"FRESH":"STALE / WAIT"),24,cy+ch+24,12,red,true);
                rect(24,cy+ch+40,720,80,panel2,8);text("LEVEL TERDEKAT",36,cy+ch+61,12,cyan,true);SwingAnalysisEngine.Result r=analyses.get(frames[selected]);if(r!=null){text("High "+fmt(r.lastConfirmedHigh),36,cy+ch+82,11,white,false);text("Low "+fmt(r.lastConfirmedLow),300,cy+ch+82,11,white,false);}
            }else if(pageIndex==1){
                float y=top+55;String[] order={"W1","D1","H4","H1","M30","M15","M5","M1"};
                for(String tf:order){rect(20,y,728,115,panel2,8);int idx=Arrays.asList(frames).indexOf(tf);text(tf,32,y+23,13,colors[idx],true);String narrative=journeyFor(tf,price());drawWrapped(narrative,32,y+45,86,11,white,14,5);SwingAnalysisEngine.Result r=analyses.get(tf);if(r!=null)text("High "+fmt(r.lastConfirmedHigh)+"   Low "+fmt(r.lastConfirmedLow),32,y+103,10,muted,false);y+=124;if(y>1410)break;}
            }else{
                float y=top+55;for(String tf:frames){SwingAnalysisEngine.Result r=analyses.get(tf);rect(20,y,728,94,panel2,8);int idx=Arrays.asList(frames).indexOf(tf);text(tf+"  •  "+(r==null?"DATA BELUM CUKUP":r.structure),32,y+24,13,colors[idx],true);text("Kondisi: "+(Boolean.TRUE.equals(freshness.get(tf))?"FRESH":"STALE / WAIT"),32,y+45,11,Boolean.TRUE.equals(freshness.get(tf))?green:red,true);if(r!=null)text("High "+fmt(r.lastConfirmedHigh)+"   Low "+fmt(r.lastConfirmedLow),32,y+67,11,white,false);y+=101;if(y>1410)break;}
            }
            rect(12,1460,744,58,0xff21111d,7);text("DATA BIQUOTE • candle tertutup • STALE = bukan sinyal live",24,1494,12,0xffff4770,true);
            rect(520,1410,220,36,panel2,7);center("GANTI HALAMAN →",630,1433,12,cyan,true);
        }
        void header(){rect(8,7,780,68,panel,9);text("◆",20,51,42,0xffffff00,true);text("XAUUSD Analyzer",82,34,26,white,true);text("MULTI-TIMEFRAME • HH/HL/LH/LL • SUPPLY & DEMAND",83,57,12,muted,false);rect(800,7,250,68,panel,9);text("HARGA SEKARANG",814,26,11,muted,true);text(livePrice,814,53,25,green,true);rect(1060,7,125,68,panel,9);text("SPREAD",1073,26,11,muted,true);text(liveSpread,1073,51,22,white,true);rect(1195,7,333,68,panel,9);text("FEED: "+liveState,1208,29,14,liveState.equals("LIVE")?green:yellow,true);text("Waktu quote: "+liveTime,1208,51,13,white,false);}
        void drawChart(){
            // PAGE 1: maximize chart area; explanatory and live-journey panels move to later pages.
            rect(8,127,1520,604,panel,9);
            List<SwingAnalysisEngine.Candle> bars;
            synchronized(data){bars=data.get(selectedTf());}
            text("CHART CANDLE TERTUTUP • "+frames[selected]+" • HINGGA 300 CANDLE",21,150,17.5,white,true);
            if(bars==null||bars.size()<2){center("Menunggu data OHLC Biquote yang valid...",768,414,23.75,yellow,true);text(engineStatus,22,179,16.25,muted,false);return;}
            drawCandles(bars,24,166,1480,525);
            text("Bar tersedia: "+bars.size()+"   Ditampilkan: "+Math.min(300,bars.size())+"   Status: "+(Boolean.TRUE.equals(freshness.get(selectedTf()))?"FRESH":"STALE / WAIT"),22,718,15,Boolean.TRUE.equals(freshness.get(selectedTf()))?green:red,true);
            rect(1427,690,84,31,panel2,7);center("DETAIL →",1469,710,13.75,cyan,true);
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
            int start=Math.max(0,bars.size()-300);
            double lo=Double.MAX_VALUE,hi=-Double.MAX_VALUE;
            for(int i=start;i<bars.size();i++){
                SwingAnalysisEngine.Candle b=bars.get(i);
                lo=Math.min(lo,b.low);hi=Math.max(hi,b.high);
            }
            if(!(hi>lo))hi=lo+1;
            // Reserve a dedicated label rail. Candles and zones never extend beneath it.
            float railW=218f;
            // Reserve genuine empty space AFTER the newest candle, inside the chart plot,
            // while keeping the price/zone-label rail completely separate on the right.
            float rightPad=34f;
            float plotW=w-railW;
            float candlePlotW=plotW-rightPad;
            float railX=x+plotW;
            for(int i=0;i<=6;i++){
                float yy=y+i*h/6;
                line(x,yy,x+candlePlotW,yy,grid,1);
            }
            int n=bars.size()-start;
            float cw=candlePlotW/n;
            double current=price();

            // Draw supply/demand fills FIRST so they remain behind every candle.
            ArrayList<String> zoneLabels=new ArrayList<>();
            ArrayList<Float> zonePriceY=new ArrayList<>();
            ArrayList<Float> zoneTopY=new ArrayList<>();
            ArrayList<Float> zoneBottomY=new ArrayList<>();
            ArrayList<Integer> zoneLabelColors=new ArrayList<>();
            ArrayList<Integer> zoneTypes=new ArrayList<>();
            ArrayList<Integer> zoneFills=new ArrayList<>();
            ArrayList<Integer> zoneBorders=new ArrayList<>();
            for(int tf=0;tf<frames.length;tf++){
                SwingAnalysisEngine.Result zoneResult=analyses.get(frames[tf]);
                if(zoneResult==null)continue;
                // Prevent dozens of historical zones from stacking into an opaque blanket:
                // keep only the newest valid supply and newest valid demand per timeframe.
                boolean keptSupply=false,keptDemand=false;
                List<SwingAnalysisEngine.Zone> tfZones=zoneResult.zones;
                for(int zi=tfZones.size()-1;zi>=0;zi--){
                    SwingAnalysisEngine.Zone z=tfZones.get(zi);
                    if((z.supply&&keptSupply)||(!z.supply&&keptDemand))continue;
                    float z1=y+(float)((hi-z.high)/(hi-lo))*h;
                    float z2=y+(float)((hi-z.low)/(hi-lo))*h;
                    if(z2<y||z1>y+h)continue;
                    if(z.supply)keptSupply=true;else keptDemand=true;
                    int base=colors[tf];
                    int border=(base&0x00ffffff)|0xb0000000;
                    // Low alpha is intentional: candles remain the dominant visual layer.
                    int translucent=(base&0x00ffffff)|0x10000000;
                    String tag=frames[tf]+(z.supply?" S ":" D ")+fmt(z.low)+"-"+fmt(z.high);
                    zoneLabels.add(tag);
                    zonePriceY.add(Math.max(y+7,Math.min(y+h-2,(z1+z2)/2)));
                    zoneTopY.add(Math.max(y,Math.min(y+h,z1)));
                    zoneBottomY.add(Math.max(y,Math.min(y+h,z2)));
                    zoneLabelColors.add(base);
                    zoneTypes.add(border);
                    zoneFills.add(translucent);
                    zoneBorders.add(border);
                }
            }
            // Paint translucent multi-timeframe zones behind the candles. Each zone uses
            // its timeframe's own color, and its midpoint will connect to the matching label
            // in the dedicated right-side rail. Candles are drawn on top for readability.
            for(int i=0;i<zoneLabels.size();i++){
                float top=zoneTopY.get(i),bottom=zoneBottomY.get(i);
                fill(zoneFills.get(i));
                c.drawRect(x,top,x+candlePlotW,bottom,p);
                p.setColor(zoneBorders.get(i));p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1f);
                c.drawRect(x,top,x+candlePlotW,bottom,p);
                p.setStyle(Paint.Style.FILL);
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
            int labelStart=0;
            ArrayList<Integer> order=new ArrayList<>();
            for(int i=labelStart;i<labelCount;i++)order.add(i);
            Collections.sort(order,(a,b)->Float.compare(zonePriceY.get(a),zonePriceY.get(b)));
            fill(0xe8051526);c.drawRect(railX,y,x+w,y+h,p);
            line(railX,y,railX,y+h,grid,1);
            // Price scale labels are painted INSIDE the reserved right rail, never outside
            // the chart card or underneath candle bodies.
            for(int i=0;i<=6;i++){
                float yy=y+i*h/6;
                text(fmt(hi-i*(hi-lo)/6),railX+6,yy+4,11.88f,muted,false);
            }
            int slots=Math.max(1,(int)(h/22f));
            int drawn=Math.min(order.size(),slots);
            for(int j=0;j<drawn;j++){
                int idx=order.get(j);
                float target=zonePriceY.get(idx);
                float labelY=y+10+j*((h-20f)/Math.max(1,drawn-1));
                String tag=zoneLabels.get(idx);
                p.setTextSize(10.6f);p.setTypeface(Typeface.DEFAULT_BOLD);
                float tagW=Math.min(railW-8,p.measureText(tag)+8);
                float labelX=x+w-tagW-3;
                fill(0xf003101e);c.drawRoundRect(labelX,labelY-10,x+w-3,labelY+3,3,3,p);
                p.setColor(zoneTypes.get(idx));p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1f);c.drawRoundRect(labelX,labelY-10,x+w-3,labelY+3,3,3,p);p.setStyle(Paint.Style.FILL);
                text(tag,labelX+4,labelY,8.5f,zoneLabelColors.get(idx),true);
                // Connector visually ties the colored zone midpoint to its right-side label.
                line(x+candlePlotW,target,labelX,labelY-3,zoneTypes.get(idx),1.4f);
                fill(zoneLabelColors.get(idx));
                c.drawCircle(x+candlePlotW,target,3f,p);
            }

            // Live-price movement: white dashed line and white price label.
            // The line stops at the plot boundary; its label sits in the dedicated rail.
            if(Double.isFinite(current)&&current>=lo&&current<=hi){
                float liveY=y+(float)((hi-current)/(hi-lo))*h;
                p.setColor(white);p.setStrokeWidth(1.6f);p.setStyle(Paint.Style.STROKE);
                p.setPathEffect(new DashPathEffect(new float[]{7f,4f},0));
                c.drawLine(x,liveY,x+candlePlotW,liveY,p);
                p.setPathEffect(null);p.setStyle(Paint.Style.FILL);
                String liveLabel=fmt(current);
                float tagW=58f,tagH=20f,tagX=x+w-tagW-2f;
                fill(0xfff4f7fb);c.drawRoundRect(tagX,liveY-tagH/2,tagX+tagW,liveY+tagH/2,4,4,p);
                text(liveLabel,tagX+5,liveY+4,13.75,0xff061322,true);
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
        String journeyFor(String tf, double px){
            SwingAnalysisEngine.Result r=analyses.get(tf);
            List<SwingAnalysisEngine.Candle> bars=data.get(tf);
            boolean fresh=Boolean.TRUE.equals(freshness.get(tf));
            if(r==null||bars==null||bars.size()<12) return "Data "+tf+" belum cukup; perjalanan harga dan zona belum dapat dikonfirmasi.";
            if(!Double.isFinite(px)) return "Harga acuan belum tersedia; level belum bisa dibandingkan.";
            SwingAnalysisEngine.Zone sup=null,dem=null;
            double sd=Double.MAX_VALUE,dd=Double.MAX_VALUE;
            for(SwingAnalysisEngine.Zone z:r.zones){
                double d=px<z.low?z.low-px:(px>z.high?px-z.high:0);
                if(z.supply&&d<sd){sd=d;sup=z;}
                if(!z.supply&&d<dd){dd=d;dem=z;}
            }
            String freshness=fresh?"candle tersedia":"data historis/stale";
            String name,body;
            boolean inSup=sup!=null&&px>=sup.low&&px<=sup.high;
            boolean inDem=dem!=null&&px>=dem.low&&px<=dem.high;
            if(inSup){name="Harga menguji supply "+tf;body="Harga berada di zona supply "+fmt(sup.low)+"–"+fmt(sup.high)+". Tunggu candle tertutup untuk membedakan penolakan dari breakout.";}
            else if(inDem){name="Harga menguji demand "+tf;body="Harga berada di zona demand "+fmt(dem.low)+"–"+fmt(dem.high)+". Pantau reaksi candle tertutup; sentuhan belum membuktikan pantulan.";}
            else if(sup!=null&&px<sup.low){name="Menuju supply "+tf;body="Kandidat supply terdekat "+fmt(sup.low)+"–"+fmt(sup.high)+", jarak ke batas bawah "+fmt(sup.low-px)+". Ini level pantauan, bukan kepastian harga akan naik.";}
            else if(dem!=null&&px>dem.high){name="Menuju demand "+tf;body="Kandidat demand terdekat "+fmt(dem.low)+"–"+fmt(dem.high)+", jarak ke batas atas "+fmt(px-dem.high)+". Ini level pantauan, bukan kepastian harga akan turun.";}
            else if(Double.isFinite(r.lastConfirmedHigh)&&px>r.lastConfirmedHigh){name="Uji breakout "+tf;body="Harga di atas swing high "+fmt(r.lastConfirmedHigh)+". Breakout baru valid setelah candle close; retest harus terlihat pada candle berikutnya.";}
            else if(Double.isFinite(r.lastConfirmedLow)&&px<r.lastConfirmedLow){name="Uji breakdown "+tf;body="Harga di bawah swing low "+fmt(r.lastConfirmedLow)+". Tunggu candle close dan uji ulang agar tidak salah membaca breakdown palsu.";}
            else {name="Konsolidasi "+tf;body="Struktur "+r.structure+"; high "+fmt(r.lastConfirmedHigh)+" dan low "+fmt(r.lastConfirmedLow)+". Tunggu harga mendekati level atau candle close mengonfirmasi arah.";}
            if(!fresh||!"LIVE".equals(liveState)) body="DATA STALE — "+body+" Kondisi ini historis dan bukan klaim pergerakan live.";
            return name+". "+body;
        }
        void drawExplanation(){
            double px=price();
            boolean live="LIVE".equals(liveState);
            // Match the supplied reference: 8 numbered journey cards on the left,
            // selected timeframe detail + zones on the upper right, scenarios below.
            rect(8,123,744,588,panel,9);
            rect(762,123,450,311,panel,9);
            rect(1219,123,309,311,panel,9);
            rect(762,443,766,168,panel,9);
            rect(762,620,766,91,panel,9);
            text("HALAMAN 2 — PERJALANAN HARGA XAUUSD",20,149,21.25,cyan,true);
            drawWrapped("Analisis pergerakan harga lintas timeframe berdasarkan struktur, zona Supply & Demand, breakout, retest, dan level penting.",20,169,88,13.13f,white,12,2);
            text("STATUS: "+(live?"FEED LIVE":"FEED STALE — REFERENSI HISTORIS, BUKAN SINYAL LIVE"),20,199,11.88f,live?green:red,true);
            // Main path is ordered weekly -> intraday and numbered like the mockup.
            String[] order={"W1","D1","H4","H1","M30","M15","M5","M1"};
            for(int i=0;i<8;i++){
                String tf=order[i]; int idx=Arrays.asList(frames).indexOf(tf);
                float y=216+i*59.1f;
                SwingAnalysisEngine.Result rr=analyses.get(tf);
                boolean fresh=Boolean.TRUE.equals(freshness.get(tf));
                String narrative=journeyFor(tf,px);
                int stop=narrative.indexOf('.');
                String narrativeTitle=stop>0?narrative.substring(0,stop):"Perjalanan harga "+tf;
                if(narrativeTitle.length()>42)narrativeTitle=narrativeTitle.substring(0,39)+"...";
                rect(17,y,725,55,panel2,6);
                rect(25,y+11,31,38.75,i%2==0?0xff2263b5:0xff574184,18);
                center(""+(i+1),40.5f,y+32,16.25,white,true);
                rect(63,y+10,49,33,0xff0b263b,5);
                center(tf,87.5f,y+31,13.75,colors[Math.max(0,idx)],true);
                text(narrativeTitle,122,y+16,13.13f,colors[Math.max(0,idx)],true);
                drawWrapped(narrative,122,y+32,62,11.5f,white,10.5f,2);
                text("High: "+fmt(rr==null?Double.NaN:rr.lastConfirmedHigh),646,y+20,10.63f,muted,false);
                text("Low: "+fmt(rr==null?Double.NaN:rr.lastConfirmedLow),646,y+37,10.63f,muted,false);
            }
            SwingAnalysisEngine.Result r=analyses.get(selectedTf());
            List<SwingAnalysisEngine.Candle> bars=data.get(selectedTf());
            boolean fresh=Boolean.TRUE.equals(freshness.get(selectedTf()));
            text("DETAIL ANALISIS TIMEFRAME TERPILIH ("+selectedTf()+")",776,149,15.63f,cyan,true);
            text("Struktur",776,176,13.13f,muted,false);text(": "+(r==null?"DATA BELUM CUKUP":r.structure),856,176,13.13f,white,true);
            text("Kondisi",776,197,13.13f,muted,false);text(": "+(fresh&&live?"CANDLE SEGAR":"STALE / HISTORIS"),856,197,13.13f,fresh&&live?green:red,true);
            text("Harga sekarang",776,218,13.13f,muted,false);text(": "+livePrice,856,218,13.75,live?green:red,true);
            text("Level terdekat",776,241,13.75,yellow,true);
            SwingAnalysisEngine.Zone ns=null,nd=null;double ds=Double.MAX_VALUE,dd=Double.MAX_VALUE;
            if(r!=null&&Double.isFinite(px)) for(SwingAnalysisEngine.Zone z:r.zones){
                double d=px<z.low?z.low-px:(px>z.high?px-z.high:0);
                if(z.supply&&d<ds){ds=d;ns=z;}if(!z.supply&&d<dd){dd=d;nd=z;}
            }
            text("● Supply: "+(ns==null?"belum terdeteksi":fmt(ns.low)+" – "+fmt(ns.high)),786,263,12.13f,0xffff6378,true);
            text("● Level kunci: "+(r==null?"—":fmt(r.lastConfirmedHigh)+" / "+fmt(r.lastConfirmedLow)),786,283,12.13f,yellow,true);
            text("● Demand: "+(nd==null?"belum terdeteksi":fmt(nd.low)+" – "+fmt(nd.high)),786,303,12.13f,green,true);
            text("Interpretasi:",776,327,13.75,white,true);
            String interpretation;
            if(!live||!fresh) interpretation="Feed stale: level berikut adalah konteks candle tersimpan. Tidak bisa dipastikan harga sedang retest/menembus sekarang.";
            else interpretation=journeyFor(selectedTf(),px);
            drawWrapped(interpretation,776,345,57,11.75f,white,11,7);
            text("BAR TERAKHIR",776,414,11.88f,cyan,true);
            if(bars!=null&&!bars.isEmpty()){SwingAnalysisEngine.Candle b=bars.get(bars.size()-1);text("O "+fmt(b.open)+"  H "+fmt(b.high)+"  L "+fmt(b.low)+"  C "+fmt(b.close),868,414,11.25,muted,false);}
            text("ZONA SUPPLY & DEMAND "+selectedTf(),1232,149,15,cyan,true);
            if(r!=null){
                int si=0,di=0;
                for(SwingAnalysisEngine.Zone z:r.zones){
                    if(z.supply&&si<2){float yy=166+si*49;rect(1230,yy,284,50,0xff301c2b,5);text("Supply "+(si+1),1240,yy+15,12.5,0xffff6378,true);text(fmt(z.low)+" – "+fmt(z.high),1240,yy+30,12.5,white,false);si++;}
                    else if(!z.supply&&di<2){float yy=266+di*49;rect(1230,yy,284,50,0xff0c332b,5);text("Demand "+(di+1),1240,yy+15,12.5,green,true);text(fmt(z.low)+" – "+fmt(z.high),1240,yy+30,12.5,white,false);di++;}
                }
                if(si==0)text("Supply: belum terdeteksi",1234,188,11.88f,muted,false);
                if(di==0)text("Demand: belum terdeteksi",1234,288,11.88f,muted,false);
            }
            text("SKENARIO PERGERAKAN HARGA",776,467,16.25,cyan,true);
            rect(774,478,373,119,0xff06251e,6);rect(1154,478,361,119,0xff2b1720,6);
            text("↗ SKENARIO BULLISH",786,497,13.13f,green,true);
            text("↘ SKENARIO BEARISH",1166,497,13.13f,red,true);
            String bull="1. Bertahan di atas level kunci. 2. Candle close menembus supply. 3. Retest dari atas berhasil. 4. Level target berikutnya mengikuti supply terdeteksi.";
            String bear="1. Gagal bertahan di level kunci. 2. Candle close menembus swing low. 3. Retest dari bawah gagal. 4. Pantau demand berikutnya.";
            if(!live||!fresh){bull="Feed STALE: skenario bullish belum aktif. Tunggu quote segar, candle close, dan retest terkonfirmasi.";bear="Feed STALE: skenario bearish belum aktif. Jangan anggap penembusan terjadi tanpa candle baru.";}
            drawWrapped(bull,786,516,54,11.88f,white,12,6);drawWrapped(bear,1166,516,52,11.88f,white,12,6);
            text("⚠ CATATAN PENTING",776,644,15,yellow,true);
            drawWrapped("Analisis berdasarkan candle tertutup. Sentuhan zona bukan bukti penolakan. Level dapat berubah saat data baru datang. Konfirmasi pergerakan berikutnya pada chart.",776,665,125,11.88f,white,12,3);
            rect(1360,680,151,26,panel2,6);center("RINGKASAN →",1435,698,12.5,cyan,true);
        }
        void drawDetail(){
            rect(8,127,745,604,panel,9);rect(763,127,765,604,panel,9);
            text("RINGKASAN MULTI-TIMEFRAME",22,153,19.2,cyan,true);
            float row=181;for(int i=0;i<10;i++){SwingAnalysisEngine.Result r=analyses.get(frames[i]);boolean fresh=Boolean.TRUE.equals(freshness.get(frames[i]));text(frames[i],23,row+i*49,14,colors[i],true);text("Struktur: "+(r==null?"MENUNGGU":r.structure),83,row+i*49,15.6,white,true);text("Signal: "+(r==null?"WAIT":r.signal),315,row+i*49,15.6,yellow,true);text("High "+fmt(r==null?Double.NaN:r.lastConfirmedHigh)+" / Low "+fmt(r==null?Double.NaN:r.lastConfirmedLow),414,row+i*49,14.4,muted,false);text(fresh?"FRESH":"WAIT",675,row+i*49,13.2,fresh?green:red,true);line(20,row+10+i*49,740,row+10+i*49,grid,1);}
            text("PENJELASAN ANALISIS",779,153,19.2,cyan,true);
            SwingAnalysisEngine.Result r=analyses.get(selectedTf());text("Timeframe terpilih: "+selectedTf(),781,184,16.8,white,true);
            text("Struktur: "+(r==null?"MENUNGGU":r.structure),781,210,16.8,white,true);
            text("Status data: "+(Boolean.TRUE.equals(freshness.get(selectedTf()))?"FRESH":"STALE / DATA BELUM TERSEDIA"),781,236,15.6,Boolean.TRUE.equals(freshness.get(selectedTf()))?green:red,true);
            text("Kesimpulan: "+(r==null?"Belum ada data valid.":r.reason),781,270,14.4,muted,false);
            text("ATURAN PEMBACAAN",781,312,16.8,yellow,true);
            text("1. Swing hanya dikonfirmasi setelah dua candle di kanan tertutup.",781,339,14.4,white,false);
            text("2. Supply/Demand adalah kandidat dari displacement, bukan order institusi pasti.",781,363,14.4,white,false);
            text("3. Breakout perlu candle close melewati swing/level.",781,387,14.4,white,false);
            text("4. Retest perlu reaksi baru; tidak diasumsikan terjadi otomatis.",781,411,14.4,white,false);
            text("5. Jika feed stale, candle tidak cukup, atau arah ambigu: WAIT.",781,435,14.4,white,false);
            text("6. Aplikasi hanya membaca data; tidak mengirim order ke broker.",781,459,14.4,green,true);
            text("RIWAYAT BAR TERAKHIR",781,499,16.8,cyan,true);
            List<SwingAnalysisEngine.Candle> bars=data.get(selectedTf());if(bars!=null){for(int i=0;i<Math.min(6,bars.size());i++){SwingAnalysisEngine.Candle b=bars.get(bars.size()-1-i);text(new SimpleDateFormat("MM-dd HH:mm",Locale.US).format(new Date(b.time)),781,526+i*27,13.2,muted,false);text("O "+fmt(b.open)+" H "+fmt(b.high)+" L "+fmt(b.low)+" C "+fmt(b.close),900,526+i*27,13.2,white,false);}}
            rect(1427,690,84,31,panel2,7);center("← CHART",1469,710,13.2,cyan,true);
        }
        @Override public boolean onTouchEvent(android.view.MotionEvent e){if(e.getAction()==MotionEvent.ACTION_UP){float x=(e.getX()-offX)/sx,y=(e.getY()-offY)/sy;if(H>W){if(y>=105&&y<191){int col=Math.max(0,Math.min(4,(int)((x-12)/150)));int row=y<145?0:1;selected=Math.max(0,Math.min(9,row*5+col));}else if(y>=1410){pageIndex=(pageIndex+1)%3;}else if(y<100&&x>500){pageIndex=(pageIndex+1)%3;}}else{if(y>=82&&y<120&&x<790){selected=Math.max(0,Math.min(9,(int)(x/77.2f)));}else if(y>670&&x>1300){pageIndex=(pageIndex+1)%3;}else if(y<75&&x>1400){pageIndex=(pageIndex+1)%3;}}invalidate();return true;}return true;}
    }
}
