package gitworks;


import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.util.RawParseUtils;


public class Commit implements Comparable<Object>, Externalizable {

ObjectId id;
private byte[] data;
ArrayList<BranchRef> branches;


Commit() {}


Commit(ObjectId id) {
  this.id = id;
}


Commit(RevCommit c) {
  data = new byte[c.getRawBuffer().length];
  System.arraycopy(c.getRawBuffer(), 0, data, 0, data.length);
  id = c.copy();
  branches = new ArrayList<BranchRef>();
}


void addBranches(ArrayList<BranchRef> b) {
  branches.addAll(b);
  Collections.sort(branches);
}


void addBranch(BranchRef b) {
  GitWorks.addUnique(branches, b);
}


int repoCount() {
  int res = 1;
  String prev, curr;
  if (branches == null || branches.isEmpty()) return 0;
  Iterator<BranchRef> brIt = branches.iterator();
  prev = brIt.next().getRepoName();
  while (brIt.hasNext()) {
    curr = brIt.next().getRepoName();
    if (!curr.equals(prev)) {
      res++;
      prev = curr;
    }
  }
  return res;
}


PersonIdent getAuthoringInfo() {
  return RawParseUtils.parsePersonIdent(data, RawParseUtils.author(data, 0));
}


PersonIdent getCommittingInfo() {
  return RawParseUtils.parsePersonIdent(data, RawParseUtils.committer(data, 0));
}


String getMessage() {
  return RawParseUtils.decode(RawParseUtils.parseEncoding(data), data,
      RawParseUtils.commitMessage(data, 0), data.length);
}


ObjectId getTreeId() {
  return RevCommit.parse(data).getTree().getId();
}


ObjectId[] getParents() {
  ObjectId[] res;
  int i = 0;
  RevCommit[] ps = RevCommit.parse(data).getParents();
  res = new ObjectId[ps.length];
  for (RevCommit p : ps) {
    res[i++] = p.getId();
  }
  return res;
}


@Override
public String toString() {
  String out = "";
  out += "id " + id.getName() + "\n";
  out += RawParseUtils.decode(RawParseUtils.parseEncoding(data), data);
  out += "\nbranches : " + branches.size() + " ; forks : " + repoCount();
//  for (BranchRef r : branches) {
//    out += " " + r.toString();
//  }
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
