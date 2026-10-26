package org.restreet.android.preview;

import org.libsdl.app.SDLActivity;
import java.io.File;

public final class GameActivity extends SDLActivity {
    private TouchControls touch;
    @Override protected void onCreate(android.os.Bundle saved) {
        try { android.system.Os.setenv("REX_NATIVE_LIBC_HOOKS","1",true); }
        catch(android.system.ErrnoException e) {throw new IllegalStateException(e);}
        super.onCreate(saved);
        if(mLayout!=null) {
            setControls(getIntent().getIntExtra("controls",1));
            android.widget.ImageButton switcher=new android.widget.ImageButton(this);
            switcher.setImageResource(android.R.drawable.ic_menu_preferences);switcher.setImageTintList(android.content.res.ColorStateList.valueOf(0xFFEAEDE7));
            switcher.setContentDescription("Controles: estilo, tamaño y transparencia");
            android.graphics.drawable.GradientDrawable background=new android.graphics.drawable.GradientDrawable();background.setShape(android.graphics.drawable.GradientDrawable.OVAL);background.setColor(0x55202523);switcher.setBackground(background);
            int target=Math.round(48*getResources().getDisplayMetrics().density);
            int inset=Math.round(14*getResources().getDisplayMetrics().density);switcher.setPadding(inset,inset,inset,inset);
            android.widget.RelativeLayout.LayoutParams position=new android.widget.RelativeLayout.LayoutParams(target,target);
            position.addRule(android.widget.RelativeLayout.CENTER_HORIZONTAL);position.addRule(android.widget.RelativeLayout.ALIGN_PARENT_TOP);
            mLayout.addView(switcher,position);
            switcher.setOnClickListener(v->{
                android.widget.PopupMenu menu=new android.widget.PopupMenu(this,switcher);
                for(int i=0;i<TouchControls.STYLES.length;i++)menu.getMenu().add(0,i,i,TouchControls.STYLES[i]);
                menu.getMenu().add(0,3,3,"Tamaño, transparencia y botones");
                menu.setOnMenuItemClickListener(item->{
                    if(item.getItemId()==3)showControlSettings();
                    else {setControls(item.getItemId());switcher.bringToFront();}
                    return true;
                });
                menu.show();
            });
        }
    }
    private void setControls(int style) {
        if(touch!=null)mLayout.removeView(touch);
        touch=new TouchControls(this,style);
        mLayout.addView(touch,new android.widget.RelativeLayout.LayoutParams(-1,-1));
    }
    private void showControlSettings() {
        touch.releaseAll();
        android.content.SharedPreferences prefs=TouchControls.options(this);
        android.widget.LinearLayout panel=new android.widget.LinearLayout(this);panel.setOrientation(android.widget.LinearLayout.VERTICAL);
        int gap=Math.round(20*getResources().getDisplayMetrics().density);panel.setPadding(gap,gap,gap,0);
        slider(panel,"Tamaño",50,110,prefs.getInt("size",70),value->{prefs.edit().putInt("size",value).apply();touch.rebuild();});
        slider(panel,"Transparencia",0,80,100-prefs.getInt("opacity",65),value->{prefs.edit().putInt("opacity",100-value).apply();touch.rebuild();});
        toggle(panel,"Mostrar cruceta",prefs.getBoolean("dpad",false),checked->{prefs.edit().putBoolean("dpad",checked).apply();touch.rebuild();});
        String stickKey="right_stick_"+touch.getStyle();
        toggle(panel,"Mostrar stick derecho (regates)",prefs.getBoolean(stickKey,touch.getStyle()==1),checked->{prefs.edit().putBoolean(stickKey,checked).apply();touch.rebuild();});
        toggle(panel,"Mostrar botones avanzados (LB, RB, L3, R3)",prefs.getBoolean("advanced",false),checked->{prefs.edit().putBoolean("advanced",checked).apply();touch.rebuild();});
        android.widget.ScrollView scroll=new android.widget.ScrollView(this);scroll.addView(panel);
        new android.app.AlertDialog.Builder(this).setTitle("Controles táctiles").setView(scroll).setPositiveButton("LISTO",null)
            .setNeutralButton("RESTABLECER",(dialog,which)->{prefs.edit().clear().apply();touch.rebuild();}).show();
    }
    private interface IntChange {void changed(int value);}
    private interface BoolChange {void changed(boolean value);}
    private void slider(android.widget.LinearLayout parent,String name,int min,int max,int value,IntChange change) {
        android.widget.TextView label=new android.widget.TextView(this);label.setText(name+": "+value+" %");parent.addView(label);
        android.widget.SeekBar slider=new android.widget.SeekBar(this);slider.setContentDescription(name);slider.setMax(max-min);slider.setProgress(value-min);
        parent.addView(slider,new android.widget.LinearLayout.LayoutParams(-1,Math.round(48*getResources().getDisplayMetrics().density)));
        slider.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener(){
            @Override public void onProgressChanged(android.widget.SeekBar bar,int progress,boolean fromUser){if(fromUser){int selected=progress+min;label.setText(name+": "+selected+" %");change.changed(selected);}}
            @Override public void onStartTrackingTouch(android.widget.SeekBar bar){touch.releaseAll();}
            @Override public void onStopTrackingTouch(android.widget.SeekBar bar){}
        });
    }
    private void toggle(android.widget.LinearLayout parent,String name,boolean value,BoolChange change) {
        android.widget.CheckBox box=new android.widget.CheckBox(this);box.setText(name);box.setChecked(value);parent.addView(box);
        box.setOnCheckedChangeListener((button,checked)->change.changed(checked));
    }
    @Override protected void onPause() {if(touch!=null)touch.releaseAll();super.onPause();}
    @Override protected String[] getLibraries() {return new String[]{"c++_shared","rexruntime","fifastreet"};}
    @Override protected String[] getArguments() {
        GpuProfile gpu=GpuProfile.fromName(getIntent().getStringExtra("gpu_profile"));
        return new String[]{
            "--game_data_root",new File(getFilesDir(),"GameData").getAbsolutePath(),
            "--user_data_root",new File(getFilesDir(),"UserData").getAbsolutePath(),
            "--video_mode_width=1280","--video_mode_height=720",
            gpu.vertexStoresArgument(),"--no-vulkan_require_geometry_shader","--no-vulkan_require_fill_mode_non_solid",
            "--async_shader_compilation=false","--occlusion_query_enable=true","--readback_memexport=true",
            "--vulkan_diagnostic_tiny_render=false","--vulkan_optimize_spirv=false",
            "--gamma_render_target_as_unorm16=true",
            "--android_thread_placement=1","--vulkan_cache_cpu_uploads=true",
            "--vulkan_hoist_shared_read_barriers=true","--vulkan_hoist_cpu_uploads=false",
            "--vulkan_preload_draw_buffers=true","--vulkan_batch_disjoint_shared_writes=true",
            "--vulkan_persistent_pipeline_cache=true","--vulkan_trim_render_area=true",
            "--vulkan_submit_on_primary_buffer_end=true",
            "--log_frame_stats","--log_level=info",
            "--log_file",new File(getFilesDir(),"restreet.log").getAbsolutePath()
        };
    }
}
