package app.blocker;

public final class DeadlineTest {
    private static void equals(Object expected,Object actual) {
        if(!expected.equals(actual))throw new AssertionError(expected+" != "+actual);
    }
    public static void main(String[] args) {
        ContinuousHold hold=new ContinuousHold();
        equals(-1L,hold.left(0));
        hold.begin(100);
        equals(false,hold.takeCompletion(300099));
        equals(1L,hold.left(300099));
        hold.cancel();
        equals(false,hold.takeCompletion(600100));
        hold.begin(600100);
        equals(300000L,hold.left(600100));
        equals(false,hold.takeCompletion(900099));
        equals(true,hold.takeCompletion(900100));
        equals(false,hold.takeCompletion(900101));
        equals("05:00:00",Deadline.display(5*3600000L,true));
        equals("10:00:00",Deadline.display(10*3600000L,true));
        equals("00:00:01",Deadline.display(1,true));
        equals("01:00:00",Deadline.display(3_600_000,true));
        equals("23:59:00",Deadline.display(86_340_000,true));
        equals("0:00",Deadline.display(-100,false));
        equals("5:00",Deadline.display(300_000,false));
        System.out.println("Five-minute continuous hold, reset, single completion and session display checks passed.");
    }
}
