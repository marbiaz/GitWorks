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

import org.apache.commons.math3.stat.descriptive.summary.Sum;
import org.eclipse.jgit.lib.PersonIdent;

//TODO : allAuthors ??? 'after-creation' version of commitDiffusion and authorsImpact
public class Features implements Externalizable {

String name;

// for each author, how many commits in each fork
public ArrayList<Double> authorsImpactPerF[];
// All fork names, in order
String allForks[];
// All fork's creation timestamps, ordered as allF.
long since[];
//For each fork, number of commits, in order
public double[] commitsOfF;
//For each fork, number of authors of commits, in order
public double[] authorsOfF;
//For each fork, number of commits after the fork's creation, in order
public double[] acCommitsOfF;
//For each fork, number of authors of commits after fork's creation, in order
public double[] acAuthorsOfF;
// For each commit, how many forks is it in
public double[] commitDiffusion;
// For each fork, number of unique commits, in order
public double[] uCommitsOfF;
// For each fork, number of authors of unique commits, in order
public double[] uAuthorsOfF;
//For each fork, ratio of the number of unique commits over the number of commits, in order
public double[] rUCommitsOfF;
//For each fork, ratio of the number of authors of unique commits over the number of authors, in order
public double[] rUAuthorsOfF;
// For each fork, ratio of the number of commits after fork's creation over the number of commits, in order
public double[] rAcCommitsOfF;
//For each fork, ratio of the number of authors of commits after fork's creation over the number of authors, in order
public double[] rAcAuthorsOfF;
// For each fork, number of branches, in order
public double[] branchesOfF;
// Index of the root fork in allF
int rootIndex;
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


void setFeatures(ForkEntry fe, GitMiner gm) {
  int j, i = 0;
  PersonFrequency p;
  PersonIdent pi;
  ArrayList <PersonFrequency> ap;
  Iterator<PersonFrequency> pIt;
  Iterator<Commit> cIt;
  Iterator<ArrayList<PersonFrequency>> apit;
  Iterator<ArrayList<BranchRef>> brAlIt;
  ArrayList<Commit> ca; Commit c; int indx;
  @SuppressWarnings("unchecked")
  ArrayList<Double> aipr[] = new ArrayList[gm.allAuthors.size()];
  authorsImpactPerF = aipr;
  commitDiffusion = new double[gm.allCommits.size()];
  branchesOfF = new double[gm.branches.size()];
  allForks = gm.comInF.keySet().toArray(new String[0]);
  rootIndex = Arrays.binarySearch(allForks, gm.name);
  uCommitsOfF = new double[allForks.length];
  uAuthorsOfF = new double[allForks.length];
  commitsOfF = new double[allForks.length];
  authorsOfF = new double[allForks.length];
  acCommitsOfF = new double[allForks.length];
  acAuthorsOfF = new double[allForks.length];
  rUCommitsOfF = new double[allForks.length];
  rUAuthorsOfF = new double[allForks.length];
  rAcCommitsOfF = new double[allForks.length];
  rAcAuthorsOfF = new double[allForks.length];
  since = new long[allForks.length];

  for (String f : allForks) {
    since[i++] = GitWorks.projects.get(
        f.replaceFirst(GitWorks.safe_sep, GitWorks.id_sep)).getCreationTimestamp();
  }
  brAlIt = gm.branches.values().iterator();
  i = 0;
  while (brAlIt.hasNext()) {
    branchesOfF[i++] = brAlIt.next().size();
  }
  apit = gm.authOfComInF.values().iterator();
  i = 0;
  while (apit.hasNext()) {
    ap = apit.next();
    authorsOfF[i++] = ap.size();
    pIt = ap.iterator();
    while (pIt.hasNext()) {
      p = pIt.next();
      if (authorsImpactPerF[p.index] == null) {
        authorsImpactPerF[p.index] = new ArrayList<Double>(gm.authOfComInF.size());
      }
      authorsImpactPerF[p.index].add(p.freq * 1.0);
    }
  }
  for (ArrayList<Double> a : authorsImpactPerF) {
    a.trimToSize();
  }
  cIt = gm.allCommits.iterator();
  i = 0;
  while (cIt.hasNext()) {
    commitDiffusion[i++] = cIt.next().repoCount();
  }

  Arrays.fill(uCommitsOfF, 0.0);
  Arrays.fill(uAuthorsOfF, 0.0);
  Arrays.fill(acCommitsOfF, 0.0);
  Arrays.fill(acAuthorsOfF, 0.0);
  String uF[] = null; Date fDate;
  int authorsHere[] = new int[gm.allAuthors.size()];
  if (gm.comOnlyInF != null)
    uF = gm.comOnlyInF.keySet().toArray(new String[0]);

  // XXX: maybe better by scanning allCommits once and for all ??
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
  for (i = 0; i < allForks.length; i++) {
    rUCommitsOfF[i] = uCommitsOfF[i] / commitsOfF[i];
    rUAuthorsOfF[i] = uAuthorsOfF[i] / authorsOfF[i];
    rAcCommitsOfF[i] = acCommitsOfF[i] / commitsOfF[i];
    rAcAuthorsOfF[i] = acAuthorsOfF[i] / authorsOfF[i];
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
  name = gm.name;
}


@Override
public String toString() {
  String res = "";
  res += "Name : " + name + " ; "
      + "Forks : " + allForks.length + " ; "
      + "Commits : " + nCommits + "\n"
      + "Root's children : " + nForks + " ; "
      + "Tot watchers : " + totWatchers + " ; "
      + "Max watchers : " + maxWatchers + "\n"
      + "Tot unique commits : " + (uCommitsOfF != null ? ((new Sum()).evaluate(uCommitsOfF)
      + " , distributed in " + uCommitsOfF.length + " forks.") : 0);
  return res;
}


@SuppressWarnings("unchecked")
@Override
public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
  int j, i, size;
  name = in.readUTF();
  authorsImpactPerF = new ArrayList[in.readInt()];
  for (i = 0; i < authorsImpactPerF.length; i++) {
    size = in.readInt();
    authorsImpactPerF[i] = new ArrayList<Double>(size);
    for (j = 0; j < size; j++) {
      authorsImpactPerF[i].add(in.readDouble());
    }
  }
  size = in.readInt();
  allForks = new String[size];
  commitsOfF = new double[size];
  authorsOfF = new double[size];
  uCommitsOfF = new double[size];
  uAuthorsOfF = new double[size];
  acCommitsOfF = new double[size];
  acAuthorsOfF = new double[size];
  rUCommitsOfF = new double[size];
  rUAuthorsOfF = new double[size];
  rAcCommitsOfF = new double[size];
  rAcAuthorsOfF = new double[size];
  since = new long[size];
  branchesOfF = new double[size];
  for (i = 0; i < size; i++) {
    allForks[i] = in.readUTF();
    since[i] = in.readLong();
    authorsOfF[i] = in.readDouble();
    commitsOfF[i] = in.readDouble();
    branchesOfF[i] = in.readDouble();
    uAuthorsOfF[i] = in.readDouble();
    uCommitsOfF[i] = in.readDouble();
    acAuthorsOfF[i] = in.readDouble();
    acCommitsOfF[i] = in.readDouble();
    rUCommitsOfF[i] = in.readDouble();
    rUAuthorsOfF[i] = in.readDouble();
    rAcCommitsOfF[i] = in.readDouble();
    rAcAuthorsOfF[i] = in.readDouble();
  }
  rootIndex = Arrays.binarySearch(allForks, name);
  commitDiffusion = new double[in.readInt()];
  for(i = 0; i < commitDiffusion.length; i++) {
    commitDiffusion[i] = in.readDouble();
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
  Iterator<Double> dit;
  out.writeUTF(name);
  out.writeInt(authorsImpactPerF.length);
  for (ArrayList<Double> ad : authorsImpactPerF) {
    out.writeInt(ad.size());
    dit = ad.iterator();
    while (dit.hasNext()) {
      out.writeDouble(dit.next());
    }
  }
  out.writeInt(allForks.length);
  for (int i = 0; i < allForks.length; i++) {
    out.writeUTF(allForks[i]);
    out.writeLong(since[i]);
    out.writeDouble(authorsOfF[i]);
    out.writeDouble(commitsOfF[i]);
    out.writeDouble(branchesOfF[i]);
    out.writeDouble(uAuthorsOfF[i]);
    out.writeDouble(uCommitsOfF[i]);
    out.writeDouble(acAuthorsOfF[i]);
    out.writeDouble(acCommitsOfF[i]);
    out.writeDouble(rUCommitsOfF[i]);
    out.writeDouble(rUAuthorsOfF[i]);
    out.writeDouble(rAcCommitsOfF[i]);
    out.writeDouble(rAcAuthorsOfF[i]);
  }
  out.writeInt(commitDiffusion.length);
  for (double d : commitDiffusion) {
    out.writeDouble(d);
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
