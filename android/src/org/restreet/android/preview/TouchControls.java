package org.restreet.android.preview;

import android.content.Context;
import android.graphics.*;
import android.hardware.input.InputManager;
import android.view.*;
import android.widget.*;
import android.os.Handler;
import java.util.*;

/** Touch gamepad shares SDL's real controller route, including proportional sticks. */
public final class TouchControls extends android.widget.RelativeLayout implements InputManager.InputDeviceListener {
    public static final String[] STYLES = { "MINIMALIST CONTROLS", "XBOX CONTROLLER", "FIFA ANDROID" };
    private static native boolean connect();
    private static native void disconnect();
    private static native void axis(int index, float value);
    private static native void button(int index, boolean down);
    private final InputManager inputs;
    private final Handler handler = new Handler();
    private final int style;
    private float controlScale, positionScale;
    private final List<Stick> sticks = new ArrayList<>();
    private final List<PadButton> buttons = new ArrayList<>();
    private final TextView state;
    private boolean active, ready;
    private boolean physical;
    private final Runnable retry = new Runnable() {
        @Override public void run() {
            if (!active || physical) return;
            ready = connect();
            state.setText(ready ? "" : "Preparando controles…");
            if (!ready) handler.postDelayed(this, 500);
        }
    };
    public TouchControls(Context context, int selection) {
        super(context); System.loadLibrary("touchcontrols"); style = Math.max(0, Math.min(2, selection));
        inputs = (InputManager)context.getSystemService(Context.INPUT_SERVICE);
        setClipChildren(false);
        state = new TextView(context); state.setTextColor(Color.WHITE); state.setTextSize(12);
        state.setPadding(dp(12), dp(4), dp(12), dp(4));
        state.setBackgroundColor(0xAA111916);
        rebuild();
    }
    public static android.content.SharedPreferences options(Context context) {
        return context.getSharedPreferences("touch-controls", Context.MODE_PRIVATE);
    }
    public int getStyle(){return style;}
    public void rebuild() {
        releaseAll();removeAllViews();sticks.clear();buttons.clear();
        android.content.SharedPreferences prefs=options(getContext());
        controlScale=prefs.getInt("size",70)/100f;
        positionScale=Math.max(0.8f,controlScale);
        setAlpha(prefs.getInt("opacity",65)/100f);
        RelativeLayout.LayoutParams status = new RelativeLayout.LayoutParams(-2, -2);
        status.addRule(CENTER_HORIZONTAL); status.addRule(ALIGN_PARENT_TOP); status.topMargin = dp(54);
        addView(state, status);
        if (style == 0) {
            if(prefs.getBoolean("dpad",false)) {
                pad("←", 13, 12, 12, false, false); pad("↑", 11, 72, 12, false, false);
                pad("↓", 12, 132, 12, false, false); pad("→", 14, 192, 12, false, false);
            } else stick(0,14,16,false,124);
            pad("B", 1, 132, 12, true, false); pad("A", 0, 72, 12, true, false);
            pad("START", 6, 12, 12, true, false);
        } else {
            stick(0, 16, 18, false, 128);
            if(prefs.getBoolean("right_stick_"+style,style==1))stick(2,238,16,true,90);
            pad(style == 2 ? "PASE" : "A", 0, 92, 16, true, false);
            pad(style == 2 ? "TIRO" : "B", 1, 20, 78, true, false);
            pad(style == 2 ? "GLOBO" : "X", 2, 164, 78, true, false);
            pad(style == 2 ? "AL HUECO" : "Y", 3, 92, 140, true, false);
            if(style==2) {
                pad("REGATE",15,166,150,true,false);
                pad("SPRINT",16,20,150,true,false);
                pad("PAUSA",6,72,8,false,true);
            } else {
                pad("LB",9,14,8,false,true);pad("LT",15,82,8,false,true);
                pad("RB",10,14,8,true,true);pad("RT",16,82,8,true,true);
                pad("BACK",4,154,8,false,true);pad("START",6,154,8,true,true);
            }
            if(prefs.getBoolean("advanced",false)) {
                pad("L3",7,226,8,false,true);pad("R3",8,226,8,true,true);
                if(style==2) {pad("LB",9,14,8,false,true);pad("RB",10,14,8,true,true);pad("BACK",4,142,8,false,true);}
            }
            if(prefs.getBoolean("dpad",false)) {
                pad("↑",11,202,116,false,false);pad("↓",12,202,4,false,false);
                pad("←",13,146,60,false,false);pad("→",14,258,60,false,false);
            }
        }
        for(PadButton b:buttons)b.setVisibility(physical?GONE:VISIBLE);
        for(Stick s:sticks)s.setVisibility(physical?GONE:VISIBLE);
    }
    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void pad(String label, int index, int x, int y, boolean right, boolean top) {
        PadButton b = new PadButton(getContext(), label, index); buttons.add(b);
        RelativeLayout.LayoutParams p = new RelativeLayout.LayoutParams(dp(48), dp(48));
        p.addRule(right ? ALIGN_PARENT_RIGHT : ALIGN_PARENT_LEFT);
        p.addRule(top ? ALIGN_PARENT_TOP : ALIGN_PARENT_BOTTOM);
        int horizontal=Math.max(dp(4),dp((x+28)*positionScale)-dp(24));
        int vertical=Math.max(dp(4),dp((y+26)*positionScale)-dp(24));
        if (right) p.rightMargin = horizontal; else p.leftMargin = horizontal;
        if (top) p.topMargin = vertical; else p.bottomMargin = vertical;
        addView(b, p);
    }
    private void stick(int firstAxis, int x, int y, boolean right, int size) {
        Stick s = new Stick(getContext(), firstAxis); sticks.add(s);
        int diameter=dp(Math.max(64,size*controlScale));
        RelativeLayout.LayoutParams p = new RelativeLayout.LayoutParams(diameter,diameter);
        p.addRule(right ? ALIGN_PARENT_RIGHT : ALIGN_PARENT_LEFT); p.addRule(ALIGN_PARENT_BOTTOM);
        if (right) p.rightMargin = dp(x*positionScale); else p.leftMargin = dp(x*positionScale);
        p.bottomMargin = dp(y*positionScale); addView(s, p);
    }
    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow(); active = true; inputs.registerInputDeviceListener(this, handler); refreshHardware();
    }
    @Override protected void onDetachedFromWindow() {
        active = false; handler.removeCallbacksAndMessages(null); inputs.unregisterInputDeviceListener(this);
        releaseAll(); disconnect(); ready = false; super.onDetachedFromWindow();
    }
    public void releaseAll() {
        for (PadButton b : buttons) b.release();
        for (Stick s : sticks) s.release();
    }
    private void refreshHardware() {
        physical = false;
        for (int id : InputDevice.getDeviceIds()) {
            InputDevice d = InputDevice.getDevice(id);
            if (d != null && !d.isVirtual() && (d.supportsSource(InputDevice.SOURCE_GAMEPAD)
                    || d.supportsSource(InputDevice.SOURCE_JOYSTICK))) physical = true;
        }
        handler.removeCallbacks(retry); releaseAll();
        for (PadButton b : buttons) b.setVisibility(physical ? GONE : VISIBLE);
        for (Stick s : sticks) s.setVisibility(physical ? GONE : VISIBLE);
        if (physical) { disconnect(); ready = false; state.setText("Mando conectado · controles táctiles ocultos"); }
        else if (active) handler.post(retry);
    }
    @Override public void onInputDeviceAdded(int id) { refreshHardware(); }
    @Override public void onInputDeviceRemoved(int id) { refreshHardware(); }
    @Override public void onInputDeviceChanged(int id) { refreshHardware(); }
    private void send(int index, boolean down) {
        if (!ready) return;
        if (index >= 15) axis(index == 15 ? 4 : 5, down ? 1 : -1);
        else button(index, down);
    }
    private final class PadButton extends Button {
        final int index; final String label;
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        PadButton(Context c, String label, int index) {
            super(c); this.label = label; this.index = index;
            setText(""); setContentDescription(label + " · " + buttonName(index));
            setTooltipText(label);
            setBackgroundColor(Color.TRANSPARENT); setMinHeight(0); setMinWidth(0); setPadding(0,0,0,0);
            setOnClickListener(v -> { if (!isPressed()) { send(index, true); handler.postDelayed(() -> send(index, false), 90); } });
        }
        void release() { send(index, false); setPressed(false); invalidate(); }
        @Override public boolean onTouchEvent(android.view.MotionEvent e) {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
                if (!ready) return false;
                send(index, true); setPressed(true);
                if (style == 2) performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            } else if (e.getActionMasked() == MotionEvent.ACTION_UP || e.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                boolean up = e.getActionMasked() == MotionEvent.ACTION_UP;
                send(index, false);
                if (up) performClick();
                setPressed(false);
            }
            invalidate(); return true;
        }
        @Override protected void onDraw(Canvas c) {
            float w=getWidth(),h=getHeight(),r=Math.min(dp(27*controlScale),Math.min(w,h)/2-dp(1));
            int accent=style==2?0xFFF1F2EE:index==0?0xFF8BD761:index==1?0xFFF07468:index==2?0xFF72BDF2:index==3?0xFFF0D365:0xFFE8EFEB;
            boolean round=style==2||index<=3;
            paint.setStyle(Paint.Style.FILL);paint.setColor(isPressed()?0xAA74847B:0x55202523);
            if(round)c.drawCircle(w/2,h/2,r,paint);
            else c.drawRoundRect(w/2-r,h/2-r*.67f,w/2+r,h/2+r*.67f,dp(5),dp(5),paint);
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(isFocused()?2:1));paint.setColor(accent);
            if(round)c.drawCircle(w/2,h/2,r,paint);
            else c.drawRoundRect(w/2-r,h/2-r*.67f,w/2+r,h/2+r*.67f,dp(5),dp(5),paint);
            paint.setStyle(Paint.Style.FILL); paint.setColor(accent); paint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            if (style == 2) {
                drawAction(c,index,w/2,h/2,r*.48f,paint);
            } else {
                paint.setTextSize(dp((label.length()>2?10:20)*controlScale));paint.setTextAlign(Paint.Align.CENTER);
                c.drawText(label,w/2,h/2-(paint.ascent()+paint.descent())/2,paint);
            }
        }
    }
    private static String buttonName(int i) {
        String[] names = {"A", "B", "X", "Y", "Back", "Guide", "Start", "Stick izquierdo", "Stick derecho", "LB", "RB", "Arriba", "Abajo", "Izquierda", "Derecha", "LT", "RT"};
        return names[i];
    }
    private void drawAction(Canvas c,int action,float x,float y,float r,Paint p) {
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(1.6f));p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
        if(action==6) {c.drawLine(x-r*.4f,y-r,x-r*.4f,y+r,p);c.drawLine(x+r*.4f,y-r,x+r*.4f,y+r,p);}
        else if(action==1) {
            c.drawRect(x-r*.15f,y-r*.9f,x+r,y+r*.9f,p);c.drawLine(x+r*.4f,y-r*.9f,x+r*.4f,y+r*.9f,p);
            ball(c,x-r*.7f,y+r*.4f,r*.45f,p);c.drawLine(x-r*1.2f,y-r*.4f,x-r*.7f,y-r*.2f,p);
        } else if(action==2) {
            ball(c,x-r*.8f,y+r*.6f,r*.4f,p);c.drawArc(x-r,y-r,x+r,y+r*.7f,195,145,false,p);
            c.drawLine(x+r*.8f,y-r*.1f,x+r*.8f,y+r*.65f,p);
        } else if(action==3) {
            c.drawCircle(x-r*.9f,y-r*.7f,r*.16f,p);c.drawCircle(x+r*.9f,y-r*.7f,r*.16f,p);
            ball(c,x,y+r*.55f,r*.38f,p);c.drawLine(x,y-r*.8f,x,y,p);c.drawLine(x,y-r*.8f,x-r*.3f,y-r*.4f,p);c.drawLine(x,y-r*.8f,x+r*.3f,y-r*.4f,p);
        } else if(action==15) {
            Path path=new Path();path.moveTo(x-r*.7f,y-r);path.lineTo(x-r*.55f,y+r*.5f);path.lineTo(x+r*.4f,y+r*.5f);path.quadTo(x+r*.8f,y+r*.25f,x+r*.2f,y);path.lineTo(x-r*.05f,y-r*.15f);path.lineTo(x+r*.1f,y-r);c.drawPath(path,p);ball(c,x+r*.8f,y+r*.7f,r*.35f,p);
        } else if(action==16) {
            c.drawCircle(x+r*.4f,y-r*.9f,r*.23f,p);Path path=new Path();path.moveTo(x+r*.2f,y-r*.45f);path.lineTo(x-r*.25f,y+r*.1f);path.lineTo(x+r*.35f,y+r*.45f);path.lineTo(x+r*.55f,y+r);path.moveTo(x-r*.25f,y+r*.1f);path.lineTo(x-r*.9f,y+r*.8f);path.moveTo(x-r*.05f,y-r*.3f);path.lineTo(x+r*.7f,y-r*.05f);path.moveTo(x-r*.05f,y-r*.3f);path.lineTo(x-r*.8f,y-r*.1f);c.drawPath(path,p);
        } else if(action==0) {
            ball(c,x-r*.65f,y+r*.1f,r*.48f,p);c.drawLine(x+r*.05f,y,x+r,y,p);c.drawLine(x+r,y,x+r*.55f,y-r*.4f,p);c.drawLine(x+r,y,x+r*.55f,y+r*.4f,p);
        } else {
            p.setStyle(Paint.Style.FILL);p.setTextAlign(Paint.Align.CENTER);p.setTextSize(r*1.1f);c.drawText(buttonName(action),x,y-(p.ascent()+p.descent())/2,p);
        }
        p.setStyle(Paint.Style.FILL);
    }
    private void ball(Canvas c,float x,float y,float r,Paint p) {
        c.drawCircle(x,y,r,p);c.drawLine(x-r*.7f,y,x+r*.7f,y,p);c.drawLine(x,y-r*.7f,x,y+r*.7f,p);
    }
    private final class Stick extends View {
        final int firstAxis; final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); float x,y;
        int pointer = -1;
        Stick(Context c,int first) { super(c);firstAxis=first;setContentDescription(first == 0 ? "Stick analógico de movimiento" : "Stick analógico de regates"); }
        void release() { if(ready) { axis(firstAxis,0);axis(firstAxis+1,0); } pointer=-1;x=y=0;invalidate(); }
        @Override public boolean onTouchEvent(MotionEvent e) {
            if(!ready)return false;
            int action=e.getActionMasked();
            if(action==MotionEvent.ACTION_DOWN)pointer=e.getPointerId(0);
            if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL) { release();return true; }
            int index=e.findPointerIndex(pointer);
            if(index<0) { release();return true; }
            float radius=Math.min(getWidth(),getHeight())*0.33f;
            x=(e.getX(index)-getWidth()/2f)/radius; y=(e.getY(index)-getHeight()/2f)/radius;
            float length=(float)Math.sqrt(x*x+y*y); if(length>1) { x/=length;y/=length; }
            float scale=length<0.12f?0:Math.min(1,(length-0.12f)/0.88f);
            float divisor=Math.max(1e-6f,Math.min(length,1));
            axis(firstAxis,x/divisor*scale);axis(firstAxis+1,y/divisor*scale);invalidate();return true;
        }
        @Override protected void onDraw(Canvas c) {
            float cx=getWidth()/2f,cy=getHeight()/2f,r=Math.min(cx,cy)-dp(3);
            p.setStyle(Paint.Style.FILL);p.setColor(0x35202523);c.drawCircle(cx,cy,r,p);
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(1));p.setColor(0xA0EDF0EA);c.drawCircle(cx,cy,r,p);
            p.setStyle(Paint.Style.FILL);p.setColor(pointer<0?0x708C9690:0xBBD7DED8);
            c.drawCircle(cx+x*r*0.58f,cy+y*r*0.58f,r*0.30f,p);
        }
    }
}
