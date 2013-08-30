package gitworks;


import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.PrintStream;
import java.util.Collections;
import java.util.Iterator;
import java.util.ArrayList;


public class ForkList extends ArrayList<ForkEntry> implements Externalizable {

private int trees = 0; // how many distinct fork trees are in this list


int howManyTrees() {
  return trees;
}


public void setTreeCounter() {
  trees = 0;
  Iterator<ForkEntry> it = iterator();
  while (it.hasNext()) {
    if (it.next().isRoot()) trees++;
  }
}


ArrayList<ForkEntry> getRoots() {
  ArrayList<ForkEntry> res = new ArrayList<ForkEntry>(trees);
  Iterator<ForkEntry> it = iterator();
  ForkEntry fe;
  while (it.hasNext()) {
    fe = it.next();
    if (fe.isRoot())
      res.add(fe);
  }
  return res;
}


/**
 * This method does nothing and must never be used.
 * @return Always false.
 */
@Deprecated
public boolean add(ForkEntry f) {return false;}


/**
 * This method does nothing and must never be used.
 */
@Deprecated
public void add(int index, ForkEntry f) {}


/**
 * This method does nothing and must never be used.
 * @return Always false.
 */
@Deprecated
public boolean addAll(int index, java.util.Collection<? extends ForkEntry> c) {return false;}


/**
 * This method does nothing and must never be used.
 * @return Always null.
 */
@Deprecated
public ForkEntry remove(int index) {return null;}


/**
 * This method does nothing and must never be used.
 * @return Always false.
 */
@Deprecated
public boolean remove(Object o) {return false;}


/**
 * This method does nothing and must never be used.
 * @return Always false.
 */
@Deprecated
public boolean removeAll(java.util.Collection<?> c) {return false;}


/**
 * This method does nothing and must never be used.
 * @return Always null.
 */
@Deprecated
public ForkEntry set(int index, java.util.Collection<? extends ForkEntry> c) {return null;}


/**
 * This method does nothing and must never be used.
 * @return Always null.
 */
@Deprecated
public ForkEntry set(int index, ForkEntry f) {return null;}


/**
 * Adds the elements of the argument list discarding duplicates.
 * @param c Collection of ForkEntry to be added.
 * @return Always true.
 */
public boolean addAll(java.util.Collection<? extends ForkEntry> f) {
  for (ForkEntry fe : f)
    addEntry(fe);
  return true;
}


// return the result of binarySearch !!
int addEntry(ForkEntry e) {
  int i = Collections.binarySearch(this, e);
  if (i < 0) {
    super.add(-i - 1, e);
    if (e.isRoot()) trees++;
  }
  return i;
}


ForkEntry remove(ForkEntry f) {
  if (isEmpty()) return null;
  int i = Collections.binarySearch(this, f);
  if (i < 0) {
    return null;
  }
  ForkEntry res = super.remove(i);
  if (res.isRoot()) trees--;
  return res;
}


public void printForkTrees(PrintStream out) throws Exception {
  for (int i = 0; i < size(); i++) {
    if (get(i).isRoot()) {
      GitWorks.dfsVisit(Integer.MAX_VALUE, get(i), ForkEntry.printAllForks, out);
      out.print("\n");
    }
  }
  out.flush();
}


public String toString() {
  String out = "";
  for (ForkEntry f : this) {
    out += f.toString() + "\n";
  }
  return out;
}


// it is meant to be used only once, i.e. if NO metaEntry has been added to this object yet.
void addMetaEntries() throws Exception { //TODO: add some trimming to save space
  ForkEntry roots[] = toArray(new ForkEntry[0]);
  for (int i = 0; i < roots.length; i++) {
    GitWorks.dfsVisit(Integer.MAX_VALUE, roots[i], ForkEntry.addTreeToList, this);
  }
}


@Override
public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
  ForkEntry fe;
  trees = in.readInt();
  for (int i = 0; i < trees; i++) {
    fe = new ForkEntry();
    fe.readExternal(in);
    super.add(fe);
  }
  try {
    addMetaEntries();
  }
  catch (Exception e) {
    e.printStackTrace();
  }
}


@Override
public void writeExternal(ObjectOutput out) throws IOException {
  out.writeInt(trees);
  ForkEntry fe;
  for (int i = 0; i < size(); i++) {
    fe = get(i);
    if (fe.isRoot()) fe.writeExternal(out);
  }
  out.flush();
}

}
