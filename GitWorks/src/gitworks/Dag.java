package gitworks;


import it.uniroma1.dis.wsngroup.gexf4j.core.Edge;
import it.uniroma1.dis.wsngroup.gexf4j.core.EdgeType;
import it.uniroma1.dis.wsngroup.gexf4j.core.Gexf;
import it.uniroma1.dis.wsngroup.gexf4j.core.Graph;
import it.uniroma1.dis.wsngroup.gexf4j.core.Mode;
import it.uniroma1.dis.wsngroup.gexf4j.core.Node;
import it.uniroma1.dis.wsngroup.gexf4j.core.data.Attribute;
import it.uniroma1.dis.wsngroup.gexf4j.core.data.AttributeClass;
import it.uniroma1.dis.wsngroup.gexf4j.core.data.AttributeList;
import it.uniroma1.dis.wsngroup.gexf4j.core.data.AttributeType;
import it.uniroma1.dis.wsngroup.gexf4j.core.impl.EdgeImpl;
import it.uniroma1.dis.wsngroup.gexf4j.core.impl.GexfImpl;
import it.uniroma1.dis.wsngroup.gexf4j.core.impl.NodeImpl;
import it.uniroma1.dis.wsngroup.gexf4j.core.impl.StaxGraphWriter;
import it.uniroma1.dis.wsngroup.gexf4j.core.impl.data.AttributeListImpl;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;


public class Dag {

class MyNode extends NodeImpl implements Comparable<Object> {

MyNode(String s) {
  super(s);
}


private void checkConnection(String id, Node target) {
  com.google.common.base.Preconditions.checkArgument(id != null, "ID cannot be null.");
  com.google.common.base.Preconditions.checkArgument(!id.trim().isEmpty(),
      "ID cannot be empty or blank.");
  com.google.common.base.Preconditions.checkArgument(target != null, "Target cannot be null.");
}


@Override
public Edge connectTo(String id, Node target) {
  Edge rv = null;
  checkConnection(id, target);
  rv = new EdgeImpl(id, this, target);
  getEdges().add(rv);
  return rv;
}


@Override
public Edge connectTo(String id, String label, Node target) {
  com.google.common.base.Preconditions.checkArgument(label != null, "label cannot be null.");
  Edge rv = connectTo(id, target);
  rv.setLabel(label);
  return rv;
}


@Override
public Edge connectTo(String id, String label, EdgeType edgeType, Node target) {
  com.google.common.base.Preconditions.checkArgument(edgeType != null, "edgeType cannot be null.");
  Edge rv = connectTo(id, label, target);
  rv.setEdgeType(edgeType);
  return rv;
}

@Override
public int compareTo(Object n) {
  String s = null;
  if (n instanceof MyNode) s = ((Node)n).getId();
  else if (n instanceof String) s = ((String)n);
  else return -1;
  return getId().compareTo(s);
}

public boolean equals(Object o) {
  return compareTo(o) == 0;
}

}

ArrayList<Commit> nodes; // every nodes in the graph but root and leaves
ArrayList<Commit> leaves; // nodes with no child
ArrayList<Commit> roots; // nodes with no parent
private ArrayList<MetaEdge> metaEdges; // graph's edges


public Dag() {
  metaEdges = new ArrayList<MetaEdge>();
  leaves = new ArrayList<Commit>();
  nodes = new ArrayList<Commit>();
  roots = new ArrayList<Commit>();
}


MetaEdge getEdge(int id) {
  return GitWorks.getElement(metaEdges, id);
}


int addEdge(MetaEdge me) {
  return GitWorks.addUnique(metaEdges, me);
}


MetaEdge removeEdge(int id) {
  int i = Collections.binarySearch(metaEdges, id);
  return i >= 0 ? metaEdges.remove(i) : null;
}


Iterator<MetaEdge> getMetaEdges() {
  return metaEdges.iterator();
}


int getNumCommits() {
  int res = 0;
  for (MetaEdge me : metaEdges)
    res += me.getWeight();
  return res + nodes.size() + roots.size() + leaves.size();
}


int getNumMetaEdges() {
  return metaEdges.size();
}


ArrayList<MetaEdge> getOutEdges(Commit c) {
  ArrayList<MetaEdge> res = new ArrayList<MetaEdge>(c.outDegree);
  MetaEdge me;
  for (int i : c.edges) {
    me = GitWorks.getElement(metaEdges, i);
    if (!me.last.equals(c)) { // c is internal or first
      GitWorks.addUnique(res, me); // sorted list
    }
  }
  return res;
}


ArrayList<MetaEdge> getInEdges(Commit c) {
  ArrayList<MetaEdge> res = new ArrayList<MetaEdge>(c.inDegree);
  MetaEdge me;
  for (int i : c.edges) {
    me = GitWorks.getElement(metaEdges, i);
    if (!me.first.equals(c)) { // c is internal or last
      GitWorks.addUnique(res, me); // sorted list
    }
  }
  return res;
}


boolean union(Dag d) {
  if (this == d) return false;
  int tot = getNumCommits() + d.getNumCommits();
  for (Commit c : d.leaves)
    GitWorks.addUnique(leaves, c);
  for (Commit c : d.roots)
    GitWorks.addUnique(roots, c);
  for (Commit c : d.nodes)
    GitWorks.addUnique(nodes, c);
  for (MetaEdge m : d.metaEdges)
    addEdge(m);
  if (getNumCommits() != tot)
    System.err.println("Dag : ERROR : something wrong while merging...");
  return true;
}


// it gives the commits in bf order, with ties decided by
// comparing commits with Metagraph.NodeDegreeComparator
Commit[] bfVisit() {
  int i, curSize, layer = 0, size = 0, next = 0,
      tot = nodes.size() + roots.size() + leaves.size();
  boolean done;
  Commit c;
  MetaGraph.NodeDegreeComparator nodeComp = new MetaGraph.NodeDegreeComparator();
  Commit[] res = new Commit[tot];
  ArrayList<Commit> prev = new ArrayList<Commit>();
  ArrayList<Commit> cur = new ArrayList<Commit>();
  ArrayList<MetaEdge> mar;
  HashMap<Commit, ArrayList<MetaEdge>> pending = new HashMap<Commit, ArrayList<MetaEdge>>();
  prev.addAll(roots);
  cur.addAll(roots);
  for (Commit r : roots) {
    res[next++] = r;
  }
  Arrays.sort(res, size, next, nodeComp);
  while (prev.size() < tot) {
    layer++;
    curSize = next;
    for (i = size; i < curSize; i++) {
      c = res[i];
      for (MetaEdge e : getOutEdges(c)) {
        done = true;
        for (MetaEdge me : getInEdges(e.last)) {
          if (Collections.binarySearch(prev, me.first) < 0) {
            done = false;
            break;
          }
        }
        if (done) {
          e.layer = layer;
          mar = pending.remove(e.last);
          if (mar != null) {
            for (MetaEdge mes : mar)
              mes.layer = layer;
          }
          if (Collections.binarySearch(cur, e.last) < 0) {
            res[next++] = e.last;
            GitWorks.addUnique(cur, e.last);
          }
        } else {
          mar = pending.get(e.last);
          if (mar == null) {
            mar = new ArrayList<MetaEdge>();
            pending.put(e.last, mar);
          }
          mar.add(e);
        }
      }
    }
    size = curSize;
    Arrays.sort(res, size, next, nodeComp);
    for (i = size; i < next; i++)
      GitWorks.addUnique(prev, res[i]);
  }
//  bfPrintout(res);
  return res;
}


// It returns a sub-dag taken from this object,
// with only commits up to the given date (committing date is considered).
// Pay attention to the fact that the pointers to the original metaedges in the commits are NOT
// modified!
Dag buildSubDag(Date maxAge) {
  int i;
  Dag res = new Dag();
  MetaEdge e;
  Date dFirst, dLast;
  for (Commit c : roots) {
    if (c.getCommittingInfo().getWhen().compareTo(maxAge) < 0) {
      GitWorks.addUnique(res.roots, c);
    }
  }
  for (Commit c : leaves) {
    if (c.getCommittingInfo().getWhen().compareTo(maxAge) <= 0) {
      GitWorks.addUnique(res.leaves, c);
    }
  }
  for (MetaEdge me : metaEdges) {
    dFirst = me.first.getCommittingInfo().getWhen();
    dLast = me.last.getCommittingInfo().getWhen();
    if (dFirst.compareTo(maxAge) < 0) {
      if (dLast.compareTo(maxAge) <= 0) {
        if (GitWorks.getElement(res.leaves, me.last) == null)
          GitWorks.addUnique(res.nodes, me.last);
        if (GitWorks.getElement(res.roots, me.first) == null)
          GitWorks.addUnique(res.nodes, me.first);
        GitWorks.addUnique(res.metaEdges, me);
      } else if (me.getWeight() > 0
          && me.getInternals().get(me.getWeight() - 1).getCommittingInfo().getWhen()
              .compareTo(maxAge) <= 0) {
        e = new MetaEdge(me.ID); // subgraph will contain a part of the original edge
        e.first = me.first;
        for (i = 0; i < me.getWeight(); i++) {
          if (me.getInternals().get(i).getCommittingInfo().getWhen()
              .compareTo(maxAge) <= 0) break;
        }
        e.last = me.getInternals().get(i++);
        for (; i < me.getWeight(); i++) {
          e.addInternal(me.getInternals().get(i));
        }
        GitWorks.addUnique(res.leaves, e.last);
        if (GitWorks.getElement(res.roots, e.first) == null)
          GitWorks.addUnique(res.nodes, e.first);
        GitWorks.addUnique(res.metaEdges, e);
      }
    }
  }
  return res;
}


double checkTimestamps() {
  Date t1, t2;
  Commit cur;
  int count = 0;
  for (MetaEdge me : metaEdges) {
    cur = me.last;
    if (me.getWeight() > 0) {
      for (Commit c : me.getInternals()) {
        t1 = cur.getCommittingInfo().getWhen();
        t2 = c.getCommittingInfo().getWhen();
        if (t1.compareTo(t2) < 0) {
          System.err.println("\tTimestamp inconsistency in commit " + cur.id.getName());
          count++;
        }
        cur = c;
      }
    }
    t1 = cur.getCommittingInfo().getWhen();
    t2 = me.first.getCommittingInfo().getWhen();
    if (t1.compareTo(t2) < 0) {
      System.err.println("\tTimestamp inconsistency in commit " + cur.id.getName());
      count++;
    }
  }
  return ((double)count) / getNumCommits();
}


boolean checkup(boolean verify) {
  boolean res = true;
  int prev = 0, tot = getNumCommits();
  metaEdges.trimToSize();
  leaves.trimToSize();
  nodes.trimToSize();
  roots.trimToSize();
  if (!verify || tot == roots.size())
    return res;
  ArrayList<Commit> terminals = new ArrayList<Commit>(metaEdges.size());
  ArrayList<Commit> internals = new ArrayList<Commit>(tot);
  for (MetaEdge me : metaEdges) {
    if (me.ID == prev) {
      System.err.println("Dag checkup : ERROR : duplicated meta-edge ID (" + me.ID + ").");
      res = false;
    }
    prev = me.ID;
    if (me.getWeight() > 0)
      for (Commit c : me.getInternals()) {
        internals.add(c);
        if (Collections.binarySearch(c.edges, me.ID) < 0) {
          System.err.println("Dag checkup : ERROR : internal commit " + c.id.getName()
              + " lacks pointer to edge " + me.ID + " .");
          res = false;
        }
        if (c.edges.size() != 1) {
          System.err.println("Dag checkup : ERROR : internal commit " + c.id.getName()
              + " points to " + c.edges.size() + " edges.");
          res = false;
        }
      }
    GitWorks.addUnique(terminals, me.first);
    if (Collections.binarySearch(me.first.edges, me.ID) < 0) {
      System.err.println("Dag checkup : ERROR : terminal commit " + me.first.id.getName()
          + " lacks pointer to edge " + me.ID + " .");
      res = false;
    }
    GitWorks.addUnique(terminals, me.last);
    if (Collections.binarySearch(me.last.edges, me.ID) < 0) {
      System.err.println("Dag checkup : ERROR : terminal commit " + me.last.id.getName()
          + " lacks pointer to edge " + me.ID + " .");
      res = false;
    }
    // System.out.println(me.toString());
  }
  // System.out.flush();
  for (Commit c : roots) // count detached roots as terminals
    if (c.outDegree == 0)
      GitWorks.addUnique(terminals, c);
  Collections.sort(internals);
  Iterator<Commit> cit = internals.iterator();
  Commit c1 = null, c2;
  if (cit.hasNext()) c1 = cit.next();
  while (cit.hasNext()) {
    c2 = cit.next();
    if (c1.equals(c2)) {
      System.err.println("Dag checkup : ERROR : duplicate internal commit ("
          + c1.id.getName() + ").");
      res = false;
    }
    c1 = c2;
  }
  for (Commit c : terminals) {
    if (Collections.binarySearch(internals, c) >= 0) {
      System.err.println("Dag checkup : ERROR : commit " + c.id.getName()
          + " is both internal and terminal.");
      res = false;
    }
    if (Collections.binarySearch(leaves, c) < 0 && Collections.binarySearch(nodes, c) < 0
        && Collections.binarySearch(roots, c) < 0) {
      System.err.println("Dag checkup : ERROR : terminal commit " + c.id.getName()
          + " is not included in the proper list of nodes.");
      res = false;
    }
  }
  if (roots.isEmpty()) {
    System.err.println("Dag checkup : ERROR : Root is not set.");
    res = false;
  } else {
    for (Commit c : roots) {
      if (c.inDegree != 0) {
        System.err.println("Dag checkup : ERROR : Wrong root (" + c.id.name() + ").");
        res = false;
      } else if (Collections.binarySearch(terminals, c) < 0) {
        System.err.println("Dag checkup : ERROR : root " + c.id.name()
            + " is not listed as terminal node.");
        res = false;
      }
      if (Collections.binarySearch(leaves, c) >= 0) {
        System.err.println("Dag checkup : ERROR : root listed as leaf (" + c.id.getName() + ").");
        res = false;
      }
    }
    if (leaves.isEmpty() && tot != roots.size()) {
        System.err.println("Dag checkup : ERROR : missing leaves.");
        res = false;
    }
  }
  for (Commit c : leaves) {
    if (Collections.binarySearch(terminals, c) < 0) {
      System.err.println("Dag checkup : ERROR : leaf " + c.id.name()
          + " is not listed as terminal node.");
      res = false;
    }
  }
  for (Commit c : nodes) {
    if (Collections.binarySearch(terminals, c) < 0) {
      System.err.println("Dag checkup : ERROR : node " + c.id.getName()
          + " is missing in one of its edges.");
      res = false;
    }
    if (Collections.binarySearch(leaves, c) >= 0) {
      System.err.println("Dag checkup : ERROR : node listed as leaf (" + c.id.getName() + ").");
      res = false;
    }
    if (Collections.binarySearch(roots, c) >= 0) {
      System.err.println("Dag checkup : ERROR : node listed as root (" + c.id.getName() + ").");
      res = false;
    }
  }
  if (!res) {
//    System.err.println("Leaves :");
//    GitWorks.printAny(leaves, "\n", System.err);
//    System.err.println("Edges :");
//    GitWorks.printAny(metaEdges, "\n", System.err);
    System.err.println("Roots :");
    GitWorks.printAny(roots, "\n============================\n", System.err);
  }
  System.gc();
  return res;
}


public String toString() {
  return roots.size() + " roots, " + leaves.size() + " leaves, "
      + nodes.size() + " nodes, " + getNumMetaEdges() + " metaedges.";
}


void exportToGexf(String name) { // XXX
  Gexf gexf = new GexfImpl();

  gexf.getMetadata()
      .setLastModified(new java.util.Date(System.currentTimeMillis()))
      .setCreator("Marco")
      .setDescription("Commit meta-graph");

  Graph graph = gexf.getGraph();
  graph.setMode(Mode.STATIC).setDefaultEdgeType(EdgeType.DIRECTED);

  AttributeList eAttrList, nAttrList;
  nAttrList = new AttributeListImpl(AttributeClass.NODE);
  eAttrList = new AttributeListImpl(AttributeClass.EDGE);
  graph.getAttributeLists().add(nAttrList);
  graph.getAttributeLists().add(eAttrList);

  Attribute attBranches = nAttrList.createAttribute("0", AttributeType.INTEGER, "branches");
  Attribute attHeads = nAttrList.createAttribute("1", AttributeType.INTEGER, "heads")
      .setDefaultValue("0");
  Attribute attExtremal = nAttrList.createAttribute("2", AttributeType.BOOLEAN, "extremal")
      .setDefaultValue("false");
  Attribute attWeight = eAttrList.createAttribute("3", AttributeType.INTEGER, "meta-weight");
  Attribute attLayer = eAttrList.createAttribute("4", AttributeType.INTEGER, "layer");

  MyNode node;
  ArrayList<MyNode> cache = new ArrayList<MyNode>(nodes.size() + roots.size() + leaves.size());
  for (Commit c : nodes) {
    node = new MyNode(c.id.getName());
    graph.getNodes().add(node);
    node.setLabel(c.id.getName().substring(0, 6))
        .getAttributeValues()
            .addValue(attBranches, "" + c.branches.size())
            .addValue(attHeads, "" + (c.isHead() ? c.heads.size() : 0))
            .addValue(attExtremal, "false");
    cache.add(node);
  }
  for (Commit c : leaves) {
    node = new MyNode(c.id.getName());
    graph.getNodes().add(node);
    node.setLabel(c.id.getName().substring(0, 6))
        .getAttributeValues()
            .addValue(attBranches, "" + c.branches.size())
            .addValue(attHeads, "" + (c.isHead() ? c.heads.size() : 0)) // possibly not head if subgraph
            .addValue(attExtremal, "true");
    cache.add(node);
  }
  for (Commit c : roots) {
    node = new MyNode(c.id.getName());
    graph.getNodes().add(node);
    node.setLabel(c.id.getName().substring(0, 6))
        .getAttributeValues()
            .addValue(attBranches, "" + c.branches.size())
            .addValue(attHeads, "" + (c.isHead() ? c.heads.size() : 0))
            .addValue(attExtremal, "true");
    cache.add(node);
  }

  Collections.sort(cache);
  Node n2;
  Edge e = null;
  int i;
  for (MetaEdge me : metaEdges) {
    node = GitWorks.getElement(cache, me.first.id.getName());
    n2 = GitWorks.getElement(cache, me.last.id.getName());
    for (i = 0; i < node.getEdges().size(); i++) {
      e = node.getEdges().get(i);
      if (e.getTarget().equals(n2)) {
        break;
      }
    }
    if(i == node.getEdges().size()) {
      e = node.connectTo("" + me.ID, "" + me.ID, n2);
      e.setEdgeType(EdgeType.DIRECTED).setWeight(0.0f);
    }
    e.setWeight(e.getWeight() + (float)me.getWeight())
        .getAttributeValues().addValue(attWeight, "" + e.getWeight())
            .addValue(attLayer, "" + me.layer);
  }

  StaxGraphWriter graphWriter = new StaxGraphWriter();
  File f = new File(name + ".gexf");
  Writer out;
  try {
    out =  new FileWriter(f, false);
    graphWriter.writeToStream(gexf, out, "UTF-8");
    System.out.println(f.getAbsolutePath());
  } catch (IOException ioe) {
    ioe.printStackTrace();
  }
}

}
