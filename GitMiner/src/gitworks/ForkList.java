package gitworks;


import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.PrintStream;
import java.util.Collections;
import java.util.Iterator;
import java.util.ArrayList;


public class ForkList implements Externalizable {

private ArrayList<ForkEntry> repos;
private int trees = 0; // how many distinct fork trees are in this list


ForkList() {
  repos = new ArrayList<ForkEntry>();
}


int size() {
  return repos.size();
}


int howManyTrees() {
  return trees;
}


public void setTreeCounter() {
  trees = 0;
  Iterator<ForkEntry> it = repos.iterator();
  while (it.hasNext()) {
    if (it.next().isRoot()) trees++;
  }
}


// return the result of binarySearch !!
int add(ForkEntry e) {
  int i = Collections.binarySearch(repos, e);
  if (i < 0) {
    repos.add(-i - 1, e);
    if (e.isRoot()) trees++;
  }
  return i;
}


ForkEntry remove(String id) {
  ForkEntry f = new ForkEntry(id);
  return remove(f);
}


ForkEntry remove(ForkEntry f) {
  if (repos.isEmpty()) return null;
  int i = Collections.binarySearch(repos, f);
  if (i < 0) {
    return null;
  }
  ForkEntry res =  repos.remove(i);
  if (res.isRoot()) trees--;
  return res;
}


ForkEntry get(ForkEntry f) {
  int i = Collections.binarySearch(repos, f);
  if (i < 0) {
    return null;
  }
  return repos.get(i);
}


ForkEntry get(String id) {
  ForkEntry t = new ForkEntry(id);
  return this.get(t);
}


ForkEntry get(int i) {
  if (i < 0 || i >= size()) return null;
  return repos.get(i);
}


Iterator<ForkEntry> getAll() {
  Iterator<ForkEntry> i = repos.iterator();
  return i;
}


int getPos(ForkEntry f) {
  int res = Collections.binarySearch(repos, f);
  return res <= 0 ? -1 : res;
}


public void printForkTrees(PrintStream out) throws Exception {
  for (int i = 0; i < size(); i++) {
    if (repos.get(i).isRoot()) {
      GitWorks.dfsVisit(100, repos.get(i), ForkEntry.printAllForks, out);
      out.print("\n");
    }
  }
  out.flush();
}


public String toString() {
  String out = "";
  for (ForkEntry f : repos) {
    out += f.toString() + "\n";
  }
  return out;
}


// it is meant to be used only once, i.e. if NO metaEntry has been added to this object yet.
void addMetaEntries() throws Exception { //TODO: add some trimming to save space
  ForkEntry roots[] = repos.toArray(new ForkEntry[0]);
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
    repos.add(fe);
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
    fe = repos.get(i);
    if (fe.isRoot()) fe.writeExternal(out);
  }
  out.flush();
}

}
