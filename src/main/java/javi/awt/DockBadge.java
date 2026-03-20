package javi.awt;

import java.awt.Taskbar;

import javi.EventQueue;
import javi.FileList;
import javi.ShellManager;

import static history.Tools.trace;

/**
 * Updates the macOS dock icon badge with the count of unsaved files
 * and active shell sessions. The badge auto-updates via the idle handler
 * mechanism, refreshing whenever the event queue becomes idle.
 */
final class DockBadge {

   private DockBadge() {
   }

   private static boolean supported;

   /**
    * Initializes dock badge support. Call once during startup.
    * Registers an idle handler to keep the badge current.
    */
   static void init() {
      if (Taskbar.isTaskbarSupported()) {
         Taskbar taskbar = Taskbar.getTaskbar();
         supported = taskbar.isSupported(Taskbar.Feature.ICON_BADGE_NUMBER);
      }
      if (supported)
         EventQueue.registerIdle(DockBadge::updateBadge);
   }

   /**
    * Recomputes and sets the dock badge. Shows the combined count of
    * modified files and active shell sessions, or clears the badge
    * if the count is zero.
    */
   static void updateBadge() {
      if (!supported)
         return;

      int count = FileList.countModified()
         + ShellManager.getInstance().getSessionCount();

      String badge = count > 0 ? Integer.toString(count) : "";
      try {
         Taskbar.getTaskbar().setIconBadge(badge);
      } catch (UnsupportedOperationException e) {
         trace("DockBadge: badge not supported: " + e);
         supported = false;
      }
   }
}
