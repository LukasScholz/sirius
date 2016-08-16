/*
 *  This file is part of the SIRIUS library for analyzing MS and MS/MS data
 *
 *  Copyright (C) 2013-2015 Kai Dührkop
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with SIRIUS.  If not, see <http://www.gnu.org/licenses/>.
 */

package de.unijena.bioinf.sirius.gui.dialogs;

import de.unijena.bioinf.sirius.gui.mainframe.MainFrame;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;


public class ImportWorkspaceDialog extends JDialog implements ActionListener {

    private final JButton replace, merge, abort;
    private Decision decision=Decision.NONE;
    private MainFrame mainFrame;

    public Decision getDecision() {
        return decision;
    }

    public static enum Decision {REPLACE, MERGE,ABORT, NONE};

    public ImportWorkspaceDialog(MainFrame owner) {
        super(owner, "Import workspace", true);
        this.mainFrame = owner;
        this.setLayout(new BorderLayout());
        JPanel northPanel = new JPanel(new FlowLayout(FlowLayout.CENTER,20,10));
        Icon icon = UIManager.getIcon("OptionPane.questionIcon");
        northPanel.add(new JLabel(icon));
        northPanel.add(new JLabel("Do you want to replace the existing workspace?"));
        this.add(northPanel,BorderLayout.CENTER);
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT,5,5));
        replace = new JButton("Replace existing workspace");
        replace.addActionListener(this);
        merge = new JButton("Merge workspaces");
        merge.addActionListener(this);
        abort = new JButton("Abort");
        abort.addActionListener(this);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                super.windowClosing(e);
                decision = Decision.ABORT;
            }
        });
        south.add(replace);
        south.add(merge);
        south.add(abort);
        this.add(south,BorderLayout.SOUTH);
        this.pack();
        setLocationRelativeTo(getParent());
    }

    public void start() {
        if (mainFrame.numberOfCompounds() > 0) {
            setVisible(true);
        } else {
            decision = Decision.MERGE;
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (e.getSource()==replace) {
            mainFrame.clearWorkspace();
            decision = Decision.REPLACE;
        } else if (e.getSource()==merge) {
            decision = Decision.MERGE;
        } else if (e.getSource()==abort) {
            decision = Decision.ABORT;
        } else return;
        dispose();
    }
}
