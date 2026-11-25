package com.laclave.boveda;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.view.inputmethod.InputMethodManager;
import android.text.InputType;
import android.text.method.PasswordTransformationMethod;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.UUID;

public class MainActivity extends Activity {
    private static final String PREFS = "vault_private_prefs";
    private static final String KEY_SALT = "pin_salt";
    private static final String KEY_HASH = "pin_hash";
    private static final String KEY_FILES = "files_json";
    private static final String KS_ALIAS = "laclave_vault_aes_v1";
    private static final int PIN_ITERATIONS = 120000;
    private static final int REQUEST_IMPORT = 2101;
    private static final int COLOR_BG = Color.rgb(8, 11, 18);
    private static final int COLOR_PANEL = Color.rgb(20, 28, 39);
    private static final int COLOR_LINE = Color.rgb(65, 75, 87);
    private static final int COLOR_MINT = Color.rgb(220, 179, 101);
    private static final int COLOR_MUTED = Color.rgb(157, 169, 181);
    private final SecureRandom random = new SecureRandom();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable delayedLock = this::lockVault;
    private SecretKey fileKey;
    private boolean pickerOpen = false;
    private boolean isUnlocked = false;
    private LinearLayout root;
    private DialView dial;
    private EditText pinField, confirmField;
    private TextView message;
    private Button actionButton;
    private LinearLayout vaultContent;
    private ScrollView vaultScroll;
    private TextView fileCount;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(COLOR_BG);
        getWindow().setNavigationBarColor(COLOR_BG);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        clearPreviewCache();
        buildAuthScreen();
    }

    private int dp(float value) { return (int)(value * getResources().getDisplayMetrics().density + 0.5f); }
    private android.graphics.drawable.Drawable rounded(int color, int stroke, int radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius));
        if (stroke != 0) d.setStroke(dp(1), stroke); return d;
    }
    private android.graphics.drawable.Drawable gradient(int top, int bottom, int stroke, int radius) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{top, bottom});
        d.setCornerRadius(dp(radius));
        if (stroke != 0) d.setStroke(dp(1), stroke);
        return d;
    }
    private TextView microLabel(String value) {
        TextView t = text(value, 9, Color.rgb(151, 167, 183), true);
        t.setLetterSpacing(.16f);
        return t;
    }
    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color);
        t.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL)); return t;
    }
    private LinearLayout column() { LinearLayout x=new LinearLayout(this); x.setOrientation(LinearLayout.VERTICAL); return x; }
    private LinearLayout row() { LinearLayout x=new LinearLayout(this); x.setOrientation(LinearLayout.HORIZONTAL); x.setGravity(Gravity.CENTER_VERTICAL); return x; }
    private void pad(View v, int l, int t, int r, int b) { v.setPadding(dp(l),dp(t),dp(r),dp(b)); }
    private LinearLayout.LayoutParams lp(int w,int h) { return new LinearLayout.LayoutParams(w<0?w:dp(w),h<0?h:dp(h)); }
    private Button button(String label, boolean primary) {
        Button b=new Button(this); b.setText(label); b.setAllCaps(false); b.setTextSize(14); b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        b.setTextColor(primary?Color.rgb(26,25,22):Color.rgb(225,231,236)); b.setBackground(primary?gradient(Color.rgb(240,209,148),Color.rgb(183,139,65),Color.rgb(255,232,181),14):gradient(Color.rgb(42,51,62),Color.rgb(21,28,37),Color.rgb(71,83,95),14));
        b.setMinHeight(dp(48)); b.setStateListAnimator(null); return b;
    }
    private void buildBase() {
        root=column(); root.setBackgroundColor(COLOR_BG); pad(root,20,22,20,24);
        ScrollView outer=new ScrollView(this); outer.setFillViewport(true); outer.setClipToPadding(false);
        outer.addView(root,new ScrollView.LayoutParams(-1,-2)); setContentView(outer);
    }
    private void buildBrand() {
        LinearLayout header=row();
        TextView icon=text("⬡",25,Color.rgb(29,24,16),true); icon.setGravity(Gravity.CENTER);
        icon.setBackground(gradient(Color.rgb(247,218,151),COLOR_MINT,Color.rgb(255,239,197),14));
        header.addView(icon,lp(48,48));
        LinearLayout titles=column(); pad(titles,12,0,0,0);
        TextView name=text("LA CLAVE",14,Color.rgb(242,244,246),true); name.setLetterSpacing(.18f); titles.addView(name);
        TextView sub=text("PRIVATE VAULT  /  LOCAL",9,Color.rgb(146,161,176),true); sub.setLetterSpacing(.12f); titles.addView(sub);
        header.addView(titles,new LinearLayout.LayoutParams(0,-2,1));
        LinearLayout badge=row(); pad(badge,9,7,9,7); badge.setBackground(rounded(Color.rgb(20,35,35),Color.rgb(63,105,96),20));
        View dot=new View(this); dot.setBackground(rounded(Color.rgb(111,231,183),0,8)); badge.addView(dot,lp(7,7));
        TextView local=text("  LOCAL",9,Color.rgb(190,222,209),true); badge.addView(local);
        header.addView(badge); root.addView(header,lp(-1,54));
    }
    private void buildAuthScreen() {
        isUnlocked=false; fileKey=null; buildBase(); buildBrand();
        LinearLayout hero=column();
        LinearLayout.LayoutParams hp=lp(-1,-2); hp.topMargin=dp(20); root.addView(hero,hp);
        LinearLayout status=row();
        TextView eyebrow=microLabel(hasPin()?"ACCESO RESTRINGIDO":"CONFIGURACIÓN INICIAL");
        status.addView(eyebrow,new LinearLayout.LayoutParams(0,-2,1));
        TextView tag=text("●  CIFRADO LOCAL",9,Color.rgb(224,190,127),true); tag.setLetterSpacing(.04f);
        pad(tag,10,7,10,7); tag.setBackground(rounded(Color.rgb(37,33,25),Color.rgb(104,84,48),14)); status.addView(tag);
        hero.addView(status,lp(-1,30));
        TextView title=text(hasPin()?"Bóveda
bajo llave":"Creá tu
bóveda privada",31,Color.rgb(247,248,250),true);
        title.setLineSpacing(-dp(2),1f); LinearLayout.LayoutParams tp=lp(-1,-2); tp.topMargin=dp(7); hero.addView(title,tp);
        TextView intro=text(hasPin()?"Acceso personal. Solo se abre con tu PIN.":"Una capa privada para tus fotos y videos.",13,COLOR_MUTED,false);
        LinearLayout.LayoutParams ip=lp(-1,-2); ip.topMargin=dp(6); hero.addView(intro,ip);

        LinearLayout vaultPlate=column(); pad(vaultPlate,13,12,13,11);
        vaultPlate.setBackground(gradient(Color.rgb(27,38,51),Color.rgb(12,19,29),Color.rgb(76,91,103),22));
        vaultPlate.setElevation(dp(7));
        LinearLayout plateHead=row();
        TextView plateTitle=text("PUERTA DE SEGURIDAD",10,Color.rgb(220,226,230),true); plateTitle.setLetterSpacing(.13f);
        plateHead.addView(plateTitle,new LinearLayout.LayoutParams(0,-2,1));
        TextView bolt=text("●  ●  ●",8,Color.rgb(172,139,82),true); plateHead.addView(bolt);
        vaultPlate.addView(plateHead,lp(-1,22));
        dial=new DialView(this); LinearLayout.LayoutParams dlp=lp(-1,222); dlp.topMargin=dp(2); vaultPlate.addView(dial,dlp);
        View divider=new View(this); divider.setBackgroundColor(Color.rgb(53,65,76)); LinearLayout.LayoutParams vlp=lp(-1,1); vlp.topMargin=dp(1); vaultPlate.addView(divider,vlp);
        LinearLayout securityRow=row();
        TextView sec=text("AES-256  ·  ARCHIVOS EN EL DISPOSITIVO",9,Color.rgb(161,176,188),true); sec.setLetterSpacing(.04f);
        securityRow.addView(sec,new LinearLayout.LayoutParams(0,-2,1));
        TextView seal=text("●",10,Color.rgb(115,224,177),true); securityRow.addView(seal);
        LinearLayout.LayoutParams srp=lp(-1,24); srp.topMargin=dp(5); vaultPlate.addView(securityRow,srp);
        LinearLayout.LayoutParams vp=lp(-1,-2); vp.topMargin=dp(15); root.addView(vaultPlate,vp);

        LinearLayout form=column(); pad(form,15,13,15,13);
        form.setBackground(gradient(Color.rgb(20,29,41),Color.rgb(13,19,28),Color.rgb(49,62,75),18));
        LinearLayout labels=row();
        TextView lab=text(hasPin()?"CLAVE DE ACCESO":"CREÁ TU PIN",10,Color.rgb(220,226,232),true); lab.setLetterSpacing(.12f);
        labels.addView(lab,new LinearLayout.LayoutParams(0,-2,1));
        TextView length=microLabel("6 — 10 DÍGITOS"); labels.addView(length);
        form.addView(labels,lp(-1,23));
        pinField=pinInput("●  ●  ●  ●  ●  ●"); LinearLayout.LayoutParams pp=lp(-1,54); pp.topMargin=dp(5); form.addView(pinField,pp);
        confirmField=pinInput("Repetí tu PIN"); LinearLayout.LayoutParams cp=lp(-1,50); cp.topMargin=dp(9); if(hasPin()) confirmField.setVisibility(View.GONE); form.addView(confirmField,cp);
        actionButton=button(hasPin()?"DESBLOQUEAR BÓVEDA":"CREAR MI BÓVEDA",true);
        actionButton.setTextSize(13); actionButton.setLetterSpacing(.08f);
        actionButton.setBackground(gradient(Color.rgb(246,212,145),Color.rgb(187,143,66),Color.rgb(255,230,178),14));
        actionButton.setTextColor(Color.rgb(30,25,16));
        LinearLayout.LayoutParams ap=lp(-1,52); ap.topMargin=dp(12); form.addView(actionButton,ap); actionButton.setOnClickListener(v->submitPin());
        message=text(hasPin()?"Ingresá tu clave para abrir la puerta.":"Elegí un PIN distinto al del teléfono.",11,COLOR_MUTED,false);
        message.setGravity(Gravity.CENTER); message.setMinHeight(dp(34)); LinearLayout.LayoutParams mp=lp(-1,-2); mp.topMargin=dp(5); form.addView(message,mp);
        LinearLayout.LayoutParams fp=lp(-1,-2); fp.topMargin=dp(12); root.addView(form,fp);
        TextView caution=text("Los originales no se eliminan automáticamente de la galería.",10,Color.rgb(115,130,146),false);
        caution.setGravity(Gravity.CENTER); LinearLayout.LayoutParams np=lp(-1,-2); np.topMargin=dp(11); root.addView(caution,np);
    }
    private EditText pinInput(String hint){EditText e=new EditText(this);e.setSingleLine(true);e.setTextSize(20);e.setTextColor(Color.rgb(245,250,253));e.setHintTextColor(Color.rgb(59,75,93));e.setHint(hint);e.setGravity(Gravity.CENTER);e.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD);e.setTransformationMethod(PasswordTransformationMethod.getInstance());e.setLetterSpacing(.32f);e.setBackground(gradient(Color.rgb(7,12,19),Color.rgb(14,21,30),Color.rgb(75,88,101),13));e.setPadding(dp(12),0,dp(12),0);e.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(10)});return e;}
    private void submitPin(){String p=pinField.getText().toString();if(!p.matches("[0-9]{6,10}")){setMessage("El PIN debe tener entre 6 y 10 números.",true);dial.playError();return;}if(!hasPin()){String c=confirmField.getText().toString();if(!p.equals(c)){setMessage("Los PIN no coinciden. Revisalos y volvé a probar.",true);dial.playError();return;}createPin(p);}else unlock(p);}
    private void setMessage(String s,boolean error){message.setText(s);message.setTextColor(error?Color.rgb(255,113,139):COLOR_MUTED);}
    private void createPin(String pin){try{ensureFileKey();byte[] salt=new byte[16];random.nextBytes(salt);byte[] hash=derive(pin,salt);getPreferences(MODE_PRIVATE).edit().putString(KEY_SALT,hex(salt)).putString(KEY_HASH,hex(hash)).apply();dial.playSuccess(()->runOnUiThread(()->showVault()));setMessage("PIN creado. Preparando bóveda…",false);}catch(Exception e){setMessage("No pude preparar el almacenamiento seguro. Probá de nuevo.",true);}}
    private void unlock(String pin){try{byte[] salt=unhex(getPreferences(MODE_PRIVATE).getString(KEY_SALT,""));byte[] expected=unhex(getPreferences(MODE_PRIVATE).getString(KEY_HASH,""));byte[] actual=derive(pin,salt);if(!MessageDigest.isEqual(expected,actual)){dial.playError();setMessage("PIN incorrecto. Tus archivos siguen cifrados.",true);return;}ensureFileKey();setMessage("PIN correcto. Abriendo la bóveda…",false);dial.playSuccess(()->runOnUiThread(()->showVault()));}catch(Exception e){setMessage("No pude verificar el PIN. Si reinstalaste la app, los datos cifrados no se pueden recuperar.",true);}}
    private boolean hasPin(){return getPreferences(MODE_PRIVATE).contains(KEY_HASH);}
    private byte[] derive(String pin,byte[] salt)throws Exception{PBEKeySpec spec=new PBEKeySpec(pin.toCharArray(),salt,PIN_ITERATIONS,256);try{return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();}finally{spec.clearPassword();}}
    private String hex(byte[] b){StringBuilder s=new StringBuilder();for(byte x:b)s.append(String.format(Locale.US,"%02x",x&255));return s.toString();}
    private byte[] unhex(String s){if(s==null||s.length()%2!=0)throw new IllegalArgumentException("bad hex");byte[] b=new byte[s.length()/2];for(int i=0;i<b.length;i++)b[i]=(byte)Integer.parseInt(s.substring(i*2,i*2+2),16);return b;}
    private SecretKey ensureFileKey()throws Exception{if(fileKey!=null)return fileKey;KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);if(ks.containsAlias(KS_ALIAS)){fileKey=(SecretKey)ks.getKey(KS_ALIAS,null);return fileKey;}KeyGenerator kg=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");kg.init(new KeyGenParameterSpec.Builder(KS_ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).setRandomizedEncryptionRequired(true).build());fileKey=kg.generateKey();return fileKey;}
    private void showVault(){hideKeyboard();isUnlocked=true;buildVaultScreen();}
    private void buildVaultScreen(){
        buildBase(); buildBrand();
        LinearLayout titleRow=row(); LinearLayout tit=column();
        TextView h=text("Mi bóveda",29,Color.rgb(247,248,250),true); tit.addView(h);
        TextView desc=text("ESPACIO PRIVADO  ·  SOLO EN ESTE DISPOSITIVO",9,Color.rgb(153,169,183),true); desc.setLetterSpacing(.06f);
        LinearLayout.LayoutParams dlp=lp(-1,-2); dlp.topMargin=dp(4); tit.addView(desc,dlp);
        titleRow.addView(tit,new LinearLayout.LayoutParams(0,-2,1));
        fileCount=text("",9,Color.rgb(231,215,180),true); fileCount.setGravity(Gravity.CENTER); pad(fileCount,11,8,11,8);
        fileCount.setBackground(rounded(Color.rgb(39,34,26),Color.rgb(102,82,48),12)); titleRow.addView(fileCount);
        LinearLayout.LayoutParams trp=lp(-1,-2); trp.topMargin=dp(19); root.addView(titleRow,trp);

        LinearLayout secureCard=row(); pad(secureCard,14,13,14,13);
        secureCard.setBackground(gradient(Color.rgb(25,39,46),Color.rgb(15,25,32),Color.rgb(54,91,84),17));
        TextView shield=text("⬡",23,Color.rgb(173,239,209),true); shield.setGravity(Gravity.CENTER); shield.setBackground(rounded(Color.rgb(26,59,52),Color.rgb(70,123,101),14)); secureCard.addView(shield,lp(44,44));
        LinearLayout status=column(); pad(status,11,0,0,0);
        TextView secureHead=text("BÓVEDA ACTIVA",11,Color.rgb(219,242,228),true); secureHead.setLetterSpacing(.08f); status.addView(secureHead);
        TextView secureSub=text("Contenido cifrado localmente",10,Color.rgb(151,183,169),false); LinearLayout.LayoutParams ssp=lp(-1,-2); ssp.topMargin=dp(4); status.addView(secureSub,ssp);
        secureCard.addView(status,new LinearLayout.LayoutParams(0,-2,1));
        TextView lockBadge=text("●",11,Color.rgb(116,231,181),true); secureCard.addView(lockBadge);
        LinearLayout.LayoutParams scp=lp(-1,-2); scp.topMargin=dp(16); root.addView(secureCard,scp);

        LinearLayout actions=row();
        Button add=button("＋   IMPORTAR ARCHIVOS",true); add.setTextSize(12); add.setLetterSpacing(.05f); add.setTextColor(Color.rgb(30,25,16));
        add.setBackground(gradient(Color.rgb(246,212,145),Color.rgb(187,143,66),Color.rgb(255,230,178),14));
        LinearLayout.LayoutParams alp=new LinearLayout.LayoutParams(0,dp(52),1); alp.topMargin=dp(15); actions.addView(add,alp);
        Button close=button("CERRAR",false); close.setTextSize(10); close.setLetterSpacing(.04f); close.setBackground(gradient(Color.rgb(39,47,58),Color.rgb(20,27,36),Color.rgb(75,87,99),14));
        LinearLayout.LayoutParams clp=lp(86,52); clp.leftMargin=dp(8); clp.topMargin=dp(15); actions.addView(close,clp); root.addView(actions,lp(-1,-2));
        add.setOnClickListener(v->pickFiles()); close.setOnClickListener(v->lockVault());

        TextView listTitle=text("TUS ARCHIVOS",10,Color.rgb(191,204,214),true); listTitle.setLetterSpacing(.14f);
        LinearLayout.LayoutParams ltp=lp(-1,30); ltp.topMargin=dp(18); root.addView(listTitle,ltp);
        vaultScroll=new ScrollView(this); vaultScroll.setClipToPadding(false); vaultContent=column(); LinearLayout.LayoutParams vslp=lp(-1,-2); root.addView(vaultScroll,vslp); vaultScroll.addView(vaultContent);
        TextView warning=text("La copia original sigue en tu galería hasta que la borres.",10,Color.rgb(140,153,166),false); warning.setGravity(Gravity.CENTER); LinearLayout.LayoutParams wnlp=lp(-1,-2); wnlp.topMargin=dp(10); root.addView(warning,wnlp);
        Button wipe=button("BORRAR TODOS LOS DATOS DE LA BÓVEDA",false); wipe.setTextSize(10); wipe.setLetterSpacing(.04f); wipe.setTextColor(Color.rgb(244,139,144)); wipe.setBackground(gradient(Color.rgb(48,28,34),Color.rgb(29,19,25),Color.rgb(104,55,63),13));
        LinearLayout.LayoutParams wlp=lp(-1,44); wlp.topMargin=dp(15); root.addView(wipe,wlp); wipe.setOnClickListener(v->confirmWipe());
        TextView footer=text("SESIÓN BLOQUEADA AUTOMÁTICAMENTE AL SALIR",9,Color.rgb(110,126,141),true); footer.setGravity(Gravity.CENTER); footer.setLetterSpacing(.04f); root.addView(footer,lp(-1,40));
        refreshFileList(fileCount);
    }
    private void refreshFileList(TextView count){JSONArray files=readFiles();count.setText(files.length()+ (files.length()==1?" ARCHIVO":" ARCHIVOS"));vaultContent.removeAllViews();if(files.length()==0){TextView empty=text("◇\n\nTODAVÍA NO HAY ARCHIVOS\nImportá fotos o videos para cifrarlos dentro de la bóveda.",12,COLOR_MUTED,false);empty.setGravity(Gravity.CENTER);empty.setLineSpacing(dp(4),1f);pad(empty,20,31,20,31);empty.setBackground(gradient(Color.rgb(24,33,44),Color.rgb(13,19,28),Color.rgb(58,70,82),18));vaultContent.addView(empty,lp(-1,-2));return;}for(int i=0;i<files.length();i++){JSONObject item=files.optJSONObject(i);if(item==null)continue;LinearLayout card=row();card.setBackground(gradient(Color.rgb(29,39,51),Color.rgb(17,24,34),Color.rgb(58,70,83),16));card.setElevation(dp(3));pad(card,13,12,10,12);LinearLayout info=column();TextView name=text(item.optString("name","Archivo"),13,Color.rgb(227,237,244),true);name.setMaxLines(1);name.setEllipsize(android.text.TextUtils.TruncateAt.END);info.addView(name);TextView sub=text(item.optString("kind","Archivo").toUpperCase(Locale.ROOT)+"  ·  "+formatSize(item.optLong("size",0))+"  ·  AES-256",9,Color.rgb(156,174,188),true);LinearLayout.LayoutParams slp=lp(-1,-2);slp.topMargin=dp(5);info.addView(sub,slp);card.addView(info,new LinearLayout.LayoutParams(0,-2,1));Button open=smallButton("Abrir");card.addView(open,lp(62,40));String id=item.optString("id");open.setOnClickListener(v->openFile(id));Button delete=smallButton("×");LinearLayout.LayoutParams delp=lp(42,40);delp.leftMargin=dp(6);card.addView(delete,delp);delete.setOnClickListener(v->confirmDelete(id,item.optString("name","archivo"),count));LinearLayout.LayoutParams cp=lp(-1,-2);cp.bottomMargin=dp(9);vaultContent.addView(card,cp);}}
    private Button smallButton(String label){Button b=button(label,false);b.setTextSize(12);b.setPadding(0,0,0,0);return b;}
    private JSONArray readFiles(){try{return new JSONArray(getPreferences(MODE_PRIVATE).getString(KEY_FILES,"[]"));}catch(Exception e){return new JSONArray();}}
    private void writeFiles(JSONArray a){getPreferences(MODE_PRIVATE).edit().putString(KEY_FILES,a.toString()).apply();}
    private String formatSize(long n){if(n<1024*1024)return Math.max(1,n/1024)+" KB";return String.format(Locale.US,"%.1f MB",n/(1024d*1024d));}
    private void pickFiles(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"image/*","video/*"});i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);pickerOpen=true;try{startActivityForResult(i,REQUEST_IMPORT);}catch(Exception e){pickerOpen=false;toast("No pude abrir el selector de archivos.");}}
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){super.onActivityResult(requestCode,resultCode,data);if(requestCode!=REQUEST_IMPORT)return;pickerOpen=false;if(resultCode!=RESULT_OK||data==null)return;try{if(data.getClipData()!=null){for(int i=0;i<data.getClipData().getItemCount();i++)importUri(data.getClipData().getItemAt(i).getUri());}else if(data.getData()!=null)importUri(data.getData());}catch(Exception e){toast("No se pudieron importar algunos archivos.");}if(fileCount!=null)refreshFileList(fileCount);}
    private void importUri(Uri uri){try{String mime=getContentResolver().getType(uri);if(mime==null||(!mime.startsWith("image/")&&!mime.startsWith("video/"))){toast("Solo se pueden importar fotos y videos.");return;}String name=displayName(uri);String id=UUID.randomUUID().toString();File dir=new File(getFilesDir(),"vault");if(!dir.exists()&&!dir.mkdirs())throw new Exception("storage");File target=new File(dir,id+".enc");byte[] iv=new byte[12];random.nextBytes(iv);Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,ensureFileKey(),new GCMParameterSpec(128,iv));long size=0;try(InputStream in=getContentResolver().openInputStream(uri);OutputStream raw=new FileOutputStream(target);CipherOutputStream out=new CipherOutputStream(raw,cipher)){if(in==null)throw new Exception("read");byte[] buffer=new byte[64*1024];int n;while((n=in.read(buffer))!=-1){out.write(buffer,0,n);size+=n;}}catch(Exception e){target.delete();throw e;}JSONObject rec=new JSONObject();rec.put("id",id);rec.put("name",name);rec.put("mime",mime);rec.put("size",size);rec.put("iv",hex(iv));rec.put("kind",mime.startsWith("video/")?"Video":"Foto");JSONArray files=readFiles();files.put(rec);writeFiles(files);toast("Importado y cifrado: "+name);}catch(Exception e){toast("Error al cifrar el archivo: "+displayName(uri));}}
    private String displayName(Uri uri){String value=null;try(Cursor c=getContentResolver().query(uri,null,null,null,null)){if(c!=null&&c.moveToFirst()){int i=c.getColumnIndex(OpenableColumns.DISPLAY_NAME);if(i>=0)value=c.getString(i);}}catch(Exception ignored){}if(value==null){value=uri.getLastPathSegment();}if(value==null)value="archivo";value=new File(value).getName().replaceAll("[^A-Za-z0-9._ -]","_").trim();if(value.isEmpty()||value.equals(".")||value.equals(".."))value="archivo";return value;}
    private void openFile(String id){try{JSONObject item=findFile(id);if(item==null)throw new Exception("missing");File cacheDir=new File(getCacheDir(),"vault_preview");if(!cacheDir.exists())cacheDir.mkdirs();File temp=new File(cacheDir,item.optString("name","archivo"));Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,ensureFileKey(),new GCMParameterSpec(128,unhex(item.getString("iv"))));File encrypted=new File(new File(getFilesDir(),"vault"),id+".enc");try(InputStream in=new CipherInputStream(new FileInputStream(encrypted),cipher);OutputStream out=new FileOutputStream(temp)){byte[] buf=new byte[64*1024];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);}Uri content=FileProvider.getUriForFile(this,getPackageName()+".fileprovider",temp);Intent view=new Intent(Intent.ACTION_VIEW);view.setDataAndType(content,item.optString("mime","application/octet-stream"));view.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(Intent.createChooser(view,"Abrir archivo privado"));}catch(ActivityNotFoundException e){toast("No hay una app compatible para abrir este archivo.");}catch(Exception e){toast("No se pudo descifrar el archivo. Revisá que la bóveda no se haya restaurado desde una copia.");}}
    private JSONObject findFile(String id){JSONArray a=readFiles();for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o!=null&&id.equals(o.optString("id")))return o;}return null;}
    private void confirmDelete(String id,String name,TextView count){new AlertDialog.Builder(this).setTitle("Eliminar archivo").setMessage("¿Borrar "+name+" de la bóveda? El original de la galería no se toca.").setNegativeButton("Cancelar",null).setPositiveButton("Eliminar",(d,w)->{JSONObject item=findFile(id);if(item!=null)new File(new File(getFilesDir(),"vault"),id+".enc").delete();JSONArray old=readFiles(),next=new JSONArray();for(int i=0;i<old.length();i++){JSONObject o=old.optJSONObject(i);if(o!=null&&!id.equals(o.optString("id")))next.put(o);}writeFiles(next);refreshFileList(count);}).show();}
    private void confirmWipe(){new AlertDialog.Builder(this).setTitle("Borrar bóveda").setMessage("Se eliminarán todos los archivos cifrados y el PIN. Esta acción no se puede deshacer. Los originales de la galería no se borran.").setNegativeButton("Cancelar",null).setPositiveButton("Borrar todo",(d,w)->wipeVault()).show();}
    private void wipeVault(){try{fileKey=null;isUnlocked=false;File dir=new File(getFilesDir(),"vault");deleteTree(dir);KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);if(ks.containsAlias(KS_ALIAS))ks.deleteEntry(KS_ALIAS);getPreferences(MODE_PRIVATE).edit().clear().apply();clearPreviewCache();buildAuthScreen();toast("Bóveda eliminada.");}catch(Exception e){toast("No se pudo completar el borrado.");}}
    private void deleteTree(File f){if(f==null||!f.exists())return;if(f.isDirectory()){File[] children=f.listFiles();if(children!=null)for(File c:children)deleteTree(c);}f.delete();}
    private void lockVault(){handler.removeCallbacks(delayedLock);if(!hasPin())return;isUnlocked=false;fileKey=null;clearPreviewCache();buildAuthScreen();}
    private void clearPreviewCache(){File d=new File(getCacheDir(),"vault_preview");deleteTree(d);}
    private void hideKeyboard(){try{((InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(pinField.getWindowToken(),0);}catch(Exception ignored){}}
    private void toast(String msg){Toast.makeText(this,msg,Toast.LENGTH_LONG).show();}
    @Override protected void onStop(){super.onStop();if(isUnlocked&&!pickerOpen)handler.postDelayed(delayedLock,30000);}
    @Override protected void onStart(){super.onStart();handler.removeCallbacks(delayedLock);}
    @Override protected void onDestroy(){handler.removeCallbacks(delayedLock);super.onDestroy();}

    private static class DialView extends View {
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);private float angle=0,scale=1f,alpha=1f;private int state=0;private Runnable done;private final Handler h=new Handler(Looper.getMainLooper());
        DialView(Context c){super(c);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
        private int dp(float value){return (int)(value*getResources().getDisplayMetrics().density+0.5f);}
        void playError(){state=1;invalidate();h.postDelayed(()->{state=0;invalidate();},900);}
        void playSuccess(Runnable r){state=2;done=r;angle=0;scale=.78f;invalidate();post(new Runnable(){long start=System.currentTimeMillis();public void run(){float t=Math.min(1f,(System.currentTimeMillis()-start)/700f);angle=720*t;scale=.78f+.22f*t;invalidate();if(t<1f)postDelayed(this,16);else{postDelayed(()->{state=0;invalidate();if(done!=null){Runnable x=done;done=null;x.run();}},180);}}});}
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);
            float cx=getWidth()/2f,cy=getHeight()/2f-dp(5);
            float r=Math.min(dp(83),Math.min(getWidth()/2f-dp(8),getHeight()/2f-dp(31)));
            if(r<dp(48))r=dp(48);
            canvas.save();
            if(state==1){float shake=(float)Math.sin(System.currentTimeMillis()*.08)*dp(5);canvas.translate(shake,0);postInvalidateDelayed(16);}
            canvas.scale(scale,scale,cx,cy);
            // Deeply inset steel housing, concentric machined rings and metallic bevels.
            p.setStyle(Paint.Style.FILL);p.setColor(Color.rgb(8,13,20));p.setShadowLayer(dp(12),0,dp(5),0x99000000);canvas.drawCircle(cx,cy,r+dp(8),p);p.clearShadowLayer();
            p.setShader(new android.graphics.RadialGradient(cx-r*.22f,cy-r*.28f,r*1.6f,new int[]{Color.rgb(75,87,98),Color.rgb(35,45,56),Color.rgb(12,18,26)},null,android.graphics.Shader.TileMode.CLAMP));canvas.drawCircle(cx,cy,r+dp(6),p);p.setShader(null);
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(2));p.setColor(Color.rgb(116,128,137));canvas.drawCircle(cx,cy,r+dp(5),p);
            p.setStrokeWidth(dp(1));p.setColor(Color.rgb(24,32,41));canvas.drawCircle(cx,cy,r+dp(1),p);
            p.setStyle(Paint.Style.FILL);
            for(int i=0;i<8;i++){double a=Math.toRadians(i*45-90);float bx=(float)(cx+Math.cos(a)*(r+dp(5))),by=(float)(cy+Math.sin(a)*(r+dp(5)));p.setColor(i%2==0?Color.rgb(157,165,165):Color.rgb(47,56,65));canvas.drawCircle(bx,by,dp(3),p);p.setColor(Color.rgb(30,36,42));canvas.drawCircle(bx,by,dp(1),p);}
            p.setShader(new android.graphics.RadialGradient(cx-r*.2f,cy-r*.26f,r*1.5f,new int[]{Color.rgb(57,68,78),Color.rgb(31,41,52),Color.rgb(16,23,32)},null,android.graphics.Shader.TileMode.CLAMP));canvas.drawCircle(cx,cy,r-dp(5),p);p.setShader(null);
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(1.2f));p.setColor(Color.rgb(111,124,132));canvas.drawCircle(cx,cy,r-dp(5),p);p.setColor(Color.rgb(12,18,25));canvas.drawCircle(cx,cy,r-dp(11),p);
            // Machined tick marks around the steel rim.
            for(int i=0;i<60;i++){double a=Math.toRadians(i*6-90);float outer=r-dp(13);float inner=outer-dp(i%5==0?8:3);p.setStrokeWidth(dp(i%5==0?1.4f:.7f));p.setColor(i%5==0?Color.rgb(213,186,130):Color.rgb(105,119,129));canvas.drawLine((float)(cx+Math.cos(a)*inner),(float)(cy+Math.sin(a)*inner),(float)(cx+Math.cos(a)*outer),(float)(cy+Math.sin(a)*outer),p);}
            float ring=r*.69f;
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(5));p.setColor(state==1?Color.rgb(255,91,112):Color.rgb(127,105,64));canvas.drawArc(cx-ring,cy-ring,cx+ring,cy+ring,-91,359,false,p);
            p.setStrokeWidth(dp(2));p.setColor(state==1?Color.rgb(255,123,139):Color.rgb(232,197,126));canvas.drawArc(cx-ring+dp(3),cy-ring+dp(3),cx+ring-dp(3),cy+ring-dp(3),-94+(state==1?18:0),104,false,p);
            if(state!=2){for(int i=0;i<10;i++){double a=Math.toRadians(i*36+angle-90);float x=(float)(cx+Math.cos(a)*ring),y=(float)(cy+Math.sin(a)*ring);p.setStyle(Paint.Style.FILL);p.setColor(i%2==0?Color.rgb(233,198,126):Color.rgb(112,87,50));canvas.drawCircle(x,y,dp(3.2f),p);p.setColor(Color.rgb(255,226,160));canvas.drawCircle(x-dp(1),y-dp(1),dp(1),p);}}
            float cr=r*.48f;p.setStyle(Paint.Style.FILL);p.setShader(new android.graphics.RadialGradient(cx-cr*.3f,cy-cr*.35f,cr*1.8f,new int[]{Color.rgb(72,86,96),Color.rgb(27,38,49),Color.rgb(10,16,24)},null,android.graphics.Shader.TileMode.CLAMP));canvas.drawCircle(cx,cy,cr,p);p.setShader(null);
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(2));p.setColor(state==1?Color.rgb(222,83,105):Color.rgb(207,173,110));canvas.drawCircle(cx,cy,cr,p);p.setStrokeWidth(dp(1));p.setColor(Color.rgb(102,116,125));canvas.drawCircle(cx,cy,cr-dp(5),p);
            // Safe-door handle spokes and central lock / verified mark.
            p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeWidth(dp(4));p.setColor(Color.rgb(150,160,164));
            for(int i=0;i<3;i++){double a=Math.toRadians(i*120+angle*.15-90);float x1=(float)(cx+Math.cos(a)*cr*.60),y1=(float)(cy+Math.sin(a)*cr*.60),x2=(float)(cx+Math.cos(a)*cr*.86),y2=(float)(cy+Math.sin(a)*cr*.86);canvas.drawLine(x1,y1,x2,y2,p);p.setStyle(Paint.Style.FILL);p.setColor(Color.rgb(74,84,91));canvas.drawCircle(x2,y2,dp(4),p);p.setStyle(Paint.Style.STROKE);p.setColor(Color.rgb(170,177,175));canvas.drawCircle(x2,y2,dp(4),p);p.setColor(Color.rgb(150,160,164));}
            p.setStyle(Paint.Style.FILL);p.setColor(state==2?Color.rgb(101,220,178):(state==1?Color.rgb(58,31,38):Color.rgb(20,32,43)));canvas.drawCircle(cx,cy,cr*.42f,p);
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(1.3f));p.setColor(state==2?Color.rgb(192,255,227):Color.rgb(136,151,159));canvas.drawCircle(cx,cy,cr*.42f,p);
            if(state==2){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(4));p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);p.setColor(Color.rgb(9,43,35));Path path=new Path();path.moveTo(cx-cr*.20f,cy);path.lineTo(cx-cr*.04f,cy+cr*.16f);path.lineTo(cx+cr*.24f,cy-cr*.17f);canvas.drawPath(path,p);}else drawLock(canvas,cx,cy,cr*.50f,state==1?Color.rgb(255,132,146):Color.rgb(226,202,155));
            canvas.restore();
            p.setStyle(Paint.Style.FILL);p.setTextAlign(Paint.Align.CENTER);p.setTypeface(Typeface.create("sans-serif-condensed",Typeface.BOLD));p.setTextSize(dp(9));p.setColor(state==2?Color.rgb(174,246,211):(state==1?Color.rgb(255,138,151):Color.rgb(197,204,208)));
            canvas.drawText(state==2?"IDENTIDAD VERIFICADA":(state==1?"CÓDIGO RECHAZADO":"ESPERANDO TU PIN"),cx,cy+r+dp(20),p);
        }
        private void drawLock(Canvas c,float x,float y,float r,int color){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(3));p.setStrokeCap(Paint.Cap.ROUND);p.setColor(color);c.drawArc(x-r*.3f,y-r*.55f,x+r*.3f,y+r*.1f,180,180,false,p);c.drawRoundRect(x-r*.38f,y-r*.08f,x+r*.38f,y+r*.48f,dp(5),dp(5),p);p.setStyle(Paint.Style.FILL);c.drawCircle(x,y+r*.12f,dp(2),p);c.drawRect(x-dp(1),y+r*.12f,x+dp(1),y+r*.28f,p);}
    }
}
