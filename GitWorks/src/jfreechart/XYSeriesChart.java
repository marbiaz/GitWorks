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
double minx;
double maxx;


public XYSeriesChart(String[] labels) {
  init(labels);
}


public XYSeriesChart(Number[][][] data, String[] labels) {
  init(labels);
  int i = 0;
  for (Number[][] nums : data) {
    addDataset("Dataset #" + (++i), nums[0], nums[1]);
  }
}


private void init(String[] labels) {
  minx = Double.MAX_VALUE;
  maxx = Double.MIN_VALUE;
  series = new XYSeriesCollection();
  XYLineChart = ChartFactory.createXYLineChart(labels[0], labels[1], labels[2], series,
      PlotOrientation.VERTICAL, true, true, false);
}


public void addDataset(String name, double[] xaxis, double[] yaxis) {
  Number[] x = new Number[xaxis.length];
  Number[] y = new Number[yaxis.length];
  int i = 0;
  for (double d : xaxis)
    x[i++] = d;
  i = 0;
  for (double d : yaxis)
    y[i++] = d;
  addDataset(name, x, y);
}


public void addDataset(String name, int[] xaxis, int[] yaxis) {
  Number[] x = new Number[xaxis.length];
  Number[] y = new Number[yaxis.length];
  int i = 0;
  for (int d : xaxis)
    x[i++] = d;
  i = 0;
  for (int d : yaxis)
    y[i++] = d;
  addDataset(name, x, y);
}


public void addDataset(String name, Number[] xaxis, Number[] yaxis) {
  XYSeries dataset = new XYSeries(name);
  if (xaxis == null) {
    xaxis = new Integer[yaxis.length];
    for (int i = 0; i < xaxis.length; i++)
      xaxis[i] = i + 1;
  }
  if (yaxis == null) {
    yaxis = new Integer[xaxis.length];
    for (int i = 0; i < yaxis.length; i++)
      yaxis[i] = i + 1;
  }
  for (int j = 0; j < xaxis.length; j++) {
    dataset.add(xaxis[j], yaxis[j]);
    minx = Math.min(minx, xaxis[j].doubleValue());
    maxx = Math.max(maxx, xaxis[j].doubleValue());
    // System.out.println(xaxis[j] + " " + yaxis[j]);
  }// System.out.println();System.out.println();
  series.addSeries(dataset);
}


private void setXRange() {
  XYPlot xyPlot = XYLineChart.getXYPlot();
  NumberAxis domainAxis = (NumberAxis)xyPlot.getDomainAxis();
  domainAxis.setRange(minx - 1, maxx + 1);
  domainAxis.setTickUnit(new NumberTickUnit(1));
}


public void plotFile() {
  setXRange();
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
  setXRange();
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
