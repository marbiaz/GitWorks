package circos;


import java.awt.Color;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;


public abstract class PrintCircosData {

CircosData data;
LinkedList<GIdeogram> ideograms;
LinkedList<GLink> links;
LinkedList<GScatter> scatters;
ArrayList<String> colors; // an array of color names
int colorRange; // this range fixes the number of colors we generate on a rainbow gradient.
                // The total number of available colors is this number * 7


PrintCircosData(CircosData cd, int cRange) {
  data = cd;
  colorRange = cRange;
  ideograms = new LinkedList<GIdeogram>();
  colors = new ArrayList<String>();
  scatters = new LinkedList<GScatter>();
  links = new LinkedList<GLink>();
}


public void createCircosFiles(String colorFileName, String ideogramsFileName, String linksFileName, String scattersFileName) {
  printColors(colorFileName, colorRange);

  buildIdeograms();
  buildLinks();
  printKaryotype(ideogramsFileName);
  if (linksFileName != null) printLinks(linksFileName);
  if (scattersFileName != null) printScatters(scattersFileName);
}


private void printScatters(String fileName) {
  PrintWriter p = null;
  try {
    p = new PrintWriter(fileName, "UTF-8");
    Iterator<GScatter> sIt = scatters.iterator();
    while (sIt.hasNext()) {
      p.println(sIt.next().toString());
    }
  }
  catch (FileNotFoundException e) {
    // TODO Auto-generated catch block
    e.printStackTrace();
  }
  catch (UnsupportedEncodingException e) {
    // TODO Auto-generated catch block
    e.printStackTrace();
  }
  finally {
    p.flush();
    p.close();
  }
}


private void printBands(PrintWriter destination, int maxBand) {
  String line;
  GIdeogram gi;
  int bandcounter = 0;
  int forkSize;
  int bandWidth;
  int colorIndex;
  int start;
  int bandsNumber;
  int bandStart;
  int bandEnd;
  Iterator<GIdeogram> gIt = ideograms.iterator();
  // String[] colors = getColors(Color.GREEN,Color.RED,forks_set.getMaxAuthors());
  // System.out.println(forks_set.getMaxAuthors());
  while (gIt.hasNext()) {
    gi = gIt.next();
    bandsNumber = gi.getBands();
    if (bandsNumber > 0) {
      // destination.println(bandsNumber);
      forkSize = gi.getEnd() - gi.getStart();
      bandWidth = forkSize / bandsNumber / 2;
      if (bandWidth == 0) bandWidth = 1;// XXX
      colorIndex = colors.size() / maxBand; // (colors.size() - 1) / bandsNumber FIXME what if maxBand is very large???
      System.err.println("colorIndex=" + colorIndex + "; maxBand=" + maxBand);
      start = forkSize / 4; // 0; // XXX
      for (int i = 0; i < bandsNumber; i++) {//bandsNumber - 1
        bandcounter++;
        bandStart = start;
        bandEnd = start + bandWidth;
        line = "band " + gi.getId() + " author" + bandcounter + " author" + bandcounter + " "
            + bandStart + " " + bandEnd + " " + (String)colors.get((i + 1) * colorIndex);
        destination.println(line);
        start = start + bandWidth;
      }
      // we treat the case of the last band separately because the width of bands is computed with
      // an int division, but we want to be sure that the last band covers exactly the rest of the
      // available space
//      bandcounter++;
//      line = "band " + di.getName() + " author" + bandcounter + " author" + bandcounter + " "
//          + start + " " + di.getValue() + " " + (String)colors.get(bandsNumber * colorIndex);
//      destination.println(line);
    }
  }
}


private void printKaryotype(String ideogramsFileName) {
  PrintWriter karyofile = null;
  String line;
  Iterator<GIdeogram> ideograms_iterator = ideograms.iterator();
  GIdeogram ideogram;
  int maxBand = 0;
  try {
    karyofile = new PrintWriter(ideogramsFileName, "UTF-8");
    while (ideograms_iterator.hasNext()) {
      ideogram = ideograms_iterator.next();
      maxBand = Math.max(maxBand, ideogram.getBands());
      line = "chr - " + ideogram.getId() + " " + ideogram.getLabel() + " " + ideogram.getStart()
          + " " + ideogram.getEnd() + " " + ideogram.getColor_label();
      karyofile.println(line);
    }
    if (maxBand > 0) printBands(karyofile, maxBand);
  }
  catch (FileNotFoundException e) {
    // TODO Auto-generated catch block
    e.printStackTrace();
  }
  catch (UnsupportedEncodingException e) {
    // TODO Auto-generated catch block
    e.printStackTrace();
  }
  finally {
    if (karyofile != null) {
      karyofile.flush();
      karyofile.close();
    }
  }
}


private void printLinks(String linksFileName) {
  PrintWriter destination;
  String line1;
  String line2;
  int counter = 0;
  Iterator<GLink> links_iterator = links.iterator();
  GLink link;
  try {
    destination = new PrintWriter(linksFileName, "UTF-8");
    while (links_iterator.hasNext()) {
      link = links_iterator.next();
      line1 = "commit" + counter + " " + link.getLeft() + " " + link.getStart_left() + " "
          + link.getEnd_left() + " " + "color=" + link.getColor_label();
      line2 = "commit" + counter + " " + link.getRight() + " " + link.getStart_right() + " "
          + link.getEnd_right() + " " + "color=" + link.getColor_label();
      destination.println(line1);
      destination.println(line2);
      counter++;
    }
    destination.flush();
    destination.close();
  }
  catch (FileNotFoundException e) {
    // TODO Auto-generated catch block
    e.printStackTrace();
  }
  catch (UnsupportedEncodingException e) {
    // TODO Auto-generated catch block
    e.printStackTrace();
  }

}


// generate the colors in a rainbow gradient with 'range' slots between each basic color
// names of the colors are stored in the colors attribute of the class
// mapping between names and RGB codes are stored in a file
private void printColors(String colorFileName, int range) {
  Color VIOLET = new Color(128, 0, 128);
  Color INDIGO = new Color(75, 0, 130);
  // Color.WHITE, Color.WHITE, VIOLET, INDIGO, Color.BLUE,Color.GREEN, Color.YELLOW, Color.ORANGE,
  // Color.RED
  try {
    PrintWriter colors_file = new PrintWriter(colorFileName, "UTF-8");
    int index = 1;
    colors.addAll(generateGradient(Color.WHITE, VIOLET, index, colors_file, range));
    index = index + range;
    colors.addAll(generateGradient(VIOLET, INDIGO, index, colors_file, range));
    index = index + range;
    colors.addAll(generateGradient(INDIGO, Color.BLUE, index, colors_file, range));
    index = index + range;
    colors.addAll(generateGradient(Color.BLUE, Color.GREEN, index, colors_file, range));
    index = index + range;
    colors.addAll(generateGradient(Color.GREEN, Color.YELLOW, index, colors_file, range));
    index = index + range;
    colors.addAll(generateGradient(Color.YELLOW, Color.ORANGE, index, colors_file, range));
    index = index + range;
    colors.addAll(generateGradient(Color.ORANGE, Color.RED, index, colors_file, range));
    colors_file.close();
  }
  catch (FileNotFoundException e) {
    // TODO Auto-generated catch block
    e.printStackTrace();
  }
  catch (UnsupportedEncodingException e) {
    // TODO Auto-generated catch block
    e.printStackTrace();
  }
}


// generates a gradient of <range> colors between start and end
private ArrayList<String> generateGradient(Color startColor, Color endColor, int colorNumber,
    PrintWriter colors_file, int range) {
  int red;
  int green;
  int blue;
  float ratio;
  ArrayList<String> names = new ArrayList<String>();
  for (int i = 0; i < range; i++) {
    ratio = (float)i / (float)range;
    red = (int)(endColor.getRed() * ratio + startColor.getRed() * (1 - ratio));
    green = (int)(endColor.getGreen() * ratio + startColor.getGreen() * (1 - ratio));
    blue = (int)(endColor.getBlue() * ratio + startColor.getBlue() * (1 - ratio));
    names.add("color" + colorNumber);
    colors_file.println("color" + colorNumber + " = " + red + "," + green + "," + blue);
    colorNumber++;
  }
  colors_file.flush();
  return names;
}


abstract void buildIdeograms();


abstract void buildLinks();

}
