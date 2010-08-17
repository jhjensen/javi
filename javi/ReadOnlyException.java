package javi;

class ReadOnlyException extends java.lang.UnsupportedOperationException {
/* Copyright 1996 James Jensen all rights reserved */

   static final String copyright = "Copyright 1996 James Jensen";
   private static final long serialVersionUID = 1;

   final transient EditContainer ev;

   ReadOnlyException(EditContainer efi, String st) {
      super(st);
      ev = efi;
   }
}
