package gitworks;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.ArrayList;
import java.util.Iterator;


public class FeatureList extends ArrayList<Features> implements Externalizable {


FeatureList(int size) {
  super(size);
}


@Override
public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
  Features f;
  int size = in.readInt();
  this.ensureCapacity(size);
  for (int i = 0; i < size; i++) {
    f = new Features();
    f.readExternal(in);
    this.add(f);
  }
}


@Override
public void writeExternal(ObjectOutput out) throws IOException {
  out.writeInt(size());
  Iterator<Features> fit = iterator();
  while (fit.hasNext()) {
    fit.next().writeExternal(out);
  }
  out.flush();
}

}
