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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;


public class GitWorks {

public static boolean anew = false; // flag to differentiate tests
public static boolean bare = false; // flag to differentiate tests

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


//Returns the project ID formatted in a convenient way to serve as a remote name...
static String getProjectNameAsRemote(ForkEntry f) {
return f.getId().replace("/", "--");
}


//It gives the absolute path (internal URI) of the repo corresponding to the given ForkEntry.
static String getProjectPath(ForkEntry f) {
String t[] = f.getId().split(GitWorks.id_sep);
return GitWorks.repo_dir + t[1] + "/" + t[0] + "/" + t[1] + ".git";
}


//as of now, it is meant to compute things in the big fork tree of each project, so that for forks
//at different layers the computed aggregation depth is parent's one - 1.
static void computeAggregates(String ids[], ForkList fl, int depth) throws Exception {
if (fl.size() == 0 || depth < 1) {
 System.err.println("computeAggregates : input ERROR.");
 return;
}
int[] r = new int[4];
if (ids == null || ids.length == 0) {
 ids = new String[fl.size()];
 for (int i = 0; i < fl.size(); i++) {
   ids[i] = fl.get(i).getId();
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


//delete from the children ForkList of the argument all the entries whose repo
//cannot be found in the local FS.
static void purgeMissingForks(ForkList globalList, ForkEntry f) throws Exception {
File fi;
if (!f.hasForks()) return;
int c = 0; // String out = "";
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

while ((line = listFile.readLine()) != null) {
 c++;
 tokens = line.split(GitWorks.field_sep);
 if (ForkEntry.isValidId(tokens[1] + GitWorks.id_sep + tokens[0])) {
   cc = l.add(new ForkEntry(tokens[1], tokens[0], Integer.valueOf(tokens[3])));
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
Iterator<ForkEntry> it = l.getAll();
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
        .println("Usage: java GitMiner <repo list file path> <repo dir path> <jgit gits out dir> <jgit trees out dir> <comma-separated no-space list of fork ids>");
    System.exit(2);
  }
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

  try {

    BufferedReader in = new BufferedReader(new InputStreamReader(System.in, "UTF-8"));
    String r = "";

    importForkList(trees_out_dir + "forkListDump");
//    projects = populateForkList(args[0].trim());
//    computeAggregates(ids, projects, 100); // with a large param value the complete fork trees will be visited
//    exportForkList(trees_out_dir + "forkListDump");
    // computeAggregates(null, projects, 1); // reset all projects aggregates
    // System.out.println(projects.toString());

    /************** build and analyze fork trees ****************/

    fe = projects.get(ids[0]);

    if (anew) {
      purgeMissingForks(projects, fe); // IRREVERSIBLE!!!
    }

    r = ""; while (!r.equals("y")) { System.out.print("May I go on, sir ? "); r = in.readLine().trim(); }

    gitMiners = new GitMiner[1];

    for (int i = 0; i < gitMiners.length; i++) {
//    gitMiners[i] = importGitMiner(trees_out_dir + getProjectNameAsRemote(fe) + ".dump" + i);
      gitMiners[i] = new GitMiner(getProjectNameAsRemote(fe));
      gitMiners[i].analyzeForkTree(fe); System.gc(); Thread.sleep(1000);

      r = ""; while (!r.equals("y")) { System.out.print("May I go on, sir ? "); r = in.readLine().trim(); }
    }

    for (int i = 0; i < gitMiners.length; i++) {
//      gitMiners[i].commitsInB = null; System.gc(); Thread.sleep(1000);
      exportGitMiner(gitMiners[i], trees_out_dir + gitMiners[i].name + ".dump" + i);

      r = ""; while (!r.equals("y")) { System.out.print("May I go on, sir ? "); r = in.readLine().trim(); }
    }

  }
  catch (Exception e) {
    // TODO Auto-generated catch block
    e.printStackTrace();
  }
}

// TODO factorize import/export methods
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

}
