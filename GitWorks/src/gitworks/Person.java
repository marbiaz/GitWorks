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


public String getUniqueID() {
  return email;
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
  String oID = "";
  if (o instanceof Person) {
    oID = ((Person)o).getUniqueID();
  } else if (o instanceof PersonIdent) {
    oID = ((PersonIdent)o).getEmailAddress();
  }
  return email.compareTo(oID);
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
