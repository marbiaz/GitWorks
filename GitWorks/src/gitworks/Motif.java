package gitworks;


import java.util.ArrayList;
import java.util.HashMap;


public class Motif implements Comparable<Object> {

String name;
ArrayList<MotifOccurrence> occurrences;
ArrayList<Double> cStats; // min mean med max stdev
int minLayer;
int maxLayer;
int numNodes;
int numEdges;
double zScore;


public Motif(String n, int nodes, int edges) {
  name = n;
  numNodes = nodes;
  numEdges = edges;
  occurrences = new ArrayList<MotifOccurrence>();
  cStats = new ArrayList<Double>();
  minLayer = Integer.MAX_VALUE;
  maxLayer = 0;
  zScore = 0;
}


void addOccurrence(MotifOccurrence mo) {
  minLayer = Math.min(minLayer, mo.minLayer);
  maxLayer = Math.max(maxLayer, mo.maxLayer);
  GitWorks.addUnique(occurrences, mo);
}


void addOccurrence(ArrayList<MetaEdge> edges, HashMap<String, ArrayList<MetaEdge>> twins) {
  MotifOccurrence mo = new MotifOccurrence(edges, twins);
  minLayer = Math.min(minLayer, mo.minLayer);
  maxLayer = Math.max(maxLayer, mo.maxLayer);
  GitWorks.addUnique(occurrences, mo);
}


public String toString() {
  String res = name + " : ";
  res += occurrences.size();
  return res;
}


int getNumParallels() {
  int res = 0;
  for (MotifOccurrence mo : occurrences)
    res += mo.numParallels;
  return res;
}


@Override
public int compareTo(Object o) {
  if (o instanceof Motif)
    return name.compareTo(((Motif)o).name);
  else if (o instanceof String)
    return name.compareTo(((String)o));
  else
    return -1;
}

}
