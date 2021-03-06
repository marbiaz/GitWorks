package gitworks;


import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.Externalizable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Array;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.math3.random.MersenneTwister;


public class GitWorks {


public static boolean bare = true; // the umbrella repo is a bare git repo

public static boolean anew = true; // (re-)create an umbrella repo anew

static boolean computeUmbrellas = false; // if true compute umbrella repos anew ; if false use
                                          // serialized forkList
static boolean newAnalysis = false; // if true perform a full gitMiner analysis ; if false use serialized gitMiner data
static boolean compuFeatures = false; // if true compute features from gitMiner data; if false, use serialized features
static boolean resultsOnly = true; // only compute results from serialized features

public static String prefix = "JGIT_"; // to be prepended to any jgit-generated output file name
public static String field_sep = "    "; // field separator in input datafile's lines
public static String id_sep = "/"; // the string that separates owner and name in a fork id string
public static String safe_sep = "__A-T__"; // weird marker that should hopefully never occur in
                                            // usernames, repo names and that is filesystem safe
public static String list_sep = ","; // fork id separator in the list taken from the input file
public static String log_sep = "<#>"; // field separator within a git log output line
public static String repo_dir; // the absolute path to the dir that contains the git repos to be
                                // imported in jgit data structures
public static String gits_out_dir; // the relative path to the dir which will contain the
                                    // jgit-generated git repos to analyse
public static String trees_out_dir; // the relative path to the dir which will contain the
                                    // jgit-generated trees of the repos
static String pwd; // set according to the current pwd

static String[] ids = null; // list of root repos to be considered to build the umbrella repos and perform analysis.
static ForkList projects;
static FeatureList features;


static void dfsVisit(int depth, ForkEntry f, DfsOperator t, Object o) throws Exception {
  if (t == null) {
    System.err.println("WARNING: dfsVisit called with null operator.");
    return;
  }
  if (f == null) {
    System.err.println("WARNING: DfsOperator " + t.getID() + " called on a null instance.");
    return;
  }
  if (o == null) {
    System.err.println("WARNING: DfsOperator " + t.getID() + " called with a null argument.");
    return;
  }
  if (depth > 0 && f.hasForks()) {
    t.initialize(f);
    Iterator<ForkEntry> it = f.getForks();
    while (it.hasNext()) {
      dfsVisit(depth - 1, it.next(), t, o);
      if (!t.runOnce()) t.run(f, o);
    }
    if (t.runOnce()) t.run(f, o);
  } else t.run(f, o);
  t.finalize(f);
}


static void dfsVisit(int depth, ForkEntry f, DfsOperator t, int[] t_arg) throws Exception {
  if (t == null) {
    System.err.println("WARNING: dfsVisit called with null operator.");
    return;
  }
  if (f == null) {
    System.err.println("WARNING: DfsOperator " + t.getID() + " called on a null instance.");
    return;
  }
  if (t_arg == null) {
    System.err.println("WARNING: DfsOperator " + t.getID() + " called with a null argument.");
    return;
  }
  if (depth > 0 && f.hasForks()) {
    t.initialize(f);
    int[] temp = new int[t_arg.length];
    Iterator<ForkEntry> it = f.getForks();
    while (it.hasNext()) {
      System.arraycopy(t_arg, 0, temp, 0, t_arg.length);
      dfsVisit(depth - 1, it.next(), t, temp);
      if (!t.runOnce()) t.run(f, temp);
    }
    if (t.runOnce()) t.run(f, temp);
    System.arraycopy(temp, 0, t_arg, 0, t_arg.length);
  } else t.run(f, t_arg);
  t.finalize(f);
}


/**
 * Returns the project ID formatted in a convenient way to serve as a remote name. It substitutes
 * occurrences of {@link #id_sep} with {@link #safe_sep}.
 *
 * @param f
 * @return The safe-name of the entry.
 */
public static String getSafeName(ForkEntry f) {
  return f.getId().replace(id_sep, safe_sep);
}


/**
 * It gives the absolute path (internal URI) of the repo corresponding to the given ForkEntry.
 *
 * @param f
 * @return
 */
static String getProjectPath(ForkEntry f) {
  String t[] = f.getId().split(id_sep);
  return repo_dir + t[0] + "/" + t[1] + ".git"; // t[1] + "/" + t[0] + "/" + t[1] + ".git";
}


/**
 * As of now, it is meant to compute things in the umbrella repo of each project, so that for forks
 * at different layers the computed aggregation depth is parent's one - 1. with a large depth param
 * value the complete umbrella repos will be visited
 *
 * @param ids
 * @param fl
 * @param depth
 * @throws Exception
 */
public static void computeAggregates(String ids[], ForkList fl, int depth) throws Exception {
  if (fl.size() == 0 || depth < 1) {
    System.err.println("computeAggregates : input ERROR.");
    return;
  }
  int i = 0, r[] = new int[5];
  if (ids == null || ids.length == 0) {
    ids = new String[fl.howManyTrees()];
    for (ForkEntry f : fl)
      if (f.isRoot()) ids[i++] = f.getId();
  }
  ForkEntry fe;
  for (String id : ids) {
    if (!ForkEntry.isValidId(id)) {
      System.err.println("computeAggregates : input ERROR (invalid id: " + id + ").");
      continue;
    }
    Arrays.fill(r, 0);
    fe = GitWorks.getElement(fl, id);
    dfsVisit(depth, fe, ForkEntry.computeAggregates, r);
  }
}


/**
 * Delete from the children ForkList of the argument all the entries whose repo cannot be found in
 * the local FS.
 *
 * @param globalList
 * @param f
 * @throws Exception
 */
static void purgeMissingForks(ArrayList<ForkEntry> globalList, ForkEntry f) throws Exception {
  File fi;
  if (!f.hasForks()) return;
  int c = 0;
  // String out = "";
  Iterator<ForkEntry> it = f.getForks();
  ForkEntry fe, fks[] = new ForkEntry[f.howManyForks()];
  while (it.hasNext()) {
    fe = it.next();
    fi = new File(getProjectPath(fe));
    if (!fi.canRead()) {
      fks[c++] = fe;
      // out += " " + fe.getId();
      globalList.remove(fe); // remove fe from the main projects list (no dangling entries)!
    }
  }
  // System.out.print("Deleting missing repos entries from the lists (" + out + " ) ... ");
  f.removeForks(Arrays.copyOf(fks, c));
  // System.out.println("done!");
}


static ForkList populateForkList(String inputFile) throws Exception {

  ForkEntry fe, fc;
  String line, tokens[];
  int c = 0, cc = 0;
  ArrayList<String> children = new ArrayList<String>();
  BufferedReader listFile = new BufferedReader(
      new InputStreamReader(new FileInputStream(inputFile)));
  ForkList l = new ForkList();
  DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX");
  while ((line = listFile.readLine()) != null) {
    c++;
    tokens = line.split(field_sep);
    if (ForkEntry.isValidId(tokens[1] + id_sep + tokens[0])) {
      cc = l.addEntry(new ForkEntry(tokens[1], tokens[0], tokens[3].equalsIgnoreCase("nan") ? -1
          : Integer.valueOf(tokens[3]), df.parse(tokens[2]).getTime(),
          df.parse(tokens[tokens.length - 1]).getTime()));
      if (cc < 0) children.add(-cc - 1, tokens.length == 6 ? tokens[4] : "");
      else System.err.println("WARNING: duplicate entry in input file (" + tokens[1] + id_sep
          + tokens[0] + ").");
    } else System.err.println("Error while reading fork data from file, at line " + c + ".");
  }
  listFile.close();
  Iterator<ForkEntry> it = l.iterator();
  for (int i = 0; it.hasNext(); i++) {
    fe = it.next();
    if (!"".equals(children.get(i))) {
      cc = 0;
      tokens = children.get(i).split(list_sep);
      for (String f : tokens) {
        cc++;
        fc = GitWorks.getElement(l, f);
        if (fc != null) fe.addFork(fc);
        else System.err.println("Error while reading fork data from file, for project " + fe.getId()
            + " about fork # " + cc + " (" + f + ").");
      }
    }
  }
  l.setTreeCounter();
  return l;
}


/**
 * @param args
 * @throws Exception
 */
public static void main(String[] args) throws Exception {

  ForkEntry fe;
  Features feat;
  GitMiner gitMiner;

  if (args.length < 4) {
    System.err
    .println("Usage: java GitWorks <repo list file path> <repo dir path> <jgit gits out dir> <jgit trees out dir> [<comma-separated no-space list of fork ids>]");
    System.exit(2);
  }
  pwd = System.getenv("PWD"); // trees_out_dir + ".."
  repo_dir = args[1].trim() + (args[1].trim().endsWith("/") ? "" : "/");
  if (args.length == 5) ids = args[4].trim().split(",");
  gits_out_dir = args[2].trim() + (args[2].trim().endsWith("/") ? "" : "/");
  trees_out_dir = args[3].trim() + (args[3].trim().endsWith("/") ? "" : "/");
  if (!new File(repo_dir).isDirectory() || !new File(trees_out_dir).isDirectory()
      || !new File(gits_out_dir).isDirectory()) {
    System.err
    .println("FATAL ERROR : Cannot find repos dir (" + repo_dir + ") or gits output dir ("
        + gits_out_dir + ") or trees output dir (" + trees_out_dir + ")");
    System.exit(1);
  }

  /************** create fork list ****************/

  System.err.println("# Computation started at " + new java.util.Date().toString() + "\n");

  if (computeUmbrellas) {
    projects = populateForkList(args[0].trim());
    computeAggregates(null, projects, Integer.MAX_VALUE);
    exportData(projects, trees_out_dir + "dumpFiles/" + "forkListDump.complete");
    // projects.printForkTrees(System.out); // from this, lists of repos to retrieve can be made
  } else {
    projects = new ForkList();
    importData(projects, trees_out_dir + "dumpFiles/" + "OSS.forkListDump"); // XXX forkListDump
    // computeAggregates(null, projects, 1); // reset all projects aggregates
    // projects.printForkTrees(new PrintStream(new FileOutputStream(trees_out_dir + "forkTree.list")));
  }

  ArrayList<ForkEntry> forkTrees = projects.getRoots();
  features = new FeatureList(projects.howManyTrees() + 1);

  /************** build and analyze fork trees ****************/

  for (int i = 0, j = 0; !resultsOnly && i < forkTrees.size() && (ids == null || j < ids.length); i++) {
    if (ids != null)
      fe = GitWorks.getElement(projects, ids[j++]);
    else
      fe = forkTrees.get(i);
    try {
      feat = new Features();
      gitMiner = new GitMiner();
      if (!newAnalysis && compuFeatures)
        Runtime.getRuntime().exec(pwd + "/loadDumps.sh " + getSafeName(fe)).waitFor();
      if (newAnalysis) {
        if (anew) Runtime.getRuntime().exec(pwd + "/loadRepos.sh " + getSafeName(fe)).waitFor();
        if (computeUmbrellas) {
          if (anew) purgeMissingForks(projects, fe);
          computeAggregates(new String[] { fe.getId() }, projects, Integer.MAX_VALUE);
        }
        gitMiner.analyzeUmbrella(fe);
        if (anew) Runtime.getRuntime().exec(pwd + "/cleanup.sh " + getSafeName(fe)).waitFor();
        if (!gitMiner.buildMetaGraph()) {
          System.err.println("ERROR : Metagraph checkup failed!!!");
          gitMiner.deleteMetaGraph();
        } else for (Dag d : gitMiner.metaGraph.dags)
          d.bfVisit();
        exportData(gitMiner, trees_out_dir + "dumpFiles/" + gitMiner.name + ".gm"); // + "_" + gitMiner.id
        System.out.println(gitMiner.getInfo()); System.out.flush();
      } else if (compuFeatures) importData(gitMiner, trees_out_dir + "dumpFiles/" + getSafeName(fe) + ".gm"); // + "_*"
      // System.out.println(gitMiner.getInfo()); System.out.flush();
      if (compuFeatures) {
        feat.setFeatures(projects, fe, gitMiner);
        features.addFeatures(feat);
        exportData(feat, trees_out_dir + "dumpFiles/" + feat.name + ".feat");
      } else importData(feat, trees_out_dir + "dumpFiles/" + getSafeName(fe) + ".feat");
      if (newAnalysis || compuFeatures)
        Runtime.getRuntime().exec(pwd + "/backupDumps.sh " + getSafeName(fe)).waitFor();
      importModStats(gitMiner);
      computeMetaGraph(gitMiner, feat); // XXX
    }
    catch (Exception e) {
      System.err.println("ERROR : computation of " + getSafeName(fe)
          + " was interrupted before completion!");
      e.printStackTrace();
    }
    finally {
      gitMiner = null;
      feat = null;
      System.gc();
    }
  }
  if (computeUmbrellas && newAnalysis) {
    exportData(projects, trees_out_dir + "dumpFiles/" + "forkListDump");
  }
  if (compuFeatures && !resultsOnly) {
    exportData(features, trees_out_dir + "dumpFiles/" + "featureListDump");
  }

  /*********************** compute results ************************/

  if (resultsOnly) {
    //    for (int i = 0, j = 0; i < forkTrees.size() && (ids == null || j < ids.length); i++) {
    //      fe = forkTrees.get(i);
    //      feat = new Features();
    //      Runtime.getRuntime().exec(pwd + "/loadDumps.sh " + getSafeName(fe)).waitFor();
    //      importData(feat, trees_out_dir + "dumpFiles/" + getSafeName(fe) + ".feat");
    //      features.addFeatures(feat);
    //    }
    //    exportData(features, trees_out_dir + "dumpFiles/" + "featureListDump");
    // waitForUser("");
    // importData(features, trees_out_dir + "dumpFiles/" + "featureListDump");
    Features ft;
    features = null;
    for (int i = 0, j = 0; i < forkTrees.size() && (ids == null || j < ids.length); i++) {
      if (ids != null)
        fe = GitWorks.getElement(projects, ids[j++]);
      else
        fe = forkTrees.get(i);
      // System.err.print("Getting " + getSafeName(fe) + "...");
      // System.err.flush();

      // ft = GitWorks.getElement(features, getSafeName(fe));
      ft = new Features();
      importData(ft, trees_out_dir + "dumpFiles/" + getSafeName(fe) + ".feat");
      // Runtime.getRuntime().exec(pwd + "/loadDumps.sh " + getSafeName(fe)).waitFor(); XXX
      gitMiner = new GitMiner();
      importData(gitMiner, trees_out_dir + "dumpFiles/" + getSafeName(fe) + ".gm");
      // Runtime.getRuntime().exec(pwd + "/backupDumps.sh " + getSafeName(fe)).waitFor(); XXX
      // System.err.println(" done.");
      // System.err.flush();

      // importModStats(gitMiner);
      // exportData(gitMiner, trees_out_dir + "dumpFiles/" + gitMiner.name + ".gm");

      computeMetaGraph(gitMiner, ft); //XXX
      // purgeMissingForks(forkTrees, fe);
      // testGitective(gitMiner, fe, ft);
      // feats.add(ft);

      gitMiner = null;
      ft = null;
      // System.gc();
    }

  }
  // Results.createCircosFiles(feats); // XXX
  // Results.printoutForkStats(feats);
  Results.metagraphStats(mgs, feats);
  // int i = 0;
  // for (MetaGraph mg : mgs) {
  // mg.getDensestDag().exportToGexf(feats.get(i++).name);
  // }

  System.err.println("\n# Computation ended at " + new java.util.Date().toString());
  System.exit(0);
}


static void importModStats(GitMiner gm) {
  BufferedReader in = null;
  String line, tokens[];
  Commit c;
  org.eclipse.jgit.lib.MutableObjectId id = new org.eclipse.jgit.lib.MutableObjectId();
  int count = 0;
  try {
    in = new BufferedReader(new FileReader(pwd + "/STORAGE/" + gm.name + ".diffie"));
    while ((line = in.readLine()) != null) {
      count++;
      tokens = line.split(" ");
      id.fromString(tokens[0]);
      c = GitWorks.getElement(gm.allCommits, id);
      if (c == null) {
        //System.err.println("WARNING : No commit " + id.getName() + " in " + gm.name + ".");
        continue;
      }
      c.mFiles = c.mLines = 0;
      switch (tokens.length) {
      case 4 :
        c.mLines = Integer.parseInt(tokens[3]);
      case 3 :
        c.mLines += Integer.parseInt(tokens[2]);
      case 2 :
        c.mFiles = Integer.parseInt(tokens[1]);
      }
      //System.err.println("LOG : " + gm.name + " : id " + id.getName() + " ; files: " + c.mFiles + " ; lines: " + c.mLines);
    }
    in.close();
  }
  catch (FileNotFoundException fnfe) {
    System.err.println("No diffie file for " + gm.name + "!");
  }
  catch (Exception e) {
    System.err.println("ERROR \"" + e.getClass().getName() + " : " + e.getMessage() + "\" while processing " + gm.name + " at line " + count + ".");
  }
}
static ArrayList<MetaGraph> mgs = new ArrayList<MetaGraph>();
static ArrayList<Features> feats = new ArrayList<Features>();
static int repoCounter = 0;

static void computeMetaGraph(GitMiner gm, Features ft) {
  Commit co;
  MetaGraph mg;
  ArrayList<Commit> heads = new ArrayList<Commit>();
  ArrayList<Commit> allComs = new ArrayList<Commit>();

  System.err.print("Creating " + gm.name + " mainline metagraph ...");
  System.err.flush();
  for (Commit c : gm.comInF.get(ft.allForks[ft.rootIndex])) {
    co = new Commit(c);
    GitWorks.addUnique(allComs, co);
    if (co.isHead()) heads.add(co);
  }
  mg = MetaGraph.createMetaGraph(allComs, heads);
  System.err.println(" done.");
  System.err.flush();
  int[] stats = mg.getDensestDag().getSummaryStats(); // mg.getSummaryStats(); // XXX
  if (stats[3] >= 10) {
    mgs.add(MetaGraph.createMetaGraph(mg.getDensestDag())); // mgs.add(mg); // XXX
    feats.add(ft);
    System.err.println("Taking repo # " + (++repoCounter) + " : " + ft.name + ", which has "
        + mg.dags.size() + " dags, " + stats[3] + " metaedges, " + stats[0] + " roots, " + stats[1]
            + " nodes and " + stats[2] + " leaves, for a total of " + stats[7]
                + " commits,\n\t of which " + stats[4] + " are branch nodes, " + stats[5]
                    + " are merge nodes and " + stats[6] + " are both.");
  } else System.err.println("Discarding " + ft.name + " which has " + stats[3] + " metaedges.");

}


public static void testMetaGraph(GitMiner gm) {
  gm.deleteMetaGraph();
  if (!gm.buildMetaGraph()) {
    System.err.print("ERROR : Metagraph checkup failed! It appears to have\n\t"
        + gm.metaGraph.toString());
    gm.deleteMetaGraph();
    System.out.println("\n" + gm.getInfo() + "\n+++++++++++++++++++++++++++++\n");
    System.out.flush();
  } else {
    for (Dag d : gm.metaGraph.dags)
      d.bfPrintout(d.bfVisit());
    gm.metaGraph.getDensestDag().exportToGexf(gm.name + "_complete");
  }
}


public static void testSubGraphs(GitMiner gm) {
  long since, until;
  since = gm.metaGraph.since; // ((gm.metaGraph.until - gm.metaGraph.since) / 8L) // gm.metaGraph.since; // 1287424351000L
  until = (gm.metaGraph.until - gm.metaGraph.since) / 4L + gm.metaGraph.since; // 1287679024000L // // since // (1000L * 3600L * 24L * 31L)
  // System.out.println(gm.name + " : Building quarter sub-graph... ");
  MetaGraph quarter = gm.metaGraph.getOldestDag().buildSubGraph(null, new java.util.Date(until));
  if (quarter != null) {
    quarter.checkup();
    System.out.println(gm.name + " quarter meta-graph (since " + new java.util.Date(since)
    + " until " + new java.util.Date(until) + ") has " + quarter.toString());
    for (Dag d : quarter.dags)
      d.bfPrintout(d.bfVisit());
    quarter.getDensestDag().exportToGexf(gm.name + "_quarter");
  } else
    System.out.println(gm.name + " quarter meta-graph (since " + new java.util.Date(since)
    + " until " + new java.util.Date(until) + ") is empty.");
  until = (gm.metaGraph.until - gm.metaGraph.since) / 2L + gm.metaGraph.since;
  // System.out.println(gm.name + " : Building half sub-graph... ");
  MetaGraph half = gm.metaGraph.getOldestDag().buildSubGraph(null, new java.util.Date(until));
  if (half != null) {
    half.checkup();
    System.out.println(gm.name + " half meta-graph (since " + new java.util.Date(since) + " until "
        + new java.util.Date(until) + ") has " + half.toString());
    for (Dag d : half.dags)
      d.bfPrintout(d.bfVisit());
    half.getDensestDag().exportToGexf(gm.name + "_half");
  } else
    System.out.println(gm.name + " half meta-graph (since " + new java.util.Date(since) + " until "
        + new java.util.Date(until) + ") is empty.");
  until = (gm.metaGraph.until - gm.metaGraph.since) * 3L / 4L + gm.metaGraph.since;
  // System.out.println(gm.name + " : Building threequarters sub-graph... ");
  MetaGraph threequarters = gm.metaGraph.getOldestDag().buildSubGraph(null,
      new java.util.Date(until));
  if (threequarters != null) {
    threequarters.checkup();
    System.out.println(gm.name + " threequarters meta-graph (since " + new java.util.Date(since)
    + " until " + new java.util.Date(until) + ") has " + threequarters.toString());
    for (Dag d : threequarters.dags)
      d.bfPrintout(d.bfVisit());
    threequarters.getDensestDag().exportToGexf(gm.name + "_threequarters");
  } else
    System.out.println(gm.name + " threequarters meta-graph (since " + new java.util.Date(since)
    + " until " + new java.util.Date(until) + ") is empty.");
  System.out.println(gm.name + " complete meta-graph (since "
      + new java.util.Date(gm.metaGraph.since) + " until " + new java.util.Date(gm.metaGraph.until)
  + ") has " + gm.metaGraph.toString());
}


public static void importData(Externalizable o, String filePath) throws FileNotFoundException,
IOException, ClassNotFoundException {
  ObjectInput in = new ObjectInputStream(new GZIPInputStream(new BufferedInputStream(
      new FileInputStream(filePath))));
  o.readExternal(in);
  in.close();
}


public static void exportData(Externalizable o, String filePath) throws IOException {
  File dump = new File(filePath);
  if (dump.exists()) dump.delete();
  GZIPOutputStream gzOut = new GZIPOutputStream(
      new BufferedOutputStream(new FileOutputStream(dump)));
  ObjectOutput out = new ObjectOutputStream(gzOut);
  o.writeExternal(out);
  gzOut.finish();
  out.close();
}


/**
 * It adds {@link java.lang.Comparable} objects (of any type) to the given list. The list will be
 * always ordered according to the natural ordering of the items. No duplicates are allowed in the
 * list, thus no addition occurs if an item is already in the list.<br>
 * No type checking on the objects being added is performed. Thus the caller must be sure that the
 * items being added are consistent with respect to their mutual comparison.
 *
 * @param set
 *          The list that hosts the items
 * @param item
 *          The object to be added
 * @return The [0, set.size()) index of the item in the List.
 */
@SuppressWarnings({ "unchecked", "rawtypes" })
public static int addUnique(List set, Comparable item) {
  int i = Collections.binarySearch(set, item);
  if (i < 0) {
    i = -i - 1;
    set.add(i, item);
  }
  return i;
}


/**
 * It gets a object from a given list, that matches the given target. The object is cast to the
 * runtime class of the variable to which is assigned to, with NO type check. The list must be
 * ordered according to the natural ordering of the items. If the list contains duplicates, the
 * element returned is the one that would be found by {@link java.util.Collections#binarySearch}.<br>
 * No type checking on the argument to search for is performed. Thus the caller must be sure that
 * the arguments are mutually comparable.
 *
 * @param list
 *          The list that hosts the items
 * @param target
 *          An object comparable with the elements in the list
 * @return The object in the list that matches target, or null.
 */
@SuppressWarnings({ "rawtypes", "unchecked" })
public static <T> T getElement(List list, Comparable target) {
  int i = Collections.binarySearch(list, target);
  if (i >= 0) return (T)list.get(i);
  else
    return null;
}


static MersenneTwister rand = null;


/**
 * It shuffles the first 'size' elements of the provided array.
 * @param a
 *          Array to shuffle
 * @param size
 *          Number of elements to shuffle, starting from the first.
 */
public static <T> void shuffle(T[] a, int size) {
  T temp;
  int ran;
  if (rand == null) rand = new MersenneTwister(123456789);
  for (int i = size - 1; i > 0; i--) {
    ran = rand.nextInt(i + 1);
    temp = a[ran];
    a[ran] = a[i];
    a[i] = temp;
  }
}


/**
 * It provides the printout of the given data in the given output stream. If the argument is an
 * array, print one element per line, each line starting with the array index of the element. It
 * does not handle Interfaces and Enums.
 *
 * @param data
 *          Data to be printed
 * @param trailer
 *          A string that will always be printed after the data
 * @param out
 *          Stream in which the data printout must be written
 */
@SuppressWarnings({ "unchecked", "rawtypes" })
static public void printAny(Object data, String trailer, PrintStream out) {
  int size, i = 0;
  if (data == null) out.print("NULL");
  else if (data instanceof Map) {
    Entry ec = null;
    Iterator ecit = ((Map)data).entrySet().iterator();
    while (ecit.hasNext()) { // && i++ < 3
      ec = (Map.Entry)ecit.next();
      printAny(ec.getKey(), " :\n", out);
      printAny(ec.getValue(), "\n------------------------------\n", out);
    }
  } else if (data instanceof List) {
    List<Object> a = (List<Object>)data;
    size = a.size();
    for (i = 0; i < size; i++) { // && i < 5
      out.print(" entry # " + i + " : ");
      printAny(a.get(i), "\n", out);
    }
  } else if (data.getClass().isArray()) {
    Object e;
    size = Array.getLength(data);
    for (i = 0; i < size; i++) { // && i < 5
      e = Array.get(data, i);
      out.print(" [" + i + "] ");
      printAny(e, "\n", out);
    }
  } else if (data.getClass().isPrimitive()) out.print(data);
  else if (!(data.getClass().isEnum() || data.getClass().isInterface())) out.print(data.getClass().cast(data).toString());
  else out.println("\nERROR : cannot print " + data.getClass().toString() + " !");
  out.print(trailer);
  out.flush();
}


static BufferedReader in = null;


public static void waitForUser(String toprint) {
  String r = "";
  System.err.println(toprint);
  try {
    if (in == null) in = new BufferedReader(new InputStreamReader(System.in, "UTF-8"));
    while (!r.equals("y")) {
      System.out.print("May I go on, sir ? ");
      r = in.readLine().trim();
    }
  }
  catch (Exception e) {
    e.printStackTrace();
  }
}

}
