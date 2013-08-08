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

private int maxID;

private ArrayList<Commit> allCommits; // it points to the same-name array in the GitMiner that contains this MetaGraph
private ArrayList<Commit> nodes; // every nodes in the graph but root and leaves
private ArrayList<Commit> leaves; // nodes with no child
private ArrayList<Commit> roots; // nodes with no parent
private ArrayList<MetaEdge> metaEdges; // graph's edges


public MetaGraph(ArrayList<Commit> all) {
  maxID = 0;
  allCommits = all;
  metaEdges = new ArrayList<MetaEdge>();
  leaves = new ArrayList<Commit>();
  nodes = new ArrayList<Commit>();
  roots = new ArrayList<Commit>();
}


MetaEdge getEdge(int id) {
  int i = Collections.binarySearch(metaEdges, id);
  return i >= 0 ? metaEdges.get(i) : null;
}


int addEdge(MetaEdge me) {
  return GitWorks.addUnique(metaEdges, me);
}


MetaEdge removeEdge(int id) {
  int i = Collections.binarySearch(metaEdges, id);
  return i >= 0 ? metaEdges.remove(i) : null;
}


private void absorbeEdge(Commit c, MetaEdge me) {
//  System.err.println("\tOverwriting edge from " + c.id.name()); // XXX
  MetaEdge curMe = removeEdge(c.edges.remove(0));
  if (!c.edges.isEmpty()) System.err.println("\tFATAL in " + c.id.name()); // XXX
  Commit c2;
  removeEdge(curMe.ID);
  me.addInternal(c);
  for (int i = 0; i < curMe.getWeight(); i++) {
    c2 = curMe.getInternals().get(i);
    me.addInternal(c2);
    c2.edges.remove(0); // XXX c2.edges.remove(Collections.binarySearch(c2.edges, curMe.ID));
    if (!c2.edges.isEmpty()) System.err.println("\tFATAL in " + c2.id.name()); // XXX
    c2.edges.add(me.ID); // XXX GitWorks.addUnique(c2.edges, me.ID);
  }
  me.first = curMe.first;
  me.first.edges.remove(Collections.binarySearch(me.first.edges, curMe.ID));
  me.first.edges.add(me.ID);
  GitWorks.addUnique(c.edges, me.ID); // to make the procedure self-contained
}


private void splitEdge(Commit c) {
//  System.err.println("\tSplitting edge from " + c.id.name()); // XXX
  MetaEdge newMe, me = getEdge(c.edges.get(0));
  Commit c2 = me.first;
  me.first = c;
  newMe = new MetaEdge(++maxID);
  addEdge(newMe);
  newMe.last = c;
  newMe.first = c2;
  c2.edges.remove(Collections.binarySearch(c2.edges, me.ID));
  GitWorks.addUnique(c2.edges, newMe.ID);
  int z = me.getInternals().indexOf(c);
  while (z < me.getWeight() - 1) {
    c2 = me.getInternals().remove(z + 1);
    newMe.addInternal(c2);
    c2.edges.remove(0); // XXX c2.edges.remove(Collections.binarySearch(c2.edges, me.ID));
    if (!c2.edges.isEmpty()) System.err.println("\tFATAL in " + c2.id.name()); // XXX
    c2.edges.add(newMe.ID); // XXX GitWorks.addUnique(c2.edges, newMe.ID);
  }
  me.getInternals().remove(z);
  GitWorks.addUnique(c.edges, newMe.ID);
}


private Commit[] addCommit(Commit c, MetaEdge me) {
  ObjectId[] parents;
  Commit res[] = null;
  c.outDegree++;
  if (c.edges.isEmpty()) { // c has never been considered before
    parents = c.getParents();
    c.inDegree = parents.length; // FIXME :  parents == null ? 0 :
    if (c.inDegree == 0 || c.inDegree > 1)
      me.first = c; // first commit of the repo or merge commit
    else
      me.addInternal(c);
    if (c.inDegree == 1) { // simple commit: chain it with its parent
      GitWorks.addUnique(c.edges, me.ID);
      return addCommit(allCommits.get(Collections.binarySearch(allCommits, parents[0])), me);
    } else { // merge commit: return the list of parents ; first commit: return it
      res = new Commit[c.inDegree + 1];
      res[0] = c;
      for (int i = 0; i < parents.length; i++)
        res[i + 1] = allCommits.get(Collections.binarySearch(allCommits, parents[i]));
    }
  } else { // c has already been considered in previous calls
    if (c.outDegree == 1 && c.inDegree == 1) { // terminal commit: change it to internal
      absorbeEdge(c, me);
    } else if (c.outDegree == 2 && c.inDegree == 1) {
      // internal commit: change it to terminal
      me.first = c;
      splitEdge(c);
    } else { // branch commit or merge commit
      me.first = c;
    }
    res = new Commit[] {me.first};
  }
  GitWorks.addUnique(c.edges, me.ID);
  return res;
}


void addHead(Commit c) {
  Commit[] p, cur;
  MetaEdge me;
  ArrayList<Commit[]> next = new ArrayList<Commit[]>();
  ObjectId[] ps = c.getParents();
  // if a branch HEAD points the first commit of the repo, just return
  if (ps.length == 0) { // XXX ps == null ||
    if(!roots.isEmpty() && Collections.binarySearch(roots, c) < 0)
      System.err.println("Metagraph : WARNING : there are more than one root!");
    GitWorks.addUnique(roots, c);
    return; 
  }
  p = new Commit[ps.length + 1];
  p[0] = c;
  for (int i = 0; i < ps.length; i++)
    p[i + 1] = allCommits.get(Collections.binarySearch(allCommits, ps[i]));
  next.add(p);
  do {
    cur = next.remove(0);
    c = cur[0];
    c.inDegree = cur.length - 1;
    for (int i = 1; i < cur.length; i++) {
      me = new MetaEdge(++maxID);
      me.last = c;
//      System.err.println("Considering " + cur[i].id.name()); // XXX
      GitWorks.addUnique(c.edges, me.ID);
      p = addCommit(cur[i], me);
      if (p[0].inDegree == 0) {
        if(!roots.isEmpty() && Collections.binarySearch(roots, p[0]) < 0)
          System.err.println("Metagraph : WARNING : there are more than one root!");
        GitWorks.addUnique(roots, p[0]);
      } else
        GitWorks.addUnique(nodes, p[0]);
      addEdge(me);
      if (p.length > 1) {
        next.add(p);
      }
    }
  } while (!next.isEmpty());
}


void addLeaf(Commit c) {
  if (c.inDegree > 0 && c.outDegree == 0) {
    GitWorks.addUnique(leaves, c);
//    System.err.println("Set leaf " + c.id.name()); // XXX
  }
}


boolean checkup() {
  boolean res = true;
  metaEdges.trimToSize();
  leaves.trimToSize();
  nodes.trimToSize();
  roots.trimToSize();
  ArrayList<Commit> terminals = new ArrayList<Commit>(metaEdges.size());
  ArrayList<Commit> internals = new ArrayList<Commit>(allCommits.size());
  for (MetaEdge me : metaEdges) {
    if (me.getWeight() > 0)
      for (Commit c : me.getInternals()) {
        internals.add(c);
        if (Collections.binarySearch(c.edges, me.ID) < 0) {
          System.err.println("Metagraph checkup : ERROR : internal commit " + c.id.getName()
              + " lacks pointer to edge " + me.ID + " !");
          res = false;
        }
        if (c.edges.size() != 1) {
          System.err.println("Metagraph checkup : ERROR : internal commit " + c.id.getName()
              + " points to " + c.edges.size() + " edges!");
          res = false;
        }
      }
    GitWorks.addUnique(terminals, me.first);
    if (Collections.binarySearch(me.first.edges, me.ID) < 0) {
      System.err.println("Metagraph checkup : ERROR : terminal commit " +
          me.first.id.getName() + " lacks pointer to edge " + me.ID + " !");
       res = false;
    }
    GitWorks.addUnique(terminals, me.last);
    if (Collections.binarySearch(me.last.edges, me.ID) < 0) {
      System.err.println("Metagraph checkup : ERROR : terminal commit " +
          me.last.id.getName() + " lacks pointer to edge " + me.ID + " !");
       res = false;
    }
//    System.out.println(me.toString()); // XXX
  }
//  System.out.flush(); // XXX
  Collections.sort(internals);
  Iterator<Commit> cit = internals.iterator();
  Commit c1 = null, c2;
  if (cit.hasNext()) c1 = cit.next();
  while (cit.hasNext()) {
    c2 = cit.next();
    if (c1.equals(c2)) {
      System.err.println("Metagraph checkup : ERROR : duplicate internal commit (" +
         c1.id.getName() + ") " + "!");
      res = false;
    }
    c1 = c2;
  }
  for (Commit c : terminals) {
    if (Collections.binarySearch(internals, c) >= 0) {
      System.err.println("Metagraph checkup : ERROR : commit " + c.id.getName() +
          " is both internal and terminal!");
       res = false;
    }
    if (Collections.binarySearch(leaves, c) < 0 &&
        Collections.binarySearch(nodes, c) < 0 && Collections.binarySearch(roots, c) < 0) {
      System.err.println("Metagraph checkup : ERROR : terminal commit " + c.id.getName() +
          " is not included in the proper list of nodes.");
       res = false;
    }
  }
  if (roots.isEmpty()) {
    System.err.println("Metagraph checkup : ERROR : Root is not set!");
    res = false;
  } else {
    for (Commit c : roots) {
      if (c.inDegree != 0) {
        System.err.println("Metagraph checkup : ERROR : Wrong root (" + c.id.name() + ")!");
        res = false;
      } else if (Collections.binarySearch(terminals, c) < 0) {
        if (c.outDegree == 0) {
          System.err.println("Metagraph checkup : WARNING : detached root " + c.id.name());
        }
        else {
          System.err.println("Metagraph checkup : ERROR : root " + c.id.name()
              + " is not listed as terminal node!");
          res = false;
        }
      }
    }
  }
  if (leaves.isEmpty()) {
    if (allCommits.size() == roots.size()) {
      System.err.println("Metagraph checkup : WARNING : only detached roots!");
    } else {
      System.err.println("Metagraph checkup : ERROR : missing leaves!");
      res = false;
    }
  }
  for (Commit c : leaves) {
    if (Collections.binarySearch(terminals, c) < 0) {
      System.err.println("Metagraph checkup : ERROR : leaf " + c.id.name() +
          " is not listed as terminal node!");
      res = false;
    }
  }
  for (Commit c : nodes) {
    if (Collections.binarySearch(terminals, c) < 0) {
      System.err.println("Metagraph checkup : ERROR : node " + c.id.getName() +
          " is missing in one of its edges.");
       res = false;
    }
  }
  System.gc();
  return res;
}


@Override
public String toString() {
  if (maxID == 0 && roots.isEmpty())
    return "not been defined yet.";
  return "" + roots.size() + " roots, " + "" + leaves.size() + " leaves, "
      + "" + nodes.size() + " nodes, " + metaEdges.size() + " meta-edges.";
}


@Override
public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
  MetaEdge e;
  maxID = in.readInt();
  int i, j, z, size = in.readInt();
  if (maxID == 0 && size == 0) return;
  roots = new ArrayList<Commit>(size);
  for (i = 0; i < size; i++) {
    roots.add(allCommits.get(in.readInt()));
  }
  size = in.readInt();
  metaEdges = new ArrayList<MetaEdge>(size);
  for (i = 0; i < size; i++) {
    e = new MetaEdge(in.readInt());
    e.first = allCommits.get(in.readInt());
    e.last = allCommits.get(in.readInt());
    j = in.readInt();
    for (z = 0; z < j; z++)
      e.addInternal(allCommits.get(in.readInt()));
    if (e.getWeight() > 0) e.getInternals().trimToSize();
    addEdge(e);
  }
  size = in.readInt();
  leaves = new ArrayList<Commit>(size);
  for (i = 0; i < size; i++) {
    leaves.add(allCommits.get(in.readInt()));
  }
  size = in.readInt();
  nodes = new ArrayList<Commit>(size);
  for (i = 0; i < size; i++) {
    nodes.add(allCommits.get(in.readInt()));
  }
}


@Override
public void writeExternal(ObjectOutput out) throws IOException {
  MetaEdge me;
  out.writeInt(maxID);
  out.writeInt(roots.size());
  if (maxID == 0 && roots.isEmpty()) {
    out.flush();
    return;
  }
  Iterator<Commit> itc = roots.iterator();
  while (itc.hasNext()) {
    out.writeInt(Collections.binarySearch(allCommits, itc.next()));
  }
  out.writeInt(metaEdges.size());
  Iterator<MetaEdge> ite = metaEdges.iterator();
  while (ite.hasNext()) {
    me = ite.next();
    out.writeInt(me.ID);
    out.writeInt(Collections.binarySearch(allCommits, me.first));
    out.writeInt(Collections.binarySearch(allCommits, me.last));
    out.writeInt(me.getWeight());
    if (me.getWeight() > 0) for (Commit c : me.getInternals())
      out.writeInt(Collections.binarySearch(allCommits, c));
  }
  out.writeInt(leaves.size());
  itc = leaves.iterator();
  while (itc.hasNext()) {
    out.writeInt(Collections.binarySearch(allCommits, itc.next()));
  }
  out.writeInt(nodes.size());
  itc = nodes.iterator();
  while (itc.hasNext()) {
    out.writeInt(Collections.binarySearch(allCommits, itc.next()));
  }
  out.flush();
}

}
