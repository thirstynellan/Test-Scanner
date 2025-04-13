import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSpinner;
import javax.swing.SpinnerModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class TestMetaData {
    public boolean doubleSided;
    public int numQuestions;
    public int numTests;
    private static final String OK_BUTTON = "OK";
    private static final String CANCEL_BUTTON = "Cancel";
    //private TestScorer scorer;
    private Main mainPanel;

    @Override
    public String toString() {
        return "two sided: " + doubleSided + "\n"
                + "#tests: " + numTests + "\n"
                + "#questions: " + numQuestions;
    }

    public void showDialog(Main parent) {
        mainPanel = parent;
        var box = new TestMetaDataDialog(parent);//(JFrame)SwingUtilities.getWindowAncestor(parent));
        box.setVisible(true);
        //System.out.println("BOX IS VISIBLE!");
    }

    public class TestMetaDataDialog extends JDialog implements ActionListener {

        JRadioButton oneButton;
        JRadioButton twoButton;
        JSpinner numTestsSpinner;
        JSpinner numQuestionsSpinner;

        public TestMetaDataDialog(Main parent) {
            super((JFrame)SwingUtilities.getWindowAncestor(parent), Dialog.DEFAULT_MODALITY_TYPE);
            buildGUI();
            setResizable(false);
            pack();
            //position this dialog in the middle of its parent window
            final Rectangle parentBounds = parent.getBounds();
            final Dimension mySize = getSize();
            setLocation(parentBounds.x + parentBounds.width/2 - mySize.width/2,
                    parentBounds.y + parentBounds.height/2 - mySize.height/2);


            //treat a window-closing event as if the user
            //had clicked the "cancel" button.
            addWindowListener(new WindowAdapter() {
                public void windowClosing(WindowEvent event) {
                    handleActionEvent(CANCEL_BUTTON);
                }
            });

        }

        private void buildGUI() {
            setLayout(new BoxLayout(getContentPane(), BoxLayout.PAGE_AXIS));
            setTitle("Here's what I found...");

            var captionPanel = new JPanel();
            captionPanel.setLayout(new FlowLayout(FlowLayout.CENTER));
            JLabel caption = new JLabel("Please adjust as needed.");
            captionPanel.add(caption);


            JPanel radioPanel = new JPanel();
            radioPanel.setLayout(new FlowLayout(FlowLayout.RIGHT));
            oneButton = new JRadioButton("One-sided");
            twoButton = new JRadioButton("Two-sided");
            if (doubleSided) {
                twoButton.setSelected(true);
            } else {
                oneButton.setSelected(true);
            }
            ButtonGroup radios = new ButtonGroup();
            radios.add(oneButton);
            radios.add(twoButton);
            radioPanel.add(oneButton);
            radioPanel.add(twoButton);

            JPanel numTestsPanel = new JPanel();
            numTestsPanel.setLayout(new FlowLayout(FlowLayout.RIGHT));
            var numTestsLabel = new JLabel("Number of tests:");
            SpinnerModel numTestsModel =
                    new SpinnerNumberModel(numTests, //initial value
                            1, //min
                            1000, //max
                            1);   //step
            numTestsSpinner = new JSpinner(numTestsModel);
            numTestsPanel.add(numTestsLabel);
            numTestsPanel.add(numTestsSpinner);

            JPanel numQuestionsPanel = new JPanel();
            numQuestionsPanel.setLayout(new FlowLayout(FlowLayout.RIGHT));
            JLabel numQuestionsLabel = new JLabel("Number of questions:");
            SpinnerModel numQuestionsModel =
                    new SpinnerNumberModel(numQuestions, //initial value
                            1, //min
                            130, //max
                            1);   //step
            numQuestionsSpinner = new JSpinner(numQuestionsModel);
            numQuestionsPanel.add(numQuestionsLabel);
            numQuestionsPanel.add(numQuestionsSpinner);

            JPanel okCancelPanel = new JPanel();
            okCancelPanel.setLayout(new FlowLayout(FlowLayout.RIGHT));
            JButton okButton = new JButton("Generate Scores");
            getRootPane().setDefaultButton(okButton);
            JButton cancelButton = new JButton("Cancel");
            okCancelPanel.add(okButton);
            okCancelPanel.add(cancelButton);
            okButton.setActionCommand(OK_BUTTON);
            cancelButton.setActionCommand(CANCEL_BUTTON);
            okButton.addActionListener(this);
            cancelButton.addActionListener(this);

            add(captionPanel);
            add(radioPanel);
            add(numTestsPanel);
            add(numQuestionsPanel);
            add(okCancelPanel);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            String command = e.getActionCommand();
            handleActionEvent(command);
        }

        private void handleActionEvent(String cmd) {
            if (cmd.equals(CANCEL_BUTTON)) {
                //TODO do nothing
            } else if (cmd.equals(OK_BUTTON)) {
                //scrape data from GUI, copy to meta-data
                doubleSided = twoButton.isSelected();
                numQuestions = (int)(numQuestionsSpinner.getValue());
                numTests = (int)(numTestsSpinner.getValue());
                System.out.println(TestMetaData.this);
                mainPanel.metaDataConfirmed();
            }
            setVisible(false);
        }
    }

}
