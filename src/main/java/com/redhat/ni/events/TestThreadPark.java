package com.redhat.ni.events;
import com.redhat.ni.tester.Test;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedClass;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedObject;
import jdk.jfr.consumer.RecordingFile;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.locks.LockSupport;
import com.redhat.ni.tester.Tester;
import static com.redhat.ni.tester.Tester.makeCopy;

public class TestThreadPark implements Test {

    class Blocker {
    }
    final Blocker blocker = new Blocker();
    @Override
    public void test() throws Exception {
        Recording recording = new Recording();
        recording.enable("jdk.ThreadPark").withThreshold(Duration.ofMillis(1));
        boolean parkNanosFound = false;
        boolean parkNanosFoundBlocker = false;
        try {
            recording.start();
            LockSupport.parkNanos(1000 * 1000000);
            LockSupport.parkNanos(blocker, 1000*1000000);
            // sleep so we know the event is recorded
            Thread.sleep(500);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            recording.stop();
        }

        List<RecordedEvent> events = RecordingFile.readAllEvents(makeCopy(recording));
        for (RecordedEvent event : events) {
            if (event.getEventType().getName().equals("jdk.ThreadPark")) {
                if (Tester.isEqualDuration(event.getDuration(), Duration.ofSeconds(1))) {
                    RecordedObject struct = event;
                    if (struct.getValue("parkedClass") == null) {
                        parkNanosFound = true;
                    } else
                        if (struct.<RecordedClass>getValue("parkedClass").getName().equals("com.redhat.ni.events.TestThreadPark$Blocker")) {
                            parkNanosFoundBlocker = true;
                        }
                }
            }
        }
        if (!parkNanosFound){
            throw new Exception("parkNanosFound false");
        }
        if (!parkNanosFoundBlocker){
            throw new Exception("parkNanosFoundBlocker false");
        }

    }

    @Override
    public String getName(){
        return "thread park test";
    }
}