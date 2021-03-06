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
import java.util.Iterator;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;


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

ArrayList<Commit> nodes; // every node in the graph but roots and leaves
ArrayList<Commit> leaves; // nodes with no child
ArrayList<Commit> roots; // nodes with no parent
private ArrayList<MetaEdge> metaEdges; // graph's edges

private int[][] layerSizes; // for each layer, number of commits and edges
private long[][] layerTimes; // for each layer, min and max timestamp
private int diameter;
private int maxWidth; // max number of commits in a layer
private int maxDensity; // max number of edges e in a layer L such that e.first.layer <= L <= e.last.layer
ArrayList<Commit> mergeCommits;
ArrayList<Commit> branchCommits;
ArrayList<Commit> coolCommits;


public Dag() {
  metaEdges = new ArrayList<MetaEdge>();
  leaves = new ArrayList<Commit>();
  nodes = new ArrayList<Commit>();
  roots = new ArrayList<Commit>();
  mergeCommits = null;
  branchCommits = null;
  coolCommits = null;
  layerSizes = null;
  diameter = 0;
  maxWidth = -1;
  maxDensity = -1;
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


int getDiameter() {
  return diameter;
}


int getMaxWidth() {
  if (maxWidth == -1) computeLayerSizes();
  return maxWidth;
}


int getMaxDensity() {
  if (maxDensity == -1) computeLayerSizes();
  return maxDensity;
}


void getInternalCommitStats(DescriptiveStatistics ds) {
  for (MetaEdge me : metaEdges)
    ds.addValue((double)me.getWeight());
}


// [0] -> vertexes ; [1] -> edges ; [2] -> time range
DescriptiveStatistics[] getLayerStats() {
  DescriptiveStatistics ds[] = new DescriptiveStatistics[3];
  if (layerSizes == null) computeLayerSizes();
  if (layerTimes == null) computeLayerTimes();
  for (int i = 0; i < ds.length; i++) {
    ds[i] = new DescriptiveStatistics();
    if (i < 2)
      for (int s : layerSizes[i])
        ds[i].addValue((double)s);
    else
      for (int t = 0; t < layerTimes[0].length; t++)
        ds[i].addValue((new Long(layerTimes[1][t] - layerTimes[0][t])).doubleValue());
  }
  return ds;
}


private void computeLayerSizes() {
  layerSizes = new int[2][diameter + 1];
  for (int i = 0; i < layerSizes.length; i++)
    Arrays.fill(layerSizes[i], 0);
  ArrayList<Commit> allComs = new ArrayList<Commit>(nodes.size() + leaves.size() + roots.size());
  allComs.addAll(nodes);
  allComs.addAll(roots);
  allComs.addAll(leaves);
  for (Commit c : allComs) {
    layerSizes[0][c.layer]++;
  }
  MetaEdge me;
  Iterator<MetaEdge> mIt = getMetaEdges();
  while (mIt.hasNext()) {
    me = mIt.next();
    for (int i = me.last.layer; i > me.first.layer; i--)
      layerSizes[1][i]++;
  }
  for (int i = 0; i <= diameter; i++) {
    maxWidth = Math.max(maxWidth, layerSizes[0][i]);
    maxDensity = Math.max(maxDensity, layerSizes[1][i]);
  }
}


int[][] getLayerSizes() {
  if (layerSizes == null) computeLayerSizes();
  int[][] res = new int[layerSizes.length][layerSizes[0].length];
  for (int i = 0; i < layerSizes.length; i++)
    for (int j = 0; j < layerSizes[i].length; j++)
      res[i][j] = layerSizes[i][j];
  return res;
}


long[][] getLayerTimes() {
  if (layerTimes == null) computeLayerTimes();
  long[][] res = new long[layerTimes.length][layerTimes[0].length];
  for (int i = 0; i < layerTimes.length; i++)
    for (int j = 0; j < layerTimes[i].length; j++)
      res[i][j] = layerTimes[i][j];
  return res;
}


private void computeLayerTimes() {
  layerTimes = new long[2][diameter + 1];
  Arrays.fill(layerTimes[0], Long.MAX_VALUE);
  Arrays.fill(layerTimes[1], 0L);
  MetaEdge me;
  long t;
  Iterator<MetaEdge> mIt = getMetaEdges();
  while (mIt.hasNext()) {
    me = mIt.next();
    layerTimes[0][me.first.layer] = Math.min(layerTimes[0][me.first.layer], me.startTimestamp);
    layerTimes[0][me.last.layer] = Math.min(layerTimes[0][me.last.layer], me.endTimestamp);
    layerTimes[1][me.first.layer] = Math.max(layerTimes[1][me.first.layer], me.startTimestamp);
    layerTimes[1][me.last.layer] = Math.max(layerTimes[1][me.last.layer], me.endTimestamp);
  }
  for (Commit c : roots) {
    t = c.getCommittingInfo().getWhen().getTime();
    layerTimes[0][0] = Math.min(layerTimes[0][0], t);
    layerTimes[1][0] = Math.max(layerTimes[1][0], t);
  }
}


/**
 * Summary statistics of the dag
 * @return Number of { roots, nodes, leaves, edges, branchCommits, mergeCommits, coolCommits, totCommits }
 */
int[] getSummaryStats() {
  boolean merge, branch;
  if (mergeCommits == null) {
    mergeCommits = new ArrayList<Commit>(nodes.size());
    branchCommits = new ArrayList<Commit>(nodes.size());
    coolCommits = new ArrayList<Commit>(nodes.size());
    ArrayList<Commit> allIn;
    allIn = new ArrayList<Commit>(leaves.size() + roots.size() + nodes.size());
    allIn.addAll(leaves);
    allIn.addAll(nodes);
    allIn.addAll(roots);
    for (Commit c : allIn) {
      if (getInEdges(c).size() > 1)
        merge = true;
      else
        merge = false;
      if (getOutEdges(c).size() > 1)
        branch = true;
      else
        branch = false;
      if (merge && branch)
        coolCommits.add(c);
      else if (merge)
        mergeCommits.add(c);
      else if (branch) branchCommits.add(c);
    }
    mergeCommits.trimToSize();
    branchCommits.trimToSize();
    coolCommits.trimToSize();
  }
  return new int[] {roots.size(), nodes.size(), leaves.size(), getNumMetaEdges(),
      branchCommits.size(), mergeCommits.size(), coolCommits.size(), getNumCommits()};
}


/**
 * It assigns layers to commits and sets the diameter. It also sets the timestamps for each
 * metaedge.
 * 
 * @return non-sequential commits in bf order, with ties decided by comparing commits with
 *         Metagraph.NodeDegreeComparator
 */
Commit[] bfVisit() {
  int i, curSize, layer = 0, size = 0, next = 0,
      tot = nodes.size() + roots.size() + leaves.size();
  boolean done;
  Commit c;
  MetaGraph.NodeDegreeComparator nodeComp = new MetaGraph.NodeDegreeComparator();
  Commit[] res = new Commit[tot];
  ArrayList<Commit> prev = new ArrayList<Commit>();
  ArrayList<Commit> cur = new ArrayList<Commit>(); // to check for duplicates in res
  prev.addAll(roots);
  cur.addAll(roots);
  for (Commit r : roots) {
    r.layer = 0;
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
          e.last.layer = layer;
          if (Collections.binarySearch(cur, e.last) < 0) {
            res[next++] = e.last;
            GitWorks.addUnique(cur, e.last);
          }
        }
      }
    }
    size = curSize;
    Arrays.sort(res, size, next, nodeComp);
    for (i = size; i < next; i++)
      GitWorks.addUnique(prev, res[i]);
  }
  diameter = layer;
  if (!metaEdges.isEmpty()) for (MetaEdge me : metaEdges) {
    me.startTimestamp = me.first.getCommittingInfo().getWhen().getTime();
    me.endTimestamp = me.last.getCommittingInfo().getWhen().getTime();
  }
  return res;
}


void bfPrintout(Commit[] commits) {
  int prev = 0;
  for (Commit c : commits) {
    System.err.print(c.id + " : " + c.layer);
    if (c.layer < prev) System.err.print(" Dag : ERROR : bf-visit layer order is wrong.");
    prev = c.layer;
    System.err.println();
  }
  System.err.println();
}


/**
 * It returns a new metagraph containing only commits within the given date range (committing date
 * is considered). If both parameters are null, it creates a metagraph which incapsulates the
 * existing dag. If at least one parameter is not null, the result is built from scratch, thus all
 * commits are duplicated and all edges are created anew (so they do not keep the original ID,
 * etc.). Note: the result may not contain all original edges within the given interval, since
 * commits that are terminal (first/last of their edges) in the original dag may not be terminal in
 * the resulting graph, due to the time range contraint which may exclude some, but not all edges
 * for given terminal commits. To obtain an exact subset of the original dag, use
 * {@link #buildSubGraph(Date, Date)}.
 * 
 * @param minAge
 * @param maxAge
 * @return
 */
MetaGraph buildNewMetaGraph(Date minAge, Date maxAge) {
  int i;
  Date dFirst, dLast;
  Commit leaf, first, c, co;
  boolean firstIn, lastIn;
  if (minAge == null && maxAge == null) return MetaGraph.createMetaGraph(this);
  ArrayList<Commit> heads = new ArrayList<Commit>(leaves.size() * 2);
  ArrayList<Commit> allCommits = new ArrayList<Commit>(getNumCommits());
  ArrayList<MetaEdge> topCut = new ArrayList<MetaEdge>();
  ArrayList<MetaEdge> middleCut = new ArrayList<MetaEdge>(metaEdges.size());
  ArrayList<MetaEdge> bottomCut = new ArrayList<MetaEdge>();
  ArrayList<MetaEdge> oddCut = new ArrayList<MetaEdge>();
  if (minAge == null) minAge = new Date(0L);
  if (maxAge == null) maxAge = new Date(System.currentTimeMillis() + (1000 * 3600 * 24 * 7));

  if (metaEdges.size() == 0) {
    for (Commit cc : roots) {
      dFirst = cc.getCommittingInfo().getWhen();
      if (dFirst.compareTo(maxAge) <= 0 && dFirst.compareTo(minAge) >= 0) {
        co = new Commit(cc);
        GitWorks.addUnique(allCommits, co);
        GitWorks.addUnique(heads, co);
      }
    }
  }
  for (MetaEdge me : metaEdges) {
    dFirst = me.first.getCommittingInfo().getWhen();
    dLast = me.last.getCommittingInfo().getWhen();
    firstIn = dFirst.compareTo(maxAge) <= 0 && dFirst.compareTo(minAge) >= 0;
    lastIn = dLast.compareTo(maxAge) <= 0 && dLast.compareTo(minAge) >= 0;
    if (firstIn && lastIn) {
      middleCut.add(me);
    } else if (firstIn && !lastIn) {
      topCut.add(me);
    } else if (!firstIn && lastIn) {
      bottomCut.add(me);
    } else if (dFirst.compareTo(minAge) < 0 && dLast.compareTo(maxAge) > 0) {
      oddCut.add(me);
    }
  }

  for (MetaEdge me : middleCut) {
    co = new Commit(me.first);
    GitWorks.addUnique(allCommits, co);
    co = new Commit(me.last);
    co = allCommits.get(GitWorks.addUnique(allCommits, co));
    if (Collections.binarySearch(leaves, me.last) >= 0) GitWorks.addUnique(heads, co);
    if (me.getWeight() > 0) {
      for (Commit cc : me.getInternals()) {
        co = new Commit(cc);
        GitWorks.addUnique(allCommits, co);
      }
    }
  }

  for (MetaEdge me : bottomCut) {
    first = null;
    ArrayList<Commit> coms;
    if (me.getWeight() > 0) {
      coms = me.getInternals();
      for (i = coms.size() - 1; i >= 0; i--) {
        c = coms.get(i);
        if (first == null && c.getCommittingInfo().getWhen().compareTo(minAge) >= 0) {
          first = new Commit(c);
          GitWorks.addUnique(allCommits, first);
        } else if (first != null) {
          co = new Commit(c);
          GitWorks.addUnique(allCommits, co);
        }
      }
    }
    co = new Commit(me.last);
    co = allCommits.get(GitWorks.addUnique(allCommits, co));
    if (Collections.binarySearch(leaves, me.last) >= 0) GitWorks.addUnique(heads, co);
  }

  for (MetaEdge me : topCut) {
    leaf = null;
    if (me.getWeight() > 0) {
      for (Commit cc : me.getInternals()) {
        dLast = cc.getCommittingInfo().getWhen();
        if (leaf == null && dLast.compareTo(maxAge) <= 0) {
          leaf = new Commit(cc);
          GitWorks.addUnique(allCommits, leaf);
        } else if (leaf != null) {
          co = new Commit(cc);
          GitWorks.addUnique(allCommits, co);
        }
      }
    }
    co = new Commit(me.first);
    co = allCommits.get(GitWorks.addUnique(allCommits, co));
    if (leaf == null)
      GitWorks.addUnique(heads, co);
    else
      GitWorks.addUnique(heads, leaf);
  }

  for (MetaEdge me : oddCut) {
    leaf = null;
    if (me.getWeight() > 0) {
      for (Commit cc : me.getInternals()) {
        dFirst = cc.getCommittingInfo().getWhen();
        firstIn = dFirst.compareTo(maxAge) <= 0 && dFirst.compareTo(minAge) >= 0;
        if (firstIn) {
          if (leaf == null) {
            leaf = new Commit(cc);
            GitWorks.addUnique(allCommits, leaf);
          } else {
            co = new Commit(cc);
            GitWorks.addUnique(allCommits, co);
          }
        }
      }
    }
    if (leaf != null) GitWorks.addUnique(heads, leaf);
  }

  allCommits.trimToSize();
  if (allCommits.size() > 0 && heads.size() == 0)
    System.err.println("Dag : ERROR : buildSubMetaGraph inconsistency.");
  return allCommits.size() == 0 ? null : MetaGraph.createMetaGraph(allCommits, heads);
}


/**
 * If both parameters are null, it creates a metagraph which incapsulates the existing dag. If at
 * least one parameter is not null, it returns a sub-graph taken from this object, with only edges
 * within the given date range (committing date is considered). All eligible edges are preserved and
 * the memory footprint is minimized by referencing the original internal commits. New edges take
 * the ID of the original ones which they derive from. The result is actually the portion of the
 * original dag within the given time interval. Note: the result is NOT equal to a metagraph built
 * from scratch with all commits in the given time range (see {@link #buildNewMetaGraph(Date, Date)}
 * ).
 */
MetaGraph buildSubGraph(Date minAge, Date maxAge) { // FIXME ?
  int i;
  MetaEdge newme;
  Date dFirst, dLast;
  Commit leaf, first, c, co;
  boolean firstIn, lastIn;
  if (minAge == null && maxAge == null) return MetaGraph.createMetaGraph(this);
  ArrayList<Commit> heads = new ArrayList<Commit>(leaves.size() * 2);
  ArrayList<Commit> allCommits = new ArrayList<Commit>(getNumCommits());
  ArrayList<MetaEdge> topCut = new ArrayList<MetaEdge>();
  ArrayList<MetaEdge> middleCut = new ArrayList<MetaEdge>();
  ArrayList<MetaEdge> bottomCut = new ArrayList<MetaEdge>();
  ArrayList<MetaEdge> oddCut = new ArrayList<MetaEdge>();
  ArrayList<MetaEdge> edges = new ArrayList<MetaEdge>(metaEdges.size());
  if (minAge == null)
    minAge = new Date(0L);
  if (maxAge == null)
    maxAge = new Date(System.currentTimeMillis() + (1000 * 3600 * 24 * 7));

  if (metaEdges.size() == 0) {
    if (roots.size() > 1) {
      System.err.println("Dag : ERROR : dag with more than one disconnected component (roots).");
      System.exit(2);
    } else if (roots.size() > 0) {
      c = roots.get(0);
      dFirst = c.getCommittingInfo().getWhen();
      if (dFirst.compareTo(maxAge) <= 0 && dFirst.compareTo(minAge) >= 0) {
        GitWorks.addUnique(allCommits, c);
      }
    }
  }
  for (MetaEdge me : metaEdges) {
    dFirst = me.first.getCommittingInfo().getWhen();
    dLast = me.last.getCommittingInfo().getWhen();
    firstIn = dFirst.compareTo(maxAge) <= 0 && dFirst.compareTo(minAge) >= 0;
    lastIn = dLast.compareTo(maxAge) <= 0 && dLast.compareTo(minAge) >= 0;
    if (firstIn && lastIn) {
      middleCut.add(me);
    } else if (firstIn && !lastIn) {
      topCut.add(me);
    } else if (!firstIn && lastIn) {
      bottomCut.add(me);
    } else if (dFirst.compareTo(minAge) < 0 && dLast.compareTo(maxAge) > 0) {
      oddCut.add(me);
    }
  }

  for (MetaEdge me : middleCut) {
    newme = new MetaEdge(me);
    newme.startTimestamp = me.startTimestamp;
    newme.endTimestamp = me.endTimestamp;
    co = new Commit(me.first);
    co = allCommits.get(GitWorks.addUnique(allCommits, co));
    newme.first = co;
    co.outDegree++;
    GitWorks.addUnique(co.edges, newme.ID);
    co = new Commit(me.last);
    co = allCommits.get(GitWorks.addUnique(allCommits, co));
    newme.last = co;
    co.inDegree++;
    GitWorks.addUnique(co.edges, newme.ID);
    if (Collections.binarySearch(leaves, me.last) >= 0)
      GitWorks.addUnique(heads, co);
    if (me.getWeight() > 0) {
      for (Commit cc : me.getInternals()) {
        GitWorks.addUnique(allCommits, cc);
        newme.addInternal(cc);
      }
    }
    GitWorks.addUnique(edges, newme);
  }

  for (MetaEdge me : bottomCut) {
    first = null;
    newme = new MetaEdge(me);
    ArrayList<Commit> coms;
    if (me.getWeight() > 0) {
      coms = me.getInternals();
      for (i = coms.size() - 1; i >= 0 && first == null; i--) {
        c = coms.get(i);
        if (c.getCommittingInfo().getWhen().compareTo(minAge) >= 0) {
          first = new Commit(c);
          newme.first = first;
          newme.startTimestamp = first.getCommittingInfo().getWhen().getTime();
          first.outDegree++;
          GitWorks.addUnique(first.edges, newme.ID);
          GitWorks.addUnique(allCommits, first);
        }
      }
      for (int j = 0; j < i; j++) {
        c = coms.get(j);
        newme.addInternal(c);
        GitWorks.addUnique(allCommits, c);
      }
    }
    co = new Commit(me.last);
    co = allCommits.get(GitWorks.addUnique(allCommits, co));
    newme.last = co;
    newme.endTimestamp = me.endTimestamp;
    if (first != null) {
      co.inDegree++;
      GitWorks.addUnique(edges, newme);
      GitWorks.addUnique(co.edges, newme.ID);
    }
    if (Collections.binarySearch(leaves, me.last) >= 0)
      GitWorks.addUnique(heads, co);
  }

  for (MetaEdge me : topCut) {
    leaf = null;
    newme = new MetaEdge(me);
    if (me.getWeight() > 0) {
      for (Commit cc : me.getInternals()) {
        dLast = cc.getCommittingInfo().getWhen();
        if (leaf == null && dLast.compareTo(maxAge) <= 0) {
          leaf = new Commit(cc);
          newme.last = leaf;
          newme.endTimestamp = leaf.getCommittingInfo().getWhen().getTime();
          leaf.inDegree++;
          GitWorks.addUnique(leaf.edges, newme.ID);
          GitWorks.addUnique(allCommits, leaf);
        } else if (leaf != null) {
          newme.addInternal(cc);
          GitWorks.addUnique(allCommits, cc);
        }
      }
    }
    co = new Commit(me.first);
    co = allCommits.get(GitWorks.addUnique(allCommits, co));
    newme.first = co;
    newme.startTimestamp = me.startTimestamp;
    if (leaf != null) {
      co.outDegree++;
      GitWorks.addUnique(edges, newme);
      GitWorks.addUnique(co.edges, newme.ID);
    }
    if (leaf == null) GitWorks.addUnique(heads, co);
    else GitWorks.addUnique(heads, leaf);
  }

  for (MetaEdge me : oddCut) {
    leaf = null;
    co = null;
    newme = new MetaEdge(me);
    if (me.getWeight() > 0) {
      for (Commit cc : me.getInternals()) {
        dFirst = cc.getCommittingInfo().getWhen();
        firstIn = dFirst.compareTo(maxAge) <= 0 && dFirst.compareTo(minAge) >= 0;
        if (firstIn) {
          if (leaf == null) {
            leaf = new Commit(cc);
            newme.last = leaf;
            newme.endTimestamp = leaf.getCommittingInfo().getWhen().getTime();
            GitWorks.addUnique(allCommits, leaf);
          } else {
            co = new Commit(cc);
            GitWorks.addUnique(allCommits, cc);
            newme.addInternal(cc);
          }
        }
      }
      if (co != null) {
        allCommits.remove(Collections.binarySearch(allCommits, co)); // remove equal cc
        GitWorks.addUnique(allCommits, co);
        newme.getInternals().remove(newme.getInternals().size() - 1); // remove equal cc
        newme.first = co;
        newme.startTimestamp = co.getCommittingInfo().getWhen().getTime();
      }
    }
    if (leaf != null) GitWorks.addUnique(heads, leaf);
    if (newme.first != null && newme.last != null) {
      newme.first.outDegree++;
      newme.last.inDegree++;
      GitWorks.addUnique(newme.first.edges, newme.ID);
      GitWorks.addUnique(newme.last.edges, newme.ID);
      GitWorks.addUnique(edges, newme);
    }
  }

  allCommits.trimToSize();
  edges.trimToSize();
  if (allCommits.size() > 0 && heads.size() == 0)
    System.err.println("Dag : ERROR : buildSubMetaGraph inconsistency.");
  if (allCommits.size() == 0) return null;
  if (edges.size() == 0) {
    MetaGraph res = new MetaGraph(allCommits);
    for (Commit cc : allCommits) {
      dFirst = cc.getCommittingInfo().getWhen();
      res.since = Math.min(dFirst.getTime(), res.since);
      res.until = Math.max(dFirst.getTime(), res.until);
      Dag d = new Dag();
      d.roots.add(cc);
      res.dags.add(d);
    }
    return res;
  }
  return MetaGraph.createMetaGraph(edges, allCommits, heads);
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
  if (!verify)
    return res;
  if (tot == roots.size())
    return metaEdges.size() == 0;
  ArrayList<Commit> terminals = new ArrayList<Commit>(leaves.size() + nodes.size() + roots.size());
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
  if (roots.size() > 1 && metaEdges.size() == 0) {
    System.err.println("Dag checkup : ERROR : multiple detached root commits.");
    res = false;
  }
  if (metaEdges.size() > 0) {
    for (Commit c : roots) {
      if (c.outDegree == 0) {
        System.err.println("Dag checkup : ERROR : detached root commit " + c.id.getName()
            + " should no be here.");
        res = false;
      }
    }
  }

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
//  if (!res) {
//    System.err.println("Leaves :");
//    GitWorks.printAny(leaves, "\n", System.err);
//    System.err.println("Edges :");
//    GitWorks.printAny(metaEdges, "\n", System.err);
//    System.err.println("Roots :");
//    GitWorks.printAny(roots, "\n============================\n", System.err);
//  }
  System.gc();
  return res;
}


public String toString() {
  return roots.size() + " roots, " + leaves.size() + " leaves, "
      + nodes.size() + " nodes, " + getNumMetaEdges() + " metaedges for "
      + getNumCommits() + " total commits.";
}


void exportToGexf(String name) { // XXX
  Gexf gexf = new GexfImpl();

  gexf.getMetadata()
      .setLastModified(new java.util.Date(System.currentTimeMillis()))
      .setCreator("gitworks")
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
  Attribute attTstamp = nAttrList.createAttribute("3", AttributeType.LONG, "timestamp");
  Attribute attWeight = eAttrList.createAttribute("4", AttributeType.INTEGER, "meta-weight");
  Attribute attLayer = eAttrList.createAttribute("5", AttributeType.INTEGER, "span");

  MyNode node;
  ArrayList<MyNode> cache = new ArrayList<MyNode>(nodes.size() + roots.size() + leaves.size());
  for (Commit c : nodes) {
    node = new MyNode(c.id.getName());
    graph.getNodes().add(node);
    node.setLabel(c.id.getName().substring(0, 6))
        .getAttributeValues()
            .addValue(attBranches, "" + c.branches.size())
            .addValue(attHeads, "" + (c.isHead() ? c.heads.size() : 0))
            .addValue(attExtremal, "false")
            .addValue(attTstamp, "" + c.getCommittingInfo().getWhen().getTime());
    cache.add(node);
  }
  for (Commit c : leaves) {
    node = new MyNode(c.id.getName());
    graph.getNodes().add(node);
    node.setLabel(c.id.getName().substring(0, 6))
        .getAttributeValues()
            .addValue(attBranches, "" + c.branches.size())
            .addValue(attHeads, "" + (c.isHead() ? c.heads.size() : 0)) // possibly not head if subgraph
            .addValue(attExtremal, "true")
            .addValue(attTstamp, "" + c.getCommittingInfo().getWhen().getTime());
    cache.add(node);
  }
  for (Commit c : roots) {
    node = new MyNode(c.id.getName());
    graph.getNodes().add(node);
    node.setLabel(c.id.getName().substring(0, 6))
        .getAttributeValues()
            .addValue(attBranches, "" + c.branches.size())
            .addValue(attHeads, "" + (c.isHead() ? c.heads.size() : 0))
            .addValue(attExtremal, "true")
            .addValue(attTstamp, "" + c.getCommittingInfo().getWhen().getTime());
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
      e = node.connectTo("" + me.ID, "" + me.ID, n2); // id , label, target
      e.setEdgeType(EdgeType.DIRECTED).setWeight(0.0f);
    }
    e.setWeight(e.getWeight() + (float)me.getWeight())
        .getAttributeValues().addValue(attWeight, "" + e.getWeight())
        .addValue(attLayer, "" + me.getLayerSpan());
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
