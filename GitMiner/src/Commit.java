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


Commit() {}


Commit(ObjectId id) {
  this.id = id;
}


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
    out += "\nbranches";
    for (BranchRef r : branches) {
      out += " " + r.toString();
    }
  }
  return out;
}


@Override
public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
  int i, size = in.readInt();
  data = new byte[size];
  for (i = 0; i < size; i++) {
    data[i] = in.readByte();
  }
  id  = (ObjectId)in.readObject();
  size = in.readInt();
  branches = new ArrayList<BranchRef>(size);
  BranchRef bb;
  for (i = 0; i < size; i++) {
    bb = new BranchRef();
    bb.index = in.readInt();
    branches.add(bb); // the complete instance must be set by GitMiner
  }
}


@Override
public void writeExternal(ObjectOutput out) throws IOException {
  out.writeInt(data.length);
  for (byte b : data) {
    out.write(b);
  }
  out.writeObject(id);
  out.writeInt(branches.size());
  for (BranchRef br : branches) {
    out.writeInt(br.index); // (see readExternal)
  }
  out.flush();
}


@Override
public int compareTo(Object o) {
  if (o instanceof Commit)
    return this.id.compareTo(((Commit)o).id);
  else if (o instanceof RevCommit)
    return this.id.compareTo(((RevCommit)o).getId());
  else if (o instanceof ObjectId)
    return this.id.compareTo((ObjectId)o);
  return -1;
}


@Override
public boolean equals(Object o) {
  return this.compareTo(o) == 0;
}

}
