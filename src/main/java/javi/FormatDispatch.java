package javi;

import java.io.IOException;

/**
 * Polymorphic format command that dispatches to the appropriate formatter
 * based on file extension.
 *
 * <p>Supports:</p>
 * <ul>
 *   <li>{@code .java} → jformat command (via plugin, e.g. JavaFormat)</li>
 *   <li>{@code .c .cc .cpp .cxx .h .hpp .hxx} → {@link ClangFormat}
 *       (clang-format)</li>
 * </ul>
 *
 * <p>Usage:</p>
 * <pre>{@code
 * :format     " Format entire buffer (auto-detects file type)
 * v}F         " Visual select, then F to format range
 * }</pre>
 *
 * @see ClangFormat
 */
public final class FormatDispatch extends Rgroup {

   enum Cmd {
      UNUSED,    // 0
      FORMAT,    // 1: format entire buffer (:format)
      FORMATR,   // 2: format range (:formatr)
   }

   private static final Cmd[] CMDS = Cmd.values();

   FormatDispatch() {
      final String[] rnames = {
         "",
         "format",     // Format entire buffer (polymorphic)
         "formatr",    // Format range (polymorphic)
      };
      register(rnames);
   }

   public Object doroutine(int rnum, Object arg, int count, int rcount,
         FvContext fvc, boolean dotmode) throws IOException {
      Cmd cmd = CMDS[rnum];
      switch (cmd) {
         case FORMAT:
            formatAll(fvc.edvec);
            return null;
         case FORMATR:
            if (count > 0 && rcount > 0)
               formatRange(count, rcount, fvc.edvec);
            else
               formatAll(fvc.edvec);
            return null;
         default:
            throw new RuntimeException("FormatDispatch:default");
      }
   }

   /**
    * Detect file type from filename extension.
    *
    * @param filename the filename to check
    * @return "java", "cpp", or null if unknown
    */
   static String detectFileType(String filename) {
      if (filename == null)
         return null;
      if (filename.endsWith(".java"))
         return "java";
      if (filename.matches(".*\\.(c|cc|cpp|cxx|h|hpp|hxx)$"))
         return "cpp";
      return null;
   }

   /**
    * Format a range of lines, dispatching to the appropriate formatter
    * based on the buffer's file extension.
    *
    * @param startLine first line to format (1-based)
    * @param endLine last line to format (1-based, inclusive)
    * @param ex the TextEdit buffer
    */
   @SuppressWarnings("unchecked")
   static void formatRange(int startLine, int endLine, TextEdit ex) {
      String name = ex.getName();
      String type = detectFileType(name);
      if ("java".equals(type)) {
         Rgroup.KeyBinding kb = Rgroup.bindingLookup("jformatr");
         if (kb != null) {
            try {
               kb.dobind(startLine, endLine, FvContext.getCurrFvc(), false);
            } catch (Exception e) {
               UI.reportError("Java format: " + e.getMessage());
            }
         } else {
            UI.reportMessage(
               "Java formatter plugin not loaded"
               + " (use :loadplugin JavaFormat)");
         }
      } else if ("cpp".equals(type)) {
         ClangFormat.format(startLine, endLine, ex);
      } else {
         UI.reportMessage("No formatter for: " + name);
      }
   }

   /**
    * Format the entire buffer, dispatching based on file extension.
    *
    * @param ex the TextEdit buffer
    */
   @SuppressWarnings("unchecked")
   static void formatAll(TextEdit ex) {
      String name = ex.getName();
      String type = detectFileType(name);
      if ("java".equals(type)) {
         Rgroup.KeyBinding kb = Rgroup.bindingLookup("jformat");
         if (kb != null) {
            try {
               kb.dobind(0, 0, FvContext.getCurrFvc(), false);
            } catch (Exception e) {
               UI.reportError("Java format: " + e.getMessage());
            }
         } else {
            UI.reportMessage(
               "Java formatter plugin not loaded"
               + " (use :loadplugin JavaFormat)");
         }
      } else if ("cpp".equals(type)) {
         ClangFormat.format(1, ex.readIn() - 1, ex);
      } else {
         UI.reportMessage("No formatter for: " + name);
      }
   }

}
