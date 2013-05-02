/**
 * 
 */
package gitworks;

import circos.CircosData;
import circos.DIdeogram;
import circos.DLink;
import circos.WriteCommitsToMain;

import gitworks.Features.CommitRank;

import java.io.IOException;
import java.util.ArrayList;
//import java.lang.reflect.Array;
//import java.lang.reflect.Field;
//import java.io.BufferedWriter;
//import java.io.FileWriter;
//
//import org.apache.commons.math3.exception.MathIllegalArgumentException;
//import org.apache.commons.math3.stat.correlation.SpearmansCorrelation;
//import org.apache.commons.math3.stat.inference.MannWhitneyUTest;
//import org.apache.commons.math3.stat.inference.WilcoxonSignedRankTest;


/**
 *
 */
public class Results {


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
  for (i = 0; i < sorting.length; i++) {
    cur = sorting[i];
    if (iCommits[cur] <= min || cur == f.rootIndex) continue;
    di = new DIdeogram(f.allForks[cur], iCommits[cur], iAuthors[cur].size());
    res.addToSetB(di);
    res.getLinks().add(new DLink(main, di, di.getValue())); // adding like this keeps the order in res.links
    if (uCommits[cur] > 0) di.setScatter(uCommits[cur]); //res.addScatter(di, uCommits[cur]);
  }
  if (iCommits[f.rootIndex] > 0) {
    if (uCommits[f.rootIndex] > 0) main.setScatter(uCommits[f.rootIndex]); //res.addScatter(main, uCommits[f.rootIndex]);
    res.addToSetA(main);
    System.err.println("\t" + f.name + " has " + iCommits[f.rootIndex] + " iCommits: OK.");
  }
  else {
    System.err.println("\t" + f.name + " has no iCommit: DISCARDED.");
    if (res.getSize() > 0) System.err.println("ERROR: " + f.name + " has inconsistent values!");
    res = null;
  }
  return res;
}


////returns all forks that have more than min commits of the given rank
//@SuppressWarnings("unused")
//private static CircosData getRankedCommitToMainline(Features f, CommitRank rank, int min) {
//  CircosData res = new CircosData();
//  int i, l, d, b;
//  ArrayList<Integer> a = new ArrayList<Integer>();
//  DIdeogram di;
//  Iterator<DIdeogram> dIt;
//  for (i = 0; i < f.allForks.length; i++) {
//    if (f.commitRankRatio[rank.getValue()] * f.acCommitDiffusion.length <= min) continue;
//    l = 0;
//    a.clear();
//    for (int cc : f.getCommits(i)) {
//      if (f.commitRank[cc] == rank.getValue()) {
//        l++;
//        GitWorks.addUnique(a, f.commitAuthor[cc]);
//      }
//    }
//    b = a.size();
//    if (i == f.getRootIndex()) {
//      res.addToSetA(new DIdeogram(f.allForks[i], l, b));
//    } else {
//      res.addToSetB(new DIdeogram(f.allForks[i], l, b));
//    }
//  }
//
//  dIt = res.getAllSets().iterator(); // get links from rank-ed commits
//  while (dIt.hasNext()) {
//    di = dIt.next();
//    d = Arrays.binarySearch(f.allForks, di.getName());
//    for (int j = 0; j < f.cLinkMap[rank.getValue() + 1][d].length; j++) {
//      a = f.cLinkMap[rank.getValue() + 1][d][j];
//      if (a == null || a.size() == 0) continue;
//      res.addLink(di.getName(), f.allForks[j], a.size());
//    }
//  }
//  return res;
//}


public static void createCircosFiles(FeatureList fl) throws InterruptedException, IOException {
  Features fe;
  CircosData data;
  WriteCommitsToMain wctm;
//  String id;
  for (int i = 0; i < fl.size(); i++) {
    fe = fl.get(i);
    if (fe.allForks.length < 3) continue;
//    for (int j = 3; j < CommitRank.values().length; j++) {
//      id = fe.name + "." + CommitRank.values()[j].name();
//      data = getCommitToMainlinePerRank(fe, CommitRank.values()[j], 0);
//      wctm = new WriteCommitsToMain(data, fe.name);
//      wctm.createCircosFiles("colors." + id + ".conf", "karyo." + id + ".txt", "links." + id + ".txt");
//      Runtime.getRuntime().exec(GitWorks.pwd + "/makeCircosPlots.sh").waitFor();
//    }
    data = getICommitsToMainline(fe, 0);
    if (data == null) continue;
    System.out.println(data.toString());
    wctm = new WriteCommitsToMain(data, fe.name);
    wctm.createCircosFiles(GitWorks.trees_out_dir + fe.name + ".colors.conf",
        GitWorks.trees_out_dir + fe.name + ".karyo.txt",
        GitWorks.trees_out_dir + fe.name + ".links.txt", GitWorks.trees_out_dir + fe.name + ".scatters.txt");
    Runtime.getRuntime().exec(GitWorks.pwd + "/makeCircosPlots.sh").waitFor();
  }
}

//
////TODO : create data files to plot: 
////commitVS#forks + authorVS#forks + timeVS#commits (global and per fork) + commitRankVS(commit|author totals)
//@SuppressWarnings("rawtypes")
//static void createDataFiles(FeatureList fl) throws IOException, IllegalArgumentException, IllegalAccessException {
//  int i, k, l, f;
//  boolean append;
//  Field fields[] = Features.class.getFields();
//  Object fi;
//  Comparable[][] dataset;
//  ArrayList<Field> vFields = new ArrayList<Field>();
//  ArrayList<Field> aFields = new ArrayList<Field>();
//  ArrayList<String> aHeader = new ArrayList<String>(), vHeader = new ArrayList<String>();
//  ArrayList<Integer> iAuth = new ArrayList<Integer>();
//  vHeader.add("forkName");
//  for (i = 0; i < fields.length; i++) {
//    fi = fields[i].get(fl.get(0));
//    if (fi.getClass().isArray()) {
//      if (fi.getClass().getComponentType().isArray()) continue;
//      aFields.add(fields[i]);
//      aHeader.add(fields[i].getName()); // aHeader.add("\"" + fields[i].getName() + "\""); //
//    } else {
//      vFields.add(fields[i]);
//      vHeader.add(fields[i].getName()); // vHeader.add("\"" + fields[i].getName() + "\""); //
//    }
//  }
//  aHeader.add("iCommits");
//  aHeader.add("iAuthors");
//  append = false;
//
//  for (Features fe : fl) {
//    k = 0;
//    for (Field fie : aFields) {
//      k = Math.max(k, Array.getLength(fie.get(fe)));
//    }
//    dataset = new Comparable[aFields.size() + 2][k];
//    for (k = 0; k < dataset.length; k++) {
//      for (l = 0; l < dataset[k].length; l++) {
//        dataset[k][l] = "";
//      }
//    }
//    k = 0;
//    for (Field fie : aFields) {
//      for (l = 0; l < Array.getLength(fie.get(fe)); l++) {
//        // if (fie.get(fe).getClass().getComponentType().equals(String.class)) {
//        // dataset[k][l] = "\"" + (Comparable)Array.get(fie.get(fe), l) + "\"";
//        // } else
//        dataset[k][l] = (Comparable)Array.get(fie.get(fe), l);
//      }
//      k++;
//    }
//    for (f = 0; f < fe.forkCommit.length; f++) { // iCommits & iAuthors
//      l = 0;
//      iAuth.clear();
//      for (int cc : fe.forkCommit[f]) {
//        if (fe.commitRank[cc] > CommitRank.UNIQUE.getValue()) {
//          l++;
//          GitWorks.addUnique(iAuth, fe.commitAuthor[cc]);
//        }
//      }
//      dataset[k][f] = l;
//      dataset[k + 1][f] = iAuth.size();
//    }
//    makeDataFiles(fe.name + ".data", false, GitWorks.field_sep, aHeader.toArray(new String[0]),
//        dataset, null);
//    makeDataFiles(fe.name + ".data.csv", false, ",", aHeader.toArray(new String[0]), dataset, null);
//
//    k = 1;
//    dataset = new Comparable[vFields.size() + 1][1];
//    dataset[0][0] = fe.name;
//    for (Field fie : vFields) {
//      dataset[k++][0] = (Comparable)fie.get(fe);
//    }
//    makeDataFiles("ALLTREES.svdata", append, GitWorks.field_sep,
//        append ? null : vHeader.toArray(new String[0]), dataset, null);
//    makeDataFiles("ALLTREES.svdata.csv", append, ",",
//        append ? null : vHeader.toArray(new String[0]), dataset, null);
//
//    dataset = new Comparable[fe.aggregateTimeLines.length + 1][1];
//    dataset[0][0] = fe.name;
//    for (i = 0; i < fe.aggregateTimeLines.length; i++) {
//      dataset[i + 1][0] = Integer.valueOf(fe.aggregateTimeLines[i]);
//    }
//    makeDataFiles("ALLTREES.agTimeLines", append, GitWorks.field_sep, append ? null : new String[] {
//        "forkTree", "#commits" }, dataset, null);
//    makeDataFiles("ALLTREES.agTimeLines.csv", append, ",", append ? null : new String[] {
//        "forkTree", "#commits" }, dataset, null);
//    append = true;
//
//    dataset = new Comparable[2][fe.commitRank.length];
//    for (i = 0; i < fe.commitRank.length; i++) {
//      dataset[0][i] = Integer.valueOf(fe.commitRank[i]);
//      dataset[1][i] = Integer.valueOf(fe.extendedTimeLines[i]);
//    }
//    makeDataFiles(fe.name + ".cRanks", false, GitWorks.field_sep, new String[] { "commitRank",
//        "commitWeek" }, dataset, null);
//    makeDataFiles(fe.name + ".cRanks.csv", false, ",", new String[] { "commitRank", "commitWeek" },
//        dataset, null);
//
//    dataset = new Comparable[3][fe.commitRankRatio.length];
//    for (i = 0; i < fe.commitRankRatio.length; i++) {
//      dataset[0][i] = CommitRank.values()[i].name();
//      dataset[1][i] = Double.valueOf(fe.commitRankRatio[i]);
//      dataset[2][i] = Double.valueOf(fe.authorRankRatio[i]);
//    }
//    makeDataFiles(fe.name + ".rankTot", false, GitWorks.field_sep, new String[] { "rank",
//        "commits", "authors" }, dataset, null);
//    makeDataFiles(fe.name + ".rankTot.csv", false, ",",
//        new String[] { "rank", "commits", "authors" }, dataset, null);
//
//    dataset = new Comparable[fe.cLinkMap[0].length + 1][fe.cLinkMap[0].length + 1];
//    dataset[0][0] = "";
//    System.arraycopy(fe.allForks, 0, dataset[0], 1, fe.allForks.length);
//    for (int q = 0; q < CommitRank.values().length; q++) {
//      for (k = 0; k < fe.cLinkMap[q].length; k++) {
//        dataset[k + 1][0] = fe.allForks[k];
//        for (l = k; l < fe.cLinkMap[q].length; l++) {
//          dataset[k + 1][l + 1] = fe.cLinkMap[q][k][l].size();
//          dataset[l + 1][k + 1] = fe.cLinkMap[q][k][l].size();
//        }
//      }
//      makeDataFiles(fe.name + "." + CommitRank.values()[q] + ".cMap", false, GitWorks.field_sep,
//          null, dataset, null);
//      makeDataFiles(fe.name + "." + CommitRank.values()[q] + ".cMap.csv", false, ",", null,
//          dataset, null);
//      for (k = 0; k < fe.aLinkMap[q].length; k++) {
//        for (l = k; l < fe.aLinkMap[q].length; l++) {
//          dataset[k + 1][l + 1] = fe.aLinkMap[q][k][l].size();
//          dataset[l + 1][k + 1] = fe.aLinkMap[q][k][l].size();
//        }
//      }
//      makeDataFiles(fe.name + "." + CommitRank.values()[q] + ".aMap", false, GitWorks.field_sep,
//          null, dataset, null);
//      makeDataFiles(fe.name + "." + CommitRank.values()[q] + ".aMap.csv", false, ",", null,
//          dataset, null);
//    }
//
//  }
//
//}
//
//
//static private void makeDataFiles(String fileName, boolean append, String fieldSep,
//    String[] header, Object[][] dataset, int[] sortedIndexes) throws IOException {
//  int i, j;
//  BufferedWriter fileData;
//  fileData = new BufferedWriter(new FileWriter(GitWorks.trees_out_dir + fileName, append));
//  // if (append) {
//  // fileData.newLine();
//  // fileData.newLine();
//  // }
//  if (header != null && header.length > 0) {
//    fileData.write(header[0]);
//    for (i = 1; i < header.length; i++) {
//      fileData.write(fieldSep + header[i]);
//    }
//    fileData.newLine();
//  }
//  boolean sorted = (sortedIndexes != null);
//  for (j = 0; j < dataset[0].length; j++) {
//    fileData.write(dataset[0][j].getClass().cast(dataset[0][sorted ? sortedIndexes[j] : j])
//        .toString());
//    for (i = 1; i < dataset.length; i++) {
//      fileData.write(fieldSep
//          + dataset[i][sorted ? sortedIndexes[j] : j].getClass()
//              .cast(dataset[i][sorted ? sortedIndexes[j] : j]).toString());
//    }
//    fileData.newLine();
//  }
//
//  fileData.flush();
//  fileData.close();
//}
//
//
//static void eigenPrintOut(Measures m) throws IllegalAccessException, IOException {
//  BufferedWriter fileCos, fileCorr, fileData;
//  double cosims[][] = new double[m.vFNames.size()][m.forkTrees.size()];
//  double res[] = m.computeEigenCosims(cosims);
//  double diff[][] = new double[m.vFNames.size()][m.forkTrees.size()];
//  int i, j, k;
//  SpearmansCorrelation co;
//  fileCos = new BufferedWriter(
//      new FileWriter(GitWorks.trees_out_dir + "PCA_ALLTREES.cosims", false));
//  fileCos.write("project");
//  for (String s : m.vFNames) {
//    fileCos.write(GitWorks.field_sep + s + GitWorks.field_sep + "cosim_" + s);
//  }
//  fileCos.write(GitWorks.field_sep + "num_eigenValues"); // + GitWorks.field_sep +
//                                                         // "eigenValues_max");
//  for (i = 0; i < m.forkTrees.size(); i++) {
//    fileCos.newLine();
//    fileCos.write(m.forkTrees.get(i));
//    for (j = 0; j < m.vFNames.size(); j++) {
//      diff[j][i] = m.targets[m.bestForkTrees[j]][j] - m.targets[i][j];
//      fileCos.write(GitWorks.field_sep + (diff[j][i]) + GitWorks.field_sep + cosims[j][i]);
//    }
//    fileCos.write(GitWorks.field_sep + res[i]); // + GitWorks.field_sep + eigenvalues[i][0]);
//  }
//  fileCos.newLine();
//  fileCos.flush();
//  fileCos.close();
//
//  fileCorr = new BufferedWriter(new FileWriter(GitWorks.trees_out_dir + "PCA_ALLTREES.corr", false));
//  fileCorr.write("#" + GitWorks.field_sep);
//  for (String s : m.vFNames) {
//    fileCorr.write(GitWorks.field_sep + s);
//  }
//  fileCorr.newLine();
//  fileCorr.write("cosims_diff");
//  double[] a1 = new double[m.forkTrees.size()];
//  double[] a2 = new double[m.forkTrees.size()];
//  for (j = 0; j < m.vFNames.size(); j++) {
//    k = 0;
//    for (i = 0; i < m.forkTrees.size(); i++) {
//      if (!Double.isNaN(cosims[j][i])) {
//        a1[k] = diff[j][i];
//        a2[k] = cosims[j][i];
//        k++;
//      }
//    }
//    co = new SpearmansCorrelation();
//    fileCorr.write(GitWorks.field_sep + co.correlation(Arrays.copyOf(a1, k), Arrays.copyOf(a2, k))); // correlation(diff[j],
//                                                                                                     // cosims[j])
//  }
//  fileCorr.newLine();
//  for (j = 0; j < m.aFNames.size(); j++) {
//    fileCorr.write(m.aFNames.get(j));
//    for (int t = 0; t < m.vFNames.size(); t++) {
//      k = 0;
//      for (i = 0; i < m.forkTrees.size(); i++) {
//        if (!Double.isNaN(m.measures[i][j])) {
//          a1[k] = m.measures[i][j];
//          a2[k] = m.targets[i][t];
//          k++;
//        }
//      }
//      co = new SpearmansCorrelation();
//      fileCorr.write(GitWorks.field_sep
//          + co.correlation(Arrays.copyOf(a1, k), Arrays.copyOf(a2, k))); // correlation(targets[*][t],
//                                                                         // measures[*][j])
//    }
//    fileCorr.newLine();
//  }
//  fileCorr.flush();
//  fileCorr.close();
//
//  fileData = new BufferedWriter(new FileWriter(GitWorks.trees_out_dir + "PCA_ALLTREES.data", false));
//  fileData.write("project");
//  for (j = 0; j < m.aFNames.size(); j++) {
//    fileData.write(GitWorks.field_sep + m.aFNames.get(j));
//  }
//  for (j = 0; j < m.vFNames.size(); j++) {
//    fileData.write(GitWorks.field_sep + m.vFNames.get(j));
//  }
//  fileData.newLine();
//  for (i = 0; i < m.forkTrees.size(); i++) {
//    fileData.write(m.forkTrees.get(i));
//    for (j = 0; j < m.aFNames.size(); j++) {
//      fileData.write(GitWorks.field_sep + m.measures[i][j]);
//    }
//    for (j = 0; j < m.vFNames.size(); j++) {
//      fileData.write(GitWorks.field_sep + m.targets[i][j]);
//    }
//    fileData.newLine();
//  }
//  fileData.flush();
//  fileData.close();
//}
//
//
//static void printOutFeatures(Object o, Measures m) {
//  int tot, i, j, k = 0;
//  if (!(o instanceof Features || o instanceof FeatureList)) {
//    System.err.println("Measures : ERROR : The argument is neither"
//        + " a Features nor a FeatureList instance (" + o.getClass().toString() + ")!");
//    return;
//  }
//  boolean single = o instanceof Features;
//  Field fields[] = Features.class.getFields();
//  double res[][] = null;
//  String names[] = null;
//  SpearmansCorrelation co = new SpearmansCorrelation();
//  WilcoxonSignedRankTest wsrt = new WilcoxonSignedRankTest();
//  MannWhitneyUTest mwut = new MannWhitneyUTest();
//  tot = fields.length * (fields.length - 1) / 2;
//  res = new double[tot][3];
//  names = new String[tot];
//  BufferedWriter fileOut = null;
//  try {
//    fileOut = new BufferedWriter(new FileWriter(GitWorks.trees_out_dir
//        + (single ? ((Features)o).name : "ALLTREES") + ".corr", false));
//    for (i = 0; i < fields.length; i++) {
//      m.tMeasure = fields[i];
//      if (single && m.tMeasure.get(o) == null) continue;
//      if (single && !m.tMeasure.get(o).getClass().isArray()) {
//        printOutFeature(((Features)o), m.tMeasure);
//        continue;
//      }
//      for (j = i + 1; j < fields.length; j++) {
//        m.cMeasure = fields[j];
//        if (single && (m.cMeasure.get(o) == null || !m.cMeasure.get(o).getClass().isArray()))
//          continue;
//        m.setMeasures(o);
//        if (m.measureIndex <= 0) continue;
//        names[k] = m.tMeasure.getName() + "    " + m.cMeasure.getName();
//        try {
//          res[k][0] = co.correlation(m.candidateMeasures, m.targetMeasures);
//          res[k][1] = wsrt.wilcoxonSignedRankTest(m.candidateMeasures, m.targetMeasures, false);
//          res[k][2] = mwut.mannWhitneyUTest(m.candidateMeasures, m.targetMeasures);
//        }
//        catch (MathIllegalArgumentException e) {
//          System.err.println(e.getMessage());
//          res[k][0] = Double.NaN;
//          res[k][1] = Double.NaN;
//          res[k][2] = Double.NaN;
//        }
//        finally {
//          m.reset();
//          k++;
//        }
//      }
//    }
//    fileOut.write("# <Feature 1 name>     <Feature 2 name>    <Spearman's correlation>"
//        + "    <Wilcoxon Signed-Rank p-value>    <Mann-Withney U p-value>\n");
//    for (i = 0; i < k; i++) {
//      fileOut.write(names[i] + "    " + res[i][0] + "    " + res[i][1] + "    " + res[i][2] + "\n");
//    }
//    fileOut.flush();
//    fileOut.close();
//  }
//  catch (Exception e) {
//    e.printStackTrace();
//  }
//}
//
//
//static void printOutFeature(Features f, Field field) throws IllegalAccessException, IOException {
//  BufferedWriter fileOut = new BufferedWriter(new FileWriter(GitWorks.trees_out_dir
//        + f.name + ".svdata", true));
//  Object o = field.get(f);
//  fileOut.write(field.getName() + "    " + ((Number)o).doubleValue() + "\n");
//  fileOut.flush();
//  fileOut.close();
//}

}
