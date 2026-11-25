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
    private static final int COLOR_PANEL = Color.rgb(17, 25, 37);
    private static final int COLOR_LINE = Color.rgb(41, 57, 73);
    private static final int COLOR_MINT = Color.rgb(101, 240, 214);
    private static final int COLOR_MUTED = Color.rgb(146, 162, 180);
    private final SecureRandom random = new SecureRandom();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable delayedLock = this::lockVault;
    private SecretKey fileKey;
    private boolean pickerOpen = false;
    private boolean isUnlocked = false;
    private LinearLayout root;
    private DialView dial;
    private EditText pinField, confirmField;
    private TextView message, dialCaption;
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
        b.setTextColor(primary?Color.rgb(7,32,31):Color.rgb(224,235,242)); b.setBackground(rounded(primary?COLOR_MINT:Color.rgb(22,32,45),primary?Color.rgb(140,255,236):COLOR_LINE,14));
        b.setMinHeight(dp(48)); b.setStateListAnimator(null); return b;
    }
    private void buildBase() {
        root=column(); root.setBackgroundColor(COLOR_BG); pad(root,20,22,20,24);
        ScrollView outer=new ScrollView(this); outer.setFillViewport(true); outer.setClipToPadding(false);
        outer.addView(root,new ScrollView.LayoutParams(-1,-2)); setContentView(outer);
    }
    private void buildBrand() {
        LinearLayout header=row();
        TextView icon=text("◆",19,Color.rgb(8,36,38),true); icon.setGravity(Gravity.CENTER); icon.setBackground(rounded(COLOR_MINT,0,13)); header.addView(icon,lp(40,40));
        LinearLayout titles=column(); pad(titles,10,0,0,0); TextView name=text("LA CLAVE",12,Color.rgb(188,202,215),true); name.setLetterSpacing(.16f); titles.addView(name);
        TextView sub=text("BÓVEDA PRIVADA",9,Color.rgb(116,134,154),true); sub.setLetterSpacing(.2f); titles.addView(sub); header.addView(titles,new LinearLayout.LayoutParams(0,-2,1));
        TextView secure=text("●  SOLO EN ESTE DISPOSITIVO",9,COLOR_MUTED,true); header.addView(secure); root.addView(header,lp(-1,46));
    }
    private void buildAuthScreen() {
        isUnlocked=false; fileKey=null; buildBase(); buildBrand();
        TextView eyebrow=text(hasPin()?"ACCESO CIFRADO":"PRIMER USO",10,Color.rgb(140,164,185),true); eyebrow.setLetterSpacing(.22f); eyebrow.setGravity(Gravity.CENTER); LinearLayout.LayoutParams ep=lp(-1,24);ep.topMargin=dp(24);root.addView(eyebrow,ep);
        TextView title=text(hasPin()?"Abrí tu bóveda":"Tu bóveda, bajo llave",27,Color.rgb(245,250,253),true); title.setGravity(Gravity.CENTER); LinearLayout.LayoutParams tp=lp(-1,-2);tp.topMargin=dp(4);root.addView(title,tp);
        TextView intro=text(hasPin()?"Ingresá tu PIN para abrir tus archivos cifrados.":"Elegí un PIN. Tus fotos y videos se cifran en este dispositivo.",13,COLOR_MUTED,false); intro.setGravity(Gravity.CENTER); intro.setLineSpacing(dp(2),1f); LinearLayout.LayoutParams ip=lp(-1,-2);ip.topMargin=dp(8);ip.leftMargin=dp(6);ip.rightMargin=dp(6);root.addView(intro,ip);
        dial=new DialView(this); LinearLayout.LayoutParams dp=lp(-1,230);dp.topMargin=dp(6);root.addView(dial,dp);
        LinearLayout form=column(); pad(form,4,0,4,0);
        LinearLayout labels=row(); TextView lab=text(hasPin()?"PIN DE LA BÓVEDA":"CREÁ UN PIN",11,Color.rgb(199,212,224),true); lab.setLetterSpacing(.06f);labels.addView(lab,new LinearLayout.LayoutParams(0,-2,1));labels.addView(text("6 A 10 DÍGITOS",9,Color.rgb(128,147,168),false));form.addView(labels,lp(-1,25));
        pinField=pinInput("PIN de 6 a 10 dígitos"); LinearLayout.LayoutParams pp=lp(-1,54);pp.topMargin=dp(4);form.addView(pinField,pp);
        confirmField=pinInput("Repetí el PIN");LinearLayout.LayoutParams cp=lp(-1,49);cp.topMargin=dp(10);if(hasPin())confirmField.setVisibility(View.GONE);form.addView(confirmField,cp);
        actionButton=button(hasPin()?"Abrir bóveda":"Crear bóveda",true);LinearLayout.LayoutParams ap=lp(-1,50);ap.topMargin=dp(12);form.addView(actionButton,ap);actionButton.setOnClickListener(v->submitPin());
        message=text(hasPin()?"Ingresá el PIN que elegiste.":"No uses el mismo PIN de desbloqueo del teléfono.",12,COLOR_MUTED,false);message.setGravity(Gravity.CENTER);message.setMinHeight(dp(38));LinearLayout.LayoutParams mp=lp(-1,-2);mp.topMargin=dp(8);form.addView(message,mp);
        TextView caution=text("Fotos y videos se guardan cifrados dentro de la app. Los originales de la galería no se borran solos.",10,Color.rgb(101,119,139),false);caution.setGravity(Gravity.CENTER);caution.setLineSpacing(dp(2),1f);LinearLayout.LayoutParams np=lp(-1,-2);np.topMargin=dp(9);form.addView(caution,np);
        root.addView(form,lp(-1,-2));
    }
    private EditText pinInput(String hint){EditText e=new EditText(this);e.setSingleLine(true);e.setTextSize(21);e.setTextColor(Color.rgb(245,250,253));e.setHintTextColor(Color.rgb(59,75,93));e.setHint(hint);e.setGravity(Gravity.CENTER);e.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD);e.setTransformationMethod(PasswordTransformationMethod.getInstance());e.setLetterSpacing(.32f);e.setBackground(rounded(Color.rgb(10,17,27),COLOR_LINE,14));e.setPadding(dp(12),0,dp(12),0);e.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(10)});return e;}
    private void submitPin(){String p=pinField.getText().toString();if(!p.matches("[0-9]{6,10}")){setMessage("El PIN debe tener entre 6 y 10 números.",true);dial.playError();return;}if(!hasPin()){String c=confirmField.getText().toString();if(!p.equals(c)){setMessage("Los PIN no coinciden. Revisalos y volvé a probar.",true);dial.playError();return;}createPin(p);}else unlock(p);}
    private void setMessage(String s,boolean error){message.setText(s);message.setTextColor(error?Color.rgb(255,113,139):COLOR_MUTED);}
    private void createPin(String pin){try{ensureFileKey();byte[] salt=new byte[16];random.nextBytes(salt);byte[] hash=derive(pin,salt);getPreferences(MODE_PRIVATE).edit().putString(KEY_SALT,hex(salt)).putString(KEY_HASH,hex(hash)).apply();dialCaption.setText("BÓVEDA PREPARADA");dial.playSuccess(()->runOnUiThread(()->showVault()));setMessage("PIN creado. Preparando bóveda…",false);}catch(Exception e){setMessage("No pude preparar el almacenamiento seguro. Probá de nuevo.",true);}}
    private void unlock(String pin){try{byte[] salt=unhex(getPreferences(MODE_PRIVATE).getString(KEY_SALT,""));byte[] expected=unhex(getPreferences(MODE_PRIVATE).getString(KEY_HASH,""));byte[] actual=derive(pin,salt);if(!MessageDigest.isEqual(expected,actual)){dialCaption.setText("PIN INCORRECTO");dial.playError();setMessage("PIN incorrecto. Tus archivos siguen cifrados.",true);return;}ensureFileKey();dialCaption.setText("IDENTIDAD VERIFICADA");setMessage("PIN correcto. Abriendo la bóveda…",false);dial.playSuccess(()->runOnUiThread(()->showVault()));}catch(Exception e){setMessage("No pude verificar el PIN. Si reinstalaste la app, los datos cifrados no se pueden recuperar.",true);}}
    private boolean hasPin(){return getPreferences(MODE_PRIVATE).contains(KEY_HASH);}
    private byte[] derive(String pin,byte[] salt)throws Exception{PBEKeySpec spec=new PBEKeySpec(pin.toCharArray(),salt,PIN_ITERATIONS,256);try{return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();}finally{spec.clearPassword();}}
    private String hex(byte[] b){StringBuilder s=new StringBuilder();for(byte x:b)s.append(String.format(Locale.US,"%02x",x&255));return s.toString();}
    private byte[] unhex(String s){if(s==null||s.length()%2!=0)throw new IllegalArgumentException("bad hex");byte[] b=new byte[s.length()/2];for(int i=0;i<b.length;i++)b[i]=(byte)Integer.parseInt(s.substring(i*2,i*2+2),16);return b;}
    private SecretKey ensureFileKey()throws Exception{if(fileKey!=null)return fileKey;KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);if(ks.containsAlias(KS_ALIAS)){fileKey=(SecretKey)ks.getKey(KS_ALIAS,null);return fileKey;}KeyGenerator kg=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");kg.init(new KeyGenParameterSpec.Builder(KS_ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).setRandomizedEncryptionRequired(true).build());fileKey=kg.generateKey();return fileKey;}
    private void showVault(){hideKeyboard();isUnlocked=true;buildVaultScreen();}
    private void buildVaultScreen(){buildBase();buildBrand();LinearLayout top=row();LinearLayout tit=column();TextView h=text("Mi bóveda",27,Color.rgb(245,250,253),true);tit.addView(h);TextView desc=text("Tus archivos privados, en este dispositivo.",12,COLOR_MUTED,false);LinearLayout.LayoutParams dlp=lp(-1,-2);dlp.topMargin=dp(4);tit.addView(desc,dlp);top.addView(tit,new LinearLayout.LayoutParams(0,-2,1));fileCount=text("",10,Color.rgb(194,209,220),true);fileCount.setGravity(Gravity.CENTER);pad(fileCount,10,7,10,7);fileCount.setBackground(rounded(COLOR_PANEL,COLOR_LINE,11));top.addView(fileCount);root.addView(top,lp(-1,-2));
        LinearLayout actions=row();Button add=button("＋  Agregar fotos o videos",true);LinearLayout.LayoutParams alp=new LinearLayout.LayoutParams(0,dp(48),1);alp.topMargin=dp(18);actions.addView(add,alp);Button close=button("Cerrar",false);LinearLayout.LayoutParams clp=lp(86,48);clp.leftMargin=dp(8);clp.topMargin=dp(18);actions.addView(close,clp);root.addView(actions,lp(-1,-2));
        add.setOnClickListener(v->pickFiles());close.setOnClickListener(v->lockVault());
        TextView notice=text("Importa una copia cifrada. El original seguirá en Fotos/Galería hasta que lo borres vos.",11,Color.rgb(153,171,190),false);notice.setLineSpacing(dp(2),1f);pad(notice,12,10,12,10);notice.setBackground(rounded(Color.rgb(13,23,34),COLOR_LINE,12));LinearLayout.LayoutParams nlp=lp(-1,-2);nlp.topMargin=dp(15);root.addView(notice,nlp);
        vaultScroll=new ScrollView(this);vaultContent=column();LinearLayout.LayoutParams vslp=lp(-1,-2);vslp.topMargin=dp(12);root.addView(vaultScroll,vslp);vaultScroll.addView(vaultContent);
        Button wipe=button("Borrar bóveda y archivos cifrados",false);wipe.setTextColor(Color.rgb(255,133,153));wipe.setBackground(rounded(Color.rgb(37,22,30),Color.rgb(87,48,61),13));LinearLayout.LayoutParams wlp=lp(-1,46);wlp.topMargin=dp(14);root.addView(wipe,wlp);wipe.setOnClickListener(v->confirmWipe());
        TextView footer=text("✦  SESIÓN PRIVADA  ·  SE CIERRA AL SALIR",9,Color.rgb(103,120,140),true);footer.setGravity(Gravity.CENTER);LinearLayout.LayoutParams flp=lp(-1,38);root.addView(footer,flp);refreshFileList(fileCount);}
    private void refreshFileList(TextView count){JSONArray files=readFiles();count.setText(files.length()+ (files.length()==1?" ARCHIVO":" ARCHIVOS"));vaultContent.removeAllViews();if(files.length()==0){TextView empty=text("Tu bóveda está vacía\nAgregá fotos o videos con el botón de arriba.",13,COLOR_MUTED,false);empty.setGravity(Gravity.CENTER);pad(empty,20,26,20,26);empty.setBackground(rounded(Color.rgb(13,20,30),COLOR_LINE,16));vaultContent.addView(empty,lp(-1,-2));return;}for(int i=0;i<files.length();i++){JSONObject item=files.optJSONObject(i);if(item==null)continue;LinearLayout card=row();card.setBackground(rounded(COLOR_PANEL,COLOR_LINE,15));pad(card,13,11,10,11);LinearLayout info=column();TextView name=text(item.optString("name","Archivo"),13,Color.rgb(227,237,244),true);name.setMaxLines(1);name.setEllipsize(android.text.TextUtils.TruncateAt.END);info.addView(name);TextView sub=text(item.optString("kind","Archivo")+" · "+formatSize(item.optLong("size",0))+" cifrado",10,COLOR_MUTED,false);LinearLayout.LayoutParams slp=lp(-1,-2);slp.topMargin=dp(5);info.addView(sub,slp);card.addView(info,new LinearLayout.LayoutParams(0,-2,1));Button open=smallButton("Abrir");card.addView(open,lp(62,40));String id=item.optString("id");open.setOnClickListener(v->openFile(id));Button delete=smallButton("×");LinearLayout.LayoutParams delp=lp(42,40);delp.leftMargin=dp(6);card.addView(delete,delp);delete.setOnClickListener(v->confirmDelete(id,item.optString("name","archivo"),count));LinearLayout.LayoutParams cp=lp(-1,-2);cp.bottomMargin=dp(9);vaultContent.addView(card,cp);}}
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
        void playError(){state=1;invalidate();h.postDelayed(()->{state=0;invalidate();},900);}
        void playSuccess(Runnable r){state=2;done=r;angle=0;scale=.78f;invalidate();post(new Runnable(){long start=System.currentTimeMillis();public void run(){float t=Math.min(1f,(System.currentTimeMillis()-start)/700f);angle=720*t;scale=.78f+.22f*t;invalidate();if(t<1f)postDelayed(this,16);else{postDelayed(()->{state=0;invalidate();if(done!=null){Runnable x=done;done=null;x.run();}},180);}}});}
        @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);float cx=getWidth()/2f,cy=getHeight()/2f-3;float r=Math.min(dp(88),Math.min(getWidth()/2f-dp(10),getHeight()/2f-dp(27)));if(r<dp(50))r=dp(50);canvas.save();if(state==1){float shake=(float)Math.sin(System.currentTimeMillis()*.08)*dp(5);canvas.translate(shake,0);postInvalidateDelayed(16);}canvas.scale(scale,scale,cx,cy);
            p.setStyle(Paint.Style.FILL);p.setColor(Color.rgb(16,27,41));p.setShadowLayer(dp(18),0,0,0x553de8d3);canvas.drawCircle(cx,cy,r,p);p.clearShadowLayer();p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(1.5f));p.setColor(state==1?Color.rgb(255,96,125):0x8865F0D6);canvas.drawCircle(cx,cy,r,p);p.setStrokeWidth(dp(1));p.setColor(0x447FFFE7);canvas.drawCircle(cx,cy,r-dp(8),p);
            if(state!=2){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(4));p.setStrokeCap(Paint.Cap.ROUND);p.setColor(state==1?Color.rgb(255,110,137):COLOR_MINT);canvas.drawArc(cx-r+dp(1),cy-r+dp(1),cx+r-dp(1),cy+r-dp(1),-94+(state==1?20:0),102,false,p);for(int i=0;i<8;i++){double a=Math.toRadians(i*45+angle-90);float x=(float)(cx+Math.cos(a)*(r-dp(3))),y=(float)(cy+Math.sin(a)*(r-dp(3)));p.setStyle(Paint.Style.FILL);p.setColor(state==1?Color.rgb(255,110,137):COLOR_MINT);canvas.drawCircle(x,y,dp(3.5f),p);} }
            float cr=r*.66f;p.setStyle(Paint.Style.FILL);p.setColor(state==2?Color.rgb(88,226,196):(state==1?Color.rgb(44,30,43):Color.rgb(25,47,59)));canvas.drawCircle(cx,cy,cr,p);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(1));p.setColor(state==2?0xffc2fff2:0x557fffe7);canvas.drawCircle(cx,cy,cr,p);
            if(state==2){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(5));p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);p.setColor(Color.rgb(6,34,30));Path path=new Path();path.moveTo(cx-cr*.36f,cy);path.lineTo(cx-cr*.08f,cy+cr*.28f);path.lineTo(cx+cr*.42f,cy-cr*.27f);canvas.drawPath(path,p);}else drawLock(canvas,cx,cy,cr,state==1?Color.rgb(255,125,149):Color.rgb(149,248,232));
            canvas.restore();p.setStyle(Paint.Style.FILL);p.setTextAlign(Paint.Align.CENTER);p.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));p.setTextSize(dp(10));p.setColor(state==2?Color.rgb(175,255,239):(state==1?Color.rgb(255,135,155):Color.rgb(145,165,185)));canvas.drawText(state==2?"IDENTIDAD VERIFICADA":(state==1?"PIN INCORRECTO":"ESPERANDO TU PIN"),cx,cy+r+dp(22),p);}
        private void drawLock(Canvas c,float x,float y,float r,int color){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(3));p.setStrokeCap(Paint.Cap.ROUND);p.setColor(color);c.drawArc(x-r*.3f,y-r*.55f,x+r*.3f,y+r*.1f,180,180,false,p);c.drawRoundRect(x-r*.38f,y-r*.08f,x+r*.38f,y+r*.48f,dp(5),dp(5),p);p.setStyle(Paint.Style.FILL);c.drawCircle(x,y+r*.12f,dp(2),p);c.drawRect(x-dp(1),y+r*.12f,x+dp(1),y+r*.28f,p);}
    }
}
