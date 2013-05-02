package circos;


import java.util.Iterator;


public class WriteCommitsToMain extends PrintCircosData {


private String mainForkName;
private int widthOfMain;
private int numberOfColors;


public WriteCommitsToMain(CircosData cd, String main_name) {
  super(cd, 50);
  mainForkName = main_name;
  numberOfColors = colorRange * 7;
}


private int getMaxLinkWidth() {
  Iterator<DLink> links_iterator = data.getLinks().iterator();
  if (!links_iterator.hasNext()) return 0;
  int max = links_iterator.next().getWidth();
  int width;
  while (links_iterator.hasNext()) {
    width = links_iterator.next().getWidth();
    if (width > max) {
      max = width;
    }
  }
  return max;
}


void buildIdeograms() {
  Iterator<DIdeogram> dIt = data.groupB.iterator();
  DIdeogram fork;
  GIdeogram gi;
  int widthMainLine = 0;
  int color_index;
  int max = data.getMaxB(); // getMaxLinkWidth(); // XXX
  while (dIt.hasNext()) {
//    link = links_iterator.next();
//    left_fork = link.getLeft();
//    right_fork = link.getRight();
//    if (left_fork.getName().equals(mainForkName)) {
//      ideograms.add(new GIdeogram(right_fork.getName(), right_fork.getName(), 0, link.getWidth()));
//      linksToDisplay.add(link);
//      widthMainLine = widthMainLine + link.getWidth();
//    }
//    if (right_fork.getName().equals(mainForkName)) {
    // ideograms.add(new GIdeogram(left_fork.getName(), left_fork.getName(), 0, link.getWidth()));
    // linksToDisplay.add(link);
    fork = dIt.next();
    gi = new GIdeogram(fork.getName(), fork.getName(), 0, fork.getValue());
    gi.setBands(fork.getBands());
    color_index = (gi.getEnd() - gi.getStart()) * numberOfColors / max;
    if (color_index == 0) color_index++;
    if (color_index > numberOfColors) {
      System.err.println("WARNING: Ideo Color=" + color_index + " for " + gi.getLabel());
      color_index = numberOfColors;
    }
    gi.setColor_label("color" + color_index);
    ideograms.add(gi);
    if (fork.getScatter() > Integer.MIN_VALUE)
      scatters.add(new GScatter(gi.getId(), gi.getStart(), gi.getEnd(), fork.getScatter(), fork
          .getScatter(), gi.getColor_label()));
    widthMainLine = widthMainLine + fork.getValue();// + link.getWidth()
    // }
  }
  widthOfMain = widthMainLine;
  fork = data.groupA.get(0);
  gi = new GIdeogram(fork.getName(), fork.getName(), 0, widthMainLine, "orange");
  gi.setBands(fork.getBands());
  if (fork.getScatter() > Integer.MIN_VALUE)
    scatters.add(new GScatter(
        gi.getId(), gi.getStart(), gi.getEnd(), fork.getScatter(), fork.getScatter(), gi.getColor_label()));
  ideograms.add(gi);

//  // set the colors of the ideograms
//  int color_range;
//  int max = data.getMaxB(); // getMaxLinkWidth(); // XXX
//  int color_index;
//  double f;
//  GIdeogram i;
////  if (max > numberOfColors) { // XXX
////    color_range = (max / numberOfColors) + 1;
////  }
////  else {
////    color_range = (numberOfColors / max) + 1;
////  }
//  //color_range = (int)(numberOfColors / Math.log(max)) + 1;
////  System.out.println("color range = " + color_range);
////  System.out.println("log max = " + (Math.log(max)));
//  Iterator<GIdeogram> ideogram_iterator = ideograms.iterator();
//  while (ideogram_iterator.hasNext()) {
//    i = ideogram_iterator.next();
//    if (!i.getLabel().equals(mainForkName)) {
//      //color_index = (int)(((Math.log(i.getEnd() - i.getStart()))) * color_range) + 1; // XXX
//      //color_index = ((int)((i.getEnd() - i.getStart()) / color_range) + 1);
//      color_index = (i.getEnd() - i.getStart()) * numberOfColors / max;
//      if (color_index == 0) color_index++;
//      if (color_index > numberOfColors) {
//        System.err.println("WARNING: Ideo Color=" + color_index + " for " + i.getLabel());
//        color_index = numberOfColors;
//      }
//      f = Math.log(i.getEnd() - i.getStart());
//      //System.out.println(i.getLabel() + " log of commits = " + f + " int log of commits = " + (int)f);
//      i.setColor_label("color" + color_index);
//    }
//  }
}


/**
 * This method fills the links collection with links that correspond to the CommitLinks of the
 * project All generated links have the same width and the same position on left and right ideograms
 * The color of the link is proportional to the width of the commitLink
 **/
void buildLinks() {
  DLink cl;
  GLink link;
  Iterator<DLink> linksToDisplay_iterator = data.getLinks().iterator();
  int max = getMaxLinkWidth();
  if (max == 0) return;
  int color_range;
  int startPosOnMain = (int)(5 * widthOfMain / 8); //(widthOfMain / 2) - (max / 2);
  int endPosOnMain;
  int color_index; int i = 0; int w = (widthOfMain / 4) / data.getLinks().size();
//  if (max > numberOfColors) { // XXX
//    color_range = (max / numberOfColors) + 1;
//  }
//  else {
//    color_range = (numberOfColors / max) + 1;
//  }
  //color_range = (int)(numberOfColors / Math.log(max)) + 1;
  while (linksToDisplay_iterator.hasNext()) {
    cl = linksToDisplay_iterator.next(); // System.err.println(i + " " + cl.getRight().getName());
    endPosOnMain = startPosOnMain + cl.getWidth();
//    color_index = (cl.getWidth() / color_range) + 1; // XXX
    //color_index = ((int)((Math.log(cl.getWidth()))) * color_range) + 1;
    color_index = cl.getWidth() * numberOfColors / max;
    if (color_index == 0) color_index++;
    if (color_index > numberOfColors) {
      System.err.println("WARNING: Link Color=" + color_index + " for " + cl.getRight().getName());
      color_index = numberOfColors;
    }
//    if (cl.getLeft().getName().equals(mainForkName)) {
//      link = new GLink(mainForkName, cl.getRight().getName(), startPosOnMain, endPosOnMain, 0,
//          cl.getWidth(), "color" + color_index);
       link = new GLink(mainForkName, cl.getRight().getName(), startPosOnMain - (i * w), startPosOnMain - (++i * w),
           0, cl.getWidth(), "color" + color_index);
//    } else {
////      link = new GLink(cl.getLeft().getName(), mainForkName, 0, cl.getWidth(), startPosOnMain,
////          endPosOnMain, "color" + color_index);
//       link = new GLink(cl.getLeft().getName(), mainForkName, 0, cl.getWidth(), 
//           startPosOnMain - (i * w), startPosOnMain - (++i * w), "color" + color_index);
//    }
    links.add(link);
  }
}

}
