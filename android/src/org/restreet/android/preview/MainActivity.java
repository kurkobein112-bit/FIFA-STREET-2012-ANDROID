package org.restreet.android.preview;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.StatFs;
import android.view.View;
import android.widget.*;
import java.io.*;
import java.nio.channels.FileChannel;
import java.util.List;

public final class MainActivity extends Activity {
    private static final int PICK_ISO = 42;
    private TextView status;
    private ProgressBar progress;
    private Button importButton, playButton, cancelButton;
    private Spinner gpu, controls;
    private volatile boolean cancelled;
    private volatile boolean importing;
    private Thread worker;
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private File data() { return new File(getFilesDir(), "GameData"); }
    private boolean gameReady() {
        return new File(data(), ".restreet-import-complete").isFile()
                && new File(data(), "default.xex").isFile() && new File(data(), "fifadllzf.xex.dll").isFile();
    }
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().setStatusBarColor(0xFF121818); getWindow().setNavigationBarColor(0xFF121818);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.HORIZONTAL); root.setBackgroundColor(0xFF121818);
        ImageView cover = new ImageView(this);
        cover.setImageResource(getResources().getIdentifier("cover", "drawable", getPackageName()));
        cover.setScaleType(ImageView.ScaleType.CENTER_CROP); cover.setContentDescription("Portada FIFA Street con Messi");
        root.addView(cover, new LinearLayout.LayoutParams(0, -1, 0.40f));
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
        LinearLayout content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(dp(24),dp(20),dp(24),dp(24));
        scroll.addView(content); root.addView(scroll,new LinearLayout.LayoutParams(0,-1,0.60f));
        TextView title = text("FIFA STREET", 30, 0xFFF4EEE0); title.setTypeface(Typeface.create("sans-serif-condensed",Typeface.BOLD)); content.addView(title);
        content.addView(text("0.1 · Android", 14, 0xFFD6B477));
        TextView guide = text("Selecciona tu ISO: (FIFA Street 2012 · Xbox 360 · región libre · Media ID 78AA9072)",15,0xFFDEE4DF);
        guide.setPadding(0,dp(14),0,dp(8)); content.addView(guide);
        status = text("",14,0xFFDEE4DF); status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE); content.addView(status);
        progress = new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal); progress.setVisibility(View.GONE); content.addView(progress,new LinearLayout.LayoutParams(-1,dp(12)));
        importButton = new Button(this); importButton.setText("IMPORTAR Y EXTRAER ISO"); importButton.setMinHeight(dp(48));
        importButton.setOnClickListener(v -> {
            Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");
            try { startActivityForResult(picker,PICK_ISO); }
            catch (android.content.ActivityNotFoundException e) { status.setText("No hay un selector de archivos disponible en este teléfono."); }
        }); content.addView(importButton);
        cancelButton = new Button(this); cancelButton.setText("CANCELAR EXTRACCIÓN"); cancelButton.setVisibility(View.GONE);
        cancelButton.setOnClickListener(v -> { cancelled=true; status.setText("Cancelando y retirando la extracción incompleta…"); }); content.addView(cancelButton);
        content.addView(label("Compatibilidad de GPU"));
        gpu = new Spinner(this); ArrayAdapter<GpuProfile> profiles = new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,GpuProfile.values());
        profiles.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);gpu.setAdapter(profiles);gpu.setContentDescription("Compatibilidad de GPU");
        gpu.setSelection(GpuProfile.fromName(getPreferences(MODE_PRIVATE).getString("gpu_profile","MALI")).ordinal());content.addView(gpu,new LinearLayout.LayoutParams(-1,dp(48)));
        content.addView(label("Estilo de controles"));
        controls = new Spinner(this);ArrayAdapter<String> styles = new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,TouchControls.STYLES);
        styles.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);controls.setAdapter(styles);controls.setContentDescription("Estilo de controles táctiles");
        controls.setSelection(Math.max(0,Math.min(2,getPreferences(MODE_PRIVATE).getInt("controls",1))));content.addView(controls,new LinearLayout.LayoutParams(-1,dp(48)));
        content.addView(text("Mando Bluetooth o USB: conéctalo antes o durante el juego. Los controles táctiles se ocultan al detectarlo.",13,0xFFBDCFC5));
        playButton=new Button(this);playButton.setText("JUGAR");playButton.setTextColor(Color.WHITE);playButton.setBackgroundTintList(new android.content.res.ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{android.R.attr.state_pressed},new int[]{}},new int[]{0xFF38444D,0xFF15334D,0xFF24577D}));playButton.setMinHeight(dp(52));
        LinearLayout.LayoutParams playLayout=new LinearLayout.LayoutParams(-1,dp(52));playLayout.topMargin=dp(16);content.addView(playButton,playLayout);
        playButton.setOnClickListener(v -> {
            if (!gameReady() || importing) return;
            GpuProfile selected=(GpuProfile)gpu.getSelectedItem();int style=controls.getSelectedItemPosition();
            getPreferences(MODE_PRIVATE).edit().putString("gpu_profile",selected.name()).putInt("controls",style).apply();
            startActivity(new Intent(this,GameActivity.class).putExtra("gpu_profile",selected.name()).putExtra("controls",style));
        });
        content.addView(text("Versión experimental. Hay fallos gráficos y rendimiento limitado. Mali y Adreno no tienen compatibilidad completa.\nISO compatible: Title ID 4541097B · Media ID 78AA9072. No incluye el juego.",12,0xFFBDCFC5));
        setContentView(root);refresh();
    }
    private TextView text(String value,int size,int color) { TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);return t; }
    private TextView label(String value) { TextView t=text(value,15,0xFFF4EEE0);t.setPadding(0,dp(16),0,dp(2));return t; }
    private void refresh() {
        boolean ready=gameReady();playButton.setEnabled(ready&&!importing);importButton.setEnabled(!importing&&!data().exists());
        gpu.setEnabled(!importing);controls.setEnabled(!importing);
        if (!importing) status.setText(ready?"Juego importado.":data().exists()?"GameData está incompleto. Se conservó sin sobrescribir; revisa el almacenamiento de la app.":"");
    }
    @Override protected void onActivityResult(int request,int result,Intent intent) {
        super.onActivityResult(request,result,intent);
        if(request!=PICK_ISO||result!=RESULT_OK||intent==null||intent.getData()==null||importing)return;
        final Uri uri=intent.getData(); cancelled=false;importing=true;refresh();
        progress.setVisibility(View.VISIBLE);progress.setIndeterminate(true);cancelButton.setVisibility(View.VISIBLE);getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        worker=new Thread(() -> importIso(uri),"ISO extractor");worker.start();
    }
    private void importIso(Uri uri) {
        File staging=null, spool=null;
        String result;
        try {
            // A provider may expose a pipe. Cache only that case; seekable local ISOs need no duplicate.
            ParcelFileDescriptor descriptor=getContentResolver().openFileDescriptor(uri,"r");
            if(descriptor==null)throw new IOException("No se pudo abrir la ISO.");
            try(ParcelFileDescriptor.AutoCloseInputStream stream=new ParcelFileDescriptor.AutoCloseInputStream(descriptor)) {
                FileChannel channel=stream.getChannel();boolean seekable;
                try { seekable=channel.size()>0;channel.position(0); } catch(IOException e) { seekable=false; }
                if(seekable) {
                    staging=extract(channel);
                } else {
                    spool=File.createTempFile("iso-import-",".iso",getCacheDir());
                    try(FileOutputStream copy=new FileOutputStream(spool)) {
                        byte[] chunk=new byte[1024*1024];int count;
                        while((count=stream.read(chunk))!=-1) {
                            checkCancel();
                            if(new StatFs(getFilesDir().getPath()).getAvailableBytes()<count+64L*1024*1024)throw new IOException("No hay espacio para copiar la ISO. Selecciona un archivo guardado en el teléfono.");
                            copy.write(chunk,0,count);
                        }
                    }
                    try(FileInputStream local=new FileInputStream(spool)) { staging=extract(local.getChannel()); }
                }
            }
            checkCancel();
            if(data().exists()||!staging.renameTo(data()))throw new IOException("No se pudo guardar GameData. Los datos anteriores se conservan.");
            staging=null;result="Juego importado y verificado. Ya puedes jugar.";
        } catch(Exception e) { result="Importación detenida: "+(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage()); }
        finally {
            if(staging!=null)removeStaging(staging);
            if(spool!=null&&!spool.delete())android.util.Log.w("FifaIso","No se pudo retirar la copia temporal de ISO");
        }
        final String message=result;
        runOnUiThread(() -> {
            importing=false;
            if(isDestroyed())return;
            progress.setVisibility(View.GONE);cancelButton.setVisibility(View.GONE);getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            refresh();status.setText(message);
        });
    }
    private void checkCancel() throws IOException { if(cancelled||Thread.currentThread().isInterrupted())throw new IOException("Cancelado. Tu ISO original no se modificó."); }
    private File extract(FileChannel channel) throws IOException {
        runOnUiThread(() -> status.setText("Verificando la ISO y su edición…"));
        IsoExtractor image=new IsoExtractor(channel);List<IsoExtractor.Entry> entries=image.inventory();checkCancel();image.validateGame(entries);checkCancel();
        long total=IsoExtractor.totalBytes(entries);
        if(new StatFs(getFilesDir().getPath()).getAvailableBytes()<total+64L*1024*1024)throw new IOException("No hay espacio. Necesitas "+((total+1048575)/1048576)+" MiB libres para extraer el juego.");
        File stage=new File(getFilesDir(),"iso-stage-"+java.util.UUID.randomUUID());
        final int[] last={-1};
        try {
            image.extract(entries,stage,(done,all) -> {
                checkCancel();int percent=all==0?100:(int)(done*100/all);
                if(percent!=last[0]) {last[0]=percent;runOnUiThread(() -> {if(!isDestroyed()){progress.setIndeterminate(false);progress.setProgress(percent);status.setText("Extrayendo ISO · "+percent+" %");}});}
            });
            try(FileOutputStream marker=new FileOutputStream(new File(stage,".restreet-import-complete"))) {marker.write("4541097B/78AA9072".getBytes(java.nio.charset.StandardCharsets.US_ASCII));}
            return stage;
        } catch(IOException|RuntimeException error) { removeStaging(stage);throw error; }
    }
    private void removeStaging(File stage) {
        try {
            File root=getFilesDir().getCanonicalFile();File target=stage.getCanonicalFile();
            if(!root.equals(target.getParentFile())||!target.getName().startsWith("iso-stage-"))return;
            removeOwned(target,root.getPath()+File.separator);
        } catch(IOException e) {android.util.Log.w("FifaIso","No se pudo retirar la extracción incompleta",e);}
    }
    private void removeOwned(File file,String prefix) throws IOException {
        if(!file.getCanonicalPath().startsWith(prefix))throw new IOException("Ruta temporal fuera de la app.");
        File[] children=file.listFiles();if(children!=null)for(File child:children)removeOwned(child,prefix);
        if(file.exists()&&!file.delete())throw new IOException("No se pudo retirar un archivo temporal.");
    }
    @Override protected void onDestroy() {cancelled=true;if(worker!=null)worker.interrupt();super.onDestroy();}
}
