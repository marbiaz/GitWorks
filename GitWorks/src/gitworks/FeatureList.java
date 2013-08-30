package gitworks;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;


public class FeatureList extends ArrayList<Features> implements Externalizable {


FeatureList(int size) {
  super(size);
}


/**
 * This method does nothing and must never be used.
 * @return Always false.
 */
@Deprecated
public boolean add(Features f) {return false;}


/**
 * This method does nothing and must never be used.
 */
@Deprecated
public void add(int index, Features f) {}


/**
 * This method does nothing and must never be used.
 * @return Always false.
 */
@Deprecated
public boolean addAll(int index, java.util.Collection<? extends Features> c) {return false;}


/**
 * This method does nothing and must never be used.
 * @return Always null.
 */
@Deprecated
public Features set(int index, Features f) {return null;}


/**
 * This method does nothing and must never be used.
 * @return Always null.
 */
@Deprecated
public Features set(int index, java.util.Collection<? extends Features> c) {return null;}


/**
 * Adds the elements of the argument list discarding duplicates.
 * @param c Collection of Features to be added.
 * @return Always true.
 */
public boolean addAll(java.util.Collection<? extends Features> c) {
  for (Features f : c)
    addFeatures(f);
  return true;
}


int addFeatures(Features f) {
  int i = Collections.binarySearch(this, f);
  if (i < 0) {
    i = -i - 1;
    super.add(i, f);
  }
  return i;
}


@Override
public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
  Features f;
  int size = in.readInt();
  this.ensureCapacity(size);
  for (int i = 0; i < size; i++) {
    f = new Features();
    f.readExternal(in);
    super.add(f);
  }
}


@Override
public void writeExternal(ObjectOutput out) throws IOException {
  out.writeInt(size());
  Iterator<Features> fit = iterator();
  while (fit.hasNext()) {
    fit.next().writeExternal(out);
  }
  out.flush();
}

}
