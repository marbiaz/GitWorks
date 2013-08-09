package gitworks;


import gitworks.GitMiner.PersonFrequency;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.Date;

import org.eclipse.jgit.lib.PersonIdent;


public class Features implements Comparable<Object>, Externalizable {

// The name of the root fork identifies the Features instance
String name = "";
//Index of the root fork in allF
int rootIndex = -1;
// for each author, how many commits in each fork (overall and after fork creation)
int[][][] authorsImpactPerF;
// For each fork, the indexes of the commits in it
int[][] forkCommit;
// for each commit, the meaningful forks it is in
int[][] vipForkForC;
// All fork names, in order
public String allForks[];
//All author unique identifiers, in order
public String allAuthors[];
// All fork's creation timestamps, ordered as allF.
public long since[];
// For each fork, time of data retrieval from github
public long until[];
// For each commit, its timestamp
public long[] commitTimeLine;
// Ordered list of root's commit indexes
int[] acRootCommits;
// For each commit, its author
public int[] commitAuthor;
//For each fork, number of commits, in order
public int[] commitsOfF;
//For each fork, number of authors of commits, in order
public int[] authorsOfF;
//For each fork, number of commits after the fork's creation, in order
public int[] acCommitsOfF;
//For each fork, number of authors of commits after fork's creation, in order
public int[] acAuthorsOfF;
// For each commit, how many forks is it in
public int[] commitDiffusion;
//For each commit, how many forks is it in, among those created before the commit time
public int[] acCommitDiffusion;
// For each fork, number of unique commits, in order
public int[] uCommitsOfF;
// For each fork, number of authors of unique commits, in order
public int[] uAuthorsOfF;
// For each fork, number of branches, in order
public int[] branchesOfF;
// Number of commits in the fork tree
public int nCommits;
// Number of watchers of the root fork
public int nWatchers;
// Number of watchers in the fork tree
public int totWatchers;
// Max number of watchers among the forks (root excluded)
public int maxWatchers;
// Depth of the fork tree
public int nGenerations;
// Max number of siblings among generations of forks (not direct children of root fork)
public int maxGenSize;
// Number of direct forks of the root fork
public int nForks;

/************  begin complex features  ************/

public static enum CommitRank {
  NONE(-1), // in root fork since before it was created
  ROOT(0), // only in root fork
  UNIQUE(1), // only in one fork (non root)
  U_VIP(2), // only in one fork and in root fork
  VIP(3), // in root fork and in more than one other fork
  SCATTERED(4), // in more than one fork but not in root fork
  PERVASIVE(5); // in every fork
  private final int id;
  CommitRank(int id) { this.id = id; }
  public int getValue() { return id; }
  static public CommitRank getRank(int value) {
    for (CommitRank c : CommitRank.values()) {
      if (c.getValue() == value) {
        return c;
      }
    }
    return null;
  }
}

//For each commit, its rank (as defined by CommitRank)
public int[] commitRank;
// For each fork : for each rank, the list of commits belonging to that rank
ArrayList<Integer>[][] vipCommitForF;
//For each ranking (+1), how many commits are ranked like that (normalized)
public double[] commitRankRatio;
// For each entry in commitRankRatio, how many authors are ranked like that (normalized)
public double[] authorRankRatio;
// For each week (counting from present time backwards), the number of commits in that week
public int[] aggregateTimeLines;
// For each commit, the week it has been committed (counting from retrieval time backwards)
public int[] extendedTimeLines;
// For each rank, matrix of the graph that models common commits among forks
ArrayList<Integer>[][][] cLinkMap;
// For each rank, matrix of the graph that models authors of common commits among forks
ArrayList<Integer>[][][] aLinkMap;


@SuppressWarnings({"unchecked"})
private void computeMore() {
  commitRank = new int[acCommitDiffusion.length];
  vipCommitForF = new ArrayList[allForks.length][CommitRank.values().length];
  commitRankRatio = new double[CommitRank.values().length];
  authorRankRatio = new double[CommitRank.values().length];
  cLinkMap = new ArrayList[CommitRank.values().length][][];
  aLinkMap = new ArrayList[CommitRank.values().length][][];
  ArrayList<Integer>[] authorsInRank = new ArrayList[CommitRank.values().length];
  int i, k;
  for (i = 0; i < authorsInRank.length; i++)
    authorsInRank[i] = new ArrayList<Integer>();

  computeTimeLines();

  for (int q = 0; q < CommitRank.values().length; q++) {
    cLinkMap[q] = new ArrayList[allForks.length][allForks.length];
    aLinkMap[q] = new ArrayList[allForks.length][allForks.length];
    for (i = 0; i < allForks.length; i++) {
      vipCommitForF[i][q] = new ArrayList<Integer>();
      for (k = 0; k < allForks.length; k++) {
        cLinkMap[q][i][k] = new ArrayList<Integer>();
        aLinkMap[q][i][k] = new ArrayList<Integer>();
      }
    }
  }
  Arrays.fill(commitRankRatio, 0.0);
  Arrays.fill(authorRankRatio, 0.0);
  for (i = 0; i < vipForkForC.length; i++) {
    switch (vipForkForC[i].length) {
    case 0:
      commitRank[i] = CommitRank.NONE.getValue();
    break;
    case 1:
      commitRank[i] = Arrays.binarySearch(acRootCommits, i) >= 0 ? CommitRank.ROOT.getValue()
          : CommitRank.UNIQUE.getValue();
    break;
    case 2:
      commitRank[i] = Arrays.binarySearch(acRootCommits, i) >= 0 ? CommitRank.U_VIP.getValue()
          : CommitRank.SCATTERED.getValue();
    break;
    default:
      if (vipForkForC[i].length == allForks.length)
        commitRank[i] = CommitRank.PERVASIVE.getValue();
      else
        commitRank[i] = Arrays.binarySearch(acRootCommits, i) >= 0 ? CommitRank.VIP.getValue()
            : CommitRank.SCATTERED.getValue();
    }

    for (k = 0; k < vipForkForC[i].length; k++) {
      GitWorks.addUnique(vipCommitForF[vipForkForC[i][k]][commitRank[i] + 1], i);
      for (int kk = k + 1; kk < vipForkForC[i].length; kk++) {
        GitWorks.addUnique(cLinkMap[commitRank[i] + 1][vipForkForC[i][k]][vipForkForC[i][kk]], i);
        GitWorks.addUnique(cLinkMap[commitRank[i] + 1][vipForkForC[i][kk]][vipForkForC[i][k]], i);
        GitWorks.addUnique(aLinkMap[commitRank[i] + 1][vipForkForC[i][k]][vipForkForC[i][kk]],
            commitAuthor[i]);
        GitWorks.addUnique(aLinkMap[commitRank[i] + 1][vipForkForC[i][kk]][vipForkForC[i][k]],
            commitAuthor[i]);
      }
    }

    commitRankRatio[commitRank[i] + 1]++;
    GitWorks.addUnique(authorsInRank[commitRank[i] + 1], commitAuthor[i]);
  }

  for (i = 0; i < commitRankRatio.length; i++) {
    commitRankRatio[i] /= acCommitDiffusion.length;
  }

  for (i = 0; i < authorRankRatio.length; i++) {
    authorRankRatio[i] = (1.0 * authorsInRank[i].size()) / allAuthors.length;
  }

}


private void computeTimeLines() {
  final long week = 1000 * 3600 * 24 * 7;
  int cur, i;
  long max = 0;
  long min = Long.MAX_VALUE;
  for (long l : until)
    max = Math.max(max, l);
  for (long l : since)
    min = Math.min(min, l);
  aggregateTimeLines = new int[(int)((max - min) / week) + 1];
  // System.err.println(f.name + " : MAX = " + max + " ; MIN = " + min + " ; #weeks = " +
  //     aggregateTimeLines.length);
  extendedTimeLines = new int[commitTimeLine.length];
  Arrays.fill(aggregateTimeLines, 0);
  Arrays.fill(extendedTimeLines, 0);
  for (i = 0; i < commitTimeLine.length; i++) {
    cur = (int)((max - commitTimeLine[i]) / week);
    cur = Math.min(cur, aggregateTimeLines.length - 1);
    aggregateTimeLines[cur]++;
    extendedTimeLines[i] = cur;
  }

}

/************  end complex features  ************/

public int getRootIndex() {
  return rootIndex;
}


public int[] getCommits(int fIndex) {
  return forkCommit[fIndex];
}


// TODO: better by scanning allCommits once and for all ??
void setFeatures(ForkList fl, ForkEntry fe, GitMiner gm) {
  int j, i = 0;
  PersonFrequency p;
  PersonIdent pi;
  ArrayList <PersonFrequency> ap;
  Iterator<PersonFrequency> pIt;
  Iterator<Commit> cIt;
  Iterator<ArrayList<PersonFrequency>> apit;
  Iterator<ArrayList<BranchRef>> brAlIt;
  ArrayList<Commit> ca; Commit c; int indx;
  ArrayList<Integer> acMainlineCommits = new ArrayList<Integer>();
  vipForkForC = new int[gm.allCommits.size()][];
  commitDiffusion = new int[gm.allCommits.size()];
  acCommitDiffusion = new int[gm.allCommits.size()];
  commitTimeLine = new long[gm.allCommits.size()];
  commitAuthor = new int[gm.allCommits.size()];
  branchesOfF = new int[gm.branches.size()];
  allForks = gm.comInF.keySet().toArray(new String[0]);
  allAuthors = new String [gm.allAuthors.size()];
  rootIndex = Arrays.binarySearch(allForks, gm.name);
  authorsImpactPerF = new int[gm.allAuthors.size()][allForks.length][2];
  uCommitsOfF = new int[allForks.length];
  uAuthorsOfF = new int[allForks.length];
  commitsOfF = new int[allForks.length];
  authorsOfF = new int[allForks.length];
  acCommitsOfF = new int[allForks.length];
  acAuthorsOfF = new int[allForks.length];
  since = new long[allForks.length];
  until = new long[allForks.length];
  forkCommit = new int[allForks.length][];

  for (Person pe : gm.allAuthors) {
    allAuthors[i++] = pe.getUniqueID();
  }
  i = 0;
  ForkEntry f2;
  for (String f : allForks) {
    f2 = GitWorks.getElement(fl, f.replaceFirst(GitWorks.safe_sep, GitWorks.id_sep));
    since[i] = f2.getCreationTimestamp();
    until[i++] = f2.getRetrievalTimestamp();
  }
  brAlIt = gm.branches.values().iterator();
  i = 0;
  while (brAlIt.hasNext()) {
    branchesOfF[i++] = brAlIt.next().size();
  }

  for (int d = 0; d < authorsImpactPerF.length; d++) {
    for (i = 0; i < authorsImpactPerF[d].length; i++)
      Arrays.fill(authorsImpactPerF[d][i], 0);
  }
  apit = gm.authOfComInF.values().iterator();
  i = 0;
  while (apit.hasNext()) {
    ap = apit.next();
    authorsOfF[i] = ap.size();
    pIt = ap.iterator();
    while (pIt.hasNext()) {
      p = pIt.next();
      authorsImpactPerF[p.index][i][0] = p.freq;
    }
    i++;
  }

  cIt = gm.allCommits.iterator();
  i = 0;
  int acRes, fIndex, k;
  boolean inRoot;
  Commit co;
  String[] repos;
  ArrayList<Integer> vipF = new ArrayList<Integer>(gm.allCommits.size());
  while (cIt.hasNext()) {
    co = cIt.next();
    acRes = 0;
    commitAuthor[i] = Collections.binarySearch(gm.allAuthors, co.getAuthoringInfo());
    commitTimeLine[i] = co.getCommittingInfo().getWhen().getTime();
    repos = co.getRepos();
    inRoot = Arrays.binarySearch(repos, allForks[rootIndex]) >= 0;

    for (String s : repos) {
      fIndex = Arrays.binarySearch(allForks, s);
      if (since[fIndex] < commitTimeLine[i]) {
        acRes++;
        if (fIndex == rootIndex) acMainlineCommits.add(i);
        authorsImpactPerF[commitAuthor[i]][fIndex][1]++;
        vipF.add(fIndex);
      } else if (!inRoot) {
        vipF.add(fIndex);
      }
    }
    commitDiffusion[i] = repos.length;
    acCommitDiffusion[i] = acRes;
    vipForkForC[i] = new int[vipF.size()];
    j = 0;
    for (int f : vipF) {
      vipForkForC[i][j++] = f;
    }

    vipF.clear();
    i++;
  }

  acRootCommits = new int[acMainlineCommits.size()];
  for (i = 0; i < acMainlineCommits.size(); i++) {
    acRootCommits[i] = acMainlineCommits.get(i).intValue();
  }

  Arrays.fill(uCommitsOfF, 0);
  Arrays.fill(uAuthorsOfF, 0);
  Arrays.fill(acCommitsOfF, 0);
  Arrays.fill(acAuthorsOfF, 0);
  String uF[] = null; Date fDate;
  int authorsHere[] = new int[gm.allAuthors.size()];
  if (gm.comOnlyInF != null)
    uF = gm.comOnlyInF.keySet().toArray(new String[0]);

  for(i = 0, j = 0; i < allForks.length; i++) {
    fDate = new Date(since[i]);
    ca = gm.comInF.get(allForks[i]);
    commitsOfF[i] = ca.size();
    authorsOfF[i] = gm.authOfComInF.get(allForks[i]).size();
    forkCommit[i] = new int[ca.size()];
    Arrays.fill(authorsHere, 0);
    cIt = ca.iterator();
    k = 0;
    while (cIt.hasNext()) {
      c = cIt.next();
      pi = c.getCommittingInfo();
      if (pi.getWhen().after(fDate)) {
        pi = c.getAuthoringInfo();
        indx = Collections.binarySearch(gm.allAuthors, pi);
        authorsHere[indx]++;
        if (authorsHere[indx] == 1) {
          acAuthorsOfF[i]++;
        }
        acCommitsOfF[i]++;
      }
      forkCommit[i][k++] = Collections.binarySearch(gm.allCommits, c);
    }
    if (uF != null && j < uF.length && allForks[i].equals(uF[j])) {
      ca = gm.comOnlyInF.get(uF[j]);
      uCommitsOfF[i] = ca.size();
      uAuthorsOfF[i] = gm.authOfComOnlyInF.get(uF[j++]).size();
    }
  }

  nCommits = gm.allCommits.size();
  nForks = fe.howManyForks();
  nWatchers = fe.getWatchers();
  totWatchers = fe.dfsChildrenWatchers + nWatchers;
  maxWatchers = fe.dfsMaxWatchers;
  nGenerations = fe.dfsAggregateDepth;
  maxGenSize = fe.dfsMaxChildren;
  if (!fe.dfsOk || nWatchers < 0) {
    System.err.println("FeatureSet: ERROR : the aggregates of " + fe.getId() + " are not valid!");
  }
  name = allForks[rootIndex];

  computeMore();
}


@Override
public String toString() {
  String res = "";
  res += "Name : " + name + " ; "
      + "Forks : " + allForks.length + " ; "
      + "Commits : " + nCommits + " ; "
      + "Authors : " + allAuthors.length + " ; "
      + "Root's children : " + nForks + " ; "
      + "Tot watchers : " + totWatchers + " ; "
      + "Max watchers : " + maxWatchers;
  return res;
}


@Override
public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
  int j, i, authors, size;
  size = in.readInt();
  authors = in.readInt();
  authorsImpactPerF = new int[authors][size][2];
  allAuthors = new String[authors];
  allForks = new String[size];
  commitsOfF = new int[size];
  authorsOfF = new int[size];
  uCommitsOfF = new int[size];
  uAuthorsOfF = new int[size];
  acCommitsOfF = new int[size];
  acAuthorsOfF = new int[size];
  since = new long[size];
  until = new long[size];
  branchesOfF = new int[size];
  forkCommit = new int[size][];
  for (i = 0; i < size; i++) {
    allForks[i] = in.readUTF();
    since[i] = in.readLong();
    until[i] = in.readLong();
    for (j = 0; j < authors; j++) {
      authorsImpactPerF[j][i][0] = in.readInt();
      authorsImpactPerF[j][i][1] = in.readInt();
    }
    authorsOfF[i] = in.readInt();
    commitsOfF[i] = in.readInt();
    branchesOfF[i] = in.readInt();
    uAuthorsOfF[i] = in.readInt();
    uCommitsOfF[i] = in.readInt();
    acAuthorsOfF[i] = in.readInt();
    acCommitsOfF[i] = in.readInt();
  }
  rootIndex = in.readInt();
  name = allForks[rootIndex];
  size = in.readInt();
  commitDiffusion = new int[size];
  acCommitDiffusion = new int[size];
  commitAuthor = new int[size];
  commitTimeLine = new long[size];
  vipForkForC = new int[size][];
  for(i = 0; i < commitDiffusion.length; i++) {
    commitDiffusion[i] = in.readInt();
    acCommitDiffusion[i] = in.readInt();
    commitAuthor[i] = in.readInt();
    commitTimeLine[i] = in.readLong();
    vipForkForC[i] = new int[in.readInt()];
    for (j = 0; j < vipForkForC[i].length; j++) {
      vipForkForC[i][j] = in.readInt();
    }
  }
  acRootCommits = new int[in.readInt()];
  for (i = 0; i < acRootCommits.length; i++) {
    acRootCommits[i] = in.readInt();
  }
  for (i = 0; i < authors; i++) {
    allAuthors[i] = in.readUTF();
  }
  for (i = 0; i < allForks.length; i++) {
    size = in.readInt();
    forkCommit[i] = new int[size];
    for (j = 0; j < size; j++) {
      forkCommit[i][j] = in.readInt();
    }
  }
  nCommits = in.readInt();
  nWatchers = in.readInt();
  totWatchers = in.readInt();
  maxWatchers = in.readInt();
  nGenerations = in.readInt();
  maxGenSize = in.readInt();
  nForks = in.readInt();

  computeMore();
}


@Override
public void writeExternal(ObjectOutput out) throws IOException {
  out.writeInt(allForks.length);
  out.writeInt(allAuthors.length);
  for (int i = 0; i < allForks.length; i++) {
    out.writeUTF(allForks[i]);
    out.writeLong(since[i]);
    out.writeLong(until[i]);
    for (int j = 0; j < authorsImpactPerF.length; j++) {
      out.writeInt(authorsImpactPerF[j][i][0]);
      out.writeInt(authorsImpactPerF[j][i][1]);
    }
    out.writeInt(authorsOfF[i]);
    out.writeInt(commitsOfF[i]);
    out.writeInt(branchesOfF[i]);
    out.writeInt(uAuthorsOfF[i]);
    out.writeInt(uCommitsOfF[i]);
    out.writeInt(acAuthorsOfF[i]);
    out.writeInt(acCommitsOfF[i]);
  }
  out.writeInt(rootIndex);
  out.writeInt(commitDiffusion.length);
  for (int d = 0; d < commitDiffusion.length; d++) {
    out.writeInt(commitDiffusion[d]);
    out.writeInt(acCommitDiffusion[d]);
    out.writeInt(commitAuthor[d]);
    out.writeLong(commitTimeLine[d]);
    out.writeInt(vipForkForC[d].length);
    for (int i = 0; i < vipForkForC[d].length; i++) {
      out.writeInt(vipForkForC[d][i]);
    }
  }
  out.writeInt(acRootCommits.length);
  for (int i = 0; i < acRootCommits.length; i++) {
    out.writeInt(acRootCommits[i]);
  }
  for (int i = 0; i < allAuthors.length; i++) {
    out.writeUTF(allAuthors[i]);
  }
  for (int i = 0; i < forkCommit.length; i++) {
    out.writeInt(forkCommit[i].length);
    for (int j = 0; j < forkCommit[i].length; j++) {
      out.writeInt(forkCommit[i][j]);
    }
  }
  out.writeInt(nCommits);
  out.writeInt(nWatchers);
  out.writeInt(totWatchers);
  out.writeInt(maxWatchers);
  out.writeInt(nGenerations);
  out.writeInt(maxGenSize);
  out.writeInt(nForks);
  out.flush();
}


@Override
public int compareTo(Object o) {
  if (o instanceof Features)
    return name.compareTo(((Features)o).name);
  else if (o instanceof String)
    return name.compareTo((String)o);
  return -1;
}


@Override
public boolean equals(Object o) {
  return compareTo(o) == 0;
}

}
