import java.io.Externalizable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
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


public class GitMiner implements Externalizable {

LinkedHashMap<String, ArrayList<Commit>> commitsInR = null;
LinkedHashMap<String, ArrayList<BranchRef>> branches = null;
LinkedHashMap<String, ArrayList<Commit>> commitsNotInR = null;
LinkedHashMap<String, ArrayList<Commit>> commitsOnlyInR = null;
LinkedHashMap<BranchRef, ArrayList<Commit>> commitsInB = null;
LinkedHashMap<BranchRef, ArrayList<Commit>> commitsOnlyInB = null;
ArrayList<Commit> allCommits = null;
ArrayList<BranchRef> allBranches = null;
Git git;
int id = 0;
String name;

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


GitMiner(String n) {
  name = n;
}


//add remotes to a jgit repo, using a given ForkEntry data structure
void addRemotes(Git git, ForkEntry project, int depth) throws Exception {
  GitWorks.dfsVisit(depth, project, GitMiner.addAsRemote, git);
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
    if (!GitWorks.bare) rd.mkdirs();
    if (gd.exists()) FileUtils.delete(gd, FileUtils.RECURSIVE);
    gd.mkdirs();
  }
  FileRepositoryBuilder frb = new FileRepositoryBuilder();
  if (!GitWorks.bare) frb.setWorkTree(rd);
  Repository repository = frb.setGitDir(gd)
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
  commits.ensureCapacity(allBranches.size()); // XXX heuristic workaround
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


private BranchRef getBranchRef(int index) {
  return allBranches.get(index);
}


private BranchRef getBranchRef(String branch) {
  return getBranchRef(Collections.binarySearch(allBranches, branch));
}


// build both the allBranches sorted arrayList and the branches map
void buildBranchesMap(int size) throws GitAPIException {

  if (branches != null) {
    System.err.println("The map of the branches has already been built!!!");
    return;
  }
  branches = new LinkedHashMap<String, ArrayList<BranchRef>>(size, 1);
  allBranches = new ArrayList<BranchRef>();

  ArrayList<BranchRef> temp = null;
  BranchRef br;
  Ref r;
  ArrayList<Ref> all = (ArrayList<Ref>)git.branchList().setListMode(ListMode.REMOTE).call();
  Iterator<Ref> allBs = all.iterator();
  String bName = "";
  allBranches.ensureCapacity(all.size()); //int j = 0;
  while (allBs.hasNext()) {/**/// System.err.println("###### Iteration " + (++j));
    r = allBs.next();
    if (!("refs/remotes/" + r.getName().split("/")[2]).equals(bName)) {
      bName = "refs/remotes/" + r.getName().split("/")[2]; // getName() format is
                                                           // refs/remotes/<remote-name>/<branch-name>
      if (temp != null) temp.trimToSize();
      temp = new ArrayList<BranchRef>();
      branches.put(bName, temp);
    }
    br = new BranchRef(r);
    temp.add(br);
    allBranches.add(br);
  }
  allBranches.trimToSize();
  Collections.sort(allBranches);
  String bhashes = "";
  for (int i = 0; i < allBranches.size(); i++) {
    allBranches.get(i).index = i;
    bhashes += allBranches.get(i).id.name();
  }
  id = bhashes.hashCode();
//  Entry<String, ArrayList<BranchRef>> e;
//  Iterator<Entry<String, ArrayList<BranchRef>>> eit = branches.entrySet().iterator();
//  while (eit.hasNext()) {
//    e = eit.next();
//    System.out.println("\t" + e.getKey() + ":\n");
//    printAny(e.getValue(), System.out);
//  }
//  printArray(allBranches, System.out);
}


// awfully time-consuming
ArrayList<BranchRef> findAllBranches(ObjectId c, String remote)
    throws MissingObjectException, IncorrectObjectTypeException, IOException {
  ArrayList<BranchRef> res;
  Iterator<BranchRef> brIt;
  BranchRef r;
  RevWalk walk = new RevWalk(git.getRepository());
  walk.setRetainBody(false); // this walk is for multiple usage
  res = new ArrayList<BranchRef>(allBranches.size());
  brIt = branches.get(remote).iterator();
  while (brIt.hasNext()) {
    r = brIt.next();
    if (walk.isMergedInto(walk.parseCommit(c), walk.parseCommit(r.id))) {
      res.add(r);
    }
  }
  res.trimToSize();
  walk.dispose();
//  System.out.print(c.getName() + " is found in\n");
//  printAny(res, System.out);
//  System.out.println("\n");
  return res;
}


// find commits that are (only?) in each remote
void getCommitsInR(RevWalk walk, boolean only)
    throws MissingObjectException, IncorrectObjectTypeException, IOException {

  Iterator<BranchRef> brIt;
  LinkedHashMap<String, ArrayList<Commit>> comms;
  ArrayList<RevCommit> comm;
  ArrayList<RevCommit> included = new ArrayList<RevCommit>();
  ArrayList<RevCommit> excluded = new ArrayList<RevCommit>();
  Entry<String, ArrayList<BranchRef>> er;
  String r;
  int c;
//  Commit newco;
  ArrayList<Commit> newcos;
//  ArrayList<BranchRef> b;
  Iterator<Entry<String, ArrayList<BranchRef>>> erit;
  Iterator<String> sit = branches.keySet().iterator();
  comms = new LinkedHashMap<String, ArrayList<Commit>>(branches.size(), 1);
  if (only)
    excluded.ensureCapacity(allBranches.size() - 1);
//  else if (allCommits.size() > 0) {
//    System.err.println("GitMiner : ERROR : The allCommits array must be filled only once!");
//    return;
//  }
//  int j = 0;
  while (sit.hasNext()) {/**/// System.err.println("###### Iteration " + (++j));
    r = sit.next();
    erit = branches.entrySet().iterator();
    while (erit.hasNext()) {
      er = erit.next();
      brIt = er.getValue().iterator();
      if (er.getKey().equals(r)) {
        while (brIt.hasNext()) {
          included.add(walk.parseCommit(brIt.next().id));
        }
      } else if (only) {
        while (brIt.hasNext()) {
          excluded.add(walk.parseCommit(brIt.next().id));
        }
      }
    }
    comm = findCommits(walk, included, excluded, false); // !only
    if (comm != null) { // if only == false this is always true
      newcos = new ArrayList<Commit>(comm.size());
      for (int i = 0; i < comm.size(); i++) {
//        if (!only) { // XXX it may be used to populate allCommits, but it is slow and inefficient
//          newco = new Commit(comm.get(i));
//          c = addUnique(allCommits, newco);
//          b = findAllBranches(newco.id, r);
//          allCommits.get(c).addBranches(b);
//        } else {
          c = Collections.binarySearch(allCommits, comm.get(i));
//        }
        newcos.add(allCommits.get(c));
      }
    } else {
      newcos = null;
    }
    comms.put(r, newcos);
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
  Iterator<BranchRef> brIt;
  LinkedHashMap<String, ArrayList<Commit>> commits;
  ArrayList<RevCommit> comm;
  ArrayList<RevCommit> included = new ArrayList<RevCommit>();
  ArrayList<RevCommit> excluded = new ArrayList<RevCommit>();

  Entry<String, ArrayList<BranchRef>> er;
  String r;
  ArrayList<Commit> newcos;
  Iterator<Entry<String, ArrayList<BranchRef>>> erit;
  Iterator<String> sit = branches.keySet().iterator();
  included.ensureCapacity(allBranches.size() - 1);
  commits = new LinkedHashMap<String, ArrayList<Commit>>(branches.size(), 1);
  walk.setRetainBody(false);// int j = 0;
  while (sit.hasNext()) {/**/// System.err.println("###### Iteration " + (++j));
    r = sit.next();
    erit = branches.entrySet().iterator();
    while (erit.hasNext()) {
      er = erit.next();
      brIt = er.getValue().iterator();
      if (er.getKey().equals(r)) {
        while (brIt.hasNext()) {
          excluded.add(walk.parseCommit(brIt.next().id));
        }
      } else {
        while (brIt.hasNext()) {
          included.add(walk.parseCommit(brIt.next().id));
        }
      }
    }
    comm = findCommits(walk, included, excluded, false);
    if (comm != null) {
      newcos = new ArrayList<Commit>(comm.size());
      for (int i = 0; i < comm.size(); i++) {
        newcos.add(allCommits.get(Collections.binarySearch(allCommits, comm.get(i))));
      }
    } else {
      newcos = null;
    }
    commits.put(r, newcos);
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

  int c;
  BranchRef b;
  Iterator<BranchRef> brIt;
  LinkedHashMap<BranchRef, ArrayList<Commit>> commits;
  Commit newco;
  ArrayList<Commit> newcos;
  ArrayList<RevCommit> comm;

  ArrayList<RevCommit> included = new ArrayList<RevCommit>();
  ArrayList<RevCommit> excluded = new ArrayList<RevCommit>();
  // excluded.add(walk.parseCommit(git.getRepository().resolve("ajaxorg--ace/anchor")));
  // included.add(walk.parseCommit(git.getRepository().resolve("ajaxorg--ace/issue-42")));
  // included.add(walk.parseCommit(git.getRepository().resolve("ajaxorg--ace/concorde")));

  ArrayList<BranchRef> temp = new ArrayList<BranchRef>();
  if (only) {
    temp.ensureCapacity(allBranches.size() - 1);
    excluded.ensureCapacity(allBranches.size() - 1);
  } else if (allCommits.size() > 0) {
    System.err.println("GitMiner : ERROR : The allCommits array must be filled only once!");
    return;
  }
  commits = new LinkedHashMap<BranchRef, ArrayList<Commit>>(allBranches.size(), 1);
  for (int i = 0; i < allBranches.size(); i++) {
    b = allBranches.get(i);/**/// System.err.println("###### Iteration " + (i+1));
    if (only) {
      temp.clear();
      if (i > 0) temp.addAll(allBranches.subList(0, i));
      temp.addAll(allBranches.subList(i + 1, allBranches.size()));
      brIt = temp.iterator();
      while (brIt.hasNext()) {
        excluded.add(walk.parseCommit(brIt.next().id));
      }
    }
    included.add(walk.parseCommit(b.id));
    comm = findCommits(walk, included, excluded, !only);
    if (comm != null) { // if only == false this is always true
      newcos = new ArrayList<Commit>(comm.size());
      for (int j = 0; j < comm.size(); j++) {
        if (!only) {
          newco = new Commit(comm.get(j)); // this RevCommit has a buffer -> populate allCommits
          c = addUnique(allCommits, newco);
          allCommits.get(c).addBranch(b);
        } else {  // this RevCommit has no buffer
          c = Collections.binarySearch(allCommits, comm.get(j));
        }
        newcos.add(allCommits.get(c));
      }
    } else {
      newcos = null;
    }
    commits.put(b, newcos);
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


private void init() {
  allCommits = new ArrayList<Commit>();
  allCommits.ensureCapacity(allBranches.size()); // XXX heuristic workaround
}


private void tailor() {
  allCommits.trimToSize();
  Iterator<Commit> it = allCommits.iterator();
  while (it.hasNext()) {
    it.next().branches.trimToSize();
  }
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
      addRemotes(git, fe, 100); // with a large param value the complete fork tree will be built
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

    if (allBranches == null) buildBranchesMap(fe.howManyForks()); // build allBranches and branches

    walk = new RevWalk(git.getRepository());
    // walk.setRetainBody(false); // this walk is for multiple usage
    // walk.sort(RevSort.COMMIT_TIME_DESC);
    // walk.sort(RevSort.TOPO);
    // walk.sort(RevSort.NONE);

    if (allCommits == null) {
      init();
      getCommitsInB(walk, false);
      tailor();
    }
    getCommitsInB(walk, true);
    getCommitsInR(walk, false);
    getCommitsInR(walk, true);
    getCommitsNotInR(walk);
    System.out.println("This big repo has " + allCommits.size() + " regular commits.");
//    printAny(commitsNotInR, System.out);
//    printAny(allCommits, System.out);

  }
  catch (Exception e) {
    e.printStackTrace();
  }
  finally {
    if (walk != null) {
      walk.dispose(); walk.release();
    }
    if (git != null) git.getRepository().close();
    System.gc();
  }
}


@SuppressWarnings({ "rawtypes" })
static void printMap(Map m, PrintStream out) {
  Entry ec = null;
  Iterator cit = null; int c; Object k,v;
  Iterator ecit = m.entrySet().iterator();
  for (int i = 0; i < 3 && ecit.hasNext(); i++) {//  while (ecit.hasNext()) {
    ec = (Map.Entry)ecit.next();
    k = ec.getKey();
    if (ec.getValue() != null) {
      cit = ((ArrayList)ec.getValue()).iterator();
      c = 0;
//      while (cit.hasNext()) {
        v = cit.next();
        out.print((k.getClass().cast(k)).toString() + " 's element # " + (++c) + ":\n"
            + (v.getClass().cast(v)).toString());
//      }
    } else {
      i--; //System.out.print((k.getClass().cast(k)).toString() + " : NO ENTRY");
    }
    out.println("\n------------------------------");
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
 * @return The [0, set.size()) index of the item in the List.
 */
@SuppressWarnings({ "unchecked", "rawtypes" })
static private int addUnique(List set, Comparable item) {
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
 * It does not handle Interfaces, Enums, arrays of arrays or lists of lists.
 * It only handles Maps declared in GitMiner.
 * 
 * @param data
 *          Data to be printed
 * @param out
 *          Stream in which the data printout must be written
 */
@SuppressWarnings({ "unchecked", "rawtypes" })
static public void printAny(Object data, PrintStream out) {
  int size;
  if (data == null) {
    out.println("\nNULL!\n");
  } else if (data instanceof Map
      && (new LinkedHashMap<Object, ArrayList>()).getClass().isAssignableFrom(data.getClass())) {
    printMap((Map)data, out);
  } else if (data instanceof List) {
    List<Object> a = (List<Object>)data;
    size = a.size();
    for (int i = 0; i < 3; i++) { // size FIXME
      out.println("[" + i + "] " + (a.get(i).getClass().cast(a.get(i))).toString());
    }
  } else if (data.getClass().isArray()) {
    Object e;
    size = Array.getLength(data);
    for (int i = 0; i < size; i++) {
      e = Array.get(data, i);
      if (e.getClass().getGenericSuperclass() != null) {
        out.println("[" + i + "] " + (e.getClass().cast(e)).toString());
      } else {
        out.println("[" + i + "] " + e.getClass().cast(e));
      }
    }
  } else if (data.getClass().isPrimitive()) {
    out.println(data);
  } else if (!(data instanceof java.util.Map || data.getClass().isEnum() || data.getClass().isInterface())) {
    out.println((data.getClass().cast(data)).toString());
  } else {
    out.println("ERROR : cannot print " + data.getClass().toString() + " !");
  }
  out.flush();
}


@SuppressWarnings({ "rawtypes", "unchecked" })
private void externalizeMap(Map map, ObjectOutput out) throws IOException {
  int keyType = 0, valueType = 1, size;
  Iterator<Map.Entry> it;
  Iterator cit;
  Entry e;
  Object value;
  if (map == null) {
    out.writeInt(0);
    out.flush();
    return;
  }
  size = map.keySet().size();
  if (map.keySet().iterator().next().getClass().equals(String.class)) {
    keyType = 1;
  }
  cit = map.values().iterator();
  do {
    value = cit.next();
  } while (value == null && cit.hasNext());
  if (value == null || ((ArrayList)value).get(0).getClass().equals(Commit.class)) {
    valueType = 0;
  }
  out.writeInt(size);
  out.writeInt(keyType);
  out.writeInt(valueType);
  it = map.entrySet().iterator();
  while (it.hasNext()) {
    e = it.next();
    if (e.getValue() == null) {
      out.writeInt(0);
    } else {
      out.writeInt(((ArrayList)e.getValue()).size());
      cit = ((ArrayList)e.getValue()).iterator();
      while (cit.hasNext()) {
        value = cit.next();
        out.writeInt(Collections.binarySearch(
            valueType == 0 ? allCommits : allBranches, value.getClass().cast(value)));
      }
    }
    switch (keyType) {
    case 0:
      out.writeInt(((BranchRef)e.getKey()).index);
    break;
    case 1:
      out.writeUTF(((String)e.getKey()));
    }
  }
  out.flush();
}


@SuppressWarnings({ "rawtypes", "unchecked" })
private Map importMap(ObjectInput in) throws IOException {
  int j, i, size, vsize, keyType, valueType;
  size = in.readInt();
  if (size == 0) {
    return null;
  }
  keyType = in.readInt();
  valueType = in.readInt();
  LinkedHashMap res = new LinkedHashMap(size);
  ArrayList values;
  for (i = 0; i < size; i++) {
    vsize = in.readInt();
    if (vsize == 0) {
      values = null;
    } else {
      values = new ArrayList(vsize);
      for (j = 0; j < vsize; j++) {
        values.add(valueType == 0 ? allCommits.get(in.readInt()) : allBranches.get(in.readInt()));
      }
    }
    switch (keyType) {
    case 0:
      res.put(allBranches.get(in.readInt()), values);
    break;
    case 1:
      res.put(in.readUTF(), values);
    }
  }
  return res;
}


@SuppressWarnings("unchecked")
@Override
public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
  int i, j, size;
  BranchRef b;
  name = in.readUTF();
  id = in.readInt();
  size = in.readInt();
  allBranches = new ArrayList<BranchRef>(size);
  for (i = 0; i < size; i++) {
    b = new BranchRef();
    b.readExternal(in);
    b.index = i;
    allBranches.add(b);
  }
  Commit c;
  size = in.readInt();
  allCommits = new ArrayList<Commit>(size);
  for (i = 0; i < size; i++) {
    c = new Commit();
    c.readExternal(in);
    for (j = 0; j < c.branches.size(); j++) {
      c.branches.set(j, getBranchRef(c.branches.get(j).index));
    }
    allCommits.add(c);
  }
  branches = (LinkedHashMap<String, ArrayList<BranchRef>>)importMap(in);
  commitsInB = (LinkedHashMap<BranchRef, ArrayList<Commit>>)importMap(in);

  commitsOnlyInB = (LinkedHashMap<BranchRef, ArrayList<Commit>>)importMap(in);
  commitsInR = (LinkedHashMap<String, ArrayList<Commit>>)importMap(in);
  commitsOnlyInR = (LinkedHashMap<String, ArrayList<Commit>>)importMap(in);
  commitsNotInR = (LinkedHashMap<String, ArrayList<Commit>>)importMap(in);
}


@Override
public void writeExternal(ObjectOutput out) throws IOException {
  out.writeUTF(name);
  out.writeInt(id);
  out.writeInt(allBranches.size());
  Iterator<BranchRef> itb = allBranches.iterator();
  while (itb.hasNext()) {
    itb.next().writeExternal(out);
  }
  out.writeInt(allCommits.size());
  Iterator<Commit> itc = allCommits.iterator();
  while (itc.hasNext()) {
    itc.next().writeExternal(out);
  }
  externalizeMap(branches, out);
  externalizeMap(commitsInB, out);

  externalizeMap(commitsOnlyInB, out);
  externalizeMap(commitsInR, out);
  externalizeMap(commitsOnlyInR, out);
  externalizeMap(commitsNotInR, out);
  out.flush();
}

}
