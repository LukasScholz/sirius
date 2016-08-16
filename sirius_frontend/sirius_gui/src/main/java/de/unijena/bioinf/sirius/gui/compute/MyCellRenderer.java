package de.unijena.bioinf.sirius.gui.compute;

import de.unijena.bioinf.myxo.structure.CompactPeak;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.util.Collection;

class MyListCellRenderer extends JLabel implements ListCellRenderer<CompactPeak>{
	
	private double maxInt;
	private Font textfont;
	private Color massColor, intColor, selectedForeground, selectedBackground;
	
	private DecimalFormat numberFormat;
	
	private CompactPeak cp;
	private Collection<CompactPeak> peaks;
	
	private int intPos; 
	
	private int idealWidth;
	
	boolean isInit;
	boolean isSelected;
	
	MyListCellRenderer(Collection<CompactPeak> peaks){
		
		initColorsAndFonts();
		
		isInit = false;
		this.peaks = peaks;
		intPos = 0;
		isSelected = false;
		
		this.numberFormat = new DecimalFormat("#0.0%");
//		FontMetrics fm = Toolkit.getDefaultToolkit().getfo
//		 = this.getGraphics().getFontMetrics(textfont);this.getgr
		
		maxInt = 0;
		for(CompactPeak peak : peaks){
			if(peak.getAbsoluteIntensity()>maxInt) maxInt = peak.getAbsoluteIntensity();
		}
		
//		this.setMinimumSize(new Dimension(145,15));
//		this.setPreferredSize(new Dimension(145,15));
//		this.setSize(new Dimension(151,15));
		
		cp=null;
		
		computeSize();
		
	}
	
	public void computeSize(){
		BufferedImage im = new BufferedImage(1, 1, BufferedImage.TYPE_4BYTE_ABGR);
		
		FontMetrics fm = im.getGraphics().getFontMetrics(this.textfont);
		
		int maxMassWidth = 0;
		int maxIntWidth = 0;
		for(CompactPeak peak : peaks){
			String massS = String.valueOf(peak.getMass());
			String intS = numberFormat.format(peak.getAbsoluteIntensity()/maxInt);
			int massWidth = fm.stringWidth(massS);
			int intWidth  = fm.stringWidth(intS);
			if(massWidth>maxMassWidth) maxMassWidth = massWidth;
			if(intWidth>maxIntWidth) maxIntWidth = intWidth;
//			int width = fm.stringWidth(massS)+fm.stringWidth(intS)+20;
//			if(width>maxWidth) maxWidth = width;
		}
		
		this.intPos = 15 + maxMassWidth;
		
		this.idealWidth = maxMassWidth + maxIntWidth + 20;
		
		this.setSize(new Dimension(idealWidth,15));
		this.setPreferredSize(new Dimension(idealWidth,15));
		this.setMinimumSize(new Dimension(idealWidth,15));
		
	}
	
	public void initColorsAndFonts(){
		try{
            InputStream fontFile = getClass().getResourceAsStream("/ttf/DejaVuSans.ttf");
			Font tempFont = Font.createFont(Font.TRUETYPE_FONT, fontFile);
			textfont = tempFont.deriveFont(12f);
		}catch(Exception e){
			e.printStackTrace();
		}
		
		massColor = Color.BLACK;
		intColor = new Color(83,134,139);
		
		
		selectedBackground = UIManager.getColor("ComboBox:\"ComboBox.listRenderer\"[Selected].background");
		selectedForeground = UIManager.getColor("ComboBox:\"ComboBox.listRenderer\"[Selected].textForeground");
//		evenBackground = UIManager.getColor("ComboBox:\"ComboBox.listRenderer\".background");
//		disableBackground = UIManager.getColor("ComboBox.background");
//		System.out.println("Farbe: "+disableBackground);
//		unevenBackground = new Color(213,227,238);
//		activatedForeground = UIManager.getColor("List.foreground");
//		deactivatedForeground = Color.GRAY;
		
	}

	@Override
	public Component getListCellRendererComponent(
			JList<? extends CompactPeak> list, CompactPeak value, int index,
			boolean isSelected, boolean cellHasFocus) {
		this.setText(value.getMass()+" "+value.getAbsoluteIntensity());
		this.cp = value;
		
		this.isSelected = isSelected;
		return this;
	}
	
	
	@Override
	public void paint(Graphics g){
		
		Graphics2D g2 = (Graphics2D) g; 
		
		FontMetrics fm = g2.getFontMetrics(this.textfont);
		
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
		
		if(isSelected){
			g2.setColor(this.selectedBackground);
		}else{
			g2.setColor(Color.white);
		}
		
		
		g2.fillRect(0, 0, (int) this.getSize().getWidth(), (int) this.getSize().getWidth());
		
		if(cp==null)return;
		
//		FontMetrics fm = g2.getFontMetrics(this.textfont);
		
		String massS = String.valueOf(cp.getMass());
		String intS = numberFormat.format(cp.getAbsoluteIntensity()/maxInt);
		
		if(isSelected){
			g2.setColor(this.selectedForeground);
		}else{
			g2.setColor(massColor);
		}
		
		g2.drawString(massS, 5, 12);
		
		if(isSelected){
			g2.setColor(this.selectedForeground);
		}else{
			g2.setColor(intColor);
		}
		
		g2.drawString(intS, intPos, 12);
		
	}
	
}