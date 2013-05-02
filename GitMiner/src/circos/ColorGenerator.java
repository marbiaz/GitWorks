package circos;

import java.awt.Color;

public class ColorGenerator {
	   public void paint() {
	        Color color2 = Color.RED;
	        Color color1 = Color.GREEN;
	        int steps = 30;

	        for (int i = 0; i < steps; i++) {
	            float ratio = (float) i / (float) steps;
	            int red = (int) (color2.getRed() * ratio + color1.getRed() * (1 - ratio));
	            int green = (int) (color2.getGreen() * ratio + color1.getGreen() * (1 - ratio));
	            int blue = (int) (color2.getBlue() * ratio + color1.getBlue() * (1 - ratio));
	            System.out.println(red+","+green+","+blue);	        
	            }
	   }
}
