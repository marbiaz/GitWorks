import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.ArrayList;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.util.RawParseUtils;


public class Commit implements Comparable<Object>, Externalizable {

ObjectId id;
byte[] data;
ArrayList<BranchRef> branches;


public Commit() {}


Commit(RevCommit c) {
  data = new byte[c.getRawBuffer().length];
  System.arraycopy(c.getRawBuffer(), 0, data, 0, data.length);
  id = c.copy();
}


void addBranches(ArrayList<BranchRef> b) {
  if (branches == null) branches = new ArrayList<BranchRef>();
  branches.addAll(b);
}


void addBranch(BranchRef b) {
  if (branches == null) branches = new ArrayList<BranchRef>();
  branches.add(b);
}


@Override
public String toString() {
  String out = "";
  out += "id " + id.getName() + "\n";
  out += RawParseUtils.decode(data);
  if (branches != null) {
    out += "branches";
    for (BranchRef r : branches) {
      out += " " + r.toString();
    }
  }
  return out;
}


@Override
public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
  // TODO

}


@Override
public void writeExternal(ObjectOutput out) throws IOException {
  // TODO

}


@Override
public int compareTo(Object o) {
  if (o instanceof Commit)
    return this.id.compareTo(((Commit)o).id);
  else if (o instanceof RevCommit)
    return this.id.compareTo(((RevCommit)o).getId());
  return -1;
}


@Override
public boolean equals(Object o) {
  return this.compareTo(o) == 0;
}

}
