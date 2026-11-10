package app.blocker;
import org.junit.Test;import static org.junit.Assert.*;
public class ContinuousHoldTest {
 @Test public void exactDeadlineOnly(){ContinuousHold h=new ContinuousHold();h.begin(1000);assertEquals(1,h.left(300999));assertFalse(h.takeCompletion(300999));assertTrue(h.takeCompletion(301000));assertFalse(h.takeCompletion(301001));}
 @Test public void liftDestroysProgress(){ContinuousHold h=new ContinuousHold();h.begin(1000);h.left(300999);h.cancel();assertEquals(-1,h.left(999999));assertFalse(h.takeCompletion(999999));h.begin(1000000);assertEquals(300000,h.left(1000000));}
 @Test public void allThreeDurations(){for(long d:new long[]{180000,300000,600000}){ContinuousHold h=new ContinuousHold();h.duration(d);h.begin(0);assertFalse(h.takeCompletion(d-1));assertTrue(h.takeCompletion(d));}}
 @Test public void backwardsClockCancels(){ContinuousHold h=new ContinuousHold();h.begin(1000);assertEquals(-1,h.left(999));assertFalse(h.takeCompletion(Long.MAX_VALUE));}
 @Test(expected=IllegalArgumentException.class) public void rejectsUnsupportedHold(){new ContinuousHold().duration(1000);}
 @Test public void durationChangeCancels(){ContinuousHold h=new ContinuousHold();h.begin(0);h.duration(600000);assertFalse(h.holding());}
 @Test public void countdownRoundsUp(){assertEquals("5:00",Deadline.display(299999,false));assertEquals("05:00:00",Deadline.display(18000000,true));assertEquals("00:00:00",Deadline.display(-1000,true));}
}
