package gitworks;


import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.PrintStream;
import java.util.Iterator;


/**
 * @author Marco Biazzini
 */
public class ForkEntry implements Comparable<Object>, Externalizable {

private  String owner;
private  String name;
// private long since;
private int watchers = -1;
private int dfsMaxWatchers = 0;
private int dfsNumForks = 0;
private int dfsChildrenWatchers = 0;
private int dfsAggregateDepth = 0;
private int dfsMaxChildren = 0;
private ForkList forks = null;
private ForkEntry parent = null;
private boolean dfsOk = true;

// this operator requires a PrintStream as parameter
static DfsOperator printAllForks  = new DfsOperator() {

  public int getID() {
    return 4;
  }

  public boolean runOnce() {
    return true;
  }

  public void initialize(ForkEntry f) {}

  public void run(ForkEntry fe, Object arg) {
    PrintStream out = ((PrintStream)arg);
    out.print(fe.getId() + " ");
  }

  public void finalize(ForkEntry f) {}
};

//this operator requires a ForkList as a r/w parameter
static DfsOperator addTreeToList  = new DfsOperator() {

  public int getID() {
    return 3;
  }

  public boolean runOnce() { // TODO: equivalent to putting everything in finalize() or initialize() ???
    return true;
  }

  public void initialize(ForkEntry f) {}

  public void run(ForkEntry fe, Object arg) {
    ForkList l = (ForkList)arg;
    l.add(fe);
  }

  public void finalize(ForkEntry f) {} //TODO: add some trimming to save space
};
// TODO: rewrite operators with apply_pre() apply_in() apply_post()
// this operator requires an int[5] as r/w parameter
public static DfsOperator computeAggregates = new DfsOperator() {

  public int getID() {
    return 1;
  }

  public boolean runOnce() {
    return false;
  }
  
  public void initialize(ForkEntry f) {
    f.dfsChildrenWatchers = 0;
    f.dfsNumForks = 0;
    f.dfsMaxChildren = 0;
    f.dfsMaxWatchers = 0;
    f.dfsAggregateDepth = 0;
  }

  public void run(ForkEntry fe, Object arg) {
    int[] res = (int[])arg;
    if (res[3] != 0) {
      fe.dfsMaxWatchers = (fe.dfsMaxWatchers < res[0]) ? res[0] : fe.dfsMaxWatchers;
      res[0] = fe.dfsMaxWatchers;
      fe.dfsChildrenWatchers += res[1];
      res[1] = fe.dfsChildrenWatchers;
      fe.dfsNumForks += res[2];
      res[2] = fe.dfsNumForks;
      fe.dfsAggregateDepth = (fe.dfsAggregateDepth < res[3]) ? res[3] : fe.dfsAggregateDepth;
      res[3] = fe.dfsAggregateDepth;
      fe.dfsMaxChildren = (fe.dfsMaxChildren < res[4]) ? res[4] : fe.dfsMaxChildren;
      res[4] = fe.dfsMaxChildren;
    }
    if (fe.watchers > res[0]) res[0] = fe.watchers;
    res[1] += fe.watchers;
    res[2]++;
    res[3]++;
    if (fe.howManyForks() > res[4]) res[4] = fe.howManyForks();
  }

  public void finalize(ForkEntry f) {
    f.dfsOk = true;
  }
};


public static boolean isValidId(String s) {
  int i = s.indexOf(GitWorks.id_sep);
  boolean b = !s.matches("[0-9A-Za-z_/\\.\\-]+") // contains invalid characters
      || i <= 0 // owner is an empty string
      || i == s.length() - 1 // name is an empty string
      || s.indexOf(GitWorks.id_sep, i + 1) != -1; // the format is not 'owner/name'
  return !b;
}


protected ForkEntry() {};


public ForkEntry(String id) {
  String[] t = id.split(GitWorks.id_sep);
  name = t[1];
  owner = t[0];
}


ForkEntry(String owner, String name, int watchers) {
  this.name = name;
  this.owner = owner;
  this.watchers = watchers;
}


boolean hasForks() {
  return forks == null ? false : forks.size() > 0;
}


boolean areDfsResultsValid() {
  return dfsOk;
}


boolean isRoot() {
  return parent == null;
}


private void setAncestorsDfsKo() {
  ForkEntry cur = this;
  while (!cur.isRoot()) {
    cur = cur.parent;
    cur.dfsOk = false;
  }
}


// after an addition, if dfs aggregates were valid for this entry, the are reset to depth 1
// and flagged as invalid upwards.
boolean addFork(ForkEntry f) throws Exception {
  int res;
  if (!f.equals(this) && f.parent == null) {
    if (forks == null) forks = new ForkList();
    f.parent = this;
    res = forks.add(f);
    if (res < 0) {
      if (dfsOk) {
        if (dfsAggregateDepth <= 1) {
          dfsMaxWatchers = (f.watchers > dfsMaxWatchers) ? f.watchers : dfsMaxWatchers;
          dfsMaxChildren = (forks.size() > dfsMaxChildren) ? forks.size() : dfsMaxChildren;
          dfsChildrenWatchers += f.watchers;
          dfsAggregateDepth = 1;
          dfsNumForks = forks.size();
        } else {
          GitWorks.dfsVisit(1, this, ForkEntry.computeAggregates, new int[5]);
        }
        setAncestorsDfsKo();
      }
      return true;
    }
  }
  return false;
}


boolean removeFork(String fid) throws Exception {
  ForkEntry fe = new ForkEntry(fid);
  return removeFork(fe);
}


// It updates valid aggregates only from the parent downward, according to the current
// dfs_aggregate_depth; it flags dfs as invalid upwards
boolean removeFork(ForkEntry f) throws Exception {
  if (forks != null) {
    if (forks.remove(f) != null && dfsOk) {
      GitWorks.dfsVisit(dfsAggregateDepth, this, ForkEntry.computeAggregates, new int[5]);
      setAncestorsDfsKo();
      return true;
    }
  }
  return false;
}


// It updates the aggregates only from the parent downward, according to the current
// dfs_aggregate_depth; it flags dfs as invalid upwards
boolean removeForks(ForkEntry[] fks) throws Exception {
  boolean res = false;
  if (forks != null) {
    for (ForkEntry f : fks) {
      if (forks.remove(f) != null) {
        res = true;
      }
    }
    if (res && dfsOk) {
      GitWorks.dfsVisit(dfsAggregateDepth, this, ForkEntry.computeAggregates, new int[5]);
      setAncestorsDfsKo();
    }
  }
  return res;
}


public String getId() {
  return "".equals(owner) ? null : owner + GitWorks.id_sep + name;
}


int howManyForks() {
  return (forks == null) ? 0 : forks.size();
}


ForkEntry getFork(String id) {
  return forks.get(id);
}


Iterator<ForkEntry> getForks() {
  return forks.iterator();
}


int getWatchers() {
  return watchers;
}


private String getForksIds() {
  String out = "";
  Iterator<ForkEntry> it = getForks();
  out += it.next().getId();
  while (it.hasNext()) {
    out += GitWorks.list_sep + it.next().getId();
  }
  return out;
}


public String toString() {
  String out = getId() + GitWorks.field_sep + (hasForks() ? forks.size() : "0")
      + GitWorks.field_sep + watchers + GitWorks.field_sep + dfsNumForks
      + GitWorks.field_sep + dfsMaxChildren + GitWorks.field_sep + dfsChildrenWatchers
      + GitWorks.field_sep + dfsMaxWatchers + (hasForks() ? GitWorks.field_sep + getForksIds() : "");
  return out;
}


public boolean equals(Object o) {
  return this.compareTo((ForkEntry)o) == 0;
}


@Override
public int compareTo(Object o) {
  if (o instanceof ForkEntry)
    return getId().compareTo(((ForkEntry)o).getId());
  if (o instanceof String)
    return getId().compareTo((String)o);
  return -1;
}


@Override
public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
  // parent is set by the caller if needed (i.e. this is not a root)
  owner = in.readUTF();
  name = in.readUTF();
  watchers = in.readInt();
  dfsOk = in.readBoolean();
  dfsMaxWatchers = in.readInt();
  dfsChildrenWatchers = in.readInt();
  dfsMaxChildren = in.readInt();
  dfsNumForks = in.readInt();
  dfsAggregateDepth = in.readInt();
  int size = in.readInt();
  if (size > 0) {
    ForkEntry fe;
    forks = new ForkList();
    for (int i = 0; i < size; i++) {
      fe = new ForkEntry();
      fe.parent = this;
      fe.readExternal(in);
      forks.add(fe);
    }
  }
}


@Override
public void writeExternal(ObjectOutput out) throws IOException {
  // parent is not serialized.
  out.writeUTF(owner);
  out.writeUTF(name);
  out.writeInt(watchers);
  out.writeBoolean(dfsOk);
  out.writeInt(dfsMaxWatchers);
  out.writeInt(dfsChildrenWatchers);
  out.writeInt(dfsMaxChildren);
  out.writeInt(dfsNumForks);
  out.writeInt(dfsAggregateDepth);
  int size = howManyForks();
  out.writeInt(size);
  for (int i = 0; i < size; i++) {
    forks.get(i).writeExternal(out);
  }
  out.flush();
}

}
