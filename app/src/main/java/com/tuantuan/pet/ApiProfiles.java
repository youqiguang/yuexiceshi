package com.tuantuan.pet;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import org.json.JSONArray;
import org.json.JSONObject;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Local, user-controlled HTTPS API connection settings. Do not bundle provider credentials. */
public final class ApiProfiles {
    private static final String ALIAS="tuan_v8_provider_secrets";
    private final SharedPreferences prefs;
    public static final String[] IDS={"official","relay1","relay2","relay3"};
    public ApiProfiles(Context ctx){prefs=ctx.getSharedPreferences("tuan_api_profiles_v8",Context.MODE_PRIVATE);}
    private String prefix(String id){return "api_"+safeId(id)+"_";}
    public static String safeId(String id){for(String x:IDS)if(x.equals(id))return x;return "official";}
    public String activeId(){return safeId(prefs.getString("active","official"));}
    public void use(String id){prefs.edit().putString("active",safeId(id)).apply();}
    public String label(String id){return "official".equals(id)?"OpenAI 官方": "relay1".equals(id)?"自定义中转 ①":"relay2".equals(id)?"自定义中转 ②":"自定义中转 ③";}
    public String mode(String id){return "chat".equals(prefs.getString(prefix(id)+"mode", "official".equals(id)?"responses":"chat"))?"chat":"responses";}
    public String model(String id){return prefs.getString(prefix(id)+"model","official".equals(id)?"gpt-4.1-mini":"");}
    public String base(String id){return prefs.getString(prefix(id)+"base","official".equals(id)?"https://api.openai.com/v1":"");}
    public String name(String id){return prefs.getString(prefix(id)+"name",label(id));}
    public String key(){return credential(prefix(activeId())+"key");}
    public String activeModel(){return model(activeId());}
    public String activeMode(){return mode(activeId());}
    public String activeUrl(){return resolve(base(activeId()),activeMode());}
    public boolean hasKey(){return !key().isEmpty();}
    public boolean isOfficial(){return "official".equals(activeId());}
    public static String sanitizeBase(String raw){
        try{
            String s=raw.trim();
            if(s.length()>500||s.isEmpty()||s.indexOf('\\')>=0)throw new Exception();
            URI uri=new URI(s);
            if(!"https".equalsIgnoreCase(uri.getScheme())||uri.getHost()==null
                 ||uri.getRawUserInfo()!=null||uri.getRawQuery()!=null||uri.getRawFragment()!=null)
                throw new Exception();
            String hostname=uri.getHost().toLowerCase(java.util.Locale.ROOT);
            if(hostname.equals("localhost")||hostname.endsWith(".local")||hostname.equals("::1")
                    ||hostname.startsWith("127.")||hostname.startsWith("10.")||hostname.startsWith("192.168."))
                throw new Exception();
            if(hostname.matches("172\\.(1[6-9]|2[0-9]|3[01])\\..*"))throw new Exception();
            if(uri.getRawPath()!=null && (uri.getRawPath().contains("..")||uri.getRawPath().contains("%")))throw new Exception();
            String path=uri.getPath()==null?"":uri.getPath().replaceAll("/+$","");
            if(path.endsWith("/chat/completions"))path=path.substring(0,path.length()-"/chat/completions".length());
            else if(path.endsWith("/responses"))path=path.substring(0,path.length()-"/responses".length());
            if(path.isEmpty())path="/v1";
            URI normalized=new URI("https",null,uri.getHost(),uri.getPort(),path,null,null);
            return normalized.toASCIIString();
        }catch(Exception e){throw new IllegalArgumentException("API 地址必须是 HTTPS，例如 https://站点域名/v1；不允许账号密码、查询参数或本地地址。");}
    }
    public static String resolve(String base,String mode){
        return sanitizeBase(base)+("responses".equals(mode)?"/responses":"/chat/completions");
    }
    public JSONObject profile(String id){
        String name=safeId(id);
        JSONObject obj=new JSONObject();
        try{
            obj.put("id",name);obj.put("name",name(name));obj.put("base",base(name));
            obj.put("model",model(name));obj.put("mode",mode(name));
            obj.put("hasKey",!credential(prefix(name)+"key").isEmpty());
            obj.put("isOfficial","official".equals(name));
        }catch(Exception ignored){}
        return obj;
    }
    public JSONObject summary(){
        JSONObject out=new JSONObject();JSONArray all=new JSONArray();
        try{
            out.put("active",activeId());
            for(String id:IDS)all.put(profile(id));
            out.put("profiles",all);
            out.put("voice",voiceConfig());
        }catch(Exception ignored){}
        return out;
    }
    public void save(String id,String title,String inputBase,String inputModel,String inputMode,String rawKey,boolean replaceKey){
        String profile=safeId(id);
        String base="official".equals(profile)?"https://api.openai.com/v1":sanitizeBase(inputBase);
        String model=inputModel.trim();
        if(model.isEmpty()||model.length()>140 ||model.contains("\n")||model.contains("\r"))
            throw new IllegalArgumentException("模型名称不能为空，最多 140 字。请使用中转站实际支持的模型 ID。");
        String mode=("chat".equals(inputMode)?"chat":"responses");
        String titleFinal=title.trim().length()>32?title.trim().substring(0,32):title.trim();
        SharedPreferences.Editor edit=prefs.edit();
        edit.putString(prefix(profile)+"name","official".equals(profile)?"OpenAI 官方":titleFinal.isEmpty()?label(profile):titleFinal);
        edit.putString(prefix(profile)+"base",base);
        edit.putString(prefix(profile)+"model",model);
        edit.putString(prefix(profile)+"mode",mode);
        edit.apply();
        if(replaceKey)storeCredential(prefix(profile)+"key",rawKey);
        use(profile);
    }
    private SecretKey secret() throws Exception{
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
        if(!store.containsAlias(ALIAS)){
            KeyGenerator gen=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
            gen.init(new KeyGenParameterSpec.Builder(ALIAS,
                KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
            gen.generateKey();
        }
        return ((KeyStore.SecretKeyEntry)store.getEntry(ALIAS,null)).getSecretKey();
    }
    private void storeCredential(String name,String value){
        String cleaned=value==null?"":value.trim();
        if(cleaned.isEmpty()){prefs.edit().remove(name).apply();return;}
        if(cleaned.length()>2048 ||cleaned.contains("\n")||cleaned.contains("\r"))
            throw new IllegalArgumentException("API Key 不应包含换行，也不能超过 2048 字。");
        try{
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE,secret());
            byte[] encoded=cipher.doFinal(cleaned.getBytes(StandardCharsets.UTF_8));
            byte[] all=new byte[12+encoded.length];
            System.arraycopy(cipher.getIV(),0,all,0,12);
            System.arraycopy(encoded,0,all,12,encoded.length);
            prefs.edit().putString(name,Base64.encodeToString(all,Base64.NO_WRAP)).apply();
        }catch(Exception e){throw new IllegalStateException("手机系统无法安全保存密钥，请检查设备锁屏设置。");}
    }
    private String credential(String name){
        try{
            String value=prefs.getString(name,"");if(value.isEmpty())return "";
            byte[] all=Base64.decode(value,Base64.NO_WRAP);
            byte[] iv=new byte[12];System.arraycopy(all,0,iv,0,12);
            byte[] body=new byte[all.length-12];System.arraycopy(all,12,body,0,body.length);
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,secret(),new GCMParameterSpec(128,iv));
            return new String(cipher.doFinal(body),StandardCharsets.UTF_8);
        }catch(Exception ignored){return "";}
    }
    public void clearKey(String id){prefs.edit().remove(prefix(id)+"key").apply();}
    public String voiceRoute(){return "custom".equals(prefs.getString("voice_route","follow"))?"custom":"follow";}
    public String voiceModel(){return prefs.getString("voice_model","gpt-4o-mini-tts");}
    public String voiceBase(){return prefs.getString("voice_base","");}
    public String voiceUrl(){return ("custom".equals(voiceRoute())?sanitizeBase(voiceBase()):sanitizeBase(base(activeId())))+"/audio/speech";}
    public String voiceKey(){return "custom".equals(voiceRoute())?credential("voice_custom_key"):key();}
    public JSONObject voiceConfig(){
        JSONObject o=new JSONObject();
        try{
            o.put("route",voiceRoute());o.put("model",voiceModel());o.put("base",voiceBase());
            o.put("hasKey",!credential("voice_custom_key").isEmpty());
        }catch(Exception ignored){}
        return o;
    }
    public void saveVoice(String route,String model,String base,String rawKey,boolean replaceKey){
        String selected="custom".equals(route)?"custom":"follow";
        String desired=model.trim();
        if(desired.isEmpty()||desired.length()>140)throw new IllegalArgumentException("语音模型 ID 不能为空。");
        String actualBase="custom".equals(selected)?sanitizeBase(base):"";
        prefs.edit().putString("voice_route",selected).putString("voice_model",desired).putString("voice_base",actualBase).apply();
        if(replaceKey)storeCredential("voice_custom_key",rawKey);
    }
    public void clearVoiceKey(){prefs.edit().remove("voice_custom_key").apply();}
}