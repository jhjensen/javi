package javi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javi.lsp.LspRegistry;
import javi.lsp.LspSession;

import static history.Tools.trace;

/**
 * LSP-based tag lookup provider that integrates with {@code :ta} (Ctrl-]).
 *
 * <p>Registered as {@link TagLookupProvider.LookupType#DEFINITIONS} so
 * LSP definitions are queried before ctags. Only queries the LSP server
 * if a session is already running for the file extension (never blocks
 * on server start per R27).</p>
 */
final class LspTagLookupProvider implements TagLookupProvider {

   @Override
   public LookupType getType() {
      return LookupType.DEFINITIONS;
   }

   @Override
   public boolean tryLookup(FvContext fvc) {
      return false; // let lookupPositions handle it
   }

   @Override
   public List<Position> lookupPositions(FvContext fvc, String tagName) {
      LspRegistry reg = LspCommands.getRegistry();
      if (null == reg || !reg.isEnabled())
         return Collections.emptyList();

      String ext = getExtension(fvc);
      LspSession session = reg.runningSessionFor(ext);
      if (null == session || !session.isReady())
         return Collections.emptyList();

      Map<String, Object> params = textDocPosition(fvc);
      Map<String, Object> result;
      try {
         var future = session.submit("textDocument/definition", params);
         EventQueue.biglock2.unlock();
         try {
            result = future.get(LspCommands.DEFAULT_REQUEST_TIMEOUT_SECONDS,
               java.util.concurrent.TimeUnit.SECONDS);
         } finally {
            EventQueue.biglock2.lock();
         }
      } catch (Exception e) {
         trace("LspTagLookup: definition failed: " + e);
         return Collections.emptyList();
      }

      if (null == result)
         return Collections.emptyList();

      return extractLocations(result);
   }

   @Override
   public String getHoverInfo(FvContext fvc) {
      LspRegistry reg = LspCommands.getRegistry();
      if (null == reg || !reg.isEnabled())
         return null;

      String ext = getExtension(fvc);
      LspSession session = reg.runningSessionFor(ext);
      if (null == session || !session.isReady())
         return null;

      Map<String, Object> params = textDocPosition(fvc);
      Map<String, Object> result;
      try {
         var future = session.submit("textDocument/hover", params);
         EventQueue.biglock2.unlock();
         try {
            result = future.get(LspCommands.DEFAULT_REQUEST_TIMEOUT_SECONDS,
               java.util.concurrent.TimeUnit.SECONDS);
         } finally {
            EventQueue.biglock2.lock();
         }
      } catch (Exception e) {
         return null;
      }

      if (null == result)
         return null;

      Object contents = result.get("contents");
      if (contents instanceof Map) {
         @SuppressWarnings("unchecked")
         Map<String, Object> mc = (Map<String, Object>) contents;
         Object val = mc.get("value");
         return val != null ? val.toString() : null;
      } else if (contents instanceof String) {
         return (String) contents;
      }
      return null;
   }

   private static String getExtension(FvContext fvc) {
      FileDescriptor fd = fvc.edvec.fdes();
      String name = fd.canonName;
      int dot = name.lastIndexOf('.');
      return dot >= 0 ? name.substring(dot) : "";
   }

   private static Map<String, Object> textDocPosition(FvContext fvc) {
      FileDescriptor fd = fvc.edvec.fdes();
      String uri;
      if (fd instanceof FileDescriptor.LocalFile lf)
         uri = "file://" + lf.canonName;
      else
         uri = "file://" + fd.canonName;

      Map<String, Object> pos = new HashMap<>();
      pos.put("line", fvc.inserty() - 1);
      pos.put("character", fvc.insertx());
      Map<String, Object> textDoc = new HashMap<>();
      textDoc.put("uri", uri);
      Map<String, Object> params = new HashMap<>();
      params.put("textDocument", textDoc);
      params.put("position", pos);
      return params;
   }

   @SuppressWarnings("unchecked")
   private static List<Position> extractLocations(Map<String, Object> result) {
      List<Position> positions = new ArrayList<>();

      // Result may contain locations directly or nested under "result" key
      Object data = result.get("uri") != null ? result : result.get("result");

      if (data instanceof List<?> list) {
         for (Object item : list) {
            if (item instanceof Map) {
               Position p = locationToPosition((Map<String, Object>) item);
               if (p != null)
                  positions.add(p);
            }
         }
      } else if (data instanceof Map) {
         Position p = locationToPosition((Map<String, Object>) data);
         if (p != null)
            positions.add(p);
      }

      return positions;
   }

   @SuppressWarnings("unchecked")
   private static Position locationToPosition(Map<String, Object> loc) {
      String uri = (String) loc.get("uri");
      if (null == uri)
         return null;

      String path = uri.startsWith("file://") ? uri.substring(7) : uri;
      int line = 1;
      int col = 0;

      Map<String, Object> range = (Map<String, Object>) loc.get("range");
      if (null != range) {
         Map<String, Object> start = (Map<String, Object>) range.get("start");
         if (null != start) {
            Object lnum = start.get("line");
            if (lnum instanceof Number)
               line = ((Number) lnum).intValue() + 1;
            Object cnum = start.get("character");
            if (cnum instanceof Number)
               col = ((Number) cnum).intValue();
         }
      }

      return new Position(col, line, path, "lspdef");
   }
}
