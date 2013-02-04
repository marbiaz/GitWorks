package gitworks;


import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;


public class BranchRef implements Comparable<Object>, Externalizable {

String name;
ObjectId id;
int index;

public BranchRef() {}


BranchRef(Ref r) {
  name = r.getName().substring(13); // strip "refs/remotes/"
  id = r.getLeaf().getObjectId().copy(); // get the commit id r points at
}


@Override
public String toString() {
  String out = "";
  out += name + " <" + id.getName() + ">";
  return out;
}


@Override
public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
  // index is set by GitMIner
  id = (ObjectId)in.readObject();
  name = in.readUTF();
}


@Override
public void writeExternal(ObjectOutput out) throws IOException {
  // index is not serialized
  out.writeObject(id);
  out.writeUTF(name);
  out.flush();
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
