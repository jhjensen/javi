package javi;

final class ReadOnlyException extends java.lang.UnsupportedOperationException {
/* Copyright 1996 James Jensen all rights reserved */

   static final String copyright = "Copyright 1996 James Jensen";

   private final transient EditContainer ev;
   EditContainer getEv() {
      return ev;
   }

   ReadOnlyException(EditContainer efi, String st) {
      super(st);
      ev = efi;
   }
}
