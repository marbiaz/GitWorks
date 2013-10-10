package jfreechart;


import gitworks.GitWorks;

import java.io.File;

import javax.swing.JFrame;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.ChartUtilities;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.axis.NumberTickUnit;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;


@SuppressWarnings("serial")
public class XYSeriesChart extends JFrame {

XYSeriesCollection series;
JFreeChart XYLineChart;

public XYSeriesChart(Number[][][] data, String[] labels) {
  series = new XYSeriesCollection();
  XYSeries dataset;
  int j, i = 0;
  double minx = Double.MAX_VALUE, maxx = Double.MIN_VALUE;
  for (Number[][] nums : data) {
    dataset = new XYSeries("Dataset #" + (++i));
    for (j = 0; j < nums[0].length; j++) {
      dataset.add(nums[0][j], nums[1][j]);
      minx = Math.min(minx, nums[0][j].doubleValue());
      maxx = Math.max(maxx, nums[0][j].doubleValue());
      //System.out.println(nums[0][j] + " " + nums[1][j]);
    }//System.out.println();System.out.println();
    series.addSeries(dataset);
  }
  XYLineChart = ChartFactory.createXYLineChart(labels[0], labels[1],
      labels[2], series, PlotOrientation.VERTICAL, true, true, false);
//  // Control Number Range for X Axis
  XYPlot xyPlot = XYLineChart.getXYPlot();
  NumberAxis domainAxis = (NumberAxis) xyPlot.getDomainAxis();
  domainAxis.setRange(minx, maxx);
  domainAxis.setTickUnit(new NumberTickUnit(1));
}


public void plotFile() {

  try {
    int width = 640;
    int height = 480;
    File fileChart = new File(XYLineChart.getTitle().getText() + ".png");
    ChartUtilities.saveChartAsPNG(fileChart, XYLineChart, width, height);
  } catch (Exception e) {
    System.err.println(e);
  }
}


public void plotWindow() {

  try {
    setTitle(XYLineChart.getTitle().getText());
    setSize(640, 480);
    setContentPane(new ChartPanel(XYLineChart));
    setVisible(true);
    GitWorks.waitForUser("Plot Window");
  } catch (Exception e) {
    System.err.println(e);
  }
}

}
