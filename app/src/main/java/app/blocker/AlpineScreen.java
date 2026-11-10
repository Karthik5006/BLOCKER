package app.blocker;
import android.app.Activity;import android.webkit.*;import android.os.*;import android.view.*;import java.io.*;import java.nio.charset.StandardCharsets;import java.util.function.Consumer;import org.json.JSONObject;
/** Offline presentation. Only native, uninterrupted touch can complete a hold. */
final class AlpineScreen extends WebView {
 private static String htmlCache;private boolean ready,sessionActive,motionAllowed,tracking;private final ContinuousHold hold=new ContinuousHold();private final Runnable completedHold;private final Handler handler=new Handler(Looper.getMainLooper());private double left,top,right,bottom;private int milestones;
 private final Runnable holdTick=new Runnable(){public void run(){
  if(!tracking)return;
  if(!hasWindowFocus()||!isShown()||!State.active(getContext())||!getContext().getSystemService(PowerManager.class).isInteractive()){cancelHold();return;}
  long remaining=hold.left(SystemClock.elapsedRealtime());double progress=Math.max(0,Math.min(1,1.0-remaining/(double)hold.duration()));int stage=Math.min(3,(int)(progress*4));if(stage>milestones){milestones=stage;performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);}
  if(ready)evaluateJavascript("holdProgress("+progress+","+remaining+","+hold.duration()+")",null);if(checkCompletion())return;handler.postDelayed(this,100);
 }};
 AlpineScreen(Activity activity,Consumer<String> actions,Runnable didLoad,Runnable completedHold){
  super(activity);this.completedHold=completedHold;setBackgroundColor(0xff142a25);setFilterTouchesWhenObscured(true);WebSettings s=getSettings();s.setJavaScriptEnabled(true);s.setAllowFileAccess(false);s.setAllowContentAccess(false);s.setBlockNetworkLoads(true);s.setMediaPlaybackRequiresUserGesture(true);
  addJavascriptInterface(new Object(){
   @JavascriptInterface public void ringBounds(double l,double t,double r,double b){activity.runOnUiThread(()->{
    if(!Double.isFinite(l)||!Double.isFinite(t)||!Double.isFinite(r)||!Double.isFinite(b)||l<0||t<0||r>1||b>1||r<=l||b<=t){left=top=right=bottom=0;cancelHold();return;}
    if(Math.abs(l-left)>.002||Math.abs(t-top)>.002||Math.abs(r-right)>.002||Math.abs(b-bottom)>.002)cancelHold();left=l;top=t;right=r;bottom=b;
   });}
   @JavascriptInterface public void action(String name){activity.runOnUiThread(()->{if(!activity.isDestroyed()&&!activity.isFinishing())actions.accept(name);});}
  },"BlockerUI");
  setWebViewClient(new WebViewClient(){public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){return true;}public boolean shouldOverrideUrlLoading(WebView v,String u){return true;}public void onPageFinished(WebView v,String u){ready=true;setMotionAllowed(motionAllowed);didLoad.run();}});
  if(htmlCache==null)htmlCache=asset(activity,"alpine.html").replace("<!--VALLEY-->",asset(activity,"valley.svg")).replace("/*NATURAL_CSS*/",asset(activity,"natural.css")).replace("/*NATURAL_JS*/",asset(activity,"natural.js"));
  loadDataWithBaseURL("https://blocker.invalid/",htmlCache,"text/html","UTF-8",null);
 }
 private static String asset(Activity a,String name){try(BufferedReader r=new BufferedReader(new InputStreamReader(a.getAssets().open(name),StandardCharsets.UTF_8))){StringBuilder out=new StringBuilder();String line;while((line=r.readLine())!=null)out.append(line).append('\n');return out.toString();}catch(IOException e){throw new IllegalStateException(name,e);}}
 void update(JSONObject s){sessionActive=s.optBoolean("active",false);long duration=s.optLong("holdDuration",300000);if(duration!=hold.duration())hold.duration(duration);if(!sessionActive)cancelHold(false);if(ready)evaluateJavascript("screenState(JSON.parse("+JSONObject.quote(s.toString())+"))",null);}
 void setMotionAllowed(boolean allowed){motionAllowed=allowed;if(ready)evaluateJavascript("setMotion("+allowed+")",null);}
 void event(String name){if(ready)evaluateJavascript("sceneEvent("+JSONObject.quote(name)+")",null);}
 void times(long session,long early,long pause){if(ready)evaluateJavascript("times("+session+","+early+","+pause+")",null);}
 private boolean inside(float x,float y){double w=(right-left)*getWidth(),h=(bottom-top)*getHeight();if(w<=0||h<=0)return false;double dx=2*(x-left*getWidth())/w-1,dy=2*(y-top*getHeight())/h-1;return dx*dx+dy*dy<=1;}
 public boolean onTouchEvent(MotionEvent e){int action=e.getActionMasked(),obscured=MotionEvent.FLAG_WINDOW_IS_OBSCURED;if(Build.VERSION.SDK_INT>=29)obscured|=MotionEvent.FLAG_WINDOW_IS_PARTIALLY_OBSCURED;if((e.getFlags()&obscured)!=0){cancelHold();return true;}
  if(action==MotionEvent.ACTION_DOWN&&sessionActive&&State.active(getContext())&&inside(e.getX(),e.getY())&&hasWindowFocus()&&e.getPointerCount()==1){tracking=true;milestones=0;hold.begin(SystemClock.elapsedRealtime());setKeepScreenOn(true);getParent().requestDisallowInterceptTouchEvent(true);event("hold-start");handler.removeCallbacks(holdTick);handler.post(holdTick);return true;}
  if(!tracking)return super.onTouchEvent(e);if(action==MotionEvent.ACTION_CANCEL||e.getPointerCount()!=1||!inside(e.getX(),e.getY())){cancelHold();return true;}for(int i=0;i<e.getHistorySize();i++)if(!inside(e.getHistoricalX(i),e.getHistoricalY(i))){cancelHold();return true;}
  if(action==MotionEvent.ACTION_UP&&!checkCompletion())cancelHold();return true;
 }
 boolean holding(){return tracking;}long holdLeft(){return hold.left(SystemClock.elapsedRealtime());}
 boolean checkCompletion(){if(!tracking)return false;if(!hasWindowFocus()||!isShown()){cancelHold();return false;}if(!hold.takeCompletion(SystemClock.elapsedRealtime()))return false;cancelHold(false);completedHold.run();return true;}
 public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);if(!focus&&hold!=null)cancelHold();}protected void onDetachedFromWindow(){cancelHold(false);super.onDetachedFromWindow();}
 void cancelHold(){cancelHold(true);}private void cancelHold(boolean feedback){boolean was=tracking;hold.cancel();tracking=false;handler.removeCallbacks(holdTick);setKeepScreenOn(false);if(was&&feedback){event("hold-lift");if(hasWindowFocus())performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);}}
 public void destroy(){handler.removeCallbacksAndMessages(null);removeJavascriptInterface("BlockerUI");super.destroy();}
}
