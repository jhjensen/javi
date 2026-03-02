package javi;

/**
 * Minimal {@link View} implementation for headless JUnit tests.
 *
 * <p>All display-related abstract methods are no-ops.  The inner
 * {@link View.COpt COpt} class already provides complete concrete
 * implementations of all {@link ChangeOpt} methods, so the anonymous
 * subclass used here is empty.
 *
 * <p><b>Usage:</b>
 * <pre>
 *   TestView view = new TestView(true);
 *   FvContext fvc = FvContext.connectFv(textEdit, view);
 * </pre>
 *
 * <p>This enables FvContext life-cycle tests to run without an
 * AWT display or the heavy {@code AwtInterface} singleton.
 */
class TestView extends View {

   private int tabStop = 8;
   private int lastCursorX;
   private int lastCursorY;

   TestView(boolean traversable) {
      super(traversable);
   }

   // ── COpt: all ChangeOpt methods are final in View.COpt ──────

   @Override
   protected ChangeOpt getChangeOpt() {
      return new COpt() { };
   }

   // ── View abstract methods ────────────────────────────────────

   @Override
   public void cursorChanged(int newX, int newY) {
      lastCursorX = newX;
      lastCursorY = newY;
   }

   @Override
   public int yCursorChanged(int newY) {
      lastCursorY = newY;
      return newY;
   }

   @Override
   public int getRows(float scramount) {
      return 24; // simulate 24-row terminal
   }

   @Override
   public int screenFirstLine() {
      return 1;
   }

   @Override
   public int screeny(int amount) {
      return amount;
   }

   @Override
   public void setTabStop(int ts) {
      tabStop = ts;
   }

   @Override
   public int getTabStop() {
      return tabStop;
   }

   @Override
   public void repaint() {
      // no-op in tests
   }

   @Override
   public boolean isVisible() {
      return true;
   }

   @Override
   public void setSizebyChar(int xchar, int ychar) {
      // no-op
   }

   @Override
   protected void startInsertion(Inserter ins) {
      // no-op
   }

   @Override
   protected void endInsertion(Inserter ins) {
      // no-op
   }

   // ── Test accessors ───────────────────────────────────────────

   int getLastCursorX() {
      return lastCursorX;
   }

   int getLastCursorY() {
      return lastCursorY;
   }
}
