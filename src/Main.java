import javax.print.Doc;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.Color;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Scanner;

public class Main extends JPanel implements MouseListener {

    private State state;
    private Rectangle box;
    private TestMetaData meta;
    private TestScorer testreader;
    private final String CONFIGFILE = ".testscorer";

    public Main() {
        state = State.INITIAL_GREETING;
        addMouseListener(this);
        setTransferHandler(handler);
    }

    @Override
    public void paintComponent(Graphics g1) {
        Graphics2D g = (Graphics2D)g1;
        int w = getWidth();
        int h = getHeight();
        if (state == State.INITIAL_GREETING) {
            String greeting = """
                    Drag and drop
                    your scanned test pages 
                    here, or click this space
                    to select a file.
                    (multi-page TIFF format)
                    """;
            String[] linez = greeting.split("\n");
            int boxWidth = Arrays.stream(linez).map(str -> g.getFontMetrics().stringWidth(str)).max(Comparator.naturalOrder()).get();
            boxWidth = (int)(boxWidth * 1.2);
            int boxHeight = linez.length * g.getFontMetrics().getHeight();
            boxHeight = (int)(boxHeight * 1.2);
            box = new Rectangle((w-boxWidth)/2, (h-boxHeight)/2, boxWidth, boxHeight);
            g.setPaint(Color.BLACK);
            g.draw(box);
            int y = box.y;
            for (var line : linez) {
                y += g.getFontMetrics().getHeight();
                int x = (int)box.getCenterX()-g.getFontMetrics().stringWidth(line)/2;
                g.drawString(line, x, y);
            }
        }


    }
    public static void main(String[] args) {
        String in;
        var window = new JFrame("BYUH Test Scorer");
        window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        window.setSize(800,600);
        window.setContentPane(new Main());
        window.setVisible(true);
    }

    private TransferHandler handler = new TransferHandler() {
        public boolean canImport(TransferHandler.TransferSupport support) {
            if (!support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                return false;
            } else {
                return true;
            }
        }

        public boolean importData(TransferHandler.TransferSupport support) {
            if (!canImport(support)) {
                return false;
            }

            Transferable t = support.getTransferable();

            try {
                List<File> l = (List<File>)t.getTransferData(DataFlavor.javaFileListFlavor);

                for (File f : l) {
                    scoreTest(f);
                }
            } catch (UnsupportedFlavorException e) {
                return false;
            } catch (IOException e) {
                return false;
            }

            return true;
        }
    };

    @Override
    public void mouseClicked(MouseEvent e) {
        var p = e.getPoint();
        if (box.contains(p)) {
            final JFileChooser fc = new JFileChooser();
            var filter = new FileNameExtensionFilter("TIFF Images", "tif", "tiff");
            fc.setFileFilter(filter);

            //try to start in the same folder as last time
            try (Scanner s = new Scanner(new File(CONFIGFILE))) {
                String previousFolder = s.nextLine();
                fc.setCurrentDirectory(new File(previousFolder));
            } catch (FileNotFoundException ex) {
                //do nothing
            }
            int returnVal = fc.showOpenDialog(this);
            if (returnVal == JFileChooser.APPROVE_OPTION) {
                File file = fc.getSelectedFile();
                scoreTest(file);
            }
        }
    }

    private void scoreTest(File file) {
        //cache the folder we were in, for next time
        File foo = new File(CONFIGFILE);
        try (FileWriter fw = new FileWriter(foo)){
            fw.write(file.getParent());
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
        testreader = new TestScorer(file);
        meta = testreader.extractMetaData();
        System.out.println(meta);
        meta.showDialog(this);
    }

    public void metaDataConfirmed() {
        JOptionPane pane = new JOptionPane();
        pane.setMessage("Scoring tests...");
        JProgressBar pb = new JProgressBar(0, meta.numTests);
        pb.setValue(0);
        pb.setStringPainted(true);
        pane.add(pb,1);
        JDialog dialog = pane.createDialog(this, "Information message");
        Runnable task = new Runnable() {
            @Override
            public void run() {
                testreader.readScores(pb);
                testreader.generateTeachersReport();
                testreader.generateStudentReports();
                dialog.setVisible(false);
                dialog.dispose();
                testreader.notifyUser(Main.this);
            }
        };
        Thread thread = Thread.ofVirtual().start(task);
        dialog.setVisible(true);
        //dialog.dispose();


    }

    @Override
    public void mousePressed(MouseEvent e) {

    }

    @Override
    public void mouseReleased(MouseEvent e) {

    }

    @Override
    public void mouseEntered(MouseEvent e) {

    }

    @Override
    public void mouseExited(MouseEvent e) {

    }
}
