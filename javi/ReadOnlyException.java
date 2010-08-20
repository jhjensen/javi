package javi;

class ReadOnlyException extends java.lang.UnsupportedOperationException {
/* Copyright 1996 James Jensen all rights reserved */

   static final String copyright = "Copyright 1996 James Jensen";
   private static final long serialVersionUID = 1;

   private final transient EditContainer ev;
   final EditContainer getEv() {
      return ev;
   }

   ReadOnlyException(EditContainer efi, String st) {
      super(st);
      ev = efi;
   }
}
