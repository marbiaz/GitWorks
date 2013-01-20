import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

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

HashMap<String, ArrayList<ObjectId>> commitsInR;
HashMap<String, ArrayList<Ref>> branches;
HashMap<String, ArrayList<ObjectId>> commitsNotInR;
HashMap<String, ArrayList<ObjectId>> commitsOnlyInR;
HashMap<String, ArrayList<ObjectId>> commitsInB;
HashMap<String, ArrayList<ObjectId>> commitsOnlyInB;
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
    String fork = GitWorks.getProjectNameAsRemote(fe);
    // System.out.print("Adding " + fork + " as mainline ...");
    // System.out.flush();
    StoredConfig config = git.getRepository().getConfig();
    config.setString("remote", fork, "url", "file:///" + GitWorks.getProjectPath(fe));
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


//add remotes to a jgit repo, using a given ForkEntry data structure
void addRemotes(Git git, ForkEntry project, int depth) throws Exception {
  dfsVisit(depth, project, GitMiner.addAsRemote, git);
  git.getRepository().scanForRepoChanges();
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
        perr = new PrintWriter(new FileWriter(GitWorks.trees_out_dir + "/" + GitWorks.getProjectNameAsRemote(fe)
            + "/" + GitWorks.prefix + "errors.log"), true);
      // e.printStackTrace(perr);
      if (objId.equals(ObjectId.zeroId()))
        perr.println("Object " + objId.getName() + " (" + path + ") is all-null!");
      else
        perr.println("Object " + objId.getName() + " (" + path + ") does not exist!");
      continue;
    }
    if (loader.getType() == Constants.OBJ_BLOB) {
      pout = new PrintStream(new FileOutputStream(new File(GitWorks.trees_out_dir + "/"
          + GitWorks.getProjectNameAsRemote(fe) + "/" + path), false), true);
      loader.copyTo(pout);
      pout.close();
    } else if (treeWalk.isSubtree()) { // loader.getType() == Constants.OBJ_TREE
      if ((new File(GitWorks.trees_out_dir + "/" + GitWorks.getProjectNameAsRemote(fe) + "/" + path)).mkdirs()) {
        treeWalk.enterSubtree();
        // System.out.println("Getting into a new tree of depth " + treeWalk.getDepth());
      } else {
        if (perr == null)
          perr = new PrintWriter(new FileWriter(GitWorks.trees_out_dir + "/" + GitWorks.getProjectNameAsRemote(fe)
              + "/" + GitWorks.prefix + "errors.log"), true);
        perr.println("Dir " + path + "(Object " + objId.getName() + ") could not be created!");
      }
    }
    // }
  }
  treeWalk.reset();
}


// printout all commit messages in a given range -> use and reset an existing RevWalk
void printCommits(String outFile, RevWalk walk)
    throws IOException, NoHeadException, GitAPIException {

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
//  PrintWriter pout = new PrintWriter(new FileWriter(outFile), true);
//  Iterator<RevCommit> itc = git.log().add(from).call().iterator();
//  RevCommit c;
//  while (itc.hasNext()) {
//    c = itc.next();
//    pout.println("===========\n" + printCommit(c));
//    if (to != null && c.equals(to)) break;
//  }
//  pout.close();
}


// format the output like in --pretty="%H<#>%aN<#>%at<#>%cN<#>%ct<#>%s
String printCommit(RevCommit c) {
  String out = "";
  PersonIdent author = c.getAuthorIdent();
  PersonIdent committer = c.getCommitterIdent();
  // long commitTime = (long)c.getCommitTime() == committer.getWhen().getTime() / 1000 (in seconds)
  out += "" + c.name()
      + GitWorks.log_sep + author.getName() + GitWorks.log_sep + author.getWhen().getTime()
      + GitWorks.log_sep + committer.getName() + GitWorks.log_sep + committer.getWhen().getTime()
      + GitWorks.log_sep + c.getShortMessage(); //c.getFullMessage(); c.getShortMessage();
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
Repository createRepo(String repoDir, String gitDir) throws IOException {

  File gd = new File(gitDir);
  File rd = new File(repoDir);
  if (GitWorks.anew) {
    if (rd.exists()) FileUtils.delete(rd, FileUtils.RECURSIVE);
    rd.mkdirs();
    if (gd.exists()) FileUtils.delete(gd, FileUtils.RECURSIVE);
    gd.mkdirs();
  }
  Repository repository = new FileRepositoryBuilder().setWorkTree(rd).setGitDir(gd)
      .readEnvironment() // scan environment GIT_* variables
      .findGitDir() // scan up the file system tree
      .setMustExist(!GitWorks.anew).build();
  if (GitWorks.anew) repository.create(GitWorks.bare);

  return repository;
}


// the RevWalk must be reset by the caller upon return!
ArrayList<RevCommit> findCommits(RevWalk walk, ArrayList<RevCommit> included,
    ArrayList<RevCommit> excluded, boolean getBody) throws MissingObjectException,
    IncorrectObjectTypeException, IOException {
  ArrayList<RevCommit> commits = new ArrayList<RevCommit>();
  commits.ensureCapacity(bSize * 100); // XXX heuristic workaround
  walk.setRetainBody(getBody);
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
    commits.add(c);
  }
  return commits.size() > 0 ? commits : null;
}


void buildBranchesMap() throws GitAPIException {

  if (branches != null) {
    System.err.println("The map of the branches has already been built!!!");
    return;
  }
  branches = new HashMap<String, ArrayList<Ref>>();

  ArrayList<Ref> temp = null;
  Ref r;
  bSize = 0;
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
    bSize++;
  }
  // Iterator<ArrayList<Ref>> rit = map.values().iterator();
  // Iterator<String> sit = map.keySet().iterator();
  // while (rit.hasNext()) {
  //   System.out.println("\t" + sit.next() + ":\n" + printArray(rit.next().toArray()));
  // }
}


HashMap<ObjectId, ArrayList<Ref>> findAllBranches() throws Exception {
  HashMap<ObjectId, ArrayList<Ref>> structure = new HashMap<ObjectId, ArrayList<Ref>>();
  ArrayList<Ref> vals;
  Iterator<Ref> brIt;
  Iterator<ArrayList<Ref>> bit;
  Entry<ObjectId, ArrayList<Ref>> re;
  Ref r;
  ObjectId k;
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
        if (walk.isMergedInto(walk.parseCommit(k), walk.parseCommit(r.getObjectId()))) {
          vals.add(r);
        }
      }
    }
    vals.trimToSize();
    // System.out.print(re.getKey().getName() + " :\n" + printArray(re.getValue().toArray()));
  }
  walk.dispose();
  return structure;
}


// find commits that are (only?) in each remote XXX
void getCommitsInR(RevWalk walk, boolean only)
    throws MissingObjectException, IncorrectObjectTypeException, IOException {

  Iterator<Ref> brIt;
  HashMap<String, ArrayList<ObjectId>> comms = new HashMap<String, ArrayList<ObjectId>>();
  ArrayList<RevCommit> comm;
  ArrayList<ObjectId> ids;
  ArrayList<RevCommit> included = new ArrayList<RevCommit>();
  ArrayList<RevCommit> excluded = new ArrayList<RevCommit>();
  Entry<String, ArrayList<Ref>> er;
  String r;
  Iterator<Entry<String, ArrayList<Ref>>> erit;
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
    ids = comm != null ? getIds(comm) : null;
    if (only) {
      comms.put(r, ids);
    } else {
      // TODO add to a global ArrayList with all Commits (call findAllBranches)
    }
    included.clear();
    excluded.clear();
    walk.reset();
  }
  included.trimToSize();
  included.ensureCapacity(50);
  excluded.trimToSize();
  excluded.ensureCapacity(50);

  if (only)
    commitsOnlyInR = comms;
  else
    commitsInR = comms;
}


// find commits that are not in a given remote
void getCommitsNotInR(RevWalk walk) throws MissingObjectException,
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
    ids = comm != null ? getIds(comm) : null;
    commits.put(r, ids);
    included.clear();
    excluded.clear();
    walk.reset();
  }
  included.trimToSize();
  included.ensureCapacity(50);
  excluded.trimToSize();
  excluded.ensureCapacity(50);

  commitsNotInR = commits;
}


// fast but resource-demanding
// find commits that are (uniquely?) in a branch
void getCommitsInB(RevWalk walk, boolean only) throws MissingObjectException,
    IncorrectObjectTypeException, IOException {

  Ref b;
  Iterator<Ref> brIt;
  HashMap<String, ArrayList<ObjectId>> commits;
  ArrayList<RevCommit> comm;
  ArrayList<ObjectId> ids;
  Iterator<ArrayList<Ref>> cit;
  ArrayList<RevCommit> included = new ArrayList<RevCommit>();
  ArrayList<RevCommit> excluded = new ArrayList<RevCommit>();
  // excluded.add(walk.parseCommit(git.getRepository().resolve("ajaxorg--ace/anchor")));
  // included.add(walk.parseCommit(git.getRepository().resolve("ajaxorg--ace/issue-42")));
  // included.add(walk.parseCommit(git.getRepository().resolve("ajaxorg--ace/concorde")));

  ArrayList<Ref> allBranches = new ArrayList<Ref>();
  ArrayList<Ref> temp = new ArrayList<Ref>();
  if (only) {
    temp.ensureCapacity(bSize - 1);
    excluded.ensureCapacity(bSize - 1);
  } else if (allCommits.size() > 0) {
    System.err.println("GitMiner : ERROR : The allCommits array must be filled only once!");
    return;
  }
  commits = new HashMap<String, ArrayList<ObjectId>>();
  cit = branches.values().iterator();
  allBranches.ensureCapacity(bSize);
  while (cit.hasNext()) {
    allBranches.addAll(cit.next());
  }
  for (int i = 0; i < allBranches.size(); i++) {
    b = allBranches.get(i);
    if (only) {
      temp.clear();
      if (i > 0) temp.addAll(allBranches.subList(0, i));
      temp.addAll(allBranches.subList(i + 1, allBranches.size()));
      brIt = temp.iterator();
      while (brIt.hasNext()) {
        excluded.add(walk.parseCommit(brIt.next().getObjectId()));
      }
    }
    included.add(walk.parseCommit(b.getObjectId()));
    comm = findCommits(walk, included, excluded, !only);
    ids = comm != null ? getIds(comm) : null;
    commits.put(b.getName(), ids);
    included.clear();
    excluded.clear();
    walk.reset();
  }
  included.trimToSize();
  included.ensureCapacity(50);
  excluded.trimToSize();
  excluded.ensureCapacity(50);

  if (only)
    commitsOnlyInB = commits;
  else
    commitsInB = commits;
}


@SuppressWarnings({ "unchecked", "rawtypes" })
ArrayList<ObjectId> getIds(ArrayList<? extends RevObject> a) {
  if (a == null) return null;
  ArrayList<ObjectId> res = new ArrayList<ObjectId>();
  res.ensureCapacity(a.size());
  Iterator<?> it = a.iterator();
  RevObject i;
  while (it.hasNext()) {
    i = (RevObject)it.next();
    res.add(i.getId());
  }
  return res;
}


void analyzeForkTree(ForkEntry fe) throws Exception {

  RevWalk walk = null;

  /************** create/load git repo ****************/

  String gitDirPath = GitWorks.gits_out_dir + GitWorks.getProjectNameAsRemote(fe)
      + ((GitWorks.bare == true) ? ".git" : "/.git");
  String treeDirPath = GitWorks.trees_out_dir + GitWorks.getProjectNameAsRemote(fe);

  try {
    // with git.init() it is not possible to specify a different tree path!!
    // git = Git.init().setBare(bare).setDirectory(new File(gitDirPath)).call();
    git = Git.wrap(createRepo(treeDirPath, gitDirPath));
//    System.out.println(printRepoInfo());

    /************** create big tree ****************/

    if (GitWorks.anew) {
      addRemotes(git, fe, 0); // with a large param value the complete fork tree will be built
    }

    /************** print commits & checkout ****************/

//    printCommits(GitWorks.trees_out_dir + GitWorks.prefix + GitWorks.getProjectNameAsRemote(fe) + "-commitList.log", refspec, null);
//    String refspec = "refs/remotes/" + GitWorks.getProjectNameAsRemote(fe) + "/master";
//    if (!GitWorks.bare) {
//      git.checkout().setStartPoint(refspec).setCreateBranch(GitWorks.anew)
//          .setName(GitWorks.getProjectNameAsRemote(fe)).call(); // .getResult() for a dry-run
//      // createTree(fe, makeTree(walk, from));
//    }

    /************** find interesting commits ***************/

    buildBranchesMap(); // build a map with all the branches in the big tree

    walk = new RevWalk(git.getRepository());
    // walk.setRetainBody(false); // this walk is for multiple usage
    // walk.sort(RevSort.COMMIT_TIME_DESC);
    // walk.sort(RevSort.TOPO);
    // walk.sort(RevSort.NONE);

    getCommitsInR(walk, true); // XXX
    getCommitsNotInR(walk);
    getCommitsInB(walk, false);

  }
  catch (Exception e) {
    e.printStackTrace();
  }
  finally {
    if (walk != null) walk.dispose();
    if (git != null) git.getRepository().close();
  }
}



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
