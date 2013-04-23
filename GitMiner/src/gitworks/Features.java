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


public class Features implements Externalizable {

// The name of the root fork identifies the Features instance
String name = "";
//Index of the root fork in allF
int rootIndex = -1;
// for each author, how many commits in each fork (overall and after fork creation)
public int[][][] authorsImpactPerF;
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
public int[] acRootCommits;
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
// Max number of siblings among generations of forks
public int maxGenSize;
// Number of direct forks of the root fork
public int nForks;

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

  for (Person pe : gm.allAuthors) {
    allAuthors[i++] = pe.getUniqueID();
  }
  i = 0;
  ForkEntry f2;
  for (String f : allForks) {
    f2 = GitWorks.projects.get(
        f.replaceFirst(GitWorks.safe_sep, GitWorks.id_sep));
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
    Arrays.fill(authorsHere, 0);
    cIt = ca.iterator();
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
}


@Override
public String toString() {
  String res = "";
  res += "Name : " + name + " ; "
      + "Forks : " + allForks.length + " ; "
      + "Commits : " + nCommits + "\n"
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
  for(i = 0; i < commitDiffusion.length; i++) {
    commitDiffusion[i] = in.readInt();
    acCommitDiffusion[i] = in.readInt();
    commitAuthor[i] = in.readInt();
    commitTimeLine[i] = in.readLong();
  }
  acRootCommits = new int[in.readInt()];
  for (i = 0; i < acRootCommits.length; i++) {
    acRootCommits[i] = in.readInt();
  }
  for (i = 0; i < authors; i++) {
    allAuthors[i] = in.readUTF();
  }
  nCommits = in.readInt();
  nWatchers = in.readInt();
  totWatchers = in.readInt();
  maxWatchers = in.readInt();
  nGenerations = in.readInt();
  maxGenSize = in.readInt();
  nForks = in.readInt();
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
  }
  out.writeInt(acRootCommits.length);
  for (int i = 0; i < acRootCommits.length; i++) {
    out.writeInt(acRootCommits[i]);
  }
  for (int i = 0; i < allAuthors.length; i++) {
    out.writeUTF(allAuthors[i]);
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

}
