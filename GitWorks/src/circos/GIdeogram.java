package circos;

import gitworks.GitWorks;

/**
 * 
 * @author bbaudry
 * This class encapsulates the data to draw an ideogram in a circos image: the chromosome and bands definitions
 */

public class GIdeogram {
	private String id;
	private String label;
	private int start;
	private int end;
	private String color_label;
	private int bands;
	//private LinkedList<Band>;
	
	
  
  public int getBands() {
    return bands;
  }

  
  public void setBands(int bands) {
    this.bands = bands;
  }

  public GIdeogram(String i, String l, int s, int e, String color){
		id = i;
		label = l.replaceAll(GitWorks.safe_sep + ".*", "").replaceAll("@.*", "");
		start = s;
		end = e;
		color_label = color;
		bands = 0;
	}

	public GIdeogram(String i, String l, int s, int e){
		id = i;
		label = l.replaceAll(GitWorks.safe_sep + ".*", "").replaceAll("@.*", "");
		start = s;
		end = e;
		bands = 0;
	}
	
	/**Generated getters and setters for the attributes of the class**/
	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getLabel() {
		return label;
	}

	public void setLabel(String label) {
		this.label = label;
	}

	public int getStart() {
		return start;
	}

	public void setStart(int start) {
		this.start = start;
	}

	public int getEnd() {
		return end;
	}

	public void setEnd(int end) {
		this.end = end;
	}

	public String getColor_label() {
		return color_label;
	}

	public void setColor_label(String color_label) {
		this.color_label = color_label;
	}

}
