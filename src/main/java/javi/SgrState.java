package javi;

import static history.Tools.trace;

/**
 * SGR (Select Graphic Rendition) state machine for terminal attributes.
 *
 * <p>Processes SGR parameter sequences from {@link Vt100Parser} and
 * maintains the current text attribute state (bold, underline, reverse,
 * foreground/background colors). The packed attribute value can be
 * retrieved via {@link #currentAttr()}.</p>
 *
 * <p>Supports:</p>
 * <ul>
 *   <li>Standard ANSI colors (SGR 30-37, 40-47)</li>
 *   <li>Bright/high-intensity colors (SGR 90-97, 100-107)</li>
 *   <li>256-color palette (SGR 38;5;N, 48;5;N)</li>
 *   <li>Truecolor RGB approximation (SGR 38;2;R;G;B,
 *       48;2;R;G;B)</li>
 *   <li>Bold (1), underline (4), reverse (7), and their resets</li>
 * </ul>
 *
 * @see CellAttr
 * @see TerminalPalette
 */
final class SgrState {

   private boolean attrBold;
   private boolean attrUnderline;
   private boolean attrReverse;
   private int attrFgColor = -1;
   private int attrBgColor = -1;

   /** Packed attribute computed from the current SGR state. */
   private int packed = CellAttr.DEFAULT;

   /**
    * Processes an array of SGR parameters and updates the
    * attribute state.
    *
    * @param params the SGR parameter values
    */
   void process(int[] params) {
      for (int i = 0; i < params.length; i++) {
         int p = params[i];
         switch (p) {
            case 0: // reset all attributes
               attrBold = false;
               attrUnderline = false;
               attrReverse = false;
               attrFgColor = -1;
               attrBgColor = -1;
               break;
            case 1: attrBold = true; break;
            case 4: attrUnderline = true; break;
            case 7: attrReverse = true; break;
            case 22: attrBold = false; break;
            case 24: attrUnderline = false; break;
            case 27: attrReverse = false; break;
            case 38: // Extended foreground color
               i = parseExtendedColor(params, i, true);
               break;
            case 48: // Extended background color
               i = parseExtendedColor(params, i, false);
               break;
            default:
               if (p >= 30 && p <= 37)
                  attrFgColor = p - 30;
               else if (p >= 40 && p <= 47)
                  attrBgColor = p - 40;
               else if (p >= 90 && p <= 97)
                  attrFgColor = p - 90 + 8;
               else if (p >= 100 && p <= 107)
                  attrBgColor = p - 100 + 8;
               else if (p == 39)
                  attrFgColor = -1;
               else if (p == 49)
                  attrBgColor = -1;
               else
                  trace("SGR " + p
                     + " — parsed, not used");
               break;
         }
      }
      refreshPacked();
   }

   /**
    * Parses extended color sequences (256-color and truecolor).
    *
    * @param params the SGR parameter array
    * @param idx current index (pointing to 38 or 48)
    * @param isFg true for foreground, false for background
    * @return the updated index past consumed params
    */
   private int parseExtendedColor(int[] params, int idx,
         boolean isFg) {
      if (idx + 2 < params.length && params[idx + 1] == 5) {
         // 256-color: 38;5;N or 48;5;N
         int color = params[idx + 2];
         if (color >= 0 && color <= 254) {
            if (isFg)
               attrFgColor = color;
            else
               attrBgColor = color;
         }
         return idx + 2;
      } else if (idx + 4 < params.length
            && params[idx + 1] == 2) {
         // True color: 38;2;R;G;B or 48;2;R;G;B
         int r = params[idx + 2];
         int g = params[idx + 3];
         int b = params[idx + 4];
         int approx = CellAttr.approxTrueColor(r, g, b);
         if (isFg)
            attrFgColor = approx;
         else
            attrBgColor = approx;
         return idx + 4;
      }
      return idx;
   }

   /** Recomputes the packed attribute from the SGR fields. */
   private void refreshPacked() {
      packed = CellAttr.pack(
         attrBold, attrUnderline, attrReverse,
         attrFgColor, attrBgColor);
   }

   /**
    * Returns the packed {@link CellAttr} value representing the
    * current SGR state.
    *
    * @return packed attribute value
    */
   int currentAttr() {
      return packed;
   }

   /** Resets all attributes to defaults. */
   void reset() {
      attrBold = false;
      attrUnderline = false;
      attrReverse = false;
      attrFgColor = -1;
      attrBgColor = -1;
      packed = CellAttr.DEFAULT;
   }

   /** Snapshot of SGR state for save/restore cursor operations. */
   private boolean savedBold;
   private boolean savedUnderline;
   private boolean savedReverse;
   private int savedFgColor = -1;
   private int savedBgColor = -1;

   /** Saves the current SGR attribute state (for DECSC). */
   void saveState() {
      savedBold = attrBold;
      savedUnderline = attrUnderline;
      savedReverse = attrReverse;
      savedFgColor = attrFgColor;
      savedBgColor = attrBgColor;
   }

   /** Restores the previously saved SGR attribute state (for DECRC). */
   void restoreState() {
      attrBold = savedBold;
      attrUnderline = savedUnderline;
      attrReverse = savedReverse;
      attrFgColor = savedFgColor;
      attrBgColor = savedBgColor;
      refreshPacked();
   }
}
