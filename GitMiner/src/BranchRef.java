
import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;


public class BranchRef implements Comparable<Object>, Externalizable {

String name;
ObjectId id;


public BranchRef() {}


BranchRef(Ref r) {
  name = r.getName();
  id = r.getLeaf().getObjectId();
}


@Override
public String toString() {
  String out = "";
  out += name + " <" + id.getName() + ">";
  return out;
}


@Override
public void readExternal(ObjectInput arg0) throws IOException, ClassNotFoundException {
  // TODO Auto-generated method stub

}


@Override
public void writeExternal(ObjectOutput arg0) throws IOException {
  // TODO Auto-generated method stub

}


@Override
public int compareTo (Object o) {
  if (o instanceof BranchRef) {
    return this.name.compareTo(((BranchRef)o).name);
  } else if (o instanceof Ref) {
    return this.name.compareTo(((Ref)o).getName());
  } else if (o instanceof String) {
    return this.name.compareTo((String)o);
  }
  return -1;
}


@Override
public boolean equals(Object o) {
  return this.compareTo(o) == 0;
}

}
