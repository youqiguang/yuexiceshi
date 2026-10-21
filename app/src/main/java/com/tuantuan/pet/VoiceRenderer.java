package com.tuantuan.pet;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/** System multilingual voice or optional AI-generated original character delivery. */
public final class VoiceRenderer {
    public interface Status { void notify(String type,String message); }
    private final Context context;
    private final SharedPreferences prefs;
    private final Supplier<String> key;
    private final ApiProfiles providers;
    private final Status callback;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private TextToSpeech tts;
    private MediaPlayer player;
    private File playingFile;
    private volatile boolean closed=false;

    public VoiceRenderer(Context context,SharedPreferences prefs,Supplier<String> key,Status callback,ApiProfiles providers){
        this.context=context.getApplicationContext();
        this.prefs=prefs;this.key=key;this.callback=callback;this.providers=providers;
        tts=new TextToSpeech(this.context,status->{
            if(status==TextToSpeech.SUCCESS && tts!=null) {
                tts.setOnUtteranceProgressListener(new UtteranceProgressListener(){
                    @Override public void onStart(String id){callback.notify("speaking","system");}
                    @Override public void onDone(String id){callback.notify("speechend","");}
                    @Override public void onError(String id){callback.notify("speechend","");}
                });
                applySystemVoice();
            } else callback.notify("error","系统语音初始化失败，请确认手机已安装语音引擎。");
        });
    }
    public static String valid(String input,String[] list,String fallback) {
        for(String candidate:list)if(candidate.equals(input))return input;
        return fallback;
    }
    public String language(){return valid(prefs.getString("voice_lang","zh"),new String[]{"zh","ja","en"},"zh");}
    public String engine(){return valid(prefs.getString("voice_engine","system"),new String[]{"system","cloud"},"system");}
    public String voiceId(){return valid(prefs.getString("voice_id","ash"),new String[]{"ash","echo","verse","cedar"},"ash");}
    public String tone(){return valid(prefs.getString("voice_tone","quiet"),new String[]{"quiet","sleepy","bright"},"quiet");}
    public int rate(){return prefs.getInt("voice_rate",90);}
    public int pitch(){return prefs.getInt("voice_pitch",108);}
    public String languagePrompt(){return "ja".equals(language())?"日本語":"en".equals(language())?"English":"简体中文";}
    public String preview(){
        return "ja".equals(language())?"……ここにいる。今日も、そばにいるよ。":
               "en".equals(language())?"...I'm here. I'll stay with you today.":
               "……我在。今天也会陪着你。";
    }
    public String recognizeLanguage(){
        return "ja".equals(language())?"ja-JP":"en".equals(language())?"en-US":"zh-CN";
    }
    public void applySystemVoice(){
        main.post(()->{
            if(tts==null||closed)return;
            Locale loc="ja".equals(language())?Locale.JAPAN:"en".equals(language())?Locale.US:Locale.SIMPLIFIED_CHINESE;
            int supports=tts.setLanguage(loc);
            if(supports==TextToSpeech.LANG_MISSING_DATA||supports==TextToSpeech.LANG_NOT_SUPPORTED){
                callback.notify("error","当前手机缺少该语言语音包，请到安卓文字转语音设置中下载后再试听。");
                return;
            }
            // Prefer an installed offline voice to avoid unexpected provider network usage.
            Voice best=null;
            Set<Voice> set=tts.getVoices();
            if(set!=null){
                for(Voice v:set){
                    if(v.getLocale().getLanguage().equals(loc.getLanguage())){
                        if(best==null||best.isNetworkConnectionRequired()&&!v.isNetworkConnectionRequired())best=v;
                    }
                }
            }
            if(best!=null)tts.setVoice(best);
            tts.setSpeechRate(Math.max(.7f,Math.min(1.25f,rate()/100f)));
            tts.setPitch(Math.max(.85f,Math.min(1.3f,pitch()/100f)));
        });
    }
    public void speak(String text){
        if(text==null||text.trim().isEmpty()||closed)return;
        String line=text.length()>440?text.substring(0,440):text;
        if("cloud".equals(engine())){
            String token=key.get();
            if(token==null||token.isEmpty()){
                callback.notify("error","AI配音需要单独的 API Key。也可以切换成免费的手机系统音色。");return;
            }
            worker.execute(()->cloud(line,token));
        } else {
            main.post(()->{
                if(tts!=null&&!closed){
                    stopCloud();
                    applySystemVoice();
                    tts.speak(line,TextToSpeech.QUEUE_FLUSH,null,"tuan_voice");
                } else callback.notify("error","系统语音还没有准备好，请再试听一次。");
            });
        }
    }
    private void stopCloud(){
        if(player!=null){
            try{player.stop();}catch(Exception ignored){}
            player.release();player=null;
        }
        if(playingFile!=null){playingFile.delete();playingFile=null;}
    }
    private void cloud(String line,String token){
        HttpURLConnection conn=null;File file=null;
        try{
            JSONObject body=new JSONObject();
            body.put("model",providers.voiceModel());
            body.put("voice",voiceId());
            body.put("input",line);
            body.put("response_format","mp3");
            body.put("speed",Math.max(.7,Math.min(1.25,rate()/100.0)));
            String instructions="Create an original youthful, light boyish voice with an anime-inspired quiet kuudere acting style; "
              +"gentle, restrained, softly affectionate, naturally paced, never exaggerated or shrill. "
              +"Do not imitate any recognizable character or voice actor. Speak exactly the input in its original language, "
              +"with accurate Japanese, Chinese, or English pronunciation. ";
            switch(tone()){
                case "sleepy":instructions+="Very soft drowsy delivery, slight breathiness, unhurried pauses.";break;
                case "bright":instructions+="Youthful clear sound with a little playfulness but controlled emotion.";break;
                default:instructions+="Understated calm, cool yet warm, soft-spoken with barely noticeable emotion.";
            }
            body.put("instructions",instructions);
            conn=(HttpURLConnection)new URL(providers.voiceUrl()).openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(14000);conn.setReadTimeout(45000);conn.setDoOutput(true);
            conn.setRequestProperty("Authorization","Bearer "+token);
            conn.setRequestProperty("Content-Type","application/json");
            try(OutputStream out=conn.getOutputStream()){
                out.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }
            int code=conn.getResponseCode();
            if(code>=400)throw new IllegalStateException(code==429?"语音服务额度不足或请求频繁（429）。":code==404?"语音中转站没有 /audio/speech 接口（404），请改用独立语音地址或系统语音。":"AI配音出错（HTTP "+code+"），可能不支持当前语音模型。");
            file=File.createTempFile("tuan_original_voice_",".mp3",context.getCacheDir());
            try(InputStream in=conn.getInputStream();FileOutputStream out=new FileOutputStream(file)){
                byte[] chunk=new byte[8192];int n;int total=0;
                while((n=in.read(chunk))!=-1) {
                    total+=n;if(total>4_000_000)throw new IllegalStateException("音频太长，请缩短回复。");
                    out.write(chunk,0,n);
                }
            }
            File ready=file;
            main.post(()->{
                if(closed){ready.delete();return;}
                stopCloud();
                if(tts!=null)tts.stop();
                try{
                    player=new MediaPlayer();
                    playingFile=ready;
                    player.setDataSource(ready.getAbsolutePath());
                    player.setOnCompletionListener(mp->{stopCloud();callback.notify("speechend","");});
                    player.setOnErrorListener((mp,what,extra)->{
                        stopCloud();callback.notify("speechend","");return true;
                    });
                    player.prepare();
                    player.start();
                    callback.notify("speaking","cloud");
                }catch(Exception err){
                    stopCloud();callback.notify("error","AI音频播放失败。");callback.notify("speechend","");
                }
            });
            file=null;
        }catch(Exception ex){
            callback.notify("error",ex.getMessage()==null?"AI配音请求失败，请重试。":ex.getMessage());
            callback.notify("speechend","");
        }finally{
            if(conn!=null)conn.disconnect();
            if(file!=null)file.delete();
        }
    }
    public void stop(){
        main.post(()->{if(tts!=null)tts.stop();stopCloud();callback.notify("speechend","");});
    }
    public void close(){
        closed=true;
        worker.shutdownNow();
        main.post(()->{
            if(tts!=null){tts.stop();tts.shutdown();tts=null;}
            stopCloud();
        });
    }
}