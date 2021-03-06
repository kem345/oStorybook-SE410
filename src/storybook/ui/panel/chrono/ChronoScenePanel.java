/*
Storybook: Scene-based software for novelists and authors.
Copyright (C) 2008 - 2011 Martin Mustun

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package storybook.ui.panel.chrono;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.beans.PropertyChangeEvent;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.util.Calendar;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.text.JTextComponent;

import org.hibernate.Session;

import storybook.SbConstants;
import storybook.SbConstants.BookKey;
import storybook.action.EditEntityAction;
import storybook.controller.BookController;
import storybook.model.BookModel;
import storybook.model.EntityUtil;
import storybook.model.hbn.dao.SceneDAOImpl;
import storybook.model.hbn.entity.Internal;
import storybook.model.hbn.entity.Scene;
import storybook.toolkit.DateUtil;
import storybook.toolkit.BookUtil;
import storybook.toolkit.I18N;
import storybook.toolkit.swing.SwingUtil;
import storybook.toolkit.swing.undo.UndoableTextArea;
import storybook.ui.panel.AbstractScenePanel;
import storybook.ui.MainFrame;
import storybook.ui.label.SceneStateLabel;
import storybook.ui.panel.linkspanel.LocationLinksPanel;
import storybook.ui.panel.linkspanel.PersonLinksPanel;
import storybook.ui.panel.linkspanel.StrandLinksPanel;

import net.miginfocom.swing.MigLayout;
import storybook.ui.panel.linkspanel.ItemLinksPanel;

@SuppressWarnings("serial")
public class ChronoScenePanel extends AbstractScenePanel implements FocusListener {

	private final String CN_TITLE = "taTitle";
	private final String CN_TEXT = "tcText";
	private final String CN_UPPER_PANEL = "upperPanel";

	private JPanel upperPanel;
	private UndoableTextArea taTitle;
	private JTextComponent tcText;
	private JLabel lbStatus;
	private JLabel lbInformational;
	private JLabel lbSceneNo;
	private JLabel lbTime;

	private Integer size;

	public ChronoScenePanel(MainFrame mainFrame, Scene scene) {
		super(mainFrame, scene, true, Color.white, scene.getStrand().getJColor());
		init();
		initUi();
	}

	@Override
	public void modelPropertyChange(PropertyChangeEvent evt) {
		// Object oldValue = evt.getOldValue();
		Object newValue = evt.getNewValue();
		String propName = evt.getPropertyName();

		if (BookController.StrandProps.UPDATE.check(propName)) {
			EntityUtil.refresh(mainFrame, scene.getStrand());
			setEndBgColor(scene.getStrand().getJColor());
			repaint();
			return;
		}

		if (BookController.SceneProps.UPDATE.check(propName)) {
			Scene newScene = (Scene) newValue;
			if (!newScene.getId().equals(scene.getId())) {
				// not this scene
				return;
			}
			scene = newScene;
			lbSceneNo.setText(scene.getChapterSceneNo(false));
			lbSceneNo.setToolTipText(scene.getChapterSceneToolTip());
			lbStatus.setIcon(scene.getStatusIcon());
			taTitle.setText(scene.getTitle());
			taTitle.setCaretPosition(0);
			tcText.setText(scene.getSummary());
			tcText.setCaretPosition(0);
			if (scene.hasSceneTs()) {
				if (!DateUtil.isZeroTimeDate(scene.getSceneTs())) {
					DateFormat formatter = I18N.getDateTimeFormatter();
					lbTime.setText(formatter.format(scene.getSceneTs()));
				} else {
					lbTime.setText("");
				}
			}
			return;
		}

		if (BookController.ChronoViewProps.ZOOM.check(propName)) {
			setZoomedSize((Integer) newValue);
			refresh();
		}
	}

	private void setZoomedSize(int zoomValue) {
		size = zoomValue * 7;
	}

	@Override
	public void init() {
		try {
			Internal internal = BookUtil.get(mainFrame, BookKey.CHRONO_ZOOM, SbConstants.DEFAULT_CHRONO_ZOOM);
			setZoomedSize(internal.getIntegerValue());
		} catch (Exception e) {
			setZoomedSize(SbConstants.DEFAULT_CHRONO_ZOOM);
		}
	}

	@Override
	public void initUi() {
		refresh();
	}

	@Override
	public void refresh() {
		MigLayout layout = new MigLayout("fill,flowy,insets 4", "[]", "[][grow]");
		setLayout(layout);
		setPreferredSize(new Dimension(size, size));
		setComponentPopupMenu(EntityUtil.createPopupMenu(mainFrame, scene));

		removeAll();

		// set dotted border for scenes of other parts
		setBorder(SwingUtil.getBorderDefault());
		if (scene.hasChapter()) {
			if (!scene.getChapter().getPart().getId().equals(mainFrame.getCurrentPart().getId())) {
				setBorder(SwingUtil.getBorderDot());
			}
		}

		// button new
		btNew = getNewButton();
		btNew.setSize20x20();
		// btNew.setName(COMP_NAME_BT_NEW);

		// button remove
		btDelete = getDeleteButton();
		btDelete.setSize20x20();
		// btDelete.setName(COMP_NAME_BT_REMOVE);

		// button edit
		btEdit = getEditButton();
		btEdit.setSize20x20();
		// btEdit.setName(COMP_NAME_BT_EDIT);

		// chapter and scene number
		lbSceneNo = new JLabel("", SwingConstants.CENTER);
		lbSceneNo.setText(scene.getChapterSceneNo(false));
		lbSceneNo.setToolTipText(scene.getChapterSceneToolTip());
		lbSceneNo.setOpaque(true);
		lbSceneNo.setBackground(Color.white);

		// status
		lbStatus = new SceneStateLabel(scene.getSceneState(), true);

		// informational
		lbInformational = new JLabel("");
		if (scene.getInformative()) {
			lbInformational.setIcon(I18N.getIcon("icon.small.info"));
			lbInformational.setToolTipText(I18N .getMsg("msg.common.informative"));
		}

		// scene time
		lbTime = addSceneTimeTextToPanel();

		// title
		JScrollPane spTitle = generateTitlePane();
		spTitle.setPreferredSize(new Dimension(50, 35));

		// text	
		JScrollPane spText = generateTextPane();
		spText.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
		spText.setPreferredSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

		// layout
		// button panel
		upperPanel = setupUpperPanel();

		// main panel
		add(upperPanel, "growx");
		add(spTitle, "growx, h 35!");
		add(spText, "grow");

		revalidate();
		repaint();

		tcText.setCaretPosition(0);
		taTitle.setCaretPosition(0);
	}
	
	/**
	 * Gets the timestamp for the scene and creates a label
	 * on the panel that displays the timestamp
	 * @return
	 */
	private JLabel addSceneTimeTextToPanel() {
		JLabel label = new JLabel();
		if (scene.hasSceneTs()) {
			DateFormat formatter = I18N.getDateTimeFormatter();
			label.setText(formatter.format(scene.getSceneTs()));
		} else {
			// Add the date of scenes with relative dates to the panel
			if(scene.hasRelativeScene()) {
				BookModel model = mainFrame.getBookModel();
				Session session = model.beginTransaction();
				SceneDAOImpl dao = new SceneDAOImpl(session);
				// Get all scenes from the book
				Scene relative = dao.findRealtiveScene(scene.getRelativeSceneId());
				session.close();
				// Create the timestamp for the relative date
				Timestamp ts = relative.getSceneTs();
				Calendar cal = Calendar.getInstance();
				cal.setTime(ts);
				cal.add(Calendar.DAY_OF_WEEK, scene.getRelativeDateDifference());
				ts.setTime(cal.getTime().getTime());
				DateFormat formatter = I18N.getDateTimeFormatter();
				label.setText("[" + formatter.format(ts) + "]");
			}
		}
		
		return label;
	}
	
	/**
	 * Create the JScroll pane for the scene's title with the 
	 * appropriate settings for it to be displayed correctly
	 * @return - the JScrollPane for the scene title
	 */
	private JScrollPane generateTitlePane() {
		taTitle = new UndoableTextArea();
		taTitle.setName(CN_TITLE);
		taTitle.setText(scene.getTitle());
		taTitle.setLineWrap(true);
		taTitle.setWrapStyleWord(true);
		taTitle.setDragEnabled(true);
		taTitle.setCaretPosition(0);
		taTitle.getUndoManager().discardAllEdits();
		taTitle.addFocusListener(this);
		SwingUtil.addCtrlEnterAction(taTitle, new EditEntityAction(mainFrame, scene,true));
		return new JScrollPane(taTitle);
	}
	
	/**
	 * Create the JScroll pane for the scene's text with the 
	 * appropriate settings for it to be displayed correctly
	 * @return - the JScrollPane for the scene text
	 */
	private JScrollPane generateTextPane() {
		tcText = SwingUtil.createTextComponent(mainFrame);
		tcText.setName(CN_TEXT);
		tcText.setText(scene.getText());
		tcText.setDragEnabled(true);
		tcText.addFocusListener(this);
		SwingUtil.addCtrlEnterAction(tcText, new EditEntityAction(mainFrame, scene,true));
		return new JScrollPane(tcText);
	}
	
	private JPanel setupUpperPanel() {
		// strand links
		StrandLinksPanel strandLinksPanel = new StrandLinksPanel(mainFrame, scene, true);
		// person links
		PersonLinksPanel personLinksPanel = new PersonLinksPanel(mainFrame, scene);
		// location links
		LocationLinksPanel locationLinksPanel = new LocationLinksPanel(mainFrame, scene);
		JPanel buttonPanel = new JPanel(new MigLayout("flowy,insets 0"));
		buttonPanel.setName("buttonpanel");
		buttonPanel.setOpaque(false);
		buttonPanel.add(btEdit);
		buttonPanel.add(btDelete);
		buttonPanel.add(btNew);
		JPanel panel = new JPanel(new MigLayout("ins 0", "[][grow][]", "[top][top][top]"));
		panel.setName(CN_UPPER_PANEL);
		panel.setOpaque(false);
		panel.add(lbSceneNo, "grow,width pref+10px,split 3");
		panel.add(lbStatus);
		panel.add(lbInformational);
		panel.add(strandLinksPanel, "grow");
		panel.add(buttonPanel, "spany 4,wrap");
		JScrollPane scroller = new JScrollPane(personLinksPanel,
				JScrollPane.VERTICAL_SCROLLBAR_NEVER,
				JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scroller.setMinimumSize(new Dimension(20, 16));
		scroller.setOpaque(false);
		scroller.getViewport().setOpaque(false);
		scroller.setBorder(null);
		panel.add(scroller, "spanx 2,growx,wrap");
		panel.add(locationLinksPanel, "spanx 2,grow,wrap");
		panel.add(lbTime);
		
		return panel;
	}

	protected ChronoScenePanel getThis() {
		return this;
	}

	@Override
	public Scene getScene() {
		return this.scene;
	}

	@Override
	public void focusGained(FocusEvent e) {
	}

	@Override
	public void focusLost(FocusEvent e) {
		if (e.getSource() instanceof JTextComponent) {
			JTextComponent tc = (JTextComponent) e.getSource();
			switch (tc.getName()) {
				case CN_TITLE:
					scene.setTitle(tc.getText());
					break;
				case CN_TEXT:
					scene.setSummary(tc.getText());
					break;
			}
			mainFrame.getBookController().updateScene(scene);
		}
	}
}
