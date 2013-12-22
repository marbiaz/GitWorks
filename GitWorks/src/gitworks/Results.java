/**
 * 
 */
package gitworks;


import edu.uci.ics.jung.graph.DirectedSparseGraph;
import gitworks.Features.CommitRank;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;

import jfreechart.XYSeriesChart;

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


static void printLatexTable(ArrayList<ArrayList<Motif>> motifs) {
  Iterator<ArrayList<Motif>> moIt = motifs.iterator();
  ArrayList<Motif> moa;
  Double z;
  String score;
  PrintWriter tableOut = null;
  try {
    Runtime.getRuntime().exec(
            "cp -f /home/mbiazzin/ecologyse/GIT/papers/github_mining/scores_template.tex "
                + GitWorks.pwd + "/table.tex").waitFor();
    tableOut = new PrintWriter(new FileWriter(GitWorks.pwd + "/table.tex", true));
    while (moIt.hasNext()) {
      moa = moIt.next();
      tableOut.write("\\verb'" + moa.get(0).name.split(GitWorks.safe_sep)[2] + "'");
      for (Motif mo : moa) {
        if (mo.name.split(GitWorks.safe_sep)[0].equals("conv2tris")) continue;
        z = mo.cStats.get(5);
        if (z.isNaN())
          score = "$0.0$";
        else if (Double.NEGATIVE_INFINITY == z)
          score = "\\textbf{$-\\infty$}";
        else if (Double.POSITIVE_INFINITY == z)
          score = "\\textbf{$+\\infty$}";
        else
          score = (Math.abs(z) > 2 ? "\\textbf{" : "") + "$"
              + z.toString().substring(0, z.toString().indexOf(".") + 3) + "$"
              + (Math.abs(z) > 2 ? "}" : "");
        tableOut.write(" & $" + mo.occurrences.size() + "$ & "// $" + mo.cStats.get(1) + "$ "& "
            + score);
      }
      tableOut.write(" \\\\\n");
    }
    tableOut.write("" + "\\hline" + "\n" + "\\end{tabular}" + "\n" + "\\caption{Motifs scores}"
        + "\n" + "\\end{table}" + "\n");
    tableOut.flush();
    Runtime.getRuntime().exec(
            "cp -f " + GitWorks.pwd + "/table.tex "
                + "/home/mbiazzin/ecologyse/GIT/papers/github_mining/").waitFor();
  }
  catch (Exception e) {
    e.printStackTrace();
  }
  finally {
    if (tableOut != null) tableOut.close();
  }
}


static void printoutMotifAggStats(String[] repos, String[] motifs, double[][][] stats) {
  PrintWriter pOut = null;
  int i = 0, j;
  try {
    for (String n : repos) {
      try {
        pOut = new PrintWriter(new FileWriter(GitWorks.pwd + "/gdata/" + n + ".allmotifs.agg.gdata",
            false));
        pOut.print("motif_name");
        for (String s : colHeader)
          pOut.print("\t" + s);
        pOut.println();
        j = 0;
        for (String m : motifs) {
          pOut.print(m);
          for (double d : stats[i][j])
            pOut.print("\t" + d);
          pOut.println();
          j++;
        }
        pOut.flush();
      }
      catch (IOException ioe) {
        ioe.printStackTrace();
      }
      finally {
        if (pOut != null) pOut.close();
        i++;
      }
    }
    j = 0;
    for (String m : motifs) {
      try {
        pOut = new PrintWriter(new FileWriter(GitWorks.pwd + "/gdata/" + m + ".allrepos.agg.gdata",
            false));
        pOut.print("repo_name");
        for (String s : colHeader)
          pOut.print("\t" + s);
        pOut.println();
        i = 0;
        for (String n : repos) {
          pOut.print(n);
          for (double d : stats[i][j])
            pOut.print("\t" + d);
          pOut.println();
          i++;
        }
        pOut.flush();
      }
      catch (IOException ioe) {
        ioe.printStackTrace();
      }
      finally {
        if (pOut != null) pOut.close();
        j++;
      }
    }
  }
  finally {
    if (pOut.checkError()) System.err.println("Results : ERROR in printing motifs aggregates");
  }
}


// silly way to simplify the computation of parallels stats
static private Motif twins2motif(String name, HashMap<MetaEdge, MetaEdge[]> twins) {
  Motif parallels = new Motif("twins" + GitWorks.safe_sep + name.split(GitWorks.safe_sep)[1], 2, 2);
  for (MetaEdge[] t : twins.values())
    parallels.addOccurrence(new MotifOccurrence(t));
  return parallels;
}


// motifs, z-scores and twins
static void metagraphStats(ArrayList<MetaGraph> mgs, ArrayList<Features> fl) {
  DirectedSparseGraph<Commit, MetaEdge> g;
  Iterator<Features> fIt = fl.iterator();
  Features f;
  HashMap<String, ArrayList<MetaEdge>> twins;
  HashMap<MetaEdge, MetaEdge[]> parallels;
  ArrayList<Motif> motifs;
  ArrayList<HashMap<MetaEdge, MetaEdge[]>> pTwins = new ArrayList<HashMap<MetaEdge, MetaEdge[]>>(
      fl.size());
  ArrayList<ArrayList<Motif>> rMotifs = new ArrayList<ArrayList<Motif>>(fl.size());
  int nEdges, k, i = 0;
  Motif par;
  String[] repoNames = new String[mgs.size()];
  String[] motifNames = null;
  double[][][] stats = new double[mgs.size()][][];
  if (colHeader == null) {
    colHeader = new String[singleValuesNames.length + metricsNames.length * aggregatesNames.length];
    for (k = 0; k < singleValuesNames.length; k++)
      colHeader[k] = singleValuesNames[k];
    for (String me : metricsNames)
      for (String st : aggregatesNames)
        colHeader[k++] = me + st;
  }
  try {
    for (MetaGraph mg : mgs) {
      f = fIt.next();
      repoNames[i] = f.name.split(GitWorks.safe_sep)[1];
      nEdges = 0;
      for (Dag d : mg.dags) {
        nEdges += d.getNumMetaEdges();
      }
      twins = new HashMap<String, ArrayList<MetaEdge>>(nEdges);
      g = makeSimpleGraph(mg, twins);
      parallels = new HashMap<MetaEdge, MetaEdge[]>(twins.keySet().size());
      for (ArrayList<MetaEdge> m : twins.values())
        parallels.put(m.get(0), m.toArray(new MetaEdge[0]));
      par = twins2motif(f.name, parallels);
      try {
        // exportGraph(f.name, g);
        // Runtime.getRuntime().exec(GitWorks.pwd + "/gitMotifs.sh " + f.name + " &>>gitMotifs.log")
        // .waitFor();
        motifs = importMotifs(f.name, mg, twins);
        motifs.add(par);
        rMotifs.add(motifs);
        pTwins.add(parallels); // XXX ???
        stats[i] = getMotifStats(mg, motifs, true);
        if (motifNames == null) {
          int j = 0;
          motifNames = new String[motifs.size()];
          for (Motif m : motifs)
            motifNames[j++] = m.name.split(GitWorks.safe_sep)[0];
        }
      }
      catch (Exception e) {
        e.printStackTrace();
        stats[i] = new double[0][0];
      }
      finally {
        i++;
      }
      // BetweennessCentrality<Commit, MetaEdge> bcRanker = new BetweennessCentrality<Commit,
      // MetaEdge>(
      // g, false, true);
      // bcRanker.evaluate();
      // bcRanker.printRankings(true, true);
    }
    printoutMotifAggStats(repoNames, motifNames, stats);
    // printLatexTable(rMotifs);
    // motifsFeatsCorrelation(mgs, fl, rMotifs);
    footPrint(mgs, fl);
  }
  catch (Exception e1) {
    e1.printStackTrace();
  }
}

// TODO
// motifs per layer (are they scattered? are they where width is larger?)
// commit density in motifs VS average commit per edge-set + #authors in motifs
// motif scores and #author #watchers
//
static String[] metricsNames = new String[] {
    "mo_min_layer", "mo_max_layer", "mo_tot_edges", "mo_num_parallels", "mo_weight", "mo_num_authors",
    "mg_weights", "mg_num_authors", "mg_layer_size"};

static String[] aggregatesNames = new String[] {"_min", "_25p", "_med", "_75p", "_max", "_avg",
    "_stdev"};

static String[] singleValuesNames = new String[] {"mo_num_occur", "\tmo_num_nodes",
    "\tmo_num_edges", "\tmo_z-score",
    "\tmg_diameter"};

static String[] colHeader = null;


static double[][] getMotifStats(MetaGraph mg, ArrayList<Motif> motifs, boolean printout) {
  // 5 + (6 + 3) metrics to aggregate x 7 aggregates XXX
  double[][] res = new double[motifs.size()][68];
  for (double[] row : res)
    Arrays.fill(row, 0.0);
  Iterator<Motif> moIt = motifs.iterator();
  DescriptiveStatistics ds[] = new DescriptiveStatistics[9]; // XXX
  for (int i = 0; i < 6; i++) { // XXX
    ds[i] = new DescriptiveStatistics();
  }
  Motif mo;
  int j = -1, nodes, edges, occur;
  double zScore;
  PrintWriter pout = null;
  try {
    ds[6] = mg.getInternalCommitStats(); // XXX
    ds[7] = mg.getMetaEdgeAuthorStats();
    ds[8] = mg.getLayerStats();
    while (moIt.hasNext()) {
      mo = moIt.next();
      try {
        for (int i = 0; i < 6; i++) { // XXX
          ds[i].clear();
        }
        j++;
        if (printout) {
          pout = new PrintWriter(new FileWriter(GitWorks.pwd + "/gdata/" + mo.name + ".gdata",
              false));
          for (String s : singleValuesNames)
            pout.print(s);
          for (String s : metricsNames)
            pout.print("\t" + s);
          pout.println();
        }
        nodes = mo.numNodes;
        edges = mo.numEdges;
        zScore = mo.zScore;
        occur = mo.occurrences.size();
        for (MotifOccurrence ma : mo.occurrences) {
          if (printout)
            pout.println(occur + "\t" + ma.mNodes.length + "\t" + ma.mEdges.size() + "\t" + zScore
                + "\t" + mg.getDiameter()
                + "\t" + ma.minLayer + "\t" + ma.maxLayer + "\t" + ma.totEdges + "\t" + ma.numParallels
                + "\t" + ma.weight + "\t" + ma.numAuthors + "\t" + ds[6].getMean() + "\t"
                + ds[7].getMean() + "\t" + ds[8].getMean());
          ds[0].addValue(ma.minLayer); // XXX
          ds[1].addValue(ma.maxLayer);
          ds[2].addValue(ma.totEdges);
          ds[3].addValue(ma.numParallels);
          ds[4].addValue(ma.weight);
          ds[5].addValue(ma.numAuthors);
        }
        res[j][0] = occur;
        res[j][1] = nodes;
        res[j][2] = edges;
        res[j][3] = zScore;
        res[j][4] = mg.getDiameter();
        for (int i = 0; i < ds.length; i++) {
          res[j][5 + i * 7 + 0] = ds[i].getMin();
          res[j][5 + i * 7 + 1] = ds[i].getPercentile(25);
          res[j][5 + i * 7 + 2] = ds[i].getPercentile(50);
          res[j][5 + i * 7 + 3] = ds[i].getPercentile(75);
          res[j][5 + i * 7 + 4] = ds[i].getMax();
          res[j][5 + i * 7 + 5] = ds[i].getMean();
          res[j][5 + i * 7 + 6] = ds[i].getStandardDeviation();
        }
        if (printout) {
          pout.flush();
          pout.close();
        }
      }
      catch (IOException ioe) {
        ioe.printStackTrace();
      }
    }
  }
  finally {
    if (pout != null) pout.close();
  }
  return res;
}


/******************* motifs and feats ******************/

// return a graph without parallel edges and the lists of them in 'twins'.
static DirectedSparseGraph<Commit, MetaEdge> makeSimpleGraph(MetaGraph mg,
    HashMap<String, ArrayList<MetaEdge>> twins) {
  DirectedSparseGraph<Commit, MetaEdge> g;
  MetaEdge me, temp;
  ArrayList<MetaEdge> parallel;
  g = new DirectedSparseGraph<Commit, MetaEdge>();

  for (Dag d : mg.dags) {
    // System.err.println("d has " + d.toString());
    Iterator<MetaEdge> mIt = d.getMetaEdges();
    while (mIt.hasNext()) {
      me = mIt.next();
      if (g.addEdge(me, me.first, me.last)) {
        parallel = new ArrayList<MetaEdge>();
        parallel.add(me);
        twins.put(me.first.id.getName() + me.last.id.getName(), parallel);
      } else { // always put in g the metaedge with the smallest id
        temp = g.findEdge(me.first, me.last);
        if (temp.compareTo(me) > 0) { // me takes the place of temp in g
          g.removeEdge(temp);
          if (!g.addEdge(me, me.first, me.last)) {
            System.err.println("Results : Unexpected ERROR in makeSimpleGraph.");
          }
        }
        parallel = twins.get(me.first.id.getName() + me.last.id.getName());
        parallel.add(me);
      }
    }
  }
  for (String k : twins.keySet().toArray(new String[0])) {
    parallel = twins.get(k);
    if (parallel.size() > 1)
      Collections.sort(parallel);
    else
      twins.remove(k);
  }
  return g;
}


static void motifsFeatsCorrelation(ArrayList<MetaGraph> mgs, ArrayList<Features> fl,
    ArrayList<ArrayList<Motif>> rMotifs) {
  double[] numMotifs = new double[mgs.size()];
  int motifRank[], i = 0, k;
  String[] motifNames = new String[rMotifs.get(0).size()];
  for (Motif m : rMotifs.get(0))
    motifNames[i++] = m.name;
  String[] featNames = {"# authors", "# commits", "# forks"};
  double[][] featVals = new double[featNames.length][mgs.size()];
  int[][] featRanks = new int[featNames.length][];
  SpearmansCorrelation sc;
  Features f;
  Iterator<Features> fIt = fl.iterator();
  i = 0;
  while (fIt.hasNext()) {
    f = fIt.next();
    featVals[0][i] = f.authorsOfF[f.rootIndex];
    featVals[1][i] = f.commitsOfF[f.rootIndex];
    featVals[2][i] = f.nForks;
    i++;
  }
  featRanks[0] = IndexedSortable.sortedPermutation(featVals[0], false);
  featRanks[1] = IndexedSortable.sortedPermutation(featVals[1], false);
  featRanks[2] = IndexedSortable.sortedPermutation(featVals[2], false);
  k = 0;
  for (String mn : motifNames) {
    i = 0;
    XYSeriesChart chart = new XYSeriesChart(new String[] {mn, " # motif", "# measure"});
    for (ArrayList<Motif> hm : rMotifs) {
      // System.out.println(f.name);
      numMotifs[i++] = hm.get(k).occurrences.size();
    }
    motifRank = IndexedSortable.sortedPermutation(numMotifs, false);
    for (int j = 0; j < featNames.length; j++) {
      sc = new SpearmansCorrelation();
      System.out.print(mn + " & " + featNames[j] + " : " + sc.correlation(numMotifs, featVals[j]));
      chart.addDataset(featNames[j], motifRank, featRanks[j]);
    }
    k++;
    chart.plotWindow();
  }
}


/********************* metagraphs' footprints *****************/
static void footPrint(ArrayList<MetaGraph> mgs, ArrayList<Features> fl) {
  Features f;
  Iterator<Features> fIt = fl.iterator();
  XYSeriesChart chart = new XYSeriesChart(new String[] {"footprints", "", ""});
  double max = 0.0;
  int i, c = 0;
  int[] lSizes;
  for (MetaGraph mg : mgs) {
    lSizes = mg.getLayerSizes();
    if (lSizes != null) for (int s : lSizes)
      max = Math.max(max, s);
  }
  for (MetaGraph mg : mgs) {
    c++;
    f = fIt.next();
    lSizes = mg.getLayerSizes();
    if (lSizes == null) continue;
    double[] x = new double[lSizes.length * 2];
    double[] vals = new double[lSizes.length * 2];
    int k = 1;
    for (i = 0; i < x.length; i++) {
      x[i] = k;
      if (i % 2 == 0) k++;
    }
    PrintWriter pOut = null;
    try {
      pOut = new PrintWriter(new FileWriter(GitWorks.pwd + "/gdata/"
          + f.name.split(GitWorks.safe_sep)[1] + ".footprint.gdata"));
      i = -1;
      for (double size : lSizes) {
        vals[++i] = (max / 2) - (size / 2);
        vals[++i] = (max / 2) + (size / 2);
        pOut.println("" + ((max / 2) - (size / 2)) + "\t" + ((max / 2) + (size / 2)));
      }
      chart.addDataset(f.name + "_" + c, x, vals);
      pOut.flush();
    }
    catch (IOException ioe) {
      ioe.printStackTrace();
    }
    finally {
      if (pOut != null) pOut.close();
    }
  }
  // chart.plotWindow();
}


private static ArrayList<Motif> importMotifs(String name, MetaGraph mg,
    HashMap<String, ArrayList<MetaEdge>> twins) throws IOException {
  ArrayList<Motif> res = new ArrayList<Motif>();
  BufferedReader in = new BufferedReader(new FileReader(GitWorks.pwd + "/motifs/" + name
      + ".motifs"));
  String line, tokens[];
  Motif motif = null;
  boolean computeScore = false;
  ArrayList<MetaEdge> edges = new ArrayList<MetaEdge>();
  while ((line = in.readLine()) != null) {
    tokens = line.split(" ");
    if (tokens.length > 1) {
      if (tokens[0].equals("[")) { // read control stats // FIXME if repo name starts with [ ???
        for (int i = 1; i < tokens.length - 1; i++)
          motif.cStats.add(Double.valueOf(tokens[i]));
        if (motif.cStats.size() == 5) { // no z-score, we should compute it and add it
          computeScore = true;
        } else {
          motif.zScore = motif.cStats.remove(5).doubleValue();
        }
      } else if (tokens[1].equals("|")) { // name, numNodes and numEdges of the motif
        motif = new Motif(tokens[0].trim() + GitWorks.safe_sep + name.split(GitWorks.safe_sep)[1],
            Integer.valueOf(tokens[2].trim()), Integer.valueOf(tokens[5].trim()));
      } else { // read a motif in this line
        for (String s : tokens) {
          edges.add(mg.getEdge(Integer.valueOf(s)));
        }
        edges.trimToSize();
        motif.addOccurrence(edges, twins);
        edges = new ArrayList<MetaEdge>();
      }
    } else if (tokens[0].equals("----")) { // end of occurrences of a motif
      motif.occurrences.trimToSize();
      motif.cStats.trimToSize();
      if (computeScore)
        motif.zScore = (motif.occurrences.size() - motif.cStats.get(1)) / motif.cStats.get(4);
      computeScore = false;
      GitWorks.addUnique(res, motif);
      motif = null; // trivial check: a malformed file causes a NullPointerException
    }
  }
  in.close();
  return res;
}


static void exportGraph(String name, DirectedSparseGraph<Commit, MetaEdge> g) throws IOException {
  File gFile = new File(GitWorks.pwd + "/" + name + ".graph");
  PrintWriter pout = new PrintWriter(new BufferedWriter(new FileWriter(gFile)));
  pout.println("nodes " + g.getVertexCount());
  for (Commit c : g.getVertices())
    pout.println(c.id.getName());
  pout.println("edges " + g.getEdgeCount());
  for (MetaEdge me : g.getEdges())
    pout.println(me.ID + " " + me.first.id.getName() + " " + me.last.id.getName());
  pout.flush();
  pout.close();
}


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
