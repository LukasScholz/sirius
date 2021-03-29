/*
 *  This file is part of the SIRIUS Software for analyzing MS and MS/MS data
 *
 *  Copyright (C) 2013-2020 Kai Dührkop, Markus Fleischauer, Marcus Ludwig, Martin A. Hoffman, Fleming Kretschmer, Marvin Meusel and Sebastian Böcker,
 *  Chair of Bioinformatics, Friedrich-Schiller University.
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Affero General Public License
 *  as published by the Free Software Foundation; either
 *  version 3 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License along with SIRIUS.  If not, see <https://www.gnu.org/licenses/agpl-3.0.txt>
 */

package de.unijena.bioinf.ms.gui.mainframe.instance_panel;
/**
 * Created by Markus Fleischauer (markus.fleischauer@gmail.com)
 * as part of the sirius_frontend
 * 01.02.17.
 */

import ca.odell.glazedlists.swing.DefaultEventListModel;
import de.unijena.bioinf.ms.gui.actions.SiriusActions;
import de.unijena.bioinf.projectspace.InstanceBean;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

/**
 * @author Markus Fleischauer (markus.fleischauer@gmail.com)
 */
public class ExperimentListView extends JScrollPane {

    final CompoundList sourceList;
    final JList<InstanceBean> compoundListView;
    final JPopupMenu expPopMenu;

    public ExperimentListView(CompoundList sourceList) {
        super(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        this.sourceList = sourceList;

        //todo move texfield and filter funktion here
        compoundListView = new JList<>(new DefaultEventListModel<>(sourceList.compoundList));
        compoundListView.setSelectionModel(sourceList.compountListSelectionModel);
        compoundListView.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        compoundListView.setCellRenderer(new CompoundCellRenderer());

        this.expPopMenu = new CompoundContextMenu();

        compoundListView.addMouseListener(new MouseListener() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    // Double-click detected
                    int index = compoundListView.locationToIndex(e.getPoint());
                    compoundListView.setSelectedIndex(index);
                    SiriusActions.COMPUTE.getInstance().actionPerformed(new ActionEvent(compoundListView, 123, SiriusActions.COMPUTE.name()));
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    int indx = compoundListView.locationToIndex(e.getPoint());
                    boolean select = true;
                    for (int i : compoundListView.getSelectedIndices()) {
                        if (indx == i) {
                            select = false;
                            break;
                        }
                    }
                    if (select) {
                        compoundListView.setSelectedIndex(indx);
                    }

                    if (e.isPopupTrigger()) {
                        expPopMenu.show(e.getComponent(), e.getX(), e.getY());
                    }
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    expPopMenu.show(e.getComponent(), e.getX(), e.getY());
                }
            }

            @Override
            public void mouseEntered(MouseEvent e) {
            }

            @Override
            public void mouseExited(MouseEvent e) {
            }
        });


        setViewportView(compoundListView);

        //decorate this guy
        KeyStroke enterKey = KeyStroke.getKeyStroke("ENTER");
        compoundListView.getInputMap().put(enterKey, SiriusActions.COMPUTE.name());

        KeyStroke delKey = KeyStroke.getKeyStroke("DELETE");
        compoundListView.getInputMap().put(delKey, SiriusActions.DELETE_EXP.name());

        compoundListView.getActionMap().put(SiriusActions.DELETE_EXP.name(), SiriusActions.DELETE_EXP.getInstance());
        compoundListView.getActionMap().put(SiriusActions.COMPUTE.name(), SiriusActions.COMPUTE.getInstance());
    }

    public JPopupMenu getExpPopMenu() {
        return expPopMenu;
    }
}
