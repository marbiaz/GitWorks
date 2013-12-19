package gitworks;


import java.util.ArrayList;


public class MetaEdge implements Comparable<Object> {

int ID;
private ArrayList<Commit> internals;
Commit first; // ancestor commit (oldest in this meta edge)
Commit last; // latest commit in this meta edge
int layer;


int getWeight() {
  return (internals == null) ? 0 : internals.size();
}


public MetaEdge(int id) {
  ID = id;
  internals = null;
  layer = 0;
}


MetaEdge(MetaEdge me) {
  ID = me.ID;
  layer = me.layer;
  first = null;
  last = null;
  internals = null;
}


void addInternal(Commit c) {
  if (internals == null)
    internals = new ArrayList<Commit>();
  internals.add(c);
}


ArrayList<Commit> getInternals() {
  return internals;
}


int getNumAuthors() {
  ArrayList<String> res = new ArrayList<String>();
  GitWorks.addUnique(res, first.getAuthoringInfo().getEmailAddress());
  GitWorks.addUnique(res, last.getAuthoringInfo().getEmailAddress());
  if (internals != null) for (Commit c : internals)
    GitWorks.addUnique(res, c.getAuthoringInfo().getEmailAddress());
  return res.size();
}


public String toString() {
  return "ID = " + ID + " ; first = " + first.id.getName().substring(0, 6)
      + " ; last = " + last.id.getName().substring(0, 6) + " ; weight = " + getWeight();
}


@Override
public int compareTo(Object o) {
  if (o instanceof Integer)
    return ID - ((Integer)o).intValue();
  else if (o instanceof MetaEdge)
    return ID - ((MetaEdge)o).ID;
  else return -1;
}


@Override
public boolean equals(Object o) {
    return this.compareTo(o) == 0;
}

}
