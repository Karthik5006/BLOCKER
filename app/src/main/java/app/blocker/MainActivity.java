package app.blocker;

import android.Manifest;
import android.app.*;
import android.app.admin.DevicePolicyManager;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

public final class MainActivity extends Activity {
    private static final int INK=0xfff4eddd,MUTED=0xffb2c2b5,PAPER=0xff142a25,GREEN=0xffc3d6ad,CARD=0xff243d34,LINE=0xff425b4e;
    private static boolean inForeground;
    static boolean isInForeground(){return inForeground;}
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private FrameLayout body;
    private AlpineScreen web;
    private Typeface outfit;
    private String page="block",query="";
    private List<AppCatalog.Entry> catalog=new ArrayList<>();
    private boolean loaded,resumed,exiting,resumeExit,exitFinished,overlayPrompted;
    private long exitStarted,lastSetupLaunch=-2000L;
    private final Runnable exitCheck=this::finishQuietExit;
    private int lastSession=-1;

    private Dialog setupDialog;
    private MotionPolicy motion;private AlpineScreen preview;private boolean sheetVisible,setupPreview,catalogLoading,catalogDirty,uninstallRequested;private long blockedSignal;private AppAdapter pickerAdapter;
    private final Set<Dialog> nativeDialogs=new HashSet<>();
    private final BroadcastReceiver packages=new BroadcastReceiver(){public void onReceive(Context c,Intent i){refreshCatalog();}};
    private boolean animations(){return motion!=null&&motion.allowed();}
    private void stopViewMotion(View v){v.animate().cancel();v.clearAnimation();v.setStateListAnimator(null);v.jumpDrawablesToCurrentState();if(v.getBackground() instanceof RippleDrawable){RippleDrawable r=(RippleDrawable)v.getBackground();if(r.getNumberOfLayers()>0)v.setBackground(r.getDrawable(0));}if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)stopViewMotion(((ViewGroup)v).getChildAt(i));}
    private void applyMotion(){boolean allowed=animations();if(web!=null)web.setMotionAllowed(allowed&&!sheetVisible&&!exiting);if(preview!=null)preview.setMotionAllowed(allowed);for(Dialog d:nativeDialogs)if(d.getWindow()!=null){d.getWindow().setWindowAnimations(allowed?(centeredDialogs.contains(d)?R.style.QuietDialogMotion:R.style.QuietSheetMotion):0);if(!allowed)stopViewMotion(d.getWindow().getDecorView());}}
    private void refreshCatalog(){if(executor.isShutdown())return;if(catalogLoading){catalogDirty=true;return;}catalogLoading=true;executor.execute(()->{List<AppCatalog.Entry> entries=AppCatalog.load(getApplicationContext());handler.post(()->{catalogLoading=false;if(isDestroyed())return;catalog=entries;loaded=true;if(pickerAdapter!=null)pickerAdapter.notifyDataSetChanged();if(resumed&&!exiting&&(web==null||!web.holding()))render();if(catalogDirty){catalogDirty=false;refreshCatalog();}});});}
    private void nativeSheetShown(Dialog d){setupDialog=d;nativeDialogs.add(d);sheetVisible=true;applyMotion();}
    private void nativeSheetClosed(Dialog closed){nativeDialogs.remove(closed);sheetVisible=!nativeDialogs.isEmpty();if(closed==setupDialog){setupPreview=false;pickerAdapter=null;if(preview!=null){preview.destroy();preview=null;}}applyMotion();if(resumed&&!exiting)render();}
    private void scheduleTick(){handler.removeCallbacks(ticker);if(!resumed||exiting)return;if(State.active(this)||(page.equals("pause")&&pauseLeft()>0))handler.postDelayed(ticker,1000);else if(State.pending(this))handler.postDelayed(ticker,Math.max(1,State.prefs(this).getLong("setupAt",0)+600000-SystemClock.elapsedRealtime()));}

    private final Runnable ticker=new Runnable() {
        @Override public void run() {
            if(!resumed)return;
            updateTimers();scheduleTick();
        }
    };
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        outfit=Typeface.createFromAsset(getAssets(),"Outfit.ttf");
        motion=new MotionPolicy(this,this::applyMotion);
        IntentFilter f=new IntentFilter();f.addAction(Intent.ACTION_PACKAGE_ADDED);f.addAction(Intent.ACTION_PACKAGE_REMOVED);f.addAction(Intent.ACTION_PACKAGE_REPLACED);f.addDataScheme("package");registerReceiver(packages,f);
        State.restoreClock(this);
        getWindow().setStatusBarColor(PAPER);getWindow().setNavigationBarColor(PAPER);
        Notices.channels(this);Notices.schedule(this);
        if(saved!=null){page=saved.getString("page","block");resumeExit=saved.getBoolean("exitPending",false);uninstallRequested=saved.getBoolean("uninstallPending",false);}
        if(State.prefs(this).getBoolean("showComplete",false)&&!State.active(this))page="complete";
        if(getIntent()!=null&&getIntent().getBooleanExtra("blocked",false)){getIntent().removeExtra("blocked");page="block";blockedSignal=SystemClock.elapsedRealtime();}
        render();
        refreshCatalog();
    }
    @Override protected void onSaveInstanceState(Bundle state){state.putString("page",page);state.putBoolean("exitPending",exiting);state.putBoolean("uninstallPending",uninstallRequested);super.onSaveInstanceState(state);}
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);setIntent(intent);
        if(intent!=null&&intent.getBooleanExtra("blocked",false)){intent.removeExtra("blocked");page="block";blockedSignal=SystemClock.elapsedRealtime();if(resumed)render();}
    }
    @Override protected void onResume() {
        super.onResume();resumed=true;inForeground=true;BlockerService.onMainActivityResumed();motion.foreground(true);if(web!=null){web.resumeTimers();web.onResume();}if(preview!=null)preview.onResume();refreshCatalog();
        if(State.expired(this)){BlockerService.complete(this);page="complete";}
        if(State.pending(this)&&(State.prefs(this).getBoolean("managedSession",false)||UninstallGuard.adminActive(this))){
            if(enabled()){
                overlayPrompted=false;
                BlockerService.connectPending(this);
            } else if(BlockingAccess.usageEnabled(this)&&!BlockingAccess.overlayEnabled(this)&&!overlayPrompted){
                overlayPrompted=true;
                openAccessibility();
            }
        } else if(State.active(this)&&enabled()){
            BlockerService.ensureRunning(this);
        }
        if(!State.active(this)&&!State.pending(this)&&(State.prefs(this).getBoolean("active",false)||State.prefs(this).getBoolean("pending",false)||UninstallGuard.active(this)||!BlockingAccess.disabled(this)))BlockerService.exit(this);
        if(resumeExit||(!State.active(this)&&State.prefs(this).getBoolean("exitAfterSession",false))){resumeExit=false;exit();return;}
        handler.removeCallbacks(ticker);handler.post(ticker);
        if(!exiting)render();else finishQuietExit();
    }
    @Override protected void onPause(){resumed=false;inForeground=false;motion.foreground(false);if(web!=null){web.cancelHold();web.setMotionAllowed(false);web.onPause();web.pauseTimers();}if(preview!=null)preview.onPause();handler.removeCallbacks(ticker);super.onPause();}
    @Override protected void onDestroy(){inForeground=false;handler.removeCallbacksAndMessages(null);executor.shutdownNow();unregisterReceiver(packages);motion.close();if(preview!=null){preview.destroy();preview=null;}if(web!=null){web.removeJavascriptInterface("BlockerUI");if(web.getParent()!=null)((ViewGroup)web.getParent()).removeView(web);web.destroy();}for(Dialog dialog:new ArrayList<>(nativeDialogs))dialog.dismiss();super.onDestroy();}
    @Override public void onBackPressed() {
        if(exiting)return;
        if(State.active(this)){exit();return;}
        if(!page.equals("block")){if(page.equals("complete"))State.prefs(this).edit().putBoolean("showComplete",false).apply();page="block";render();}
        else exit();
    }
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private GradientDrawable surface(int color,int radius) {
        GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;
    }
    private Drawable touch(int color) {
        if(!animations())return surface(color,18);
        return new RippleDrawable(android.content.res.ColorStateList.valueOf(0x228fcdb9),surface(color,18),surface(Color.WHITE,18));
    }
    private TextView text(String label,int size,int color) {
        TextView v=new TextView(this);v.setTypeface(outfit);v.setFontVariationSettings("'wght' 400");v.setText(label);v.setTextSize(size);v.setTextColor(color);
        v.setFontFeatureSettings("tnum");v.setLineSpacing(dp(2),1);return v;
    }
    private Button button(String title,boolean primary,Runnable action) {
        Button b=new Button(this);b.setText(title);b.setAllCaps(false);b.setTextSize(14);
        b.setStateListAnimator(null);b.setElevation(0);b.setTranslationZ(0);b.setOutlineProvider(null);b.setBackgroundTintList(null);
        b.setTypeface(outfit);b.setFontVariationSettings("'wght' 500");b.setTextColor(primary?PAPER:INK);
        b.setMinWidth(0);b.setMinimumWidth(0);b.setMinHeight(dp(52));b.setMinimumHeight(dp(52));b.setGravity(Gravity.CENTER);
        b.setPadding(dp(16),dp(12),dp(16),dp(12));b.setBackground(touch(primary?GREEN:0xff304b3e));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(4),0,dp(4));b.setLayoutParams(p);
        if(action!=null)b.setOnClickListener(v->action.run());return b;
    }
    private void heading(LinearLayout p,String label){TextView t=text(label,25,INK);t.setFontVariationSettings("'wght' 540");p.addView(t);}
    private void note(LinearLayout p,String label){TextView t=text(label,14,MUTED);t.setPadding(0,dp(7),0,dp(12));p.addView(t);}
    private LinearLayout column(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);v.setPadding(dp(4),dp(8),dp(4),dp(4));v.setClipChildren(false);v.setClipToPadding(false);return v;}
    private EditText field(String label,String value,boolean numeric){
        EditText f=new EditText(this);f.setTypeface(outfit);f.setFontVariationSettings("'wght' 400");f.setTextSize(16);f.setTextColor(INK);f.setHintTextColor(MUTED);f.setContentDescription(label);f.setSingleLine(true);f.setSelectAllOnFocus(true);f.setMinHeight(dp(54));f.setPadding(dp(16),dp(12),dp(16),dp(12));
        if(numeric)f.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        StateListDrawable states=new StateListDrawable();GradientDrawable focus=surface(PAPER,16);focus.setStroke(dp(1),GREEN);GradientDrawable idle=surface(PAPER,16);idle.setStroke(dp(1),LINE);states.addState(new int[]{android.R.attr.state_focused},focus);states.addState(new int[]{},idle);f.setBackgroundTintList(null);f.setBackground(states);f.setText(value);return f;
    }
    private TextView errorLabel(LinearLayout parent){TextView v=text("",13,0xffefb8a7);v.setPadding(0,dp(8),0,dp(8));v.setVisibility(View.GONE);v.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);parent.addView(v);return v;}
    private void fieldError(TextView error,EditText input,String message){error.setText(message);error.setVisibility(View.VISIBLE);input.requestFocus();}
    private void clearErrorOnEdit(EditText input,TextView error){input.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence value,int start,int count,int after){}public void onTextChanged(CharSequence value,int start,int before,int count){}public void afterTextChanged(android.text.Editable value){error.setVisibility(View.GONE);}});}
    private void styleToggle(CompoundButton control){
        control.setTypeface(outfit);control.setFontVariationSettings("'wght' 400");control.setTextSize(14);control.setTextColor(INK);control.setMinHeight(dp(48));control.setPadding(dp(2),dp(6),dp(2),dp(6));
        android.content.res.ColorStateList colors=new android.content.res.ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{GREEN,0xff839b87});
        if(control instanceof Switch){Switch s=(Switch)control;s.setShowText(false);s.setThumbTintList(colors);s.setTrackTintList(new android.content.res.ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{0xff8fa979,0xff6d8373}));s.setSwitchPadding(dp(16));}else control.setButtonTintList(colors);
    }
    private void showColumn(LinearLayout v){ScrollView s=new ScrollView(this);s.addView(v);body.addView(s,new FrameLayout.LayoutParams(-1,-1));}
    private int sessionCode(){return (State.active(this)?2:State.pending(this)?1:0)+(enabled()?4:0)+(UninstallGuard.active(this)?8:0)+(UninstallGuard.managedBlocked(this)?16:0);}
    private boolean enabled(){return BlockingAccess.enabled(this);}
    private void render() {
        if(exiting)return;
        if(web==null) {
            body=new FrameLayout(this);body.setBackgroundColor(PAPER);
            body.setOnApplyWindowInsetsListener((v,insets)->{
                v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets;
            });
            web=new AlpineScreen(this,this::action,this::render,this::finishHeldSession);
            body.addView(web,new FrameLayout.LayoutParams(-1,-1));setContentView(body);
        }
        JSONObject state=new JSONObject();
        try {
            state.put("page",page);state.put("active",State.active(this));state.put("pending",State.pending(this));
            state.put("release",!State.pending(this)&&!State.active(this)&&(!BlockingAccess.disabled(this)||UninstallGuard.active(this)));
            state.put("loaded",loaded);state.put("minutes",State.prefs(this).getInt("duration",25));
            Set<String> selected=State.selected(this);
            state.put("count",catalog.stream().filter(e->!e.essential&&selected.contains(e.pkg)).count());
            boolean graphics=State.prefs(this).getBoolean("graphics",true);
            state.put("holdDuration",State.active(this)?State.sessionHold(this):State.holdDuration(this));state.put("setup",setupPreview||State.pending(this));state.put("adminReady",UninstallGuard.adminActive(this)||UninstallGuard.managedBlocked(this));state.put("accessReady",enabled());state.put("blockedSignal",blockedSignal);state.put("game",State.prefs(this).getBoolean("game",false));state.put("jar",State.prefs(this).getInt("jar",0));
            state.put("followTime",false);state.put("scene",currentScene());state.put("graphics",graphics);
            state.put("still",!animations()||sheetVisible);
            state.put("interrupted",State.active(this)&&!enabled());
            state.put("guardMissing",State.active(this)&&!UninstallGuard.adminActive(this)&&!UninstallGuard.managedBlocked(this));
            state.put("reason",State.reason(this));state.put("step",State.step(this));
            state.put("pauseStep",State.prefs(this).getString("pauseStep",State.step(this)));state.put("paused",pauseLeft()>0);
            int[] week=State.week(this);state.put("free",week[0]);state.put("logged",week[1]);
            JSONArray days=new JSONArray();
            for(int i=6;i>=0;i--) {
                LocalDate date=LocalDate.now().minusDays(i);String entry=State.prefs(this).getString("day_"+date,"");
                String result=entry.equals("free")?"Porn-free":entry.equals("watched")?"Watched":"No check-in";
                String trigger=State.prefs(this).getString("trigger_"+date,"");
                if(entry.equals("watched")&&!trigger.isEmpty())result+=" · "+trigger;
                days.put(new JSONArray().put(date.format(DateTimeFormatter.ofPattern("EEE, d MMM"))).put(result).put(entry).put(State.prefs(this).getInt("sessions_"+date,0)));
            }
            state.put("days",days);
        }catch(JSONException e){throw new IllegalStateException(e);}
        lastSession=sessionCode();web.update(state);applyMotion();updateTimers();scheduleTick();
    }
    private String currentScene(){return page.equals("complete")?"Dawn":State.active(this)?"Night":"Dusk";}
    private long pauseLeft() {
        return State.prefs(this).getInt("pauseBoot",-2)==State.boot(this)?
            Math.max(0,State.prefs(this).getLong("pauseEnd",0)-SystemClock.elapsedRealtime()):0;
    }
    private void action(String name) {
        if(!resumed||exiting)return;
        if(name.equals("exit")){exit();return;}
        if(State.active(this)) {
            if(name.equals("restore"))startActivity(BlockingAccess.nextPermissionIntent(this));
            return;
        }
        switch(name) {
            case "continue":State.prefs(this).edit().putBoolean("showComplete",false).apply();page="block";render();break;
            case "jar":if(!State.pending(this)&&State.prefs(this).getBoolean("game",false)){State.prefs(this).edit().putInt("jar",Math.min(5,State.prefs(this).getInt("jar",0)+1)).apply();render();}break;
            case "home":case "block":case "week":page=name;render();break;
            case "apps":if(loaded)picker();else toast("Loading apps…");break;
            case "settings":permissions();break;
            case "duration":duration();break;
            case "plan":plan();break;
            case "log":checkIn();break;
            case "delete":deleteEntries();break;
            case "start":
                if(State.pending(this)){overlayPrompted=false;lastSetupLaunch=-2000L;openAccessibility();}
                else if(State.selected(this).isEmpty())picker();
                else startBlocking();
                break;
            case "cancel":if(State.pending(this)){overlayPrompted=false;BlockerService.exit(this);render();}break;
            case "startPause":if(pauseLeft()>0){page="pause";render();}else startPause();break;
            case "returned":returned();break;

        }
    }
    private final Set<Dialog> centeredDialogs=new HashSet<>();
    private final class PopupScroll extends ScrollView {
        private final Path corners=new Path();
        @Override protected void onSizeChanged(int w,int h,int oldW,int oldH){super.onSizeChanged(w,h,oldW,oldH);corners.reset();corners.addRoundRect(0,0,w,h,dp(26),dp(26),Path.Direction.CW);}
        @Override protected void dispatchDraw(Canvas canvas){int saved=canvas.save();canvas.translate(getScrollX(),getScrollY());canvas.clipPath(corners);canvas.translate(-getScrollX(),-getScrollY());super.dispatchDraw(canvas);canvas.restoreToCount(saved);}
        PopupScroll(){super(MainActivity.this);setFillViewport(false);setOverScrollMode(View.OVER_SCROLL_NEVER);setVerticalScrollBarEnabled(false);setHorizontalScrollBarEnabled(false);setPadding(0,0,0,0);}
        @Override protected void onMeasure(int width,int height){int cap=(int)(getResources().getDisplayMetrics().heightPixels*.86);int limit=View.MeasureSpec.getMode(height)==View.MeasureSpec.UNSPECIFIED?cap:Math.min(cap,View.MeasureSpec.getSize(height));super.onMeasure(width,View.MeasureSpec.makeMeasureSpec(limit,View.MeasureSpec.AT_MOST));}
    }
    private LinearLayout popupPanel(String title,boolean handle){
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(20),dp(handle?12:22),dp(20),dp(20));
        box.setBackground(surface(CARD,26));box.setClipToOutline(true);
        if(handle){View grip=new View(this);grip.setBackground(surface(0xff758a76,2));LinearLayout.LayoutParams gp=new LinearLayout.LayoutParams(dp(30),dp(3));gp.gravity=Gravity.CENTER_HORIZONTAL;gp.bottomMargin=dp(18);box.addView(grip,gp);}
        TextView titleView=text(title,22,INK);titleView.setFontVariationSettings("'wght' 540");titleView.setPadding(dp(4),0,dp(4),dp(8));if(Build.VERSION.SDK_INT>=28)titleView.setAccessibilityHeading(true);box.addView(titleView);return box;
    }
    private void showPopup(Dialog d,View panel,boolean primary,boolean centered,int height){
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);d.setContentView(panel);d.setCanceledOnTouchOutside(true);
        Window w=d.getWindow();
        if(w!=null){w.setBackgroundDrawableResource(android.R.color.transparent);w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);w.getDecorView().setElevation(0);w.getDecorView().setOutlineProvider(null);WindowManager.LayoutParams attrs=w.getAttributes();attrs.gravity=centered?Gravity.CENTER:Gravity.BOTTOM;attrs.dimAmount=.42f;attrs.y=centered?0:dp(12);w.setAttributes(attrs);w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE|WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);}
        if(centered)centeredDialogs.add(d);d.setOnDismissListener(ignored->{centeredDialogs.remove(d);nativeSheetClosed(d);});
        if(primary)nativeSheetShown(d);else{nativeDialogs.add(d);sheetVisible=true;applyMotion();}
        d.show();if(w!=null)w.setLayout(Math.min(getResources().getDisplayMetrics().widthPixels-dp(24),dp(centered?400:560)),height);
    }
    private void sheet(String title,LinearLayout content){
        Dialog d=new Dialog(this);LinearLayout box=popupPanel(title,true);box.addView(content,new LinearLayout.LayoutParams(-1,-2));PopupScroll scroll=new PopupScroll();scroll.setBackground(surface(CARD,26));scroll.setClipToOutline(true);scroll.addView(box);showPopup(d,scroll,true,false,-2);
    }
    private void confirmation(String title,String message,String cancel,String confirm,Runnable action,boolean destructive){
        Dialog d=new Dialog(this);LinearLayout box=popupPanel(title,false),form=column();note(form,message);
        Button yes=button(confirm,true,()->{d.dismiss();action.run();});if(destructive){yes.setBackground(touch(0xffd9b6a6));yes.setTextColor(PAPER);}form.addView(yes);
        if(cancel!=null)form.addView(button(cancel,false,d::dismiss));box.addView(form);PopupScroll scroll=new PopupScroll();scroll.setBackground(surface(CARD,26));scroll.setClipToOutline(true);scroll.addView(box);showPopup(d,scroll,false,true,-2);
    }
    private GridLayout chipGrid(int columns){GridLayout grid=new GridLayout(this);grid.setColumnCount(columns);grid.setClipChildren(false);grid.setClipToPadding(false);grid.setPadding(0,dp(3),0,dp(3));return grid;}
    private void addChip(GridLayout grid,Button b){b.setTextSize(13);b.setPadding(dp(4),dp(10),dp(4),dp(10));b.setMinHeight(dp(48));b.setMinimumHeight(dp(48));GridLayout.LayoutParams lp=new GridLayout.LayoutParams(GridLayout.spec(GridLayout.UNDEFINED),GridLayout.spec(GridLayout.UNDEFINED,1f));lp.width=0;lp.height=-2;lp.setMargins(dp(4),dp(4),dp(4),dp(4));grid.addView(b,lp);}
    private void selectChip(Button b,boolean selected){b.animate().cancel();GradientDrawable fill=surface(selected?0xff46644f:0xff2d473b,16);fill.setStroke(dp(1),selected?0xff66836b:0xff3c5948);b.setBackground(fill);float scale=selected?1.04f:1;if(animations())b.animate().scaleX(scale).scaleY(scale).setDuration(250).start();else{b.setScaleX(scale);b.setScaleY(scale);}b.setSelected(selected);b.setContentDescription(b.getText()+(selected?", selected":""));}
    private void duration(){
        if(State.pending(this))return;LinearLayout form=column();note(form,"Choose how long your selected apps will stay blocked.");
        EditText minutes=field("Session length in minutes",String.valueOf(State.prefs(this).getInt("duration",25)),true);minutes.setTextSize(32);minutes.setFontVariationSettings("'wght' 350");minutes.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(4)});form.addView(minutes);note(form,"Minutes · 1 to 1,439");TextView error=errorLabel(form);clearErrorOnEdit(minutes,error);
        boolean compact=getResources().getConfiguration().screenWidthDp<360||getResources().getConfiguration().fontScale>1.15f;
        GridLayout quick=chipGrid(compact?2:4);List<Button> presets=new ArrayList<>();
        for(int value:new int[]{5,25,60,120}){Button b=button(value<60?value+" min":(value/60)+" h",false,null);presets.add(b);addChip(quick,b);b.setOnClickListener(v->{minutes.animate().cancel();minutes.setText(String.valueOf(value));minutes.setAlpha(1);error.setVisibility(View.GONE);if(animations()){minutes.setAlpha(.3f);minutes.animate().alpha(1).setDuration(250).start();}for(Button chip:presets)selectChip(chip,chip==b);});selectChip(b,value==State.prefs(this).getInt("duration",25));}form.addView(quick);
        note(form,"Hold to end early");int[] hold={(int)(State.holdDuration(this)/60000)};GridLayout row=chipGrid(getResources().getConfiguration().fontScale>1.6f?2:3);List<Button> choices=new ArrayList<>();
        for(int value:new int[]{3,5,10}){Button b=button(value+" min",false,null);choices.add(b);addChip(row,b);b.setOnClickListener(v->{hold[0]=value;for(Button chip:choices)selectChip(chip,chip==b);});selectChip(b,hold[0]==value);}form.addView(row);note(form,"One continuous hold. Lifting, another finger, or leaving the screen resets it.");
        form.addView(button("Use these times",true,()->{int total;try{total=Integer.parseInt(minutes.getText().toString().trim());}catch(NumberFormatException bad){total=0;}if(total<1||total>1439){fieldError(error,minutes,"Choose 1 to 1,439 minutes.");return;}State.prefs(this).edit().putInt("duration",total).putInt("holdMinutes",hold[0]).apply();setupDialog.dismiss();render();}));sheet("Session length",form);
    }
    private TextView selectionCount;
    private void picker() {
        if(State.active(this)||State.pending(this))return;
        LinearLayout content=column();content.setClipChildren(true);content.setClipToPadding(true);
        EditText search=field("Search installed apps",query,false);search.setHint("Search apps");
        search.setTextSize(16);search.setTextColor(INK);search.setHintTextColor(MUTED);
        
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,-2);sp.setMargins(0,dp(11),0,dp(5));content.addView(search,sp);
        LinearLayout options=new LinearLayout(this);options.setGravity(Gravity.CENTER_VERTICAL);boolean stack=getResources().getConfiguration().screenWidthDp<360||getResources().getConfiguration().fontScale>1.15f;if(stack)options.setOrientation(LinearLayout.VERTICAL);
        CheckBox all=new CheckBox(this);all.setText("Select all");styleToggle(all);
        options.addView(all,stack?new LinearLayout.LayoutParams(-1,-2):new LinearLayout.LayoutParams(0,-2,1));
        Switch system=new Switch(this);system.setText("System apps");styleToggle(system);
        system.setChecked(State.prefs(this).getBoolean("includeSystem",true));options.addView(system,new LinearLayout.LayoutParams(stack?-1:-2,-2));content.addView(options);
        selectionCount=text(loaded?"":"Loading apps…",12,MUTED);selectionCount.setPadding(dp(4),dp(2),0,dp(6));content.addView(selectionCount);
        ListView list=new ListView(this);list.setSelector(new ColorDrawable(Color.TRANSPARENT));list.setCacheColorHint(Color.TRANSPARENT);
        list.setLayoutAnimation(null);list.setOverScrollMode(View.OVER_SCROLL_NEVER);list.setDivider(new ColorDrawable(Color.TRANSPARENT));list.setDividerHeight(dp(6));
        list.setVerticalScrollBarEnabled(false);LinearLayout.LayoutParams listSpace=new LinearLayout.LayoutParams(-1,0,1);listSpace.bottomMargin=dp(12);content.addView(list,listSpace);
        AppAdapter adapter=new AppAdapter(all);list.setAdapter(adapter);
        all.setEnabled(loaded);all.setOnClickListener(v->{
            Set<String> chosen=State.selected(this);
            boolean every=catalog.stream().filter(e->!e.essential).allMatch(e->chosen.contains(e.pkg));
            if(every)chosen.clear();else for(AppCatalog.Entry e:catalog)if(!e.essential)chosen.add(e.pkg);
            State.select(this,chosen);adapter.notifyDataSetChanged();
        });
        system.setOnCheckedChangeListener((v,checked)->{State.prefs(this).edit().putBoolean("includeSystem",checked).apply();adapter.notifyDataSetChanged();if(!animations())v.jumpDrawablesToCurrentState();});
        search.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){query=s.toString();adapter.notifyDataSetChanged();}
            public void afterTextChanged(android.text.Editable e){}
        });
        list.setOnItemClickListener((parent,view,position,id)->{
            AppCatalog.Entry entry=adapter.visible.get(position);
            if(entry.essential){toast("Essential controls stay available.");return;}
            Set<String> chosen=State.selected(this);
            if(!chosen.add(entry.pkg))chosen.remove(entry.pkg);
            State.select(this,chosen);adapter.pendingCheck=chosen.contains(entry.pkg)?entry.pkg:null;adapter.notifyDataSetChanged();
        });
        Dialog appDialog=new Dialog(this);content.addView(button("Done",true,appDialog::dismiss));
        LinearLayout panel=popupPanel("Apps to block",true);panel.addView(content,new LinearLayout.LayoutParams(-1,0,1));pickerAdapter=adapter;
        showPopup(appDialog,panel,true,false,(int)(getResources().getDisplayMetrics().heightPixels*.81));
        adapter.notifyDataSetChanged();
    }
    private final class AppAdapter extends BaseAdapter {
        private final CheckBox all;private String pendingCheck;
        private final List<AppCatalog.Entry> visible=new ArrayList<>();
        AppAdapter(CheckBox all){this.all=all;}
        @Override public void notifyDataSetChanged() {
            visible.clear();boolean system=State.prefs(MainActivity.this).getBoolean("includeSystem",true);
            for(AppCatalog.Entry e:catalog)if((system||!e.system)&&(e.name+" "+e.pkg).toLowerCase(Locale.getDefault()).contains(query.toLowerCase(Locale.getDefault())))visible.add(e);
            Set<String> chosen=State.selected(MainActivity.this);
            long total=catalog.stream().filter(e->!e.essential&&chosen.contains(e.pkg)).count();
            all.setChecked(catalog.stream().anyMatch(e->!e.essential)&&catalog.stream().filter(e->!e.essential).allMatch(e->chosen.contains(e.pkg)));
            if(!animations())all.jumpDrawablesToCurrentState();selectionCount.setText(loaded?total+" selected · All includes system + installed":"Loading apps…");super.notifyDataSetChanged();
        }
        @Override public int getCount(){return visible.size();}
        @Override public Object getItem(int p){return visible.get(p);}
        @Override public long getItemId(int p){return p;}
        @Override public View getView(int p,View recycled,ViewGroup parent) {
            AppCatalog.Entry e=visible.get(p);
            LinearLayout row;
            if(recycled instanceof LinearLayout)row=(LinearLayout)recycled;
            else {
                row=new LinearLayout(MainActivity.this);row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(13),dp(8),dp(13),dp(8));row.setClipToOutline(true);row.setMinimumHeight(dp(66));
                ImageView icon=new ImageView(MainActivity.this);row.addView(icon,new LinearLayout.LayoutParams(dp(36),dp(36)));
                LinearLayout labels=new LinearLayout(MainActivity.this);labels.setOrientation(LinearLayout.VERTICAL);labels.setPadding(dp(13),0,dp(8),0);
                labels.addView(text("",15,INK));labels.addView(text("",12,MUTED));row.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
                TextView check=text("",20,GREEN);check.setGravity(Gravity.CENTER);row.addView(check,new LinearLayout.LayoutParams(dp(32),dp(40)));
            }
            ImageView icon=(ImageView)row.getChildAt(0);
            try {icon.setImageDrawable(getPackageManager().getApplicationIcon(e.pkg));}catch(PackageManager.NameNotFoundException ex){icon.setImageDrawable(getDrawable(R.drawable.ic_blocker));}
            LinearLayout labels=(LinearLayout)row.getChildAt(1);
            ((TextView)labels.getChildAt(0)).setText(e.name);
            ((TextView)labels.getChildAt(1)).setText(e.essential?"Essential · stays available":e.system?"System app":"Installed app");
            boolean chosen=!e.essential&&State.selected(MainActivity.this).contains(e.pkg);
            GradientDrawable fill=surface(chosen?0xff3d5947:CARD,18);
            fill.setStroke(dp(1),chosen?GREEN:LINE);
            row.setBackground(animations()?new RippleDrawable(android.content.res.ColorStateList.valueOf(0x33f7c99b),fill,surface(Color.WHITE,18)):fill);
            row.setClipToOutline(true);
            TextView check=(TextView)row.getChildAt(2);check.animate().cancel();check.setAlpha(1);check.setScaleX(1);check.setText(e.essential?"—":chosen?"✓":"○");row.setAlpha(e.essential?.55f:1f);
            if(chosen&&e.pkg.equals(pendingCheck)){pendingCheck=null;if(animations()){check.setAlpha(0);check.setScaleX(.7f);check.animate().alpha(1).scaleX(1).setDuration(150).start();}}
            row.setContentDescription(e.name+", "+(e.essential?"essential, stays available":chosen?"selected":"not selected"));
            return row;
        }
    }
    private void startPause() {
        State.prefs(this).edit().putLong("pauseEnd",SystemClock.elapsedRealtime()+600000)
            .putInt("pauseBoot",State.boot(this)).putString("pauseStep",State.step(this)).apply();
        page="pause";render();
    }
    private void returned(){LinearLayout form=column();note(form,"Notice where you are now.");form.addView(leafChip("It eased",()->{setupDialog.dismiss();finishPause();}));form.addView(leafChip("Still there",()->{State.prefs(this).edit().putString("pauseStep","Get a glass of water and sit somewhere else.").putInt("pauseBoot",State.boot(this)).putLong("pauseEnd",SystemClock.elapsedRealtime()+600000).apply();setupDialog.dismiss();render();}));form.addView(button("Finish for now",false,()->{setupDialog.dismiss();finishPause();}));sheet("How’s the urge now?",form);}
    private void finishPause(){State.prefs(this).edit().remove("pauseEnd").apply();page="home";render();}
    private void updatePreview(){if(preview==null)return;JSONObject state=new JSONObject();try{state.put("preview",true);state.put("scene","Dusk");state.put("still",!animations());}catch(JSONException e){throw new IllegalStateException(e);}preview.update(state);preview.setMotionAllowed(animations());}
    private void plan(){
        LinearLayout form=column();preview=new AlpineScreen(this,ignored->{},this::updatePreview,()->{});preview.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);form.addView(preview,new LinearLayout.LayoutParams(-1,dp(145)));updatePreview();
        Switch graphics=new Switch(this);graphics.setText("Animated landscape");styleToggle(graphics);graphics.setChecked(State.prefs(this).getBoolean("graphics",true));form.addView(graphics);graphics.setOnCheckedChangeListener((v,on)->{State.prefs(this).edit().putBoolean("graphics",on).apply();applyMotion();updatePreview();});note(form,"Motion also rests in Battery Saver, with system animations off, and when BLOCKER is out of view.");
        Switch game=new Switch(this);game.setText("Firefly jar on Home");styleToggle(game);game.setChecked(State.prefs(this).getBoolean("game",false));form.addView(game);game.setOnCheckedChangeListener((v,on)->{State.prefs(this).edit().putBoolean("game",on).apply();if(!animations())v.jumpDrawablesToCurrentState();});
        EditText reason=field("My reason",State.reason(this),false),step=field("My next step",State.step(this),false);step.setSingleLine(false);step.setMinLines(2);step.setMaxLines(3);note(form,"My reason");form.addView(reason);TextView reasonError=errorLabel(form);note(form,"My next step");form.addView(step);TextView stepError=errorLabel(form);clearErrorOnEdit(reason,reasonError);clearErrorOnEdit(step,stepError);
        CheckBox remind=new CheckBox(this);remind.setText("Remind me each day");styleToggle(remind);remind.setChecked(State.prefs(this).getBoolean("remind",false));remind.setOnCheckedChangeListener((v,on)->{if(!animations())v.jumpDrawablesToCurrentState();});form.addView(remind);int[] time={State.prefs(this).getInt("hour",22),State.prefs(this).getInt("minute",30)};
        Button timeButton=button(reminderLabel(time),false,null);timeButton.setOnClickListener(v->reminderTime(time,timeButton));form.addView(timeButton);note(form,"Landscape and jar preferences save immediately.");
        form.addView(button("Save my plan",true,()->{String r=reason.getText().toString().trim(),next=step.getText().toString().trim();if(r.isEmpty()){fieldError(reasonError,reason,"Add your reason.");return;}if(next.isEmpty()){fieldError(stepError,step,"Choose a next step.");return;}State.prefs(this).edit().putString("reason",r).putString("step",next).putBoolean("remind",remind.isChecked()).putBoolean("alertsPaused",false).putInt("hour",time[0]).putInt("minute",time[1]).apply();if(remind.isChecked())notificationPermission();Notices.schedule(this);setupDialog.dismiss();render();}));sheet("My plan & landscape",form);
    }
    private String reminderLabel(int[] time){return String.format(Locale.getDefault(),"Reminder · %02d:%02d",time[0],time[1]);}
    private void reminderTime(int[] time,Button label){
        Dialog d=new Dialog(this);LinearLayout panel=popupPanel("Reminder time",false),form=column();note(form,"Choose a time in 24-hour format.");LinearLayout row=new LinearLayout(this);
        EditText hour=field("Hour, 0 to 23",String.format(Locale.getDefault(),"%02d",time[0]),true),minute=field("Minute, 0 to 59",String.format(Locale.getDefault(),"%02d",time[1]),true);
        int index=0;for(EditText f:new EditText[]{hour,minute}){LinearLayout group=new LinearLayout(this);group.setOrientation(LinearLayout.VERTICAL);TextView caption=text(index++==0?"Hour":"Minute",13,MUTED);caption.setPadding(0,0,0,dp(7));group.addView(caption);f.setTextSize(28);f.setGravity(Gravity.CENTER);f.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(2)});group.addView(f,new LinearLayout.LayoutParams(-1,-2));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.setMargins(dp(4),0,dp(4),0);row.addView(group,lp);}form.addView(row);TextView error=errorLabel(form);clearErrorOnEdit(hour,error);clearErrorOnEdit(minute,error);
        form.addView(button("Set time",true,()->{int h,m;try{h=Integer.parseInt(hour.getText().toString());m=Integer.parseInt(minute.getText().toString());}catch(NumberFormatException e){fieldError(error,hour,"Enter an hour and minute.");return;}if(h<0||h>23){fieldError(error,hour,"Hours run from 0 to 23.");return;}if(m<0||m>59){fieldError(error,minute,"Minutes run from 0 to 59.");return;}time[0]=h;time[1]=m;label.setText(reminderLabel(time));d.dismiss();}));form.addView(button("Cancel",false,d::dismiss));panel.addView(form);PopupScroll scroll=new PopupScroll();scroll.setBackground(surface(CARD,26));scroll.setClipToOutline(true);scroll.addView(panel);showPopup(d,scroll,false,true,-2);
    }
    private Button leafChip(String label,Runnable run){Button b=button("❧  "+label,false,run);GradientDrawable d=surface(0xff3d5947,22);d.setCornerRadii(new float[]{dp(26),dp(26),dp(7),dp(7),dp(26),dp(26),dp(7),dp(7)});b.setBackground(d);return b;}
    private void checkIn(){String day=LocalDate.now().toString();LinearLayout form=column();note(form,"Every honest check-in counts. You can edit today’s entry later.");form.addView(leafChip("I didn’t watch",()->{State.prefs(this).edit().putString("day_"+day,"free").remove("trigger_"+day).apply();page="week";setupDialog.dismiss();render();}));form.addView(leafChip("I watched",()->{State.prefs(this).edit().putString("day_"+day,"watched").remove("trigger_"+day).apply();setupDialog.dismiss();LinearLayout triggers=column();note(triggers,"A little context can help you notice patterns.");for(String t:new String[]{"Scrolling","Bored","Stressed","Lonely","Something else","Skip"})triggers.addView(leafChip(t,()->{State.prefs(this).edit().putString("trigger_"+day,t.equals("Skip")?"":t).apply();page="week";setupDialog.dismiss();render();}));sheet("What was happening?",triggers);}));form.addView(button("Later",false,()->setupDialog.dismiss()));sheet("How did today go?",form);}
    private void deleteEntries(){
        confirmation("Delete all check-ins?","This removes your daily entries and trigger notes.","Keep check-ins","Delete check-ins",()->{android.content.SharedPreferences.Editor edit=State.prefs(this).edit();for(String key:State.prefs(this).getAll().keySet())if(key.startsWith("day_")||key.startsWith("trigger_"))edit.remove(key);edit.apply();render();},true);
    }
    private void permissions() {
        LinearLayout content=column();
        note(content,"Blocking runs in a foreground service only while a session is active.");
        note(content,"Usage & overlay access · "+(enabled()?"Enabled":State.pending(this)?"Waiting for setup":"Asked when you start"));
        content.addView(button(State.pending(this)?"Finish access setup":"About blocking access",false,()->{
            setupDialog.dismiss();
            if(State.pending(this)){lastSetupLaunch=-2000L;openAccessibility();}
            else confirmation("Blocking access","BLOCKER uses Android Usage Access (UsageStatsManager) and Display over other apps to detect foreground app launches and return selected apps, app-info/permission screens, app stores and uninstall screens to your timer without using AccessibilityService.",null,"Got it",()->{},false);
        }));
        boolean alerts=getSystemService(NotificationManager.class).areNotificationsEnabled();
        content.addView(button("Session alerts · "+(alerts?"On":"Off")+"    ›",false,()->{
            setupDialog.dismiss();
            if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},10);
            else startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName()));
        }));
        note(content,protectionExplanation());
        note(content,"The foreground monitoring service stops at session expiry or after the full chosen continuous hold.");
        content.addView(button("Uninstall BLOCKER",false,()->{setupDialog.dismiss();confirmUninstall();}));content.addView(button("Done",true,()->setupDialog.dismiss()));sheet("Session setup",content);
    }
    private boolean managedAvailable(){return UninstallGuard.owner(this)||UninstallGuard.delegated(this)||UninstallGuard.managedBlocked(this);}
    private String protectionExplanation(){
        return managedAvailable()?"Managed session. The timer starts only after Android confirms the uninstall policy.":
            "During a session, BLOCKER guards app-info, permission, device-admin, app store and uninstall screens while keeping main Settings available. Device admin must be active before the timer starts.";
    }
    private void startBlocking() {
        if(State.active(this)||State.pending(this))return;
        Set<String> chosen=State.selected(this);chosen.removeAll(AppCatalog.essential(this));
        if(chosen.isEmpty()){toast("Select at least one app first.");return;}
        int minutes=State.prefs(this).getInt("duration",25);
        boolean managed=managedAvailable();
        overlayPrompted=false;
        lastSetupLaunch=-2000L;
        State.prefs(this).edit().putBoolean("managedSession",managed).apply();
        State.select(this,chosen);State.prepare(this,minutes);render();
        if(!managed&&!UninstallGuard.adminActive(this))requestGuard();
        else openAccessibility();
    }
    private void requestGuard() {
        Intent request=new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN,UninstallGuard.component(this))
            .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,"Android requires deactivation before uninstalling. BLOCKER releases this guard after the session ends or you complete the chosen continuous hold.");
        try {startActivityForResult(request,20);}
        catch(ActivityNotFoundException error){cancelUnprotectedSetup();}
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(request==20&&State.pending(this)) {
            if(UninstallGuard.adminActive(this))openAccessibility();
            else if(result==RESULT_OK)handler.postDelayed(()->{if(UninstallGuard.adminActive(this))openAccessibility();else cancelUnprotectedSetup();},150);
            else cancelUnprotectedSetup();
        }
    }
    private void cancelUnprotectedSetup(){BlockerService.exit(this);render();web.event("setup-failed");LinearLayout form=column();note(form,"Android did not confirm the guard. Your session has not started.");form.addView(button("Go back",true,()->setupDialog.dismiss()));sheet("Setup wasn’t completed",form);if(animations())form.startAnimation(android.view.animation.AnimationUtils.loadAnimation(this,R.anim.failure_shake));}
    private void openAccessibility() {
        if(!State.pending(this))return;
        if(!State.prefs(this).getBoolean("managedSession",false)&&!UninstallGuard.adminActive(this)){requestGuard();return;}
        BlockingAccess.prepare(this);
        if(enabled()) {
            BlockerService.connectPending(this);render();
        } else {
            long now=SystemClock.elapsedRealtime();
            if(now-lastSetupLaunch<900L)return;
            lastSetupLaunch=now;
            try {startActivity(BlockingAccess.nextPermissionIntent(this));}
            catch(ActivityNotFoundException e){toast("Enable Usage Access and Display over other apps in Android Settings.");}
        }
    }
    private void updateTimers() {
        if(State.expired(this)){BlockerService.complete(this);page="complete";}else if(State.prefs(this).getBoolean("pending",false)&&!State.pending(this))BlockerService.exit(this);
        if(!State.active(this)&&State.prefs(this).getBoolean("exitAfterSession",false)&&!exiting){exit();return;}
        if(!State.active(this)&&State.prefs(this).getBoolean("showComplete",false)&&(lastSession&2)!=0)page="complete";
        int phase=State.active(this)?2:State.pending(this)?1:0;
        if(phase!=(lastSession&3)&&!exiting){render();return;}
        if(web!=null&&!exiting&&(State.active(this)||page.equals("pause"))) {
            long full=Math.max(0,State.prefs(this).getLong("sessionEnd",0)-SystemClock.elapsedRealtime());
            if(State.active(this)&&web.checkCompletion())return;
            web.times(full,web.holdLeft(),pauseLeft());
        }
    }
    private void finishHeldSession(){if(!resumed||!State.active(this)||exiting)return;BlockerService.complete(this);exit();}
    private void confirmUninstall(){if(State.active(this)){toast("Finish the session or your hold first.");return;}confirmation("Uninstall BLOCKER?","BLOCKER will stop its service and release its guard, then open Android’s uninstall confirmation. Uninstalling removes your local plan and history.","Keep BLOCKER","Continue",()->{uninstallRequested=true;exit();},true);}
    private void exit(){
        if(State.expired(this))BlockerService.complete(this);
        if(State.active(this)){
            State.prefs(this).edit().putBoolean("exitAfterSession",true).apply();page="block";render();
            confirmation("Session in progress","Wait for the timer ("+State.clock(State.remaining(this))+" remaining), or keep one finger inside the circle for "+(State.sessionHold(this)/60000)+" continuous minutes to exit.",null,"Back to timer",()->{},false);return;
        }
        if(exiting)return;exiting=true;exitStarted=SystemClock.elapsedRealtime();handler.removeCallbacks(ticker);
        if(web!=null){web.cancelHold();web.setMotionAllowed(false);web.onPause();web.pauseTimers();}
        for(Dialog dialog:new ArrayList<>(nativeDialogs))dialog.dismiss();
        State.prefs(this).edit().putBoolean("exitAfterSession",false).putBoolean("showComplete",false).apply();
        BlockerService.exit(this);Notices.quietExit(this);finishQuietExit();
    }
    private void finishQuietExit(){
        if(!exiting||exitFinished||isDestroyed()||isFinishing())return;
        handler.removeCallbacks(exitCheck);
        boolean released=BlockingAccess.disabled(this)&&!UninstallGuard.active(this);
        if(!released&&SystemClock.elapsedRealtime()-exitStarted<3000){BlockerService.exit(this);handler.postDelayed(exitCheck,250);return;}
        exitFinished=true;BlockingAccess.disable(this);
        if(uninstallRequested)try{startActivity(new Intent(Intent.ACTION_DELETE,android.net.Uri.parse("package:"+getPackageName())));}catch(ActivityNotFoundException ignored){}
        BlockingAccess.releaseRuntimePermissions(this);finishAndRemoveTask();
    }
    private void notificationPermission() {
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},10);
    }
    private void toast(String message){Toast.makeText(this,message,Toast.LENGTH_SHORT).show();}
}
