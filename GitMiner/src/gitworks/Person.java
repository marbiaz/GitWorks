package gitworks;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import org.eclipse.jgit.lib.PersonIdent;


public class Person implements Comparable<Object>, Externalizable {

String name;
String email;


Person(PersonIdent p) {
  name = p.getName();
  email = p.getEmailAddress();
}


Person() {
  name = "";
  email = "";
}


@Override
public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
  name = in.readUTF();
  email = in.readUTF();
}


@Override
public void writeExternal(ObjectOutput out) throws IOException {
  out.writeUTF(name);
  out.writeUTF(email);
  out.flush();
}


@Override
public int compareTo(Object o) {
  int res;
  if (o instanceof Person) {
    res = name.compareTo(((Person)o).name);
    if (res == 0) return email.compareTo(((Person)o).email);
    return res;
  }
  if (o instanceof PersonIdent) {
    res = name.compareTo(((PersonIdent)o).getName());
    if (res == 0) return email.compareTo(((PersonIdent)o).getEmailAddress());
    return res;
  }
  return -1;
}


@Override
public boolean equals(Object o) {
  return this.compareTo(o) == 0;
}


@Override
public String toString() {
  String out = "";
  out += name + " [ " + email + " ]";
  return out;
}

}
