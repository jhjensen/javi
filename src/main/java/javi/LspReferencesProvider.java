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
 * LSP-based references provider that integrates with {@code :ta} (Ctrl-]).
 *
 * <p>Registered as {@link TagLookupProvider.LookupType#REFERENCES} so
 * LSP references are queried after ctags (per R25/R26).</p>
 */
final class LspReferencesProvider implements TagLookupProvider {

   @Override
   public LookupType getType() {
      return LookupType.REFERENCES;
   }

   @Override
   public boolean tryLookup(FvContext fvc) {
      return false;
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
      Map<String, Object> context = new HashMap<>();
      context.put("includeDeclaration", Boolean.TRUE);
      params.put("context", context);

      Map<String, Object> result;
      try {
         result = session.submit("textDocument/references", params)
            .get(LspCommands.DEFAULT_REQUEST_TIMEOUT_SECONDS,
               java.util.concurrent.TimeUnit.SECONDS);
      } catch (Exception e) {
         trace("LspReferencesProvider: failed: " + e);
         return Collections.emptyList();
      }

      if (null == result)
         return Collections.emptyList();

      return extractLocations(result);
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

      return new Position(col, line, path, "lspref");
   }
}
