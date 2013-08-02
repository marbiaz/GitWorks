package gitworks;


import java.io.Externalizable;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Iterator;
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
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevFlag;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.CommitTimeRevFilter;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.RefSpec;


public class GitMiner implements Externalizable {

class PersonFrequency implements Comparable<PersonFrequency> {

  int index;
  int freq;
  long since;
  long until;

  PersonFrequency(int i) {
    index = i;
    freq = 0;
    since = Long.MAX_VALUE;
    until = 0;
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof PersonFrequency)
      return this.compareTo((PersonFrequency)o) == 0;
    return false;
  }

  @Override
  public int compareTo(PersonFrequency p) {
    return index - p.index;
  }

  @Override
  public String toString() {
    String out = "";
    out += allAuthors.get(index).toString() + " => " + freq;
    return out;
  }

}


LinkedHashMap<String, ArrayList<Commit>> comInF = null;
LinkedHashMap<String, ArrayList<BranchRef>> branches = null;
LinkedHashMap<String, ArrayList<Commit>> comNotInF = null;
LinkedHashMap<String, ArrayList<Commit>> comOnlyInF = null;
LinkedHashMap<Commit, ArrayList<Commit>> comInB = null;
LinkedHashMap<Commit, ArrayList<Commit>> comOnlyInB = null;
LinkedHashMap<String, ArrayList<PersonFrequency>> authOfComInF = null;
LinkedHashMap<String, ArrayList<PersonFrequency>> authOfComNotInF = null;
LinkedHashMap<String, ArrayList<PersonFrequency>> authOfComOnlyInF = null;
LinkedHashMap<Commit, ArrayList<PersonFrequency>> authOfComInB = null;
LinkedHashMap<Commit, ArrayList<PersonFrequency>> authOfComOnlyInB = null;
ArrayList<Commit> allCommits = null;
ArrayList<BranchRef> allBranches = null;
ArrayList<Person> allAuthors = null;
Git git;
long id = 0;
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
    String fork = GitWorks.getSafeName(fe);
//    System.out.print("Adding " + fork + " ...");
//    System.out.flush();
    StoredConfig config = git.getRepository().getConfig();
    config.setString("remote", fork, "url", GitWorks.getProjectPath(fe));
    config.setString("remote", fork, "fetch", "+refs/heads/*:refs/remotes/" + fork + "/*");
    config.save();
    all = new RefSpec(config.getString("remote", fork, "fetch"));
    git.fetch().setRemote(fork).setRefSpecs(all).call();
//    System.out.println(" done!");
//    System.out.flush();
  }


  public void finalize(ForkEntry fe) {}
};


GitMiner() {}


@SuppressWarnings({ "rawtypes", "unchecked" })
private LinkedHashMap computePersonFreq(LinkedHashMap map) {
  if (map == null) return null;
  ArrayList<PersonFrequency> values;
  ArrayList ev;
  LinkedHashMap res = new LinkedHashMap<Object, ArrayList<PersonFrequency>>(map.size(), 1);
  Entry e;
  Set<Entry> es = map.entrySet();
  Iterator<Entry> esit = es.iterator();
  Iterator<Commit> evit;
  PersonFrequency p;
  long pTs;
  Commit c;
  int i;
  while (esit.hasNext()) {
    e = esit.next();
    ev = (ArrayList)e.getValue();
    evit = ev.iterator();
    values = new ArrayList<PersonFrequency>(ev.size());
    while (evit.hasNext()) {
      c = evit.next();
      pTs = c.getCommittingInfo().getWhen().getTime();
      i = Collections.binarySearch(allAuthors, c.getAuthoringInfo());
      p = new PersonFrequency(i);
      i = GitWorks.addUnique(values, p);
      p = values.get(i);
      p.freq++;
      if (p.since > pTs)
        p.since = pTs;
      if (p.until < pTs)
        p.until = pTs;
    }
    values.trimToSize();
    res.put(e.getKey(), values);
  }
  return res;
}


//add remotes to a jgit repo, using a given ForkEntry data structure
//with a large depth param value the complete fork tree will be built
private void addRemotes(Git git, ForkEntry project, int depth) throws Exception {
  GitWorks.dfsVisit(depth, project, GitMiner.addAsRemote, git);
  git.getRepository().scanForRepoChanges();
}


// printout all commit messages in a given range -> use and reset an existing RevWalk
private void printCommits(String outFile, RevWalk walk)
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
  AnyObjectId to = (to_ref == null || to_ref.equals("")) ? null
      : git.getRepository().resolve(to_ref);
  RevWalk walk = new RevWalk(git.getRepository());
  walk.sort(RevSort.COMMIT_TIME_DESC, true);
  walk.sort(RevSort.TOPO, true);
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
private String printCommit(RevCommit c) {
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
private Repository createRepo(String repoDir, String gitDir) throws IOException {

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
private ArrayList<RevCommit> findCommits(RevWalk walk, ArrayList<RevCommit> included,
    ArrayList<RevCommit> excluded, boolean getBody, long after) throws MissingObjectException,
    IncorrectObjectTypeException, IOException {
  ArrayList<RevCommit> commits = new ArrayList<RevCommit>();
  commits.ensureCapacity(allBranches.size()); // heuristic workaround
  walk.sort(RevSort.COMMIT_TIME_DESC, true);
  walk.sort(RevSort.TOPO, true);
  walk.setRetainBody(getBody);
  if (after > 0)
    walk.setRevFilter(CommitTimeRevFilter.after(after));
  else
    walk.setRevFilter(null);
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


BranchRef getBranchRef(String branch) {
  return getBranchRef(Collections.binarySearch(allBranches, branch));
}


// build both the allBranches sorted arrayList and the branches map
private void buildBranchesMap(int size) throws GitAPIException {

  if (branches != null) {
    System.err.println("GitMIner ( " + name + " -- " + id
        + " ) ERROR : the map of the branches has already been built!");
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
    if (!(r.getName().split("/")[2]).equals(bName)) {
      bName = r.getName().split("/")[2]; // getName() format:
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
  for (int i = 0; i < allBranches.size(); i++) {
    allBranches.get(i).index = i;
  }
//  Entry<String, ArrayList<BranchRef>> e;
//  Iterator<Entry<String, ArrayList<BranchRef>>> eit = branches.entrySet().iterator();
//  while (eit.hasNext()) {
//    e = eit.next();
//    System.out.println("\t" + e.getKey() + ":\n");
//    printAny(e.getValue(), System.out);
//  }
//  printArray(allBranches, System.out);
}


// find commits that are (only?) in each remote
private void getCommitsInR(RevWalk walk, boolean only)
    throws MissingObjectException, IncorrectObjectTypeException, IOException {

  Iterator<BranchRef> brIt;
  LinkedHashMap<String, ArrayList<Commit>> commits;
  ArrayList<RevCommit> comm;
  ArrayList<RevCommit> included = new ArrayList<RevCommit>();
  ArrayList<RevCommit> excluded = new ArrayList<RevCommit>();
  Entry<String, ArrayList<BranchRef>> er;
  String r;
  int c;
  ArrayList<Commit> newcos;
  Iterator<Entry<String, ArrayList<BranchRef>>> erit;
  Iterator<String> sit = branches.keySet().iterator();
  commits = new LinkedHashMap<String, ArrayList<Commit>>(branches.size() / 2, 1);
  if (only)
    excluded.ensureCapacity(allBranches.size() - 1);
//  int j = 0;
  while (sit.hasNext()) {/**/// System.err.println("###### Iteration " + (++j));
    r = sit.next();
    erit = branches.entrySet().iterator();
    while (erit.hasNext()) {
      er = erit.next();
      brIt = er.getValue().iterator();
      if (er.getKey().equals(r)) {
        while (brIt.hasNext()) {
          GitWorks.addUnique(included, walk.parseCommit(brIt.next().id));
        }
      } else if (only) {
        while (brIt.hasNext()) {
          GitWorks.addUnique(excluded, walk.parseCommit(brIt.next().id));
        }
      }
    }
    comm = findCommits(walk, included, excluded, false, 0);
    if (comm != null) {
      newcos = new ArrayList<Commit>(comm.size());
      for (int i = 0; i < comm.size(); i++) {
        c = Collections.binarySearch(allCommits, comm.get(i));
        newcos.add(allCommits.get(c));
      }
      commits.put(r, newcos);
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
    comOnlyInF = commits.isEmpty() ? null : commits;
  else
    comInF = commits.isEmpty() ? null : commits;
}


// find commits that are not in a given remote
private void getCommitsNotInR(RevWalk walk) throws MissingObjectException,
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
  commits = new LinkedHashMap<String, ArrayList<Commit>>(branches.size() / 2, 1);
//  int j = 0;
  while (sit.hasNext()) {/**/// System.err.println("###### Iteration " + (++j));
    r = sit.next();
    erit = branches.entrySet().iterator();
    while (erit.hasNext()) {
      er = erit.next();
      brIt = er.getValue().iterator();
      if (er.getKey().equals(r)) {
        while (brIt.hasNext()) {
          GitWorks.addUnique(excluded, walk.parseCommit(brIt.next().id));
        }
      } else {
        while (brIt.hasNext()) {
          GitWorks.addUnique(included, walk.parseCommit(brIt.next().id));
        }
      }
    }
    comm = findCommits(walk, included, excluded, false, 0);
    if (comm != null) {
      newcos = new ArrayList<Commit>(comm.size());
      for (int i = 0; i < comm.size(); i++) {
        newcos.add(allCommits.get(Collections.binarySearch(allCommits, comm.get(i))));
      }
      commits.put(r, newcos);
    }
    included.clear();
    excluded.clear();
    walk.reset();
  }
  included.trimToSize();
  included.ensureCapacity(50);
  excluded.trimToSize();
  excluded.ensureCapacity(50);

  comNotInF = commits.isEmpty() ? null : commits;
}


// fast but resource-demanding
// find commits that are (uniquely?) in a branch
// used to build the allCommits and allAuthors arrays as well
private void getCommitsInB(RevWalk walk, boolean only) throws MissingObjectException,
    IncorrectObjectTypeException, IOException {

  int c;
  BranchRef b;
  Iterator<BranchRef> brIt;
  LinkedHashMap<Commit, ArrayList<Commit>> commits;
  Commit newco, co;
  Person newpe;
  ArrayList<Commit> newcos;
  ArrayList<RevCommit> comm;

  ArrayList<RevCommit> included = new ArrayList<RevCommit>();
  ArrayList<RevCommit> excluded = new ArrayList<RevCommit>();

  ArrayList<BranchRef> temp = new ArrayList<BranchRef>();
  if (only) {
    temp.ensureCapacity(allBranches.size() - 1);
    excluded.ensureCapacity(allBranches.size() - 1);
  } else if (allCommits.size() > 0) {
    System.err.println("GitMIner ( " + name + " -- " + id
        + " ) ERROR : the allCommits array has already been built!");
    return;
  }
  commits = only ? new LinkedHashMap<Commit, ArrayList<Commit>>()
      : new LinkedHashMap<Commit, ArrayList<Commit>>(allBranches.size(), 1);
  for (int i = 0; i < allBranches.size(); i++) {
    b = allBranches.get(i);/**/// System.err.println("###### Iteration " + (i+1));
    if (only) {
      temp.clear();
      if (i > 0) temp.addAll(allBranches.subList(0, i));
      temp.addAll(allBranches.subList(i + 1, allBranches.size()));
      brIt = temp.iterator();
      while (brIt.hasNext()) {
        GitWorks.addUnique(excluded, walk.parseCommit(brIt.next().id));
      }
    }
    GitWorks.addUnique(included, walk.parseCommit(b.id));
    comm = findCommits(walk, included, excluded, !only, 0);
    if (comm != null) { // if only == false this is always true
      newcos = new ArrayList<Commit>(comm.size());
      for (int j = 0; j < comm.size(); j++) {
        if (!only) {
          newco = new Commit(comm.get(j)); // this RevCommit has a buffer -> populate allCommits
          c = GitWorks.addUnique(allCommits, newco);
          co = allCommits.get(c);
          co.addBranch(b);
          newpe = new Person(co.getAuthoringInfo());
          GitWorks.addUnique(allAuthors, newpe);
        } else {  // this RevCommit has no buffer
          c = Collections.binarySearch(allCommits, comm.get(j));
          co = allCommits.get(c);
        }
        newcos.add(co);
      }
      commits.put(newcos.get(0), newcos);
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
    comOnlyInB = commits.isEmpty() ? null : commits;
  else
    comInB = commits;
}




private void init() {
  allCommits = new ArrayList<Commit>();
  allCommits.ensureCapacity(allBranches.size()); // heuristic workaround
  allAuthors = new ArrayList<Person>();
  allAuthors.ensureCapacity(allBranches.size()); // heuristic workaround
}


private void tailor() {
  allCommits.trimToSize();
  Iterator<Commit> it = allCommits.iterator();
  while (it.hasNext()) {
    it.next().branches.trimToSize();
  }
  allAuthors.trimToSize();
}


String getInfo() {
  if (allCommits == null) return "GitMiner : uninitialized.";
  return "GitMiner : " + name + " ( " + id + " ) has " + allCommits.size()
      + " commits, " + allAuthors.size() + " authors, "
      + branches.size() + " forks and " + allBranches.size() + " branches.";
}


@SuppressWarnings("unchecked")
void analyzeForkTree(ForkEntry fe) throws Exception {

  RevWalk walk = null;

  String gitDirPath = GitWorks.gits_out_dir + GitWorks.getSafeName(fe)
      + ((GitWorks.bare == true) ? ".git" : "/.git");
  String treeDirPath = GitWorks.trees_out_dir + GitWorks.getSafeName(fe);

  try {
    // with git.init() it is not possible to specify a different tree path!!
    // git = Git.init().setBare(bare).setDirectory(new File(gitDirPath)).call();
    git = Git.wrap(createRepo(treeDirPath, gitDirPath));
//    System.out.println(printRepoInfo());

    if (GitWorks.anew) {
      addRemotes(git, fe, Integer.MAX_VALUE);
    }

    name = GitWorks.getSafeName(fe);
    id = fe.getRetrievalTimestamp();
    if (allBranches == null) buildBranchesMap(fe.howManyForks());

    walk = new RevWalk(git.getRepository());

    if (allCommits == null) {
      init();
      getCommitsInB(walk, false);
      tailor();
    }
    getCommitsInB(walk, true);
    getCommitsInR(walk, false);
    getCommitsInR(walk, true);
    getCommitsNotInR(walk);

    authOfComInB = computePersonFreq(comInB);
    authOfComOnlyInB = computePersonFreq(comOnlyInB);
    authOfComInF = computePersonFreq(comInF);
    authOfComOnlyInF = computePersonFreq(comOnlyInF);
    authOfComNotInF = computePersonFreq(comNotInF);

  }
  catch (Exception e) {
    e.printStackTrace();
  }
  finally {
    if (walk != null) {
      walk.dispose();
      walk.release();
      walk = null;
    }
    if (git != null) {
      git.getRepository().close();
      git = null;
    }
    System.gc();
  }
}


@SuppressWarnings({ "rawtypes", "unchecked" })
private void externalizeMap(LinkedHashMap map, ObjectOutput out) throws IOException {
  int keyType = 0, valueType = 1, size;
  Iterator<Entry> it;
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
  value = cit.next();
  if (((ArrayList)value).get(0).getClass().equals(Commit.class)) {
    valueType = 0;
  } else if (((ArrayList)value).get(0).getClass().equals(PersonFrequency.class)) {
    valueType = 2;
  }
  out.writeInt(size);
  out.writeInt(keyType);
  out.writeInt(valueType);
  it = map.entrySet().iterator();
  while (it.hasNext()) {
    e = it.next();
    out.writeInt(((ArrayList)e.getValue()).size());
    cit = ((ArrayList)e.getValue()).iterator();
    while (cit.hasNext()) {
      value = cit.next();
      switch (valueType) {
      case 0:
        out.writeInt(Collections.binarySearch(allCommits, ((Commit)value)));
        break;
      case 1:
        out.writeInt(((BranchRef)value).index);
        break;
      default:
        out.writeInt(((PersonFrequency)value).index);
        out.writeInt(((PersonFrequency)value).freq);
        out.writeLong(((PersonFrequency)value).since);
        out.writeLong(((PersonFrequency)value).until);
      }
    }
    switch (keyType) {
    case 0:
      out.writeInt(Collections.binarySearch(allCommits, (Commit)e.getKey()));
    break;
    case 1:
      out.writeUTF(((String)e.getKey()));
    }
  }
  out.flush();
}


@SuppressWarnings({ "rawtypes", "unchecked" })
private LinkedHashMap importMap(ObjectInput in) throws IOException {
  int j, i, size, vsize, keyType, valueType;
  PersonFrequency p;
  size = in.readInt();
  if (size == 0) {
    return null;
  }
  keyType = in.readInt();
  valueType = in.readInt();
  LinkedHashMap res = new LinkedHashMap(size, 1);
  ArrayList values;
  for (i = 0; i < size; i++) {
    vsize = in.readInt();
    values = new ArrayList(vsize);
    switch (valueType) {
    case 0:
      for (j = 0; j < vsize; j++) {
        values.add(allCommits.get(in.readInt()));
      }
    break;
    case 1:
      for (j = 0; j < vsize; j++) {
        values.add(allBranches.get(in.readInt()));
      }
    break;
    default:
      for (j = 0; j < vsize; j++) {
        p = new PersonFrequency(in.readInt());
        p.freq = in.readInt();
        p.since = in.readLong();
        p.until = in.readLong();
        values.add(p);
      }
    }
    switch (keyType) {
    case 0:
      res.put(allCommits.get(in.readInt()), values);
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
  id = in.readLong();
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
  Person p;
  size = in.readInt();
  allAuthors = new ArrayList<Person>(size);
  for (i = 0; i < size; i++) {
    p = new Person();
    p.readExternal(in);
    allAuthors.add(p);
  }
  branches = importMap(in);
  comInB = importMap(in);

  comOnlyInB = importMap(in);
  comInF = importMap(in);
  comOnlyInF = importMap(in);
  comNotInF = importMap(in);
  authOfComInB = importMap(in);
  authOfComOnlyInB = importMap(in);
  authOfComInF = importMap(in);
  authOfComOnlyInF = importMap(in);
  authOfComNotInF = importMap(in);
}


@Override
public void writeExternal(ObjectOutput out) throws IOException {
  out.writeUTF(name);
  out.writeLong(id);
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
  out.writeInt(allAuthors.size());
  Iterator<Person> itp = allAuthors.iterator();
  while (itp.hasNext()) {
    itp.next().writeExternal(out);
  }
  externalizeMap(branches, out);
  externalizeMap(comInB, out);

  externalizeMap(comOnlyInB, out);
  externalizeMap(comInF, out);
  externalizeMap(comOnlyInF, out);
  externalizeMap(comNotInF, out);
  externalizeMap(authOfComInB, out);
  externalizeMap(authOfComOnlyInB, out);
  externalizeMap(authOfComInF, out);
  externalizeMap(authOfComOnlyInF, out);
  externalizeMap(authOfComNotInF, out);
  out.flush();
}

}
