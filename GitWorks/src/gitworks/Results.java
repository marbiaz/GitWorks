/**
 * 
 */
package gitworks;


import gitworks.Features.CommitRank;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;

//import org.apache.commons.math3.exception.MathIllegalArgumentException;
//import org.apache.commons.math3.stat.correlation.SpearmansCorrelation;
//import org.apache.commons.math3.stat.inference.MannWhitneyUTest;
//import org.apache.commons.math3.stat.inference.WilcoxonSignedRankTest;
import circos.CircosData;
import circos.DIdeogram;
import circos.DLink;
import circos.WriteCommitsToMain;


/**
 *
 */
public class Results {

private static CircosData getIAuthorsInMainline(Features f, int min) {
  CircosData res = new CircosData();
  // get the acAuthorImpactPerFork and plot it as histo OR
  // get the commitRank of their commits and plot histo 1bin-per-rank OR from aLinkMap...
  int[] iCommits = new int[f.acCommitDiffusion.length];
  int[] iAuthors = new int[f.acCommitDiffusion.length];
  LinkedHashMap<Integer, Integer> aFreq = new LinkedHashMap<Integer, Integer>();
  Long[] tstamps = new Long[f.acCommitDiffusion.length];
  int[] sorting;
  int cur, i, c = 0;
  Integer val;
  DIdeogram di, main;
  System.err.println("INFO: " + f.toString());
  for (i = 0; i < f.vipCommitForF[f.rootIndex][CommitRank.U_VIP.getValue() + 1].size(); i++) {
    iCommits[c] = f.vipCommitForF[f.rootIndex][CommitRank.U_VIP.getValue() + 1].get(i);
    tstamps[c] = f.commitTimeLine[iCommits[c]];
    iAuthors[c] = f.commitAuthor[iCommits[c]];
    if (aFreq.containsKey(iAuthors[c]))
      aFreq.put(iAuthors[c], aFreq.get(iAuthors[c]).intValue() + 1);
    else
      aFreq.put(iAuthors[c], 1);
    c++;
  }
  for (i = 0; i < f.vipCommitForF[f.rootIndex][CommitRank.VIP.getValue() + 1].size(); i++) {
    iCommits[c] = f.vipCommitForF[f.rootIndex][CommitRank.VIP.getValue() + 1].get(i);
    tstamps[c] = f.commitTimeLine[iCommits[c]];
    iAuthors[c] = f.commitAuthor[iCommits[c]];
    if (aFreq.containsKey(iAuthors[c]))
      aFreq.put(iAuthors[c], aFreq.get(iAuthors[c]).intValue() + 1);
    else
      aFreq.put(iAuthors[c], 1);
    c++;
  }
  for (i = 0; i < f.vipCommitForF[f.rootIndex][CommitRank.PERVASIVE.getValue() + 1].size(); i++) {
    iCommits[c] = f.vipCommitForF[f.rootIndex][CommitRank.PERVASIVE.getValue() + 1].get(i);
    tstamps[c] = f.commitTimeLine[iCommits[c]];
    iAuthors[c] = f.commitAuthor[iCommits[c]];
    if (aFreq.containsKey(iAuthors[c]))
      aFreq.put(iAuthors[c], aFreq.get(iAuthors[c]).intValue() + 1);
    else
      aFreq.put(iAuthors[c], 1);
    c++;
  }
  sorting = IndexedSortable.sortedPermutation(Arrays.copyOf(tstamps, c), false);
  if (aFreq.size() <= min) {
    System.err.println("\t" + f.name + " has no iAuthor in mainline: DISCARDED.");
    // if (res.getSize() > 0) System.err.println("ERROR: " + f.name + " has inconsistent values!");
    return null;
  } else {
    System.err.println("\t" + f.name + " has " + aFreq.size() + " iAuthors in mainline: OK.");
    main = new DIdeogram(f.allForks[f.rootIndex], aFreq.size(), 0);
    res.addToSetA(main);
  }
  for (i = 0; i < sorting.length; i++) {
    cur = sorting[i];
    val = aFreq.remove(iAuthors[iCommits[cur]]);// =
                                                // f.authorsImpactPerF[iAuthors[iCommits[cur]]][f.rootIndex][1]
                                                // XXX
    if (val == null) continue; // || val <= min XXX
    di = new DIdeogram(f.allAuthors[iAuthors[iCommits[cur]]], val, 0);
    res.addToSetB(di);
    res.getLinks().add(new DLink(main, di, di.getValue())); // adding like this keeps the order in
                                                            // res.links
    // if (uCommits[cur] > 0) di.setScatter(uCommits[cur]); //res.addScatter(di, uCommits[cur]);
  }

  return res;
}


private static CircosData getICommitsToMainline(Features f, int min) {
  CircosData res = new CircosData();
  int[] iCommits = new int[f.allForks.length];
  int[] uCommits = new int[f.allForks.length];
  Long[] tstamps = new Long[f.allForks.length];
  @SuppressWarnings("unchecked")
  ArrayList<Integer>[] iAuthors = new ArrayList[f.allForks.length];
  @SuppressWarnings("unchecked")
  ArrayList<Integer>[] uAuthors = new ArrayList[f.allForks.length];
  DIdeogram di, main = null;
  int i, cur, sorting[];
  System.err.println("INFO: " + f.toString());
  for (i = 0; i < f.allForks.length; i++) {
    tstamps[i] = f.since[i];
    iCommits[i] = f.vipCommitForF[i][CommitRank.U_VIP.getValue() + 1].size()
        + f.vipCommitForF[i][CommitRank.VIP.getValue() + 1].size()
        + f.vipCommitForF[i][CommitRank.PERVASIVE.getValue() + 1].size();
    uCommits[i] = (i == f.rootIndex) ? f.vipCommitForF[i][CommitRank.ROOT.getValue() + 1].size()
        : f.vipCommitForF[i][CommitRank.UNIQUE.getValue() + 1].size();
    iAuthors[i] = new ArrayList<Integer>();
    uAuthors[i] = new ArrayList<Integer>();
    if (iCommits[i] <= min) continue;
    for (Integer inte : f.vipCommitForF[i][CommitRank.U_VIP.getValue() + 1]) {
      GitWorks.addUnique(iAuthors[i], f.commitAuthor[inte]);
    }
    for (Integer inte : f.vipCommitForF[i][CommitRank.VIP.getValue() + 1]) {
      GitWorks.addUnique(iAuthors[i], f.commitAuthor[inte]);
    }
    for (Integer inte : f.vipCommitForF[i][CommitRank.PERVASIVE.getValue() + 1]) {
      GitWorks.addUnique(iAuthors[i], f.commitAuthor[inte]);
    }
    if (i == f.rootIndex) {
      for (Integer inte : f.vipCommitForF[i][CommitRank.ROOT.getValue() + 1]) {
        GitWorks.addUnique(uAuthors[i], f.commitAuthor[inte]);
      }
    } else {
      for (Integer inte : f.vipCommitForF[i][CommitRank.UNIQUE.getValue() + 1]) {
        GitWorks.addUnique(uAuthors[i], f.commitAuthor[inte]);
      }
    }
  }
  sorting = IndexedSortable.sortedPermutation(tstamps, false);
  main = new DIdeogram(f.allForks[f.rootIndex], iCommits[f.rootIndex], iAuthors[f.rootIndex].size());
  if (iCommits[f.rootIndex] > 0) {
    if (uCommits[f.rootIndex] > 0) main.setScatter(uCommits[f.rootIndex]); // res.addScatter(main,
                                                                           // uCommits[f.rootIndex]);
    res.addToSetA(main);
    System.err.println("\t" + f.name + " has " + iCommits[f.rootIndex] + " iCommits: OK.");
  } else {
    System.err.println("\t" + f.name + " has no iCommit: DISCARDED.");
    if (res.getSize() > 0) System.err.println("ERROR: " + f.name + " has inconsistent values!");
    res = null;
    return res;
  }
  for (i = 0; i < sorting.length; i++) {
    cur = sorting[i];
    if (iCommits[cur] <= min || cur == f.rootIndex) continue;
    di = new DIdeogram(f.allForks[cur], iCommits[cur], iAuthors[cur].size());
    res.addToSetB(di);
    res.getLinks().add(new DLink(main, di, di.getValue())); // adding like this keeps the order in
                                                            // res.links
    if (uCommits[cur] > 0) di.setScatter(uCommits[cur]); // res.addScatter(di, uCommits[cur]);
  }
  return res;
}


// //returns all forks that have more than min commits of the given rank
// @SuppressWarnings("unused")
// private static CircosData getRankedCommitToMainline(Features f, CommitRank rank, int min) {
// CircosData res = new CircosData();
// int i, l, d, b;
// ArrayList<Integer> a = new ArrayList<Integer>();
// DIdeogram di;
// Iterator<DIdeogram> dIt;
// for (i = 0; i < f.allForks.length; i++) {
// if (f.commitRankRatio[rank.getValue()] * f.acCommitDiffusion.length <= min) continue;
// l = 0;
// a.clear();
// for (int cc : f.getCommits(i)) {
// if (f.commitRank[cc] == rank.getValue()) {
// l++;
// GitWorks.addUnique(a, f.commitAuthor[cc]);
// }
// }
// b = a.size();
// if (i == f.getRootIndex()) {
// res.addToSetA(new DIdeogram(f.allForks[i], l, b));
// } else {
// res.addToSetB(new DIdeogram(f.allForks[i], l, b));
// }
// }
//
// dIt = res.getAllSets().iterator(); // get links from rank-ed commits
// while (dIt.hasNext()) {
// di = dIt.next();
// d = Arrays.binarySearch(f.allForks, di.getName());
// for (int j = 0; j < f.cLinkMap[rank.getValue() + 1][d].length; j++) {
// a = f.cLinkMap[rank.getValue() + 1][d][j];
// if (a == null || a.size() == 0) continue;
// res.addLink(di.getName(), f.allForks[j], a.size());
// }
// }
// return res;
// }

public static void createCircosFiles(FeatureList fl) throws InterruptedException, IOException {
  Features fe;
  CircosData data;
  WriteCommitsToMain wctm;
  // String id;
  for (int i = 0; i < fl.size(); i++) {
    fe = fl.get(i);
    if (fe.allForks.length < 3) continue;
    // for (int j = 3; j < CommitRank.values().length; j++) {
    // id = fe.name + "." + CommitRank.values()[j].name();
    // data = getCommitToMainlinePerRank(fe, CommitRank.values()[j], 0);
    // wctm = new WriteCommitsToMain(data, fe.name);
    // wctm.createCircosFiles("colors." + id + ".conf", "karyo." + id + ".txt", "links." + id +
    // ".txt");
    // Runtime.getRuntime().exec(GitWorks.pwd + "/makeCircosPlots.sh").waitFor();
    // }
    data = getIAuthorsInMainline(fe, 0); // getICommitsToMainline(fe, 0);
    if (data == null) continue;
    System.out.println(data.toString());
    wctm = new WriteCommitsToMain(data, fe.name);
    wctm.createCircosFiles(GitWorks.trees_out_dir + fe.name + ".colors.conf",
        GitWorks.trees_out_dir + fe.name + ".karyo.txt", GitWorks.trees_out_dir + fe.name
            + ".links.txt", GitWorks.trees_out_dir + fe.name + ".scatters.txt");
    Runtime.getRuntime().exec(GitWorks.pwd + "/makeCircosPlots.sh").waitFor();
  }
}


}
