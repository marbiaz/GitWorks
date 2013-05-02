package circos;


public class DIdeogram implements Comparable<Object> {

private String name;
private int value;
private int bands;
private int scatter;



public int getScatter() {
  return scatter;
}



public void setScatter(int scatter) {
  this.scatter = scatter;
}


public int getValue() {
  return value;
}


public void setValue(int value) {
  this.value = value;
}


public int getBands() {
  return bands;
}


public void setBands(int bands) {
  this.bands = bands;
}


public DIdeogram(String n, int c, int a) {
  name = n;
  value = c;
  bands = a;
  scatter = Integer.MIN_VALUE;
}


public String getName() {
  return name;
}


public String toString() {
  String res = "Name: [" + name + "] ; Value: [" + value
      + "] ; Band: [" + bands + "] ; Scatter : [" + scatter + "].";
  return res;
}


@Override
public int compareTo(Object o) {
  if (o instanceof String)
    return name.compareTo((String)o);
  else
    return name.compareTo(((DIdeogram)o).name);
}


public boolean equals(Object o) {
  return compareTo(o) == 0;
}


}
