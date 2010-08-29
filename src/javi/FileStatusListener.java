package javi;

interface FileStatusListener {
   void fileAdded(EditContainer ev);
   void fileWritten(EditContainer ev);
   boolean fileDisposed(EditContainer ev);
}
