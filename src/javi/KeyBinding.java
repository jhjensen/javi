package javi;


class KeyBinding {
/* Copyright 1996 James Jensen all rights reserved */
   static final String copyright = "Copyright 1996 James Jensen";
   final Rgroup rg;
   final Object arg;
   final int index;
   KeyBinding(Rgroup rgi, Object argi , int indexi) {
      rg = rgi;
      arg = argi;
      index = indexi;
   }
   public String toString() {
      return (rg + "|" + arg + "|" + index);
   }
}
