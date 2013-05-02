package circos;

import gitworks.GitWorks;

public class GLink {
	
	private String left;
	private String right;
	private int start_left;
	private int start_right;
	private int end_left;
	private int end_right;
	private String color_label;
	
	public GLink(String l, String r, int start_l,  int end_l,  int start_r, int end_r, String color){
		left = l;
		right = r;
		start_left = start_l;
		start_right = start_r;
		end_left = end_l;
		end_right = end_r;
		color_label = color;
	}
	
	public GLink(String l, String r, int start_l, int start_r, int end_l, int end_r){
		left = l;
		right = r;
		start_left = start_l;
		start_right = start_r;
		end_left = end_l;
		end_right = end_r;
	}

	/**Automatically generated getters and setters**/
	public String getLeft() {
		return left;
	}

	public void setLeft(String left) {
		this.left = left;
	}

	public String getRight() {
		return right;
	}

	public void setRight(String right) {
		this.right = right;
	}

	public int getStart_left() {
		return start_left;
	}

	public void setStart_left(int start_left) {
		this.start_left = start_left;
	}

	public int getStart_right() {
		return start_right;
	}

	public void setStart_right(int start_right) {
		this.start_right = start_right;
	}

	public int getEnd_left() {
		return end_left;
	}

	public void setEnd_left(int end_left) {
		this.end_left = end_left;
	}

	public int getEnd_right() {
		return end_right;
	}

	public void setEnd_right(int end_right) {
		this.end_right = end_right;
	}

	public String getColor_label() {
		return color_label;
	}

	public void setColor_label(String color_label) {
		this.color_label = color_label;
	}

}
