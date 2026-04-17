package javi;

/**
 * Shared buffer management for help content display.
 *
 * <p>Encapsulates the common pattern of creating, clearing,
 * and appending to a {@link TextEdit} buffer backed by a
 * {@link StringIoc}. Used by both {@link ContextHelp} (dynamic
 * keybinding help) and {@link HelpSystem} (static topic help).</p>
 *
 * @see ContextHelp
 * @see HelpSystem
 */
final class HelpBuffer {

   private final String bufferName;
   private TextEdit<String> buffer;

   /**
    * Create a help buffer manager with the given buffer name.
    *
    * @param name the internal buffer name
    *             (e.g. "*help*", "*context-help*")
    */
   HelpBuffer(String name) {
      this.bufferName = name;
   }

   /**
    * Ensure the underlying TextEdit buffer exists.
    * Creates it on first call; subsequent calls are no-ops.
    */
   void ensure() {
      if (buffer == null) {
         StringIoc sio = new StringIoc(bufferName, "");
         buffer = new TextEdit<>(sio, sio.prop);
      }
   }

   /**
    * Clear all content from the buffer, leaving it empty
    * and ready for new content.
    */
   void clear() {
      int finish = buffer.finish();
      if (finish > 2)
         buffer.remove(1, finish - 2);
   }

   /**
    * Append a line of text to the buffer.
    *
    * @param line the text to append
    */
   void append(String line) {
      buffer.insertOne(line, buffer.finish());
   }

   /**
    * Get the underlying TextEdit buffer.
    *
    * @return the buffer, or null if {@link #ensure()} has
    *         not been called
    */
   TextEdit<String> getBuffer() {
      return buffer;
   }
}
