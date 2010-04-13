package javi;

import java.awt.AWTEvent;

abstract class ExecuteEvent extends AWTEvent implements Runnable {
   public static final int eventId = AWTEvent.RESERVED_ID_MAX + 1;

   private static java.awt.EventQueue eventQueue =
      java.awt.Toolkit.getDefaultToolkit().getSystemEventQueue();
   ExecuteEvent(Object target) {
      super(target, eventId);
      eventQueue.postEvent(this);
   }
}
