package gitworks;


import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;


public class GitWorks {

public static boolean anew = true; // (re-)create a forktree anew
public static boolean bare = true; // the forktree is a bare git repo

public static String prefix = "JGIT_"; // to be prepended to any jgit-generated output file name
public static String field_sep = "    "; // field separator in input datafile's lines
public static String id_sep = "/"; // the string that separates owner and name in a fork id string
public static String list_sep = ","; // fork id separator in the list taken from the input file
public static String log_sep = "<#>"; // field separator within a git log output line
public static String repo_dir; // the absolute path to the dir that contains the git repos to be
                               // imported in jgit data structures
public static String gits_out_dir; // the relative path to the dir which will contain the
                                    // jgit-generated git repos to analyse
public static String trees_out_dir; // the relative path to the dir which will contain the
                                     // jgit-generated trees of the repos
public static String pwd; // set according to the current pwd

static String[] ids = null; // list of root repos to be considered to build the fork trees and perform analysis.
static ForkList projects;
static GitMiner[] gitMiners;


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
  } else {
    t.run(f, o);
  }
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
  } else {
    t.run(f, t_arg);
  }
  t.finalize(f);
}


// Returns the project ID formatted in a convenient way to serve as a remote name...
static String getProjectNameAsRemote(ForkEntry f) {
  return f.getId().replace("/", "--");
}


// It gives the absolute path (internal URI) of the repo corresponding to the given ForkEntry.
static String getProjectPath(ForkEntry f) {
  String t[] = f.getId().split(GitWorks.id_sep);
  return GitWorks.repo_dir + t[0] + "/" + t[1] + ".git"; // t[1] + "/" + t[0] + "/" + t[1] + ".git";
}


// as of now, it is meant to compute things in the big fork tree of each project, so that for forks
// at different layers the computed aggregation depth is parent's one - 1.
// with a large depth param value the complete fork trees will be visited
static void computeAggregates(String ids[], ForkList fl, int depth) throws Exception {
  if (fl.size() == 0 || depth < 1) {
    System.err.println("computeAggregates : input ERROR.");
    return;
  }
  int i = 0, r[] = new int[5];
  if (ids == null || ids.length == 0) {
    ids = new String[fl.howManyTrees()];
    for (ForkEntry f : fl) {
      if (f.isRoot()) ids[i++] = f.getId();
    }
  }
  for (String id : ids) {
    if (!ForkEntry.isValidId(id)) {
      System.err.println("computeAggregates : input ERROR (invalid id: " + id + ").");
      continue;
    }
    Arrays.fill(r, 0);
    dfsVisit(depth, fl.get(id), ForkEntry.computeAggregates, r);
  }
}


// delete from the children ForkList of the argument all the entries whose repo
// cannot be found in the local FS.
static void purgeMissingForks(ForkList globalList, ForkEntry f) throws Exception {
  File fi;
  if (!f.hasForks()) return;
  int c = 0;
//  String out = "";
  Iterator<ForkEntry> it = f.getForks();
  ForkEntry fe, fks[] = new ForkEntry[f.howManyForks()];
  while (it.hasNext()) {
    fe = it.next();
    fi = new File(getProjectPath(fe));
    if (!fi.canRead()) {
      fks[c++] = fe;
//      out += " " + fe.getId();
      globalList.remove(fe); // remove fe from the main projects list (no dangling entries)!
    }
  }
//  System.out.print("Deleting missing repos entries from the lists (" + out + " ) ... ");
  f.removeForks(Arrays.copyOf(fks, c));
//  System.out.println("done!");
}


static ForkList populateForkList(String inputFile) throws Exception {

  ForkEntry fe, fc;
  String line, tokens[];
  int c = 0, cc = 0;
  ArrayList<String> children = new ArrayList<String>();
  BufferedReader listFile = new BufferedReader(
      new InputStreamReader(new FileInputStream(inputFile)));
  ForkList l = new ForkList();

  while ((line = listFile.readLine()) != null) {
    c++;
    tokens = line.split(GitWorks.field_sep);
    if (ForkEntry.isValidId(tokens[1] + GitWorks.id_sep + tokens[0])) {
      cc = l.add(new ForkEntry(tokens[1], tokens[0], tokens[3].equalsIgnoreCase("nan") ? -1
          : Integer.valueOf(tokens[3])));
      if (cc < 0) {
        children.add(-cc - 1, tokens.length == 5 ? tokens[4] : "");
      } else {
        System.err.println("WARNING: duplicate entry in input file (" + tokens[1] + GitWorks.id_sep
            + tokens[0] + ").");
      }
    } else {
      System.err.println("Error while reading fork data from file, at line " + c + ".");
    }
  }
  listFile.close();
  Iterator<ForkEntry> it = l.iterator();
  for (int i = 0; it.hasNext(); i++) {
    fe = it.next();
    if (!"".equals(children.get(i))) {
      cc = 0;
      tokens = children.get(i).split(GitWorks.list_sep);
      for (String f : tokens) {
        cc++;
        fc = l.get(f);
        if (fc != null) {
          fe.addFork(fc);
        } else {
          System.err.println("Error while reading fork data from file, for project " + fe.getId()
              + " about fork # " + cc + " (" + f + ").");
        }
      }
    }
  }
  l.setTreeCounter();
  return l;
}


/**
 * @param args
 * @throws ClassNotFoundException 
 * @throws IOException 
 * @throws FileNotFoundException 
 */
public static void main(String[] args) throws FileNotFoundException, IOException, ClassNotFoundException {

  ForkEntry fe;

  if (args.length < 5) {
    System.err
        .println("Usage: java GitWorks <repo list file path> <repo dir path> <jgit gits out dir> <jgit trees out dir> <comma-separated no-space list of fork ids>");
    System.exit(2);
  }
  pwd = System.getenv("PWD");
  repo_dir = args[1].trim() + (args[1].trim().endsWith("/") ? "" : "/");
  ids = args[4].trim().split(",");
  gits_out_dir = args[2].trim() + (args[2].trim().endsWith("/") ? "" : "/");
  trees_out_dir = args[3].trim() + (args[3].trim().endsWith("/") ? "" : "/");
  if (!new File(repo_dir).isDirectory() || !(new File(trees_out_dir)).isDirectory()
      || !new File(gits_out_dir).isDirectory()) {
    System.err
        .println("FATAL ERROR : Cannot find repos dir (" + repo_dir + ") or gits output dir ("
            + gits_out_dir + ") or trees output dir (" + trees_out_dir + ")");
    System.exit(1);
  }

    /************** create fork list ****************/

//  BufferedReader in = new BufferedReader(new InputStreamReader(System.in, "UTF-8"));
//  String r = "";
  System.out.println("# Computation started at " + (new java.util.Date()).toString());

  try {
    importForkList(trees_out_dir + "forkListDump");
//    projects = populateForkList(args[0].trim());
//    computeAggregates(null, projects, Integer.MAX_VALUE);
//    exportForkList(trees_out_dir + "forkListDump");
//    projects.printForkTrees(System.out); // from this printout, lists of repos to retrieve can be made
//    //computeAggregates(null, projects, 1); // reset all projects aggregates

    /************** build and analyze fork trees ****************/

    gitMiners = new GitMiner[1];

    for (int j = 0; j < projects.size(); j++) {
      fe = projects.get(j); // (ids[0]) (j);
      if (!fe.isRoot()) continue;

      try {
        Runtime.getRuntime().exec(pwd + "/loadRepos.sh " + getProjectNameAsRemote(fe)).waitFor();
        if (anew) {
          purgeMissingForks(projects, fe); // IRREVERSIBLE!!!
        }

//        r = ""; while (!r.equals("y")) { System.out.print("May I go on, sir ? "); r = in.readLine().trim(); }

        for (int i = 0; i < gitMiners.length; i++) {
//          gitMiners[i] = importGitMiner(trees_out_dir + getProjectNameAsRemote(fe) + ".dump"); // + i
          gitMiners[i] = new GitMiner(getProjectNameAsRemote(fe));
          gitMiners[i].analyzeForkTree(fe);
          exportGitMiner(gitMiners[i], trees_out_dir + gitMiners[i].name + "_"  + gitMiners[i].id + ".dump");
          printAny(gitMiners[i].commitsInB, System.out); System.out.println("\n");
          gitMiners[i] = null;
          System.gc();
          Thread.sleep(1000);

//          r = ""; while (!r.equals("y")) { System.out.print("May I go on, sir ? "); r = in.readLine().trim(); }
        }
        Runtime.getRuntime().exec(pwd + "/cleanAndBackup.sh " + getProjectNameAsRemote(fe)).waitFor();
      }
      catch (InterruptedException ie) {
        System.err.println("ERROR : computation of " + getProjectNameAsRemote(fe) + " was interrupted before completion!");
      }
      catch (Exception e) {
        System.err.println("ERROR : computation of " + getProjectNameAsRemote(fe) + " was interrupted before completion!");
        e.printStackTrace();
      }
//    }
  }
  catch (Exception e) {
    e.printStackTrace();
  }
  finally {
    System.out.println("# Computation ended at " + (new java.util.Date()).toString());
  }

}


static void exportForkList(String filePath) throws IOException {
  File dump = new File(filePath);
  if (dump.exists()) dump.delete();
  GZIPOutputStream gzOut = new GZIPOutputStream(
      new BufferedOutputStream(new FileOutputStream(dump)));
  ObjectOutput out = new ObjectOutputStream(gzOut);
  projects.writeExternal(out);
  gzOut.finish();
  out.close();
}


static void importForkList(String filePath) throws FileNotFoundException, IOException,
    ClassNotFoundException {
  projects = new ForkList();
  ObjectInput in = new ObjectInputStream(new GZIPInputStream(new BufferedInputStream(
      new FileInputStream(filePath))));
  projects.readExternal(in);
  in.close();
}


static void exportGitMiner(GitMiner gm, String filePath) throws IOException {
  if (gm.id == 0) {
    System.err.println("GitWorks: WARNING : attempted serialization of an unprocessed GitMiner instance.");
    return;
  }
  File dump = new File(filePath);
  if (dump.exists()) dump.delete();
  GZIPOutputStream gzOut = new GZIPOutputStream(
      new BufferedOutputStream(new FileOutputStream(dump)));
  ObjectOutput out = new ObjectOutputStream(gzOut);
  gm.writeExternal(out);
  gzOut.finish();
  out.close();
}


static GitMiner importGitMiner(String filePath) throws IOException, ClassNotFoundException {
  ObjectInput in = new ObjectInputStream(new GZIPInputStream(new BufferedInputStream(
      new FileInputStream(filePath))));
  GitMiner gm = new GitMiner("");
  gm.readExternal(in);
  in.close();
  return gm;
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
static int addUnique(List set, Comparable item) {
  int i = Collections.binarySearch(set, item);
  if (i < 0) {
    i = -i - 1;
    set.add(i, item);
  }
  return i;
}


/**
 * It provides the printout of the given data in the given output stream.
 * If the argument is an array, print one element per line, each line starting
 * with the array index of the element.
 * It does not handle Interfaces and Enums.
 *
 * @param data
 *          Data to be printed
 * @param out
 *          Stream in which the data printout must be written
 */
@SuppressWarnings({ "unchecked", "rawtypes" })
static public void printAny(Object data, PrintStream out) {
  int size, i = 0;
  if (data == null) {
    out.print("NULL");
  } else if (data instanceof Map) {
    Entry ec = null;
    Iterator ecit = ((Map)data).entrySet().iterator();
    while (ecit.hasNext()) { // && i++ < 3
      ec = (Map.Entry)ecit.next();
      printAny(ec.getKey(), out);
      out.println(" :");
      printAny(ec.getValue(), out);
      out.println("\n------------------------------");
    }
  } else if (data instanceof List) {
    List<Object> a = (List<Object>)data;
    size = a.size();
    for (i = 0; i < size; i++) { // && i < 5
      out.print(" entry # " + i + " : ");
      printAny(a.get(i), out);
      out.println();
    }
  } else if (data.getClass().isArray()) {
    Object e;
    size = Array.getLength(data);
    for (i = 0; i < size; i++) { // && i < 5
      e = Array.get(data, i);
      out.print(" [" + i + "] ");
      printAny(e, out);
      out.println();
    }
  } else if (data.getClass().isPrimitive()) {
    out.print(data);
  } else if (!(data.getClass().isEnum() || data.getClass().isInterface())) {
    out.print((data.getClass().cast(data)).toString());
  } else {
    out.println("\nERROR : cannot print " + data.getClass().toString() + " !");
  }
  out.flush();
}

}
