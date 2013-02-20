package gitworks;


import gitworks.GitMiner.PersonFrequency;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;

import org.apache.commons.math3.stat.descriptive.summary.Sum;


public class Features implements Externalizable {

String name;

// for each author, how many commits in each fork
public ArrayList<Double> authorsImpactPerF[];
// All fork names, in order
String allF[];
// Names of forks with unique commits, in order
String uF[];
// Number of commits in each fork, in order
public double[] commitsOfF;
// For each commit, how many forks is it in
public double[] commitDiffusion;
// For each fork, how many unique commits
public double[] uCommitsOfF;
// For each fork, how many authors of unique commits
public double[] uAuthorsOfF;
// For each fork, the number of branches
public double[] branchesOfF;
// Index of the root fork in allF
int rootAIndex;
// Index of the root fork in uF ( < 0 if not present)
int rootUIndex;
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
  int i;
  PersonFrequency p;
  String fork, cur;
  Iterator<PersonFrequency> pIt;
  Iterator<Commit> cIt;
  Iterator<BranchRef> brIt;
  Iterator<ArrayList<PersonFrequency>> apit;
  Iterator<ArrayList<BranchRef>> brAlIt;
  @SuppressWarnings("unchecked")
  ArrayList<Double> aipr[] = new ArrayList[gm.allAuthors.size()];
  authorsImpactPerF = aipr;
  allF = gm.comInF.keySet().toArray(new String[0]);
  rootAIndex = Arrays.binarySearch(allF, gm.name);
  if (gm.comOnlyInF != null) {
    uF = gm.comOnlyInF.keySet().toArray(new String[0]);
    rootUIndex = Arrays.binarySearch(uF, gm.name);
    uCommitsOfF = new double[uF.length];
    uAuthorsOfF = new double[uF.length];
    for(i = 0; i < uF.length; i++) {
      uAuthorsOfF[i] = gm.authOfComOnlyInF.get(uF[i]).size();
      uCommitsOfF[i] = gm.comOnlyInF.get(uF[i]).size();
    }
  } else {
    uF = null;
    rootUIndex = -1;
    uCommitsOfF = uAuthorsOfF = null;
  }
  commitsOfF = new double[allF.length];
  commitDiffusion = new double[gm.allCommits.size()];
  apit = gm.authOfComInF.values().iterator();
  while (apit.hasNext()) {
    pIt = apit.next().iterator();
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
  fork = "";
  Arrays.fill(commitDiffusion, 0.0);
  while (cIt.hasNext()) {
    brIt = cIt.next().branches.iterator();
    while (brIt.hasNext()) {
      cur = brIt.next().name.split("/")[0];
      if (!cur.equals(fork)) {
        commitDiffusion[i]++;
        fork = cur;
      }
    }
    i++;
  }
  for(i = 0; i < allF.length; i++) {
    commitsOfF[i] = gm.comInF.get(allF[i]).size();
  }
  branchesOfF = new double[gm.branches.size()];
  brAlIt = gm.branches.values().iterator();
  i = 0;
  while (brAlIt.hasNext()) {
    branchesOfF[i++] = brAlIt.next().size();
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
      + "Forks : " + allF.length + " ; "
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
  allF = new String[in.readInt()];
  for (i = 0; i < allF.length; i++) {
    allF[i] = in.readUTF();
  }
  rootAIndex = Arrays.binarySearch(allF, name);
  size = in.readInt();
  if (size > 0) {
    uF = new String[size];
    uCommitsOfF = new double[uF.length];
    uAuthorsOfF = new double[uF.length];
    for (i = 0; i < uF.length; i++) {
      uF[i] = in.readUTF();
    }
    for(i = 0; i < uF.length; i++) {
      uAuthorsOfF[i] = in.readDouble();
      uCommitsOfF[i] = in.readDouble();
    }
    rootUIndex = Arrays.binarySearch(uF, name);
  } else {
    uF = null;
    uCommitsOfF = uAuthorsOfF = null;
    rootUIndex = -1;
  }
  commitsOfF = new double[in.readInt()];
  for(i = 0; i < commitsOfF.length; i++) {
    commitsOfF[i] = in.readDouble();
  }
  commitDiffusion = new double[in.readInt()];
  for(i = 0; i < commitDiffusion.length; i++) {
    commitDiffusion[i] = in.readDouble();
  }
  branchesOfF = new double[in.readInt()];
  for(i = 0; i < branchesOfF.length; i++) {
    branchesOfF[i] = in.readDouble();
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
  out.writeInt(allF.length);
  for (String s : allF) {
    out.writeUTF(s);
  }
  if (uF != null) {
    out.writeInt(uF.length);
    for (String s : uF) {
      out.writeUTF(s);
    }
    for(int i = 0; i < uF.length; i++) {
      out.writeDouble(uAuthorsOfF[i]);
      out.writeDouble(uCommitsOfF[i]);
    }
  } else {
    out.writeInt(0);
  }
  out.writeInt(commitsOfF.length);
  for (double d : commitsOfF) {
    out.writeDouble(d);
  }
  out.writeInt(commitDiffusion.length);
  for (double d : commitDiffusion) {
    out.writeDouble(d);
  }
  out.writeInt(branchesOfF.length);
  for (double d : branchesOfF) {
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
