package javi;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

import static history.Tools.trace;

final class EditCache<OType> implements Iterable<OType> {

   private ArrayList<OType> varray = new ArrayList<OType>(1024);

   public void clear(int start, int end) {
      varray.subList(start, end).clear();
   }

   void addSome(EditCache<OType> nlist, int offset) {
      trace("nlist.size " + nlist.size() + " offset " + offset);
      addAll(1, nlist.varray.subList(offset, nlist.size()));
   }

   public void clear(int start) {
      varray.subList(start, varray.size()).clear();
   }

   public void clear() {
      clear(0);
   }

   public void add(OType ob) {
      varray.add(ob);
   }

   public void add(OType[] objs) {
      for (OType obj : objs)
         varray.add(obj);
   }

   // should only be called by changerecords.
   public void addAll(int index, OType[] objs) {
      varray.addAll(index, java.util.Arrays.asList(objs));
      //Tools.trace("after addAll:"); dump();
   }

   public void addAll(int index, EditCache<OType> ec) {
      varray.addAll(index, ec.varray);
   }

   public void addAll(int index, Collection<OType> objs) {
      //trace("cindex = " + cindex + " currsize = " + varray.size() +  " objtype = " + objs[0].getClass() );
      varray.addAll(index, objs);
   }

   public void addAll(Collection<OType> objs) {
      //trace("cindex = " + cindex + " currsize = " + varray.size() + " objtype = " + objs[0].getClass() );
      varray.addAll(objs);
   }

   // should only be called by changerecords.
   public ArrayList<String> rangeAsStrings(int cindex, int eindex) {
      //trace("cindex = " + cindex + " currsize = " + varray.size()
      //  + " objtype = " + objs[0].getClass() );
      ArrayList<String> outarray = new ArrayList<String>(eindex - cindex);

      for (int i = cindex; i < eindex; i++)
         outarray.add(varray.get(i).toString());
      return outarray;

   }

   void set(int index, OType obj) {
      varray.set(index, obj);
   }
   /** get multiple objects at a given index */
   /** this function removes the specified elements from the editvec*/

   public synchronized ArrayList<String> getElementsAt(int start, int end) {

      ArrayList<String>  outarray = new ArrayList<String>();

      for (int i = start; i < end; i++)
         outarray.add(varray.get(i).toString());
      return outarray;
   }

   @SuppressWarnings("unchecked")
   public OType[] getArr(int index, int count) {
      OType[]  outarray = (OType[]) new Object[count];

      for (int i = 0; i < count; i++)
         outarray[i] = varray.get(index + i);
      return outarray;
   }

   /** return the object at the given index calls to this function must
       already know that the index is valid by using contains()*/
   public OType get(int index) {
      //if (varray ==null)
      //   trace("ex = " + toString() + " varray = " + varray);
      return varray.get(index);
   }

   public boolean contains(OType o) {
      for (OType obj : varray)
         if (o == obj)
            return true;
      return false;
   }
   public int indexOf(OType o) {
      return varray.indexOf(o);
   }

   public int size() {
      return varray.size();
   }

   public Iterator<OType> iterator() {
      return varray.iterator();
   }

   void dump() {
      trace("dumping ecache");
      for (int i = 0; i < size(); i++)
         trace("  [" + i + " ] :" + varray.get(i));

   }

}
