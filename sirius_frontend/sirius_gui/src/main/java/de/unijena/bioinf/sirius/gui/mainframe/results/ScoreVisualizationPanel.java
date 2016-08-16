package de.unijena.bioinf.sirius.gui.mainframe.results;

import de.unijena.bioinf.myxo.gui.tree.render.NodeColorManager;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.InputStream;

public class ScoreVisualizationPanel extends JPanel {
	
	private NodeColorManager ncm;
	private Font valueFont;

	public ScoreVisualizationPanel() {
		ncm = null;
		this.valueFont = null;
		initColorsAndFonts();
		this.setMinimumSize(new Dimension(200,25));
		this.setPreferredSize(new Dimension(200,25));
	}
	
	public void initColorsAndFonts(){
		
//		try{
//			File fontFile = new File("ttf/DejaVuSans-Bold.ttf");
//			Font tempFont = Font.createFont(Font.TRUETYPE_FONT, fontFile);
//			compoundFont = tempFont.deriveFont(13f);
//			
//			propertyFont = tempFont.deriveFont(12f);
//		}catch(Exception e){
//			System.out.println(e.getMessage());
//		}
		
		try{
			InputStream fontFile = getClass().getResourceAsStream("/ttf/DejaVuSans.ttf");
			Font tempFont = Font.createFont(Font.TRUETYPE_FONT, fontFile);
			valueFont = tempFont.deriveFont(10f);
			
		}catch(Exception e){
			e.printStackTrace();
		}
		
	}
	
	public void setNodeColorManager(NodeColorManager ncm){
		this.ncm = ncm;
	}
	
	@Override
	public void paintComponent(Graphics g){
		Graphics2D g2 = (Graphics2D) g;
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
		g2.setColor(Color.white);
		g2.fillRect(0, 0, 200, 25);
		if(ncm==null){
			return;
		}
		double diff = ncm.getMaximalValue()-ncm.getMinimalValue();
		double stepSize = diff/200;
//		double value = ncm.getMinimalValue();
		for(int i=0;i<199;i++){
			Color c = ncm.getColor(ncm.getMinimalValue() + (i*stepSize));
			g2.setColor(c);
			g2.drawLine(i,0,i,12);
//			value += stepsize;
		}
		Color c = ncm.getColor(ncm.getMaximalValue());
		g2.setColor(c);
		g2.drawLine(199,0,199,12);
		
		g2.setFont(valueFont);
		g2.setColor(c);
		
		g2.setColor(Color.black);
		FontMetrics fm = g2.getFontMetrics(valueFont);
		g2.drawString("low",5, 22);
		
		int middleLength = fm.stringWidth("average");
		g2.drawString("average",100-(middleLength/2), 22);
		
		int rightLength = fm.stringWidth("high");
		g2.drawString("high",194-rightLength, 22);
	}

}
