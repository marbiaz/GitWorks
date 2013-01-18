import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.eclipse.jgit.util.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand.ListMode;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevFlag;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.treewalk.TreeWalk;


public class GitMiner {

public static String prefix = "JGIT_"; // to be prepended to any jgit-generated output file name
public static String field_sep = "    "; // field separator in input datafile's lines
public static String id_sep = "/"; // the string that separates owner and name in a fork id string
public static String list_sep = ","; // fork id separator in the list taken from the input dataset
                                     // file
public static String log_sep = "<#>"; // field separator within a git log output line
public static String repo_dir; // the absolute path to the dir that contains the git repos to be
                               // imported in jgit data structures
public String[] ids = null; // list of repos to be considered to build the fork tree and
                              // perform analysis.
public String gits_out_dir; // the relative path to the dir which will contain the
                             // jgit-generated git repos to analyse
public String trees_out_dir; // the relative path to the dir which will contain the
                              // jgit-generated trees of the repos

boolean anew = false; // flag to differentiate tests
boolean bare = false; // flag to differentiate tests

HashMap<String, ArrayList<ObjectId>> commitsInR;
HashMap<String, ArrayList<Ref>> branches;
HashMap<String, ArrayList<ObjectId>> commitsNotInR;
HashMap<String, ArrayList<ObjectId>> commitsOnlyInR;
HashMap<String, ArrayList<ObjectId>> commitsInB;
int bSize;
Git git;

// this operator requires a Git object as parameter
static DfsOperator addAsRemote = new DfsOperator() {

  public int getID() {
    return 2;
  }


  public boolean runOnce() {
    return true;
  }


  public void initialize(ForkEntry fe) {}


  public void run(ForkEntry fe, Object arg) throws Exception {
    Git git = (Git)arg;
    RefSpec all;
    String fork = getProjectNameAsRemote(fe);
    // System.out.print("Adding " + fork + " as mainline ...");
    // System.out.flush();
    StoredConfig config = git.getRepository().getConfig();
    config.setString("remote", fork, "url", "file:///" + getProjectPath(fe));
    config.setString("remote", fork, "fetch", "+refs/heads/*:refs/remotes/" + fork + "/*");
    config.save();
    all = new RefSpec(config.getString("remote", fork, "fetch"));
    git.fetch().setRemote(fork).setRefSpecs(all).call();
    // System.out.println(" done!");
    // System.out.flush();
  }


  public void finalize(ForkEntry fe) {}
};


// TODO: factorize dfsVists
static void dfsVisit(int depth, ForkEntry f, DfsOperator t, ForkList l) throws Exception {
  if (t == null) {
    System.err.println("WARNING: dfsVisit called with null operator.");
    return;
  }
  if (f == null) {
    System.err.println("WARNING: DfsOperator " + t.getID() + " called on a null instance.");
    return;
  }
  if (l == null) {
    System.err.println("WARNING: DfsOperator " + t.getID() + " called with a null argument.");
    return;
  }
  if (depth > 0 && f.hasForks()) {
    t.initialize(f);
    Iterator<ForkEntry> it = f.getForks();
    while (it.hasNext()) {
      dfsVisit(depth - 1, it.next(), t, l);
      if (!t.runOnce()) t.run(f, l);
    }
    if (t.runOnce()) t.run(f, l);
  } else {
    t.run(f, l);
  }
  t.finalize(f);
}


static void dfsVisit(int depth, ForkEntry f, DfsOperator t, Git git) throws Exception {
  if (t == null) {
    System.err.println("WARNING: dfsVisit called with null operator.");
    return;
  }
  if (f == null) {
    System.err.println("WARNING: DfsOperator " + t.getID() + " called on a null instance.");
    return;
  }
  if (git == null) {
    System.err.println("WARNING: DfsOperator " + t.getID() + " called with a null argument.");
    return;
  }
  if (depth > 0 && f.hasForks()) {
    t.initialize(f);
    Iterator<ForkEntry> it = f.getForks();
    while (it.hasNext()) {
      dfsVisit(depth - 1, it.next(), t, git);
      if (!t.runOnce()) t.run(f, git);
    }
    if (t.runOnce()) t.run(f, git);
  } else {
    t.run(f, git);
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


TreeWalk makeTree(RevWalk walk, AnyObjectId ref) throws Exception {

  TreeWalk treeWalk = new TreeWalk(walk.getObjectReader());
  // treeWalk.setRecursive(true);
  treeWalk.addTree(walk.parseTree(walk.parseAny(ref)));
  walk.reset();
  return treeWalk;
}


// checkout from a given jgit tree pointer
void createTree(ForkEntry fe, TreeWalk treeWalk) throws Exception {

  String path;
  ObjectReader reader = treeWalk.getObjectReader();
  ObjectLoader loader = null;
  PrintStream pout = null;
  ObjectId objId;
  PrintWriter perr = null;

  // System.out.println("Getting into a new tree of depth " + treeWalk.getDepth());
  while (treeWalk.next()) {
    // for (int k = 0; k < treeWalk.getTreeCount(); k++) {
    objId = treeWalk.getObjectId(0); // k 0
    path = treeWalk.isRecursive() ? treeWalk.getNameString() : treeWalk.getPathString();
    try {
      loader = reader.open(objId);
    }
    catch (Exception e) {
      if (perr == null)
        perr = new PrintWriter(new FileWriter(trees_out_dir + "/" + getProjectNameAsRemote(fe)
            + "/" + prefix + "errors.log"), true);
      // e.printStackTrace(perr);
      if (objId.equals(ObjectId.zeroId()))
        perr.println("Object " + objId.getName() + " (" + path + ") is all-null!");
      else
        perr.println("Object " + objId.getName() + " (" + path + ") does not exist!");
      continue;
    }
    if (loader.getType() == Constants.OBJ_BLOB) {
      pout = new PrintStream(new FileOutputStream(new File(trees_out_dir + "/"
          + getProjectNameAsRemote(fe) + "/" + path), false), true);
      loader.copyTo(pout);
      pout.close();
    } else if (treeWalk.isSubtree()) { // loader.getType() == Constants.OBJ_TREE
      if ((new File(trees_out_dir + "/" + getProjectNameAsRemote(fe) + "/" + path)).mkdirs()) {
        treeWalk.enterSubtree();
        // System.out.println("Getting into a new tree of depth " + treeWalk.getDepth());
      } else {
        if (perr == null)
          perr = new PrintWriter(new FileWriter(trees_out_dir + "/" + getProjectNameAsRemote(fe)
              + "/" + prefix + "errors.log"), true);
        perr.println("Dir " + path + "(Object " + objId.getName() + ") could not be created!");
      }
    }
    // }
  }
  treeWalk.reset();
}


// printout all commit messages in a given range -> use and reset an existing RevWalk
void printCommits(String outFile, RevWalk walk) throws IOException, NoHeadException,
    GitAPIException {

  PrintWriter pout = new PrintWriter(new FileWriter(outFile), true);
  RevCommit c = walk.next();
  while (c != null && !c.has(RevFlag.UNINTERESTING)) {
    pout.println(printCommit(c));
    c = walk.next();
  }
  walk.reset();
  pout.close();
}


// printout all commit messages in a given range -> expensive: creates a one-time only RevWalk
void printCommits(String outFile, String from_ref, String to_ref)
    throws IOException, NoHeadException, GitAPIException {

  AnyObjectId from = git.getRepository().resolve(from_ref);
  AnyObjectId to = (to_ref == null || to_ref.equals("")) ? null : git.getRepository().resolve(
      to_ref);
  RevWalk walk = new RevWalk(git.getRepository());
  // walk.sort(RevSort.COMMIT_TIME_DESC);
  // walk.sort(RevSort.REVERSE);
  walk.markStart(walk.parseCommit(from));
  if (to != null) walk.markUninteresting(walk.parseCommit(to));

  printCommits(outFile, walk);
  walk.dispose();
  // PrintWriter pout = new PrintWriter(new FileWriter(outFile), true);
  // Iterator<RevCommit> itc = git.log().add(from).call().iterator();
  // RevCommit c;
  // while (itc.hasNext()) {
  // c = itc.next();
  // pout.println("===========\n" + printCommit(c));
  // if (to != null && c.equals(to)) break;
  // }
  // pout.close();
}


// format the output like in --pretty="%H<#>%aN<#>%at<#>%cN<#>%ct<#>%s
String printCommit(RevCommit c) {
  String out = "";
  PersonIdent author = c.getAuthorIdent();
  PersonIdent committer = c.getCommitterIdent();
  // long commitTime = (long)c.getCommitTime() == committer.getWhen().getTime() / 1000 (in seconds)
  out += "" + c.name()
      + log_sep + author.getName() + log_sep + author.getWhen().getTime()
      + log_sep + committer.getName() + log_sep + committer.getWhen().getTime()
      + log_sep + c.getShortMessage(); //c.getFullMessage(); c.getShortMessage();
  return out;
}


// print some structural info about the git repo
String printRepoInfo() {

  String out = "Current GIT_DIR : " + git.getRepository().getDirectory().getAbsolutePath() + "\n";
  StoredConfig config = git.getRepository().getConfig();
  Set<String> sections = config.getSections();
  Set<String> subsections;
  out += "This repository has " + sections.size() + " sections:\n";
  for (String s : sections) {
    out += s + " : <";
    subsections = config.getSubsections(s);
    for (String ss : subsections)
      out += ss + "  ";
    out += ">\n";
  }
  return out;
}


// import a git repo in jgit data structures or create a new one
Repository createRepo(String repoDir, String gitDir)
    throws IOException {

  File gd = new File(gitDir);
  File rd = new File(repoDir);
  if (anew) {
    if (rd.exists()) FileUtils.delete(rd, FileUtils.RECURSIVE);
    rd.mkdirs();
    if (gd.exists()) FileUtils.delete(gd, FileUtils.RECURSIVE);
    gd.mkdirs();
  }
  Repository repository = new FileRepositoryBuilder().setWorkTree(rd).setGitDir(gd)
      .readEnvironment() // scan environment GIT_* variables
      .findGitDir() // scan up the file system tree
      .setMustExist(!anew).build();
  if (anew) repository.create(bare);

  return repository;
}


// Returns the project ID formatted in a convenient way to serve as a remote name...
static String getProjectNameAsRemote(ForkEntry f) {
  return f.getId().replace("/", "--");
}


// It gives the absolute path (internal URI) of the repo corresponding to the given ForkEntry.
static String getProjectPath(ForkEntry f) {
  String t[] = f.getId().split(id_sep);
  return GitMiner.repo_dir + t[1] + "/" + t[0] + "/" + t[1] + ".git";
}


// add remotes to a jgit repo, using a given ForkEntry data structure
void addRemotes(Git git, ForkEntry project, int depth) throws Exception {
  dfsVisit(depth, project, GitMiner.addAsRemote, git);
  git.getRepository().scanForRepoChanges();
}


// as of now, it is meant to compute things in the big fork tree of each project, so that for forks
// at different layers the computed aggregation depth is parent's one - 1.
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


// delete from the children ForkList of the argument all the entries whose repo cannot be found in
// the local FS.
static void purgeMissingForks(ForkList globalList, ForkEntry f) throws Exception {
  File fi;
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
    tokens = line.split(field_sep);
    if (ForkEntry.isValidId(tokens[1] + id_sep + tokens[0])) {
      cc = l.add(new ForkEntry(tokens[1], tokens[0], Integer.valueOf(tokens[3])));
      if (cc < 0) {
        children.add(-cc - 1, tokens.length == 5 ? tokens[4] : "");
      } else {
        System.err.println("WARNING: duplicate entry in input file (" + tokens[1] + id_sep
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
      tokens = children.get(i).split(list_sep);
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


// depending on getBody, the runtime type of the results will be either RevCommit or Commit
ArrayList<RevCommit> findCommits(RevWalk walk, ArrayList<RevCommit> included,
    ArrayList<RevCommit> excluded, boolean getBody) throws MissingObjectException,
    IncorrectObjectTypeException, IOException {
  ArrayList<RevCommit> commits = new ArrayList<RevCommit>();
  walk.markStart(included);
  RevCommit c;
  Iterator<RevCommit> it = excluded.iterator();
  while (it.hasNext()) {
    walk.markUninteresting(it.next());
  }
  it = walk.iterator();
  while (it.hasNext()) {
    c = it.next();
    if (getBody) walk.parseBody(c);
    // addUnique(commits, c); // commits are naturally ordered by SHA-1
    commits.add(getBody == false ? c : Commit.parse(c.getRawBuffer()));
  }
  walk.reset();
  return commits.size() > 0 ? commits : null;
}


int buildBranchesMap() throws GitAPIException {

  if (branches != null) {
    System.err.println("The map of the branches has already been built!!!");
    return bSize;
  }
  branches = new HashMap<String, ArrayList<Ref>>();

  ArrayList<Ref> temp = null;
  Ref r;
  int res = 0;
  Iterator<Ref> allBranches = ((ArrayList<Ref>)git.branchList().setListMode(ListMode.REMOTE).call())
      .iterator();
  String bName = "";
  while (allBranches.hasNext()) {
    r = allBranches.next();
    if (!("refs/remotes/" + r.getName().split("/")[2]).equals(bName)) {
      bName = "refs/remotes/" + r.getName().split("/")[2]; // geName() format is
                                                           // refs/remotes/<remote-name>/<branch-name>
      temp = new ArrayList<Ref>();
      branches.put(bName, temp);
    }
    temp.add(r);
    res++;
  }
  // Iterator<ArrayList<Ref>> rit = map.values().iterator();
  // Iterator<String> sit = map.keySet().iterator();
  // while (rit.hasNext()) {
  // System.out.println("\t" + sit.next() + ":\n" + printArray(rit.next().toArray()));
  // }
  return res;
}


HashMap<ObjectId, ArrayList<Ref>> findAllBranches() throws Exception {
  HashMap<ObjectId, ArrayList<Ref>> structure = new HashMap<ObjectId, ArrayList<Ref>>();
  ArrayList<Ref> vals; Iterator<Ref> brIt;
  Iterator<ArrayList<Ref>> bit; Entry<ObjectId, ArrayList<Ref>> re; Ref r; ObjectId k;
  Iterator<Entry<ObjectId, ArrayList<Ref>>> sit;
  Iterator<RevCommit> allIn = git.log().all().call().iterator();
  RevWalk walk = new RevWalk(git.getRepository());
  walk.setRetainBody(false); // this walk is for multiple usage
  while (allIn.hasNext()) {
    vals = new ArrayList<Ref>();
    vals.ensureCapacity(bSize);
    structure.put(allIn.next().getId(), vals);
  }
  sit = structure.entrySet().iterator();
  while (sit.hasNext()) {
    re = sit.next();
    k = re.getKey();
    vals = re.getValue();
    bit = branches.values().iterator();
    while (bit.hasNext()) {
      brIt = bit.next().iterator();
      while (brIt.hasNext()) {
        r = brIt.next();
        if (walk.isMergedInto(walk.parseCommit(k),
            walk.parseCommit(r.getObjectId()))) {
          vals.add(r);
        }
      }
    }
    vals.trimToSize();
    //System.out.print(re.getKey().getName() + " :\n" + printArray(re.getValue().toArray()));
  }
  walk.dispose();
  return structure;
}


// find commits that are (only?) in each remote XXX
HashMap<String, ArrayList<ObjectId>> getCommitsInR(RevWalk walk, boolean only)
    throws MissingObjectException, IncorrectObjectTypeException, IOException {

  Iterator<Ref> brIt;
  HashMap<String, ArrayList<ObjectId>> comms = new HashMap<String, ArrayList<ObjectId>>();
  ArrayList<RevCommit> comm;
  ArrayList<ObjectId> ids;
  ArrayList<RevCommit> included = new ArrayList<RevCommit>();
  ArrayList<RevCommit> excluded = new ArrayList<RevCommit>();
  Entry<String,ArrayList<Ref>> er; String r;
  Iterator<Entry<String,ArrayList<Ref>>> erit;
  Iterator<String> sit = branches.keySet().iterator();
  if (only) excluded.ensureCapacity(bSize - 1);
  while (sit.hasNext()) {
    r = sit.next();
    erit = branches.entrySet().iterator();
    while (erit.hasNext()) {
      er = erit.next();
      brIt = er.getValue().iterator();
      if (er.getKey().equals(r)) {
        while (brIt.hasNext()) {
          included.add(walk.parseCommit(brIt.next().getObjectId()));
        }
      } else if (only) {
        while (brIt.hasNext()) {
          excluded.add(walk.parseCommit(brIt.next().getObjectId()));
        }
      }
    }
    comm = findCommits(walk, included, excluded, !only);
    if (only) {
      // TODO add to a global ArrayList with all Commits (call findAllBranches)
    } else {
      ids = comm != null ? getIds(comm.toArray(new RevObject[0])) : null;
      comms.put(r, ids);
    }
    included.clear();
    excluded.clear();
  }
  included.trimToSize();
  included.ensureCapacity(50);
  excluded.trimToSize();
  excluded.ensureCapacity(50);

  return comms;
}


// find commits that are not in a given remote
HashMap<String, ArrayList<ObjectId>> getCommitsNotInR(RevWalk walk) throws MissingObjectException,
    IncorrectObjectTypeException, IOException {
  Iterator<Ref> brIt;
  HashMap<String, ArrayList<ObjectId>> commits = new HashMap<String, ArrayList<ObjectId>>();
  ArrayList<RevCommit> comm;
  ArrayList<ObjectId> ids;
  ArrayList<RevCommit> included = new ArrayList<RevCommit>();
  ArrayList<RevCommit> excluded = new ArrayList<RevCommit>();

  Entry<String, ArrayList<Ref>> er;
  String r;
  Iterator<Entry<String, ArrayList<Ref>>> erit;
  Iterator<String> sit = branches.keySet().iterator();
  included.ensureCapacity(bSize - 1);
  while (sit.hasNext()) {
    r = sit.next();
    erit = branches.entrySet().iterator();
    while (erit.hasNext()) {
      er = erit.next();
      brIt = er.getValue().iterator();
      if (er.getKey().equals(r)) {
        while (brIt.hasNext()) {
          excluded.add(walk.parseCommit(brIt.next().getObjectId()));
        }
      } else {
        while (brIt.hasNext()) {
          included.add(walk.parseCommit(brIt.next().getObjectId()));
        }
      }
    }
    comm = findCommits(walk, included, excluded, false);
    ids = comm != null ? getIds(comm.toArray(new RevObject[0])) : null;
    commits.put(r, ids);
    included.clear();
    excluded.clear();
  }
  included.trimToSize();
  included.ensureCapacity(50);
  excluded.trimToSize();
  excluded.ensureCapacity(50);

  return commits;
}


// find commits that are unique to a given branch
HashMap<String, ArrayList<ObjectId>> getCommitsInB(RevWalk walk) throws MissingObjectException,
    IncorrectObjectTypeException, IOException {

  Iterator<Ref> brIt;
  HashMap<String, ArrayList<ObjectId>> commits = new HashMap<String, ArrayList<ObjectId>>();
  ArrayList<RevCommit> comm;
  ArrayList<ObjectId> ids;
  ArrayList<RevCommit> included = new ArrayList<RevCommit>();
  ArrayList<RevCommit> excluded = new ArrayList<RevCommit>();
  // excluded.add(walk.parseCommit(git.getRepository().resolve("ajaxorg--ace/anchor"))); // refspec
  // included.add(walk.parseCommit(git.getRepository().resolve("ajaxorg--ace/issue-42"))); // ui/optimize-experimental
  // included.add(walk.parseCommit(git.getRepository().resolve("ajaxorg--ace/concorde"))); // fix/search_ui

  ArrayList<Ref> allBranches = new ArrayList<Ref>();
  ArrayList<Ref> temp = new ArrayList<Ref>();
  Iterator<ArrayList<Ref>> cit = branches.values().iterator();
  allBranches.ensureCapacity(bSize);
  while (cit.hasNext()) {
  allBranches.addAll(cit.next());
  }
  temp.ensureCapacity(bSize - 1);
  excluded.ensureCapacity(bSize - 1);
  for (int i = 0; i < allBranches.size(); i++) {
  temp.clear();
  if (i > 0) temp.addAll(allBranches.subList(0, i));
  temp.addAll(allBranches.subList(i + 1, allBranches.size()));
  included.add(walk.parseCommit(allBranches.get(i).getObjectId()));
  brIt = temp.iterator();
  while (brIt.hasNext()) {
  excluded.add(walk.parseCommit(brIt.next().getObjectId()));
  }
  comm = findCommits(walk, included, excluded, false);
  ids = comm != null ? getIds(comm.toArray(new RevObject[0])) : null;
  commits.put(allBranches.get(i).getName(), ids);
  included.clear();
  excluded.clear();
  }
  included.trimToSize();
  included.ensureCapacity(50);
  excluded.trimToSize();
  excluded.ensureCapacity(50);


  return commits;
}
ArrayList<ObjectId> getIds(RevObject[] a) {
  if (a == null) return null;
  ArrayList<ObjectId> res = new ArrayList<ObjectId>();
  res.ensureCapacity(a.length);
  for (RevObject i : a) {
    res.add(i.copy());
  }
  return res;
}


void analyzeForkTree(String[] args) throws Exception {

  ForkList projects;
  ForkEntry fe;
  RevWalk walk = null;

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

  projects = populateForkList(args[0].trim());
  //projects = importForkList(trees_out_dir + "listDump");
  computeAggregates(ids, projects, 100); // with a large param value the complete fork trees will be
                                         // visited
  exportForkList(trees_out_dir + "listDump", projects);
  // computeAggregates(null, projects, 1); // reset all projects aggregates
  System.out.println(projects.toString());

  /************** create/load git repo ****************/

  fe = projects.get(ids[0]);
  String gitDirPath = gits_out_dir + getProjectNameAsRemote(fe)
      + ((bare == true) ? ".git" : "/.git");
  String treeDirPath = trees_out_dir + getProjectNameAsRemote(fe);
  String refspec = "refs/remotes/" + getProjectNameAsRemote(fe) + "/master";
  try {
    // with git.init() it is not possible to specify a different tree path!!
    // git = Git.init().setBare(bare).setDirectory(new File(gitDirPath)).call();
    git = Git.wrap(createRepo(treeDirPath, gitDirPath));
     System.out.println(printRepoInfo());

    /************** create big tree ****************/
    if (!anew) {
      purgeMissingForks(projects, fe); // IRREVERSIBLE!!!
      addRemotes(git, fe, 0); // with a large param value the complete fork tree will be built
    }

    /************** print commits & checkout ****************/

     printCommits(trees_out_dir + prefix + getProjectNameAsRemote(fe) + "-commitList.log",
         refspec, null);
    if (!bare) {
      git.checkout().setStartPoint(refspec).setCreateBranch(anew)
          .setName(getProjectNameAsRemote(fe)).call(); // .getResult() for a dry-run
      // createTree(fe, makeTree(walk, from));
    }

    /************** build a map with all the branches in the big tree ***************/

    bSize = buildBranchesMap();
    // find all branches that contain a given commit
    HashMap<ObjectId, ArrayList<Ref>> structure = findAllBranches(git, branches, bSize);
    //Entry<ObjectId, ArrayList<Ref>> re;
    //Iterator<Entry<ObjectId, ArrayList<Ref>>> sit;
    //sit = structure.entrySet().iterator();
    //while (sit.hasNext()) {
    //  re = sit.next();
    //  System.out.print(re.getKey().getName() + " :\n" + printArray(re.getValue().toArray()));
    //}

    /************** find interesting commits ***************/

    walk = new RevWalk(git.getRepository());
    // walk.setRetainBody(false); // this walk is for multiple usage
    // walk.sort(RevSort.COMMIT_TIME_DESC);
    // walk.sort(RevSort.TOPO);
    // walk.sort(RevSort.NONE);

    commitsOnlyInR = getCommitsInR(walk, true); // XXX
    commitsInR = getCommitsInR(walk, false);
    commitsNotInR = getCommitsNotInR(walk);
    commitsInB = getCommitsInB(walk);

  }
  catch (Exception e) {
    e.printStackTrace();
  }
  finally {
    if (walk != null) walk.dispose();
    if (git != null) git.getRepository().close();
  }
}


static void main(String[] args) throws Exception {

  GitMiner gm = new GitMiner();
  gm.analyzeForkTree(args);
}


static void exportForkList(String filePath, ForkList l) throws IOException {
  File dump = new File(filePath);
  if (dump.exists()) dump.delete();
  GZIPOutputStream gzOut = new GZIPOutputStream(
      new BufferedOutputStream(new FileOutputStream(dump)));
  ObjectOutput out = new ObjectOutputStream(gzOut);
  l.writeExternal(out);
  gzOut.finish();
  out.close();
}


static ForkList importForkList(String filePath) throws FileNotFoundException, IOException,
    ClassNotFoundException {
  ForkList res = new ForkList();
  ObjectInput in = new ObjectInputStream(new GZIPInputStream(new BufferedInputStream(
      new FileInputStream(filePath))));
  res.readExternal(in);
  return res;
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
 */
@SuppressWarnings({ "unchecked", "rawtypes" })
static private void addUnique(List set, Comparable item) {
  int i = Collections.binarySearch(set, item);
  if (i < 0) {
    set.add(-i - 1, item);
  }
}


/**
 * It provides the printout of all the objects in the given array, one per line, each line starting
 * with the array index of the object.
 * 
 * @param a
 *          array of objects to be printed
 * @return A String containing the objects printout.
 */
static public String printArray(Object[] a) {
  String res = "";
  if (a == null) {
    res = "\nNULL!\n";
  } else {
    for (int i = 0; i < a.length; i++) {
      res += "[" + i + "] " + a[i].toString() + "\n";
    }
  }
  return res;
}

}
