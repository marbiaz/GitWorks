package gitworks;


import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;

import org.eclipse.jgit.lib.ObjectId;


public class MetaGraph implements Externalizable {

static class NodeDegreeComparator implements java.util.Comparator<Commit> {

  @Override
  public int compare(Commit c1, Commit c2) {
    int res;
    res = c2.outDegree - c1.outDegree;
    if (res == 0) res = c2.inDegree - c1.inDegree;
    return res;
  }

}


private int maxID;
private ArrayList<Commit> allCommits;
ArrayList<Dag> dags;
long since;
long until;


public MetaGraph(ArrayList<Commit> all) {
  maxID = 0;
  allCommits = all;
  dags = new ArrayList<Dag>();
  since = Long.MAX_VALUE;
  until = 0;
}


static MetaGraph createMetaGraph(ArrayList<Commit> allComs, Commit[] heads) {
  MetaGraph res = new MetaGraph(allComs);
  for (Commit c : heads) {
    if (c.edges.isEmpty()) { // was not found in previous iterations
      res.addHead(c);
    }
  }
  for (Commit h : heads) {
    res.addLeaf(h);
  }
  return res;
}


private Dag getDag(int edgeId) {
  for (Dag d : dags)
    if (d.getEdge(edgeId) != null)
      return d;
  return null;
}


private void absorbeEdge(Dag d, Commit c, MetaEdge me) {
  MetaEdge curMe = d.removeEdge(c.edges.remove(0));
  Commit c2;
  me.addInternal(c);
  for (int i = 0; i < curMe.getWeight(); i++) {
    c2 = curMe.getInternals().get(i);
    me.addInternal(c2);
    c2.edges.remove(0);
    c2.edges.add(me.ID);
  }
  me.first = curMe.first;
  me.first.edges.remove(Collections.binarySearch(me.first.edges, curMe.ID));
  me.first.edges.add(me.ID);
  GitWorks.addUnique(c.edges, me.ID); // to make the procedure self-contained
}


private void splitEdge(Dag d, Commit c, int newID) {
  MetaEdge newMe, me = d.getEdge(c.edges.get(0));
  Commit c2 = me.first;
  me.first = c;
  newMe = new MetaEdge(newID);
  d.addEdge(newMe);
  newMe.last = c;
  newMe.first = c2;
  c2.edges.remove(Collections.binarySearch(c2.edges, me.ID));
  GitWorks.addUnique(c2.edges, newMe.ID);
  int z = me.getInternals().indexOf(c);
  while (z < me.getWeight() - 1) {
    c2 = me.getInternals().remove(z + 1);
    newMe.addInternal(c2);
    c2.edges.remove(0);
    c2.edges.add(newMe.ID);
  }
  me.getInternals().remove(z);
  GitWorks.addUnique(c.edges, newMe.ID);
}


private Commit[] addCommit(Dag d, Commit c, MetaEdge me) {
  ObjectId[] parents;
  Commit co, res[] = null;
  c.outDegree++;
  if (c.edges.isEmpty()) { // c has never been considered before
    parents = c.getParents();
    c.inDegree = parents.length;
    if (c.inDegree == 0 || c.inDegree > 1)
      me.first = c; // first commit of the repo or merge commit
    else
      me.addInternal(c);
    if (c.inDegree == 1) { // simple commit: chain it with its parent
      GitWorks.addUnique(c.edges, me.ID);
      co = GitWorks.getElement(allCommits, parents[0]);
      return addCommit(d, co, me);
    } else { // return the commit and its list of parents
      res = new Commit[c.inDegree + 1];
      res[0] = c;
      for (int i = 0; i < parents.length; i++)
        res[i + 1] = GitWorks.getElement(allCommits, parents[i]);
    }
  } else { // c has already been considered in previous calls
    Dag d1 = getDag(c.edges.get(0));
    if (d1 == null) d1 = d;
    if (c.outDegree == 1 && c.inDegree == 1) { // terminal commit: change it to internal
      absorbeEdge(d1, c, me);
    } else if (c.outDegree == 2 && c.inDegree == 1) {
      // internal commit: change it to terminal
      me.first = c;
      splitEdge(d1, c, ++maxID);
    } else { // branch commit or merge commit
      me.first = c;
    }
    if (d.union(d1))
      dags.remove(dags.indexOf(d1));
    res = new Commit[] {me.first};
  }
  GitWorks.addUnique(c.edges, me.ID);
  return res;
}


// must NOT be called more then once for the came commit!
void addHead(Commit c) {
  Commit[] p, cur;
  MetaEdge me;
  ArrayList<Commit[]> next = new ArrayList<Commit[]>();
  long tStamp;
  ObjectId[] ps = c.getParents();
  Dag d = new Dag();
  // if a branch HEAD points the first commit of the repo, just return
  if (ps.length == 0) {
    if(!dags.isEmpty()) {
      for (Dag d1 : dags)
        if (Collections.binarySearch(d1.roots, c) >= 0)
          return;
    }
    GitWorks.addUnique(d.roots, c);
    tStamp = c.getCommittingInfo().getWhen().getTime();
    if (since > tStamp)
      since = tStamp;
    dags.add(d);
    return; 
  }
  p = new Commit[ps.length + 1];
  p[0] = c;
  for (int i = 0; i < ps.length; i++)
    p[i + 1] = GitWorks.getElement(allCommits, ps[i]);
  next.add(p);
  do {
    cur = next.remove(0);
    c = cur[0];
    c.inDegree = cur.length - 1;
    for (int i = 1; i < cur.length; i++) {
      me = new MetaEdge(++maxID);
      me.last = c;
      GitWorks.addUnique(c.edges, me.ID);
      p = addCommit(d, cur[i], me);
      if (p[0].inDegree == 0) {
        if(!dags.isEmpty()) {
          for (Dag d1 : dags)
            if (Collections.binarySearch(d1.roots, p[0]) >= 0) {
              d.union(d1);
              dags.remove(dags.indexOf(d1));
              break;
            }
        }
        GitWorks.addUnique(d.roots, p[0]);
        tStamp = c.getCommittingInfo().getWhen().getTime();
        if (since > tStamp)
          since = tStamp;
      } else
        GitWorks.addUnique(d.nodes, p[0]);
      d.addEdge(me);
      if (p.length > 1) {
        next.add(p);
      }
    }
  } while (!next.isEmpty());
  dags.add(d);
}


void addLeaf(Commit c) {
  long tStamp;
  if (c.inDegree > 0 && c.outDegree == 0) {
    GitWorks.addUnique(getDag(c.edges.get(0)).leaves, c);
    tStamp = c.getCommittingInfo().getWhen().getTime();
    if (until < tStamp)
      until = tStamp;
  }
}


boolean checkup() {
  boolean res;
  int max = 0;
  dags.trimToSize();
  res = allCommits == null || allCommits.size() == 0;
  if (res)
    System.err.println("MetaGraph: ERROR : allCommits array is not set.");
  else {
    for (Dag d : dags) {
      max += d.getNumMetaEdges();
      res = res || !d.checkup(true);
    }
    if (res)
      System.err.println("MetaGraph : ERROR : inconsistent dags.");
    else {
      res = res || (maxID < max);
      if (res) System.err.println("MetaGraph : ERROR : maxID is too low.");
    }
//    if (res) {
//      System.err.println("MetaGraph : allCommits :");
//      GitWorks.printAny(allCommits, "\n", System.err);
//    }
  }
  return !res;
}


@Override
public String toString() {
  int i = 1;
  String res = "", out[];
  if (dags.isEmpty())
    return "not been defined yet.";
  else
    out = new String[dags.size() + 1];
  int roots = 0, leaves = 0, nodes = 0, edges = 0, tot = 0;
  for (Dag d : dags) {
    roots += d.roots.size();
    leaves += d.leaves.size();
    nodes += d.nodes.size();
    edges += d.getNumMetaEdges();
    tot += d.getNumCommits();
    out[i] = "\tDag " + i + " : " + d.toString();
    i++;
  }
  out[0] = dags.size() + " dag" + (dags.size() > 1 ? "s, " : ", ") + roots + " roots, " + leaves + " leaves, "
      + nodes + " nodes, " + edges + " meta-edges for " + tot + " total commits" + (dags.size() > 1 ? ", distributed as follows:\n" : ".");
  if (out.length > 2)
    for (String s : out)
      res += s;
  else
    res = out[0];
  return res;
}


@Override
public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
  MetaEdge e;
  int i, j, z, size, dagsNum = in.readInt();
  if (dagsNum == 0) return;
  since = in.readLong();
  until = in.readLong();
  maxID = in.readInt();
  for (int k = 0; k < dagsNum; k++) {
    Dag d = new Dag();
    dags.add(d);
    size = in.readInt();
    for (i = 0; i < size; i++) {
      d.roots.add(allCommits.get(in.readInt()));
    }
    size = in.readInt();
    for (i = 0; i < size; i++) {
      e = new MetaEdge(in.readInt());
      e.layer = in.readInt();
      e.first = allCommits.get(in.readInt());
      e.last = allCommits.get(in.readInt());
      j = in.readInt();
      for (z = 0; z < j; z++)
        e.addInternal(allCommits.get(in.readInt()));
      if (e.getWeight() > 0) e.getInternals().trimToSize();
      d.addEdge(e);
    }
    size = in.readInt();
    for (i = 0; i < size; i++) {
      d.leaves.add(allCommits.get(in.readInt()));
    }
    size = in.readInt();
    for (i = 0; i < size; i++) {
      d.nodes.add(allCommits.get(in.readInt()));
    }
    d.checkup(false);
  }
}


@Override
public void writeExternal(ObjectOutput out) throws IOException {
  MetaEdge me;
  out.writeInt(dags.size());
  if (dags.isEmpty()) {
    out.flush();
    return;
  }
  out.writeLong(since);
  out.writeLong(until);
  out.writeInt(maxID);
  for (Dag d : dags) {
    out.writeInt(d.roots.size());
    Iterator<Commit> itc = d.roots.iterator();
    while (itc.hasNext()) {
      out.writeInt(Collections.binarySearch(allCommits, itc.next()));
    }
    out.writeInt(d.getNumMetaEdges());
    Iterator<MetaEdge> ite = d.getMetaEdges();
    while (ite.hasNext()) {
      me = ite.next();
      out.writeInt(me.ID);
      out.writeInt(me.layer);
      out.writeInt(Collections.binarySearch(allCommits, me.first));
      out.writeInt(Collections.binarySearch(allCommits, me.last));
      out.writeInt(me.getWeight());
      if (me.getWeight() > 0) for (Commit c : me.getInternals())
        out.writeInt(Collections.binarySearch(allCommits, c));
    }
    out.writeInt(d.leaves.size());
    itc = d.leaves.iterator();
    while (itc.hasNext()) {
      out.writeInt(Collections.binarySearch(allCommits, itc.next()));
    }
    out.writeInt(d.nodes.size());
    itc = d.nodes.iterator();
    while (itc.hasNext()) {
      out.writeInt(Collections.binarySearch(allCommits, itc.next()));
    }
  }
  out.flush();
}

}
