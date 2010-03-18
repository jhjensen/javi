package javi;

interface FileStatusListener {
abstract void fileAdded(EditContainer ev);
abstract void fileWritten(EditContainer ev);
abstract boolean fileDisposed(EditContainer ev);
}
