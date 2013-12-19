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

import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.stat.Frequency;
import org.apache.commons.math3.stat.correlation.SpearmansCorrelation;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math3.stat.inference.MannWhitneyUTest;
import org.apache.commons.math3.util.FastMath;

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
    if (val == null) continue; // || val <= min XXX
    di = new DIdeogram(f.allAuthors[iAuthors[iCommits[cur]]], val, 0);
    res.addToSetB(di);
    res.getLinks().add(new DLink(main, di, di.getValue())); // keep the order in res.links
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
    res.getLinks().add(new DLink(main, di, di.getValue())); // keep the order in res.links
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


static private boolean haveSameDistribution(double[] val1, double[] val2) {
  int min = 0, i, t;
  // GTest test = new GTest();
  // WilcoxonSignedRankTest test = new WilcoxonSignedRankTest();
  MannWhitneyUTest test = new MannWhitneyUTest();
  double[] v2Freq = null;
  double[] v1Freq = null;
  Comparable<?> v1[], v2[];
  java.util.Map.Entry<Comparable<?>, Long> co;
  Iterator<java.util.Map.Entry<Comparable<?>, Long>> cit;
  DescriptiveStatistics ds = new DescriptiveStatistics();
  Frequency v1f = new Frequency(), v2f = new Frequency();
  for (double d : val1) {
    ds.addValue(d);
    if (d >= 2.0) v1f.addValue(d);
  }
  System.err.println("unique count = " + v1f.getUniqueCount()); // XXX
  v1 = new Comparable[v1f.getUniqueCount()];
  v1Freq = new double[v1.length];
  i = 0;
  cit = v1f.entrySetIterator();
  while (cit.hasNext()) {
    co = cit.next();
    v1[i] = co.getKey();
    v1Freq[i++] = co.getValue();
  }
  if (val2 == null) {
    v2 = new Comparable<?>[v1.length];
    v2Freq = new double[v1.length];
    NormalDistribution gd = new NormalDistribution(ds.getMean(), ds.getStandardDeviation());
    t = 0;
    do {
      t++;
      val2 = gd.sample(val1.length * 2);
      if (v2f.getUniqueCount() > 0)
        System.err.println("Sampled " + v2f.getUniqueCount() + " / " + v1.length);
      for (double d : val2)
        // if (d >= 2.0)
        v2f.addValue(FastMath.round(d));
    }
    while (v2f.getUniqueCount() < v1.length);
    i = 0;
    cit = v2f.entrySetIterator();
    while (i < v1.length) {
      co = cit.next();
      v2[i] = co.getKey();
      v2Freq[i++] = t > 1 ? FastMath.max(co.getValue() / t, 1) : co.getValue();
    }
  } else {
    for (double d : val2)
      // if (d >= 2.0)
      v2f.addValue(d);
    v2 = new Comparable[v2f.getUniqueCount()];
    v2Freq = new double[v2.length];
    i = 0;
    cit = v2f.entrySetIterator();
    while (cit.hasNext()) {
      co = cit.next();
      v2[i] = co.getKey();
      v2Freq[i++] = co.getValue();
    }
    if (v1.length != v2.length) {
      min = FastMath.min(v1.length, v2.length);
      // shuffle the larger array
      if (v1.length < v2.length) {
        GitWorks.shuffle(v2, v2.length);
        i = 0;
        for (Comparable<?> c : v2)
          v2Freq[i++] = v2f.getCount(c);
      } else {
        GitWorks.shuffle(v1, v1.length);
        i = 0;
        for (Comparable<?> c : v1)
          v1Freq[i++] = v1f.getCount(c);
      }
    }
  }
  if (min == 0) {
    min = v1.length;
  }

  Number[][][] data = new Number[2][][];
  data[0] = new Number[2][v1.length];
  data[1] = new Number[2][v2.length];
  for (i = 0; i < v1.length; i++) {
    data[0][0][i] = ((Double)v1[i]).intValue();
    data[0][1][i] = v1Freq[i];
  }
  for (i = 0; i < v2.length; i++) {
    data[1][0][i] = ((Number)v2[i]).intValue();
    data[1][1][i] = v2Freq[i];
  }
  jfreechart.XYSeriesChart chart = new jfreechart.XYSeriesChart(data, new String[] {
      "Degree distributions", "data points", "freq"});
  chart.plotWindow();
  if (min < 5)
    System.err.println("Results : WARNING : Test not meaningful with only " + min
        + " distinct values.");
  // return !test.gTest(Arrays.copyOfRange(v2Freq, 0, min), Arrays.copyOfRange(v1Freq, 0, min),
  // 0.05);
  double p;
  // p = test.wilcoxonSignedRankTest(Arrays.copyOfRange(v2Freq, 0, min),
  // Arrays.copyOfRange(v1Freq, 0, min), false);
  p = test.mannWhitneyUTest(Arrays.copyOfRange(v2Freq, 0, min), Arrays.copyOfRange(v1Freq, 0, min));
  System.err.println("Results : INFO : p-value = " + p);
  System.err.flush();
  return p < 0.05;
}


static private ArrayList<Commit> selectMeaningful(Dag d) {
  ArrayList<Commit> res = new ArrayList<Commit>(d.nodes.size() + d.leaves.size() + d.roots.size());
  res.addAll(d.nodes);
  for (Commit c : d.leaves) {
    if (c.inDegree > 1) GitWorks.addUnique(res, c);
  }
  for (Commit c : d.roots) {
    if (c.outDegree > 1) GitWorks.addUnique(res, c);
  }
  return res;
}


static void computeDistros(ArrayList<MetaGraph> mgs, FeatureList fl) {
  // compute correlation among couples within the same groups.
  int i, j, k;
  ArrayList<Commit> coms;
  double[] deg1, deg2;
  MetaGraph mg1, mg2;
  Dag d;
  ArrayList<MetaGraph> normal = new ArrayList<MetaGraph>();
  ArrayList<MetaGraph> non_normal = new ArrayList<MetaGraph>();
  // NormalDistribution gd = new NormalDistribution(6, 6);
  // deg1 = gd.sample(1000);
  // for (i = 0; i < deg1.length; i++)
  // deg1[i] = FastMath.round(deg1[i]);

  for (MetaGraph mg : mgs) {
    d = mg.getDensierDag();
    coms = selectMeaningful(d);
    deg1 = new double[coms.size()];
    i = 0;
    for (Commit c : coms)
      deg1[i++] = (double)(c.inDegree + c.outDegree);
    if (haveSameDistribution(deg1, null))
      normal.add(mg);
    else
      non_normal.add(mg);
  }
  // GitWorks.printAny(normal, "NORMALS ABOVE\n", System.out);
  // GitWorks.printAny(non_normal, "NON NORMALS ABOVE\n", System.out);

  for (i = 0; i < non_normal.size() - 1; i++) {
    mg1 = non_normal.get(i);
    d = mg1.getDensierDag();
    System.err.println("Got the dag for " + mg1);
    System.err.flush();
    coms = selectMeaningful(d);
    System.err.println("Got its meaningful commits as well.");
    System.err.flush();
    deg1 = new double[coms.size()];
    k = 0;
    for (Commit c : coms)
      deg1[k++] = (double)(c.inDegree + c.outDegree);
    for (j = i + 1; j < non_normal.size(); j++) {
      mg2 = non_normal.get(j);
      d = mg2.getDensierDag();
      System.err.println("Got the dag for " + mg2);
      System.err.flush();
      coms = selectMeaningful(d);
      System.err.println("Got its meaningful commits as well.");
      System.err.flush();
      deg2 = new double[coms.size()];
      k = 0;
      for (Commit c : coms)
        deg2[k++] = (double)(c.inDegree + c.outDegree);
      if (haveSameDistribution(deg1, deg2))
        System.err.println("YES!!");
      else
        System.err.println("NO!!");
    }
  }
}

static final String[] resultNames = {"totCommits", "totNodes", "numLeaves", "maxInDegree",
    "maxOutDegree", "medInDegree", "medOutDegree", "maxDegree", "medDegree", "maxDiff", "medDiff"};

static final int totCommits = 0;
static final int totNodes = 1;
static final int numLeaves = 2;
static final int maxInDegree = 3;
static final int maxOutDegree = 4;
static final int medInDegree = 5;
static final int medOutDegree = 6;
static final int maxDegree = 7;
static final int medDegree = 8;
static final int maxDiff = 9;
static final int medDiff = 10;
static final int ages = 5;



static void computeStats(ArrayList<MetaGraph> mgs, ArrayList<Features> fl) {
  MetaGraph mgNext;
  Features f;
  Dag d;
  XYSeriesChart chart;
  ArrayList<Commit> coms, allComs;
  // DescriptiveStatistics ds = new DescriptiveStatistics();
  ArrayList<Number[][]> counters = new ArrayList<Number[][]>(mgs.size());
  int i, j;
  long curTstamp, prevTstamp;
  double[] comDiff;
  int[] inDegs, outDegs, degs, comIndx, comRank, comAuthor;
  Number[][] counter;
  Iterator<Features> fIt = fl.iterator();
  for (MetaGraph mg : mgs) {
    counter = new Number[Results.resultNames.length][Results.ages];
    f = fIt.next();
    f.computeExtra();
    allComs = mg.allCommits;
    curTstamp = prevTstamp = mg.since - 1;
    for (i = 0; i < Results.ages; i++) {
      // ds.clear();
      prevTstamp = curTstamp + 1; // XXX to get subsequent disjoint
                                  // (VS incrementally inclusive) subgraphs
      curTstamp = i * (mg.until - mg.since) / Results.ages + mg.since;
      mgNext = mg.getDensierDag().buildSubGraph(new java.util.Date(prevTstamp),
          new java.util.Date(curTstamp));
      d = mgNext.getOldestDag(); // XXX
      d.exportToGexf(f.name + "." + (i + 1) + "-" + Results.ages);
      coms = selectMeaningful(d);
      if (coms.isEmpty()) {
        for (Number[] n : counter)
          n[i] = 0;
        System.err.println("Skipping uninteresting dag (" + f.name + "." + (i + 1) + "-"
            + Results.ages + ").");
        continue;
      }
      counter[Results.totCommits][i] = ((double)d.getNumCommits()) / mg.allCommits.size();
      counter[Results.totNodes][i] = ((double)coms.size())
          / (d.leaves.size() + d.nodes.size() + d.roots.size());
      counter[Results.numLeaves][i] = ((double)d.leaves.size())
          / (d.leaves.size() + d.nodes.size() + d.roots.size());
      inDegs = new int[coms.size()];
      outDegs = new int[coms.size()];
      degs = new int[coms.size()];
      comIndx = new int[coms.size()];
      comDiff = new double[coms.size()];
      comRank = new int[coms.size()];
      comAuthor = new int[coms.size()];
      j = 0;
      for (Commit c : coms) {
        inDegs[j] = c.inDegree;
        outDegs[j] = c.outDegree;
        degs[j] = c.outDegree + c.inDegree;
        comIndx[j] = Collections.binarySearch(allComs, c);
        comDiff[j] = ((double)f.acCommitDiffusion[comIndx[j]]) / f.commitDiffusion[comIndx[j]];
        comRank[j] = f.commitRank[comIndx[j]]; // TODO
        comAuthor[j] = f.commitAuthor[comIndx[j]]; // TODO
        j++;
      }
      Arrays.sort(inDegs);
      Arrays.sort(outDegs);
      Arrays.sort(degs);
      Arrays.sort(comDiff);
      counter[Results.maxInDegree][i] = inDegs[inDegs.length - 1];
      counter[Results.medInDegree][i] = inDegs[inDegs.length / 2];
      counter[Results.maxOutDegree][i] = outDegs[outDegs.length - 1];
      counter[Results.medOutDegree][i] = outDegs[outDegs.length / 2];
      counter[Results.maxDegree][i] = degs[degs.length - 1];
      counter[Results.medDegree][i] = degs[degs.length / 2];
      counter[Results.maxDiff][i] = comDiff[comDiff.length - 1];
      counter[Results.medDiff][i] = comDiff[comDiff.length / 2];
    }
    counters.add(counter);
    f.deleteExtra();
    chart = new XYSeriesChart(new String[] {f.name, "Ages", "Values"});
    for (i = 0; i < Results.resultNames.length; i++) {
      chart.addDataset(resultNames[i], null, counter[i]);
    }
    chart.plotWindow();
  }

}

}
