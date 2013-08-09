package circos;


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import gitworks.GitWorks;


public class CircosData {

ArrayList<DIdeogram> groupA;
ArrayList<DIdeogram> groupB;
ArrayList<DIdeogram> allIn;
ArrayList<DLink> links;
//ArrayList<DScatter> scatters;
int maxValueA;
int maxValueB;
int minValueA;
int minValueB;
int maxBandA;
int maxBandB;
int minBandA;
int minBandB;


public CircosData() {
  groupA = new ArrayList<DIdeogram>();
  groupB = new ArrayList<DIdeogram>();
  allIn = new ArrayList<DIdeogram>();
  links = new ArrayList<DLink>();
//  scatters = new ArrayList<DScatter>();
  maxValueA = maxValueB = maxBandA = maxBandB = Integer.MIN_VALUE;
  minValueA = minValueB = minBandA = minBandB = Integer.MAX_VALUE;
}


//public void addScatter(DIdeogram d, int value) {
//  scatters.add(new DScatter(d, value));
//}
//
//
//public void addScatter(String id, int value) {
//  scatters.add(new DScatter(allIn.get(Collections.binarySearch(allIn, id)), value));
//}


public void addLink(DLink l) {
  if (l.getLeft().equals(l.getRight())) return;
  GitWorks.addUnique(links, l);
}


public void addLink(String left, String right, int val) {
  if (left.equals(right)) return;
  DIdeogram dLeft = GitWorks.getElement(allIn, left);
  DIdeogram dRight = GitWorks.getElement(allIn, right);
  DLink l = new DLink(dLeft, dRight, val);
  addLink(l);
}


public ArrayList<DLink> getLinks() {
  return links;
}


public void addToSetA(DIdeogram f) {
  GitWorks.addUnique(allIn, f);
  groupA.add(f);
  if (f.getValue() < minValueA) minValueA = f.getValue();
  if (f.getValue() > maxValueA) maxValueA = f.getValue();
  if (f.getBands() < minBandA) minBandA = f.getBands();
  if (f.getBands() > maxBandA) maxBandA = f.getBands();
}


public void addToSetB(DIdeogram f) {
  GitWorks.addUnique(allIn, f);
  groupB.add(f);
  if (f.getValue() < minValueB) minValueB = f.getValue();
  if (f.getValue() > maxValueB) maxValueB = f.getValue();
  if (f.getBands() < minBandB) minBandB = f.getBands();
  if (f.getBands() > maxBandB) maxBandB = f.getBands();
}


public List<DIdeogram> getAllSets() {
  return allIn;
}


// returns the fork that is called name
public DIdeogram getDIdeogram(String name) {
  return GitWorks.getElement(allIn, name);
}


public void print() {
  for (DIdeogram di : allIn) {
    System.out.println(di.toString());
  }
}


public int getSize() {
  return allIn.size();
}


public int getMinA() {
  return minValueA;
}


public void setMinA(int min) {
  minValueA = min;
}


public int getMaxA() {
  return maxValueA;
}


public void setMaxA(int max) {
  this.maxValueA = max;
}


public int getMinB() {
  return minValueB;
}


public void setMinB(int min) {
  this.minValueB = min;
}


public int getMaxB() {
  return maxValueB;
}


public void setMaxB(int max) {
  this.maxValueB = max;
}


@Override
public String toString() {
  String res = "";
  for (DIdeogram di : allIn) {
    res += di.toString() + "\n";
  }
  return res;
}

}
