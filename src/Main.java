import jigl.gui.JImageCanvas;
import jigl.image.GrayImage;
import jigl.image.ImageNotSupportedException;
import jigl.image.io.ImageOutputStream;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.swing.JFrame;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class Main {
    private final int COLUMNS = 10;
    private final int ROWS_ON_PAGE1 = 50;
    private final int ROWS_ON_PAGE2 = 80;
    private final int NUM_QUESTIONS;// = ROWS_ON_PAGE1+ROWS_ON_PAGE2;
    private Set<Point> idBoxPixels;
    private boolean doubleSided;
    private char[][] responses;//1st dimension=number of students; 2nd dim=number of questions

    public Main(String inputFileName, int ds, int n) {
        final int KEY = 0;
        //final int SCALED_WIDTH = 1000;//500
        //final int SCALED_HEIGHT = 1294;//547
        doubleSided = (ds == 2);
        NUM_QUESTIONS = n;
        idBoxPixels = new HashSet<>();
        int numTests;
        //TODO calculate this programmatically
        try {
            //read in image data
            GrayImage image;
            var fis = new FileInputStream(inputFileName);
            List<BufferedImage> pics = extractImages(fis);
            String[] ids;
            if (doubleSided) {
                numTests = pics.size()/2;
                responses = new char[pics.size()][ROWS_ON_PAGE1+ROWS_ON_PAGE2];
                ids = new String[numTests];

                for (int i = 0; i < numTests; i++) {
                    image = new GrayImage(pics.get(i*2));
                    if (i != 0) { //don't read ID for answer key page
                        Rectangle idBox = idBoxDimensions(image);
                        ids[i] = getIDNumber(idBox, image);
                        //System.out.println(i + ": " + " ID=" + ids[i]);
                    }
                    char[] tmp = readFrontSide(image);
                    if (i == 4) {
                        ImageOutputStream os = new ImageOutputStream("debugging.pgm");
                        os.write(image);
                        os.close();
                    }
                    for (int j=0; j<ROWS_ON_PAGE1; j++) {
                        responses[i][j] = tmp[j];
                    }
                    image = new GrayImage(pics.get(i*2+1));
                    tmp = readBackSide(image);
//                    if (i == 8) {
//                        ImageOutputStream os = new ImageOutputStream("debugging.pgm");
//                        os.write(image);
//                        os.close();
//                    }
                    for (int j=0; j<ROWS_ON_PAGE2; j++) {
                        responses[i][j+ROWS_ON_PAGE1] = tmp[j];
                    }
                }
            } else {
                //just read one side
                numTests = pics.size();
                responses = new char[numTests][ROWS_ON_PAGE1];
                ids = new String[numTests];
                for (int i = 0; i < numTests; i++) {
                    image = new GrayImage(pics.get(i));
                    //image = new GrayImage(pics.get(i).getScaledInstance(SCALED_WIDTH,SCALED_HEIGHT,java.awt.Image.SCALE_FAST));
                    if (i != 0) {
                        Rectangle idBox = idBoxDimensions(image);
                        //if (i==39) debugRect(idBox);
                        ids[i] = getIDNumber(idBox, image);
                        System.out.println(i + ": " + " ID=" + ids[i]);
                    }

                    responses[i] = readFrontSide(image);

                    /*if (i == 12) {
                        ImageOutputStream os = new ImageOutputStream("debugging.pgm");
                        os.write(image);
                        os.close();
                    }*/
                }
            }

            //generate teacher's report
            try (PrintWriter out = new PrintWriter(new FileWriter("teacher_report.csv"))) {
                out.println("Student ID,Score,Points Possible,Percentage");
                for (int i=1; i<numTests; i++) {
                    int score = calculateScore(i);
                    String percent = String.format("%.2f", ((double)score/NUM_QUESTIONS)*100);
                    out.println(ids[i]+","+score+","+NUM_QUESTIONS+","+percent);
                }
            } catch (IOException e) {
                System.out.println("Error writing teacher report");
            }

            //generate individual student reports
            new File("students").mkdir();
            for (int i=1; i<numTests; i++) {
                try (PrintWriter out = new PrintWriter(new FileWriter("students/student" + i + "_" + ids[i] + ".csv"))) {
                    out.println("Student ID: " + ids[i] + " Score: " + calculateScore(i) + " / " + NUM_QUESTIONS);
                    //print column labels
                    out.println("QUESTION,YOUR ANSWER,CORRECT ANSWER,MATCH");
                    //print results
                    for (int j=0; j<NUM_QUESTIONS; j++) {
                        out.println((j+1) + "," + responses[i][j] + "," + responses[KEY][j] + "," + (isCorrect(i,j) ? "YES" : "NO"));
                    }
                } catch (IOException e) {
                    System.out.println("Error writing report for student " + i + "(" + ids[i] + ")");
                }
            }
        } catch (Exception e) {
            System.out.println("something bad happened.");
            e.printStackTrace();
        }
    }

    private void debugRect(Rectangle box) {
        String foo = "left: " + box.x
                + ", top:" + box.y
                + ", right: " + (int)(box.getMaxX())
                + ", bottom: " + (int)(box.getMaxY());
        System.out.println(foo);

    }

    private void searchNeighbors(GrayImage img, int x, int y) {
        final int BLACK = 0;
        //top neighbor
        if (img.get(x, y-1) == BLACK) {
            img.set(x, y-1, 200);
            if (idBoxPixels.add(new Point(x, y-1))) {
                searchNeighbors(img, x, y - 1);
            }
        }
        //top-left neighbor
        if (img.get(x-1, y-1) == BLACK) {
            img.set(x-1, y-1, 200);
            if (idBoxPixels.add(new Point(x, y-1))) {
                searchNeighbors(img, x-1, y - 1);
            }
        }
        //top-right neighbor
        if (img.get(x+1, y-1) == BLACK) {
            img.set(x+1, y-1, 200);
            if (idBoxPixels.add(new Point(x+1, y-1))) {
                searchNeighbors(img, x+1, y - 1);
            }
        }
        //bottom neighbor
        if (img.get(x, y+1) == BLACK) {
            img.set(x, y+1, 200);
            if (idBoxPixels.add(new Point(x, y+1))) {
                searchNeighbors(img, x, y+1);
            }
        }
        //bottom-left neighbor
        if (img.get(x-1, y+1) == BLACK) {
            img.set(x-1, y+1, 200);
            if (idBoxPixels.add(new Point(x, y+1))) {
                searchNeighbors(img, x-1, y+1);
            }
        }
        //bottom-right neighbor
        if (img.get(x+1, y+1) == BLACK) {
            img.set(x+1, y+1, 200);
            if (idBoxPixels.add(new Point(x+1, y+1))) {
                searchNeighbors(img, x+1, y+1);
            }
        }
    }

    private Rectangle idBoxDimensions_try1(GrayImage img) {
        int w = img.X();
        int h = img.Y();
        int guessX = w/20;
        int guessY = h/10;
        int previousX = -1;
        Point idBoxBL = null, idBoxTL = null, idBoxTR = null, idBoxBR = null;
        outer1:
        for (int y=guessY; y<h; y++) {
            boolean blackFound = false;
            for (int x=guessX; x<w; x++) {
                if (img.get(x,y) == 0) {
                    blackFound = true;
                    if (previousX > 0) {
                        int dx = Math.abs(x-previousX);
                        if (dx > (int)(x*0.05)) {
                            idBoxBL = new Point(previousX , y-1);
                            break outer1;
                        }
                    }
                    previousX = x;
                    break;
                }
            }
            if (!blackFound) {
                idBoxBL = new Point(previousX , y-1);
                break;
            }
        }
        previousX = -1;
        outer2:
        for (int y=guessY; y>0; y--) {
            boolean blackFound = false;
            for (int x=guessX; x<w; x++) {
                if (img.get(x,y) == 0) {
                    blackFound = true;
                    if (previousX > 0) {
                        int dx = Math.abs(x-previousX);
                        if (dx > (int)(x*0.05)) {
                            idBoxTL = new Point(previousX,y+1);
                            break outer2;
                        }
                    }
                    previousX = x;
                    break;
                }
            }
            if (!blackFound) {
                idBoxTL = new Point(previousX,y+1);
                break;
            }
        }
        int dx = idBoxTL.x-idBoxBL.x;
        double heightOverWidth = 1.129;
        int dy = (int)(dx/heightOverWidth);
        double leftSideLength = idBoxBL.distance(idBoxTL);
        double topSideLength = leftSideLength/heightOverWidth;
        idBoxTR = new Point((int)(idBoxTL.x+topSideLength), idBoxTL.y+dy);
        idBoxBR = new Point((int)(idBoxBL.x+topSideLength), idBoxBL.y+dy);
        int rowHeight = (int)(leftSideLength/11);
        int newTop = Math.min(idBoxTL.y+rowHeight, idBoxTR.y+rowHeight);
        int bottom = Math.min(idBoxBL.y,idBoxBR.y);
        int heightSansHeader = bottom-newTop;
        Rectangle box = new Rectangle(Math.max(idBoxBL.x, idBoxTL.x),
                newTop,
                (int)(topSideLength*0.98), (int)(heightSansHeader*0.98));
        return box;
    }

    private Rectangle idBoxDimensions(GrayImage img) {
        final int BLACK = 0;
        int w = img.X();
        int h = img.Y();
        int guessX = w/20;
        int guessY = h/10;
            int y = guessY;
            for (int x=guessX; x<w; x++) {
                if (img.get(x,y) == BLACK) {
                    searchNeighbors(img, x, y);
                    break;
                }
            }
        //TODO Find outliers in X and discard
        int top = idBoxPixels.stream().mapToInt(p -> p.y).min().getAsInt();
        int bottom = idBoxPixels.stream().mapToInt(p -> p.y).max().getAsInt();
        int left = idBoxPixels.stream().mapToInt(p -> p.x).min().getAsInt();
        int right = idBoxPixels.stream().mapToInt(p -> p.x).max().getAsInt();
        int width = right-left;
        int height = bottom-top;
        //chop off top row (handwritten ID numbers)
        int rowHeight = (int)(height/11.0);
        top = top + rowHeight;
        height = bottom - top;
        //bring in the edges just a bit, to avoid touching the walls
        left += (int)(width*0.01);
        top += (int)(height*0.01);
        width = (int)(width*0.98);
        height = (int)(height*0.97);
        return new Rectangle(left, top, width, height);
    }


    private int calculateScore(int student) {
        int score = 0;
        for (int i=0; i<NUM_QUESTIONS; i++) {
            if (isCorrect(student,i)) {
                score++;
            }
        }
        return score;
    }

    private boolean isCorrect(int student, int q) {
        final int KEY = 0;
        return responses[KEY][q] == responses[student][q];
    }

    private String getIDNumber(Rectangle bounds, GrayImage img) {
        double[][] ratios = new double[10][7];
        int w = img.X();
        int h = img.Y();

        //System.out.println(w + "," + h);
        final double top = bounds.y;
        final double left1 = bounds.x;
        final double colWidth1 = (bounds.getWidth())/7;
        final double rowHeight = bounds.getHeight()/10;

        for (int i=0; i<10; i++) {
            for (int j=0; j<7; j++) {
                ratios[i][j] = getRatio(img, left1+j*colWidth1, top+i*rowHeight, left1+(j+1)*colWidth1, top+(i+1)*rowHeight);
                //System.out.print(j+":"+ratios[i][j]+",");
            }
            //System.out.println();
        }
        String id = "";
        for (int i=0; i<7; i++) {
            int maxIndex = -1;
            double max = -1;
            for (int j=0; j<10; j++) {
                if (max < ratios[j][i]) {
                    max = ratios[j][i];
                    maxIndex = j;
                }
            }
            //System.out.println("column"+i+" max=" + max + " maxIndex="+maxIndex);
            id += (char)(48+maxIndex);
        }

        //draw the lines for debugging
        /*for (int i=0; i<11; i++) {
            for (int x = 0; x < w; x++) {
                img.set(x, (int)(top+(i*rowHeight)), 0);
            }
        }
        for (int i=0; i<8; i++) {
            for (int y=0; y<h; y++) {
                img.set((int)(left1+i*colWidth1), y, 0);
            }
        }*/
        return id;
    }

    private char[] readBackSide(GrayImage img) {
        double[][] ratios = new double[ROWS_ON_PAGE2][COLUMNS];
        int w = img.X();
        int h = img.Y();

        //System.out.println(w + "," + h);
        final double top = h*0.105;
        final double left1 = w*0.17;
        final double left2 = w*0.571;
        final double colWidth1 = w*0.036;
        final double colWidth2 = w*0.035;
        final double rowHeight = h*0.0196;

        //draw the lines for debugging
        /*for (int i=0; i<41; i++) {
            for (int x = 0; x < w; x++) {
                img.set(x, (int)(top+(i*rowHeight)), 0);
            }
        }
        for (int i=0; i<11; i++) {
            for (int y=0; y<h; y++) {
                img.set((int)(left1+i*colWidth1), y, 0);
                img.set((int)(left2+i*colWidth2), y, 0);
            }
        }*/

        //see which circles are filled in
        for (int i=0; i<ROWS_ON_PAGE2/2; i++) {
            for (int j=0; j<COLUMNS; j++) {
                ratios[i][j] = getRatio(img, left1+j*colWidth1, top+i*rowHeight, left1+(j+1)*colWidth1, top+(i+1)*rowHeight);
                ratios[i+ROWS_ON_PAGE2/2][j] = getRatio(img, left2+j*colWidth2, top+i*rowHeight, left2+(j+1)*colWidth2, top+(i+1)*rowHeight);
                //System.out.println("Question " + (i+1) + ", ratio: " + (char)(65+j) + ": " + responses[i][j]);
            }
        }
        char[] studentAnswers = new char[ROWS_ON_PAGE2];
        for (int i=0; i<ROWS_ON_PAGE2; i++) {
            studentAnswers[i] = getResponseLetter(i, ratios);
            //System.out.println("Question " + (i+1) + ", answer " + studentAnswers[i]);
        }

        return studentAnswers;
    }

    private char[] readFrontSide(GrayImage img) {

        double[][] ratios = new double[ROWS_ON_PAGE1][COLUMNS];
        int w = img.X();
        int h = img.Y();

        //System.out.println(w + "," + h);
        final double top = h*0.36;//0.37;//0.361;
        final double left1 = w*0.16;
        final double left2 = w*0.564;
        final double colWidth1 = w*0.036;
        final double colWidth2 = w*0.035;
        final double rowHeight = h*0.0196;

        //draw the lines for debugging
//        for (int i=0; i<26; i++) {
//            for (int x = 0; x < w; x++) {
//                img.set(x, (int)(top+(i*rowHeight)), 0);
//            }
//        }
//        for (int i=0; i<11; i++) {
//            for (int y=0; y<h; y++) {
//                img.set((int)(left1+i*colWidth1), y, 0);
//                img.set((int)(left2+i*colWidth2), y, 0);
//            }
//        }

        //see which circles are filled in
        for (int i=0; i<ROWS_ON_PAGE1/2; i++) {
            for (int j=0; j<COLUMNS; j++) {
                ratios[i][j] = getRatio(img, left1+j*colWidth1, top+i*rowHeight, left1+(j+1)*colWidth1, top+(i+1)*rowHeight);
                ratios[i+ROWS_ON_PAGE1/2][j] = getRatio(img, left2+j*colWidth2, top+i*rowHeight, left2+(j+1)*colWidth2, top+(i+1)*rowHeight);
                //System.out.println("Question " + (i+1) + ", ratio: " + (char)(65+j) + ": " + responses[i][j]);
            }
        }
        char[] studentAnswers = new char[ROWS_ON_PAGE1];
        for (int i=0; i<ROWS_ON_PAGE1; i++) {
            studentAnswers[i] = getResponseLetter(i, ratios);
            //System.out.println("Question " + (i+1) + ", answer " + studentAnswers[i]);
        }

        return studentAnswers;
    }

    private double getRatio(GrayImage image, double left, double top, double right, double bottom) {
        double numWhitePixels = 0;
        double numBlackPixels = 0;
        for (int x=(int)left; x<(int)right; x++) {
            for (int y=(int)top; y<(int)bottom; y++) {
                if (image.get(x,y) > 0) {
                    numWhitePixels++;
                } else {
                    numBlackPixels++;
                }
                //image.set(x,y,0);
            }
        }
        return numBlackPixels/numWhitePixels;

    }

    private char getResponseLetter(int q, double[][] ratios) {
        //char result = 'N';
        double max = -1;
        int maxIndex = -1;
        for (int i=0; i<COLUMNS; i++) {
            if (max < ratios[q][i]) {
                max = ratios[q][i];
                maxIndex = i;
            }
        }
        return (char)(65+maxIndex);
    }

    private void showInWindow(GrayImage img) throws ImageNotSupportedException {
        JFrame window = new JFrame();
        JImageCanvas jpanel = new JImageCanvas(img);
        window.setContentPane(jpanel);
        window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        window.setSize(img.X(), img.Y());
        window.setVisible(true);

    }

    //thank you Stack Overflow!
    //https://stackoverflow.com/questions/17770071/splitting-a-multipage-tiff-image-into-individual-images-java
    private List<BufferedImage> extractImages(InputStream fileInput) throws Exception {
        List<BufferedImage> extractedImages = new ArrayList<>();

        try (var iis = ImageIO.createImageInputStream(fileInput)) {

            var reader = getTiffImageReader();
            reader.setInput(iis);

            int pages = reader.getNumImages(true);
            for (int imageIndex = 0; imageIndex < pages; imageIndex++) {
                var bufferedImage = reader.read(imageIndex);
                extractedImages.add(bufferedImage);
            }
        }

        return extractedImages;
    }

    //thank you Stack Overflow!
    //https://stackoverflow.com/questions/17770071/splitting-a-multipage-tiff-image-into-individual-images-java
    private ImageReader getTiffImageReader() {
        Iterator<ImageReader> imageReaders = ImageIO.getImageReadersByFormatName("TIFF");
        if (!imageReaders.hasNext()) {
            throw new UnsupportedOperationException("No TIFF Reader found!");
        }
        return imageReaders.next();
    }

    public static void main(String[] args) {
        String in;
        int sides;
        int n;
        if (args.length == 0) {
            System.out.println("USAGE: java ScantronReader <inputfile> <#sides> <#questions>");
            System.out.println("where <inputfile> is a grayscale multi-page TIFF image,");
            System.out.println("and <#sides> is either 1 or 2 depending on whether your bubble sheet scans are 1- or 2-sided,");
            System.out.println("and <#questions> is the number of questions in your test.");
            return;
        } else {
            in = args[0];
            sides = Integer.parseInt(args[1]);
            n = Integer.parseInt(args[2]);
        }
        new Main(in, sides, n);
    }
}