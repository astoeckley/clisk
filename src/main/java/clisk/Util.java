package clisk;

import java.awt.Dimension;

import java.awt.FileDialog;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;

import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;

import mikera.gui.Frames;
import mikera.gui.ImageUtils;
import mikera.util.Maths;

import clojure.lang.Compiler;

public class Util {
	// TODO: refactor to use methods from mikera-gui library
	
	/**
	 * Create a new blank BufferedImage
	 * @param w
	 * @param h
	 * @return
	 */
	public static BufferedImage newImage(int w, int h) {
		return ImageUtils.newImage(w, h);
	}
	
	public static BufferedImage scaleImage(BufferedImage img, int w, int h) {
		return ImageUtils.scaleImage(img, w, h);
	}
	
	/**
	 * Clamp an integer to byte range
	 */
	public static int clampToByte (int v) {
		return Maths.bound(v, 0, 255);
	}
	
	public static int toARGB(double r, double g, double b) {
		return getARGBQuick(
				clampToByte((int)(r*256)),
				clampToByte((int)(g*256)),
				clampToByte((int)(b*256)),
				255);
	}
	
	public static int toARGB(double r, double g, double b, double a) {
		return getARGBQuick(
				clampToByte((int)(r*256)),
				clampToByte((int)(g*256)),
				clampToByte((int)(b*256)),
				clampToByte((int)(a*256)));
	}
	
	public static int getARGBQuick(int r, int g, int b, int a) {
		return (a<<24)|(r<<16)|(g<<8)|b;
	} 
	
	@SuppressWarnings("serial")
	public static JFrame cliskFrame(final BufferedImage image) {
		final JFrame f=new JFrame("Clisk Image");
		f.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

		// f.setFocusableWindowState(false);
		
		JMenuBar menuBar=new JMenuBar();
		JMenu menu=new JMenu("File");
		menuBar.add(menu);
		final JMenuItem jmi=new JMenuItem("Save As...");	
		menu.add(jmi);
		jmi.addActionListener(new ActionListener () {
 			@Override
			public void actionPerformed(ActionEvent arg0) {
 				FileDialog fileDialog = new FileDialog(f, "Save Image As...", FileDialog.SAVE);
 				fileDialog.setFile("*.png");
 				
				fileDialog.setVisible(true);			
				String fileName = fileDialog.getFile();
				if (fileName !=null) {
					File outputFile=new File(fileDialog.getDirectory(), fileName);
			        try {
						ImageIO.write(image, "png", outputFile);
						System.out.println("Saving: "+ outputFile.getAbsolutePath());
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
		});
		
		JComponent c=new JComponent() {
			@Override
			public void paint(Graphics g) {
				g.drawImage(image,0,0,null);
			}
		};
		c.setMinimumSize(new Dimension(image.getWidth(null),image.getHeight(null)));
		f.setMinimumSize(new Dimension(image.getWidth(null)+20,image.getHeight(null)+100));
		f.add(c);
		f.setJMenuBar(menuBar);
		f.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		f.pack();
		return f;
	}	
	
	public static JFrame show(final BufferedImage image) {
		return Frames.displayImage(image, "Clisk Image");
	}	
	
	/**
	 * Shows an image generated from the given script in a new clisk JFrame
	 * @param script
	 * @return
	 */
	public static JFrame show(String script) {
		return show(Generator.generate(script));
	}	
	
	public static final long longHash(long a) {
		a ^= (a << 21);
		a ^= (a >>> 35);
		a ^= (a << 4);
		return a;
	}
	
	public static final long hash (double x) {
		return longHash(longHash(
				0x8000+Long.rotateLeft( longHash(Double.doubleToRawLongBits(x)),17)));
	}
	
	public static final long hash (double x, double y) {
		return longHash(longHash(
				hash(x)+Long.rotateLeft(longHash(Double.doubleToRawLongBits(y)),17)));
	}
	
	public static final long hash (double x, double y, double z) {
		return longHash(longHash(
				hash(x,y)+Long.rotateLeft(longHash(Double.doubleToRawLongBits(z)),17)));
	}
	
	public static final long hash (double x, double y, double z, double t) {
		return longHash(longHash(
				hash(x,y,z)+Long.rotateLeft(longHash(Double.doubleToRawLongBits(t)),17)));
	}
	
	private static final double LONG_SCALE_FACTOR=1.0/(Long.MAX_VALUE+1.0);
	
	public static final double dhash(double x) {
		long h = hash(x);
		return (h&Long.MAX_VALUE)*LONG_SCALE_FACTOR;
	}
	
	public static final double dhash(double x, double y) {
		long h = hash(x,y);
		return (h&Long.MAX_VALUE)*LONG_SCALE_FACTOR;
	}
	
	public static final double dhash(double x, double y, double z) {
		long h = hash(x,y,z);
		return (h&Long.MAX_VALUE)*LONG_SCALE_FACTOR;
	}
	
	public static final double dhash(double x, double y , double z, double t) {
		long h = hash(x,y,z,t);
		return (h&Long.MAX_VALUE)*LONG_SCALE_FACTOR;
	}

	public static Object execute(String script) {
		return Compiler.load(new StringReader(script));
	}
	
	private static double componentFromPQT(double p, double q, double h) {
		 h=mikera.util.Maths.mod(h, 1.0);
         if (h < 1.0/6.0) return p + (q - p) * 6.0 * h;
         if (h < 0.5) return q;
         if (h < 2.0/3.0) return p + (q - p) * (2.0/3.0 - h) * 6.0;
         return p;
	}
	
	public static double redFromHSL(double h, double s, double l) {
		if (s==0.0) return l;
	    double q = (l < 0.5) ? (l * (1 + s)) : (l + s - l * s);
        double p = (2 * l) - q;
        return componentFromPQT(p, q, h + 1.0/3.0);
	}
	
	public static double greenFromHSL(double h, double s, double l) {
		if (s==0.0) return l;
	    double q = (l < 0.5) ? (l * (1 + s)) : (l + s - l * s);
        double p = (2 * l) - q;
        return componentFromPQT(p, q, h);
	}
	
	public static double blueFromHSL(double h, double s, double l) {
		if (s==0.0) return l;
	    double q = (l < 0.5) ? (l * (1 + s)) : (l + s - l * s);
        double p = (2 * l) - q;
        return componentFromPQT(p, q, h - 1.0/3.0);
	}
	
	public static void main(String[] args) {
		BufferedImage b=Generator.generate("[x y]");
		show(b);
		Util.execute("(shutdown-agents)");
	}
}
