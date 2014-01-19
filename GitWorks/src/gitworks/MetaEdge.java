package gitworks;


import java.util.ArrayList;


public class MetaEdge implements Comparable<Object> {

int ID;
private ArrayList<Commit> internals;
Commit first; // ancestor commit (oldest in this meta edge)
Commit last; // latest commit in this meta edge
long startTimestamp;
long endTimestamp;


int getWeight() {
  return (internals == null) ? 0 : internals.size();
}


public MetaEdge(int id) {
  ID = id;
  internals = null;
  startTimestamp = -1;
  endTimestamp = -1;
}


MetaEdge(MetaEdge me) {
  ID = me.ID;
  first = null;
  last = null;
  internals = null;
  startTimestamp = -1;
  endTimestamp = -1;
}


void addInternal(Commit c) {
  if (internals == null)
    internals = new ArrayList<Commit>();
  internals.add(c);
}


ArrayList<Commit> getInternals() {
  return internals;
}


int getSpan() {
  return last.layer - first.layer;
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
