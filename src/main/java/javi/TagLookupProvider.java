package javi;

import java.util.Collections;
import java.util.List;

/**
 * Interface for tag lookup providers that integrate with {@code :ta}.
 *
 * <p>Implementations provide tag lookup mechanisms (LSP definitions,
 * ctags, LSP references, lid/mkid) that are queried by {@code :ta}.
 * Results are collected from all providers, grouped by
 * {@link LookupType}: DEFINITIONS first, then BOTH, then REFERENCES.
 * Within each group, providers are queried in registration order.
 * Duplicate positions (same file:line) are eliminated, keeping
 * the entry from the higher-priority provider.</p>
 *
 * @see PosListList.Cmd
 */
public interface TagLookupProvider {

   /**
    * Classifies what kind of results a provider returns.
    *
    * <p>Used by {@code :ta} to group results in the correct order:
    * definitions first, then general results, then references.</p>
    */
   enum LookupType {
      /** Provider returns definition locations (e.g., LSP definition). */
      DEFINITIONS,
      /** Provider returns references (e.g., LSP references, lid/mkid). */
      REFERENCES,
      /** Provider returns both definitions and references (e.g., ctags). */
      BOTH
   }

   /**
    * Returns what kind of results this provider produces.
    *
    * <p>Used to order results: DEFINITIONS providers are queried first,
    * then BOTH, then REFERENCES.</p>
    *
    * @return the lookup type for this provider
    */
   default LookupType getType() {
      return LookupType.BOTH;
   }

   /**
    * Attempts to look up the tag/symbol at the current cursor position.
    *
    * <p>If the provider can handle the request (e.g., LSP server is
    * running for this file type), it should navigate to the definition
    * and return true. If it cannot handle it, return false to let
    * the next provider try.</p>
    *
    * @param fvc the current file-view context
    * @return true if the lookup was handled, false to fall through
    */
   boolean tryLookup(FvContext fvc);

   /**
    * Returns positions for the given symbol name.
    *
    * <p>Positions returned should have an appropriate source marker
    * in their comment field (e.g., "lspdef", "tag:name", "lid").</p>
    *
    * @param fvc the current file-view context
    * @param tagName the symbol name being looked up
    * @return list of positions, or empty list if none found
    */
   default List<Position> lookupPositions(FvContext fvc, String tagName) {
      return Collections.emptyList();
   }

   /**
    * Returns hover/type information for the symbol at the cursor.
    *
    * <p>Called after a successful definition lookup to enrich the
    * Position comment field with type or documentation info.
    * May return null if no hover info is available.</p>
    *
    * @param fvc the current file-view context
    * @return hover text, or null
    */
   default String getHoverInfo(FvContext fvc) {
      return null;
   }
}
