package gitworks;


import java.util.ArrayList;


public class MetaEdge implements Comparable<Object> {

int ID;
private ArrayList<Commit> internals;
Commit first; // ancestor commit (oldest in this meta edge) 
Commit last; // latest commit in this meta edge


int getWeight() {
  return (internals == null) ? 0 : internals.size();
}


public MetaEdge(int id) {
  ID = id;
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


public String toString() {
  return "ID = " + ID + " ; first = " + first.id.getName()
      + " ; last = " + last.id.getName() + " ; weight = " + getWeight();
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
    return this.compareTo((MetaEdge)o) == 0;
}

}
