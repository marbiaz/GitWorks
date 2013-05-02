package circos;


class GScatter {

String id;
int start;
int end;
int value;
int size;
String color_label;


GScatter(String id, int start, int end, int value, int size, String color_label) {
  this.id = id;
  this.start = start;
  this.end = end;
  this.value = value;
  this.size = size;
  this.color_label = color_label;
}


void setColor(String color) {
  color_label = color;
}


@Override
public String toString() {
  return "" + id + " " + start + " " + end + " " + value
      + " glyph_size=" + size + ",stroke_color=" + color_label;
}

}
