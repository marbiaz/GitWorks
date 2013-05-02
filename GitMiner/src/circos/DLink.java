package circos;

public class DLink implements Comparable<DLink> {

private DIdeogram left;
private DIdeogram right;
private int width;

public DLink(DIdeogram l, DIdeogram r, int w) {
  left = l;
  right = r;
  width = w;
}


public DIdeogram getLeft() {
  return left;
}


public DIdeogram getRight() {
  return right;
}


public int getWidth() {
  return width;
}


@Override
public int compareTo(DLink dl) {
  String s, ds;
  if (left.getName().compareTo(right.getName()) < 0)
    s = left.getName() + right.getName();
  else s = right.getName() + left.getName();
  if (dl.left.getName().compareTo(dl.right.getName()) < 0)
    ds = dl.left.getName() + dl.right.getName();
  else ds = dl.right.getName() + dl.left.getName();
  s += width;
  ds += dl.width;
  return s.compareTo(ds);
}


public boolean equals(Object o) {
  return (o instanceof DLink) && (compareTo((DLink)o) == 0);
}

}
