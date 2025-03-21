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
    //private final int NUM_QUESTIONS;
    private Set<Point> idBoxPixels;
    private boolean doubleSided;
    private char[][] responses;//1st dimension=number of students; 2nd dim=number of questions

    public Main(String inputFileName, /*int ds,*/ int n) {
        final int KEY = 0;
        //final int SCALED_WIDTH = 1000;//500
        //final int SCALED_HEIGHT = 1294;//547
        //doubleSided = (ds == 2);
        //NUM_QUESTIONS = n;
        idBoxPixels = new HashSet<>();
        int numTests = 0;
        String[] ids = new String[0];
        //TODO calculate this programmatically
        try {
            //read in image data
            GrayImage image;
            var fis = new FileInputStream(inputFileName);
            List<BufferedImage> pics = extractImages(fis);
            int guess = guess1or2sided(new GrayImage(pics.get(0)), new GrayImage(pics.get(1)));
            doubleSided = (guess == 2);

            if (doubleSided) {
                numTests = pics.size()/2;
                responses = new char[pics.size()][ROWS_ON_PAGE1+ROWS_ON_PAGE2];
                ids = new String[numTests];

                for (int i = 0; i < numTests; i++) {
                    image = new GrayImage(pics.get(i*2));
                    Rectangle idBox = idBoxDimensions(image);
                    ids[i] =  getIDNumber(idBox, image);
                    //System.out.println("READING TEST#" + i + "(ID#"+ids[i]+")");
                    char[] tmp = readFrontSide(image, getTopRowOfAnswers(idBox));
                    for (int j=0; j<ROWS_ON_PAGE1; j++) {
                        responses[i][j] = tmp[j];
                    }
                    image = new GrayImage(pics.get(i*2+1));
                    tmp = readBackSide(image);
//                    if (i == 0) {
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
                    Rectangle idBox = idBoxDimensions(image);
                    ids[i] = getIDNumber(idBox, image);
                    //System.out.println("READING TEST#" + i + "(ID#"+ids[i]+")");
                    responses[i] = readFrontSide(image, getTopRowOfAnswers(idBox));

                    if (i == 0) {
                        ImageOutputStream os = new ImageOutputStream("debugging.pgm");
                        os.write(image);
                        os.close();
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("something bad happened.");
            e.printStackTrace();
        }

        //generate teacher's report
        final int NUM_QUESTIONS = (n == -1) ? guessNumberOfQuestions() : n;
        System.out.println("Number of questions: " + NUM_QUESTIONS);
        File teacherReport = new File("teacher_report.csv");
        try (PrintWriter out = new PrintWriter(new FileWriter(teacherReport))) {
            System.out.println("Writing teacher's report to " + teacherReport.getAbsolutePath());
            out.println("Student ID,Score,Points Possible,Percentage");
            for (int i=1; i<numTests; i++) {
                int score = calculateScore(i, NUM_QUESTIONS);
                String percent = String.format("%.2f", ((double)score/NUM_QUESTIONS)*100);
                out.println(ids[i]+","+score+","+NUM_QUESTIONS+","+percent);
            }
        } catch (IOException e) {
            System.out.println("Error writing teacher report");
        }

        //generate individual student reports
        File studentFolder = new File("students");
        studentFolder.mkdir();
        System.out.println("Writing students' reports to " + studentFolder.getAbsolutePath() + "/");
        for (int i=1; i<numTests; i++) {
            try (PrintWriter out = new PrintWriter(new FileWriter("students/student" + i + "_" + ids[i] + ".csv"))) {
                out.println("Student ID: " + ids[i] + " Score: " + calculateScore(i,NUM_QUESTIONS) + " / " + NUM_QUESTIONS);
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
    }

    private int guessNumberOfQuestions_helper(final int MAX) {
        final char BLANK = '-';
        int n = MAX;
        final int KEY = 0;
        for (int i=MAX-1; i>=0; i--) {
            if (responses[KEY][i] == BLANK) {
                n--;
            }
        }
        return n;
    }

    private int guessNumberOfQuestions() {
        if (doubleSided) {
            return guessNumberOfQuestions_helper(130);
        } else {
            return guessNumberOfQuestions_helper(50);
        }
    }

    private double getTopRowOfAnswers(Rectangle idBox) {
        return idBox.getMaxY()+idBox.height*0.28;
    }

//    private void debugRect(Rectangle box) {
//        String foo = "left: " + box.x
//                + ", top:" + box.y
//                + ", right: " + (int)(box.getMaxX())
//                + ", bottom: " + (int)(box.getMaxY());
//        System.out.println(foo);
//    }

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

    private Rectangle idBoxDimensions(GrayImage img) {
        idBoxPixels.clear();
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
        if (idBoxPixels.isEmpty()) {
            return new Rectangle();
        } else {
            //TODO Find outliers in X and discard
            int top = idBoxPixels.stream().mapToInt(p -> p.y).min().getAsInt();
            int bottom = idBoxPixels.stream().mapToInt(p -> p.y).max().getAsInt();
            int left = idBoxPixels.stream().mapToInt(p -> p.x).min().getAsInt();
            int right = idBoxPixels.stream().mapToInt(p -> p.x).max().getAsInt();
            int width = right - left;
            int height = bottom - top;
            //chop off top row (handwritten ID numbers)
            int rowHeight = (int) (height / 11.0);
            top = top + rowHeight;
            height = bottom - top;
            //bring in the edges just a bit, to avoid touching the walls
            left += (int) (width * 0.01);
            top += (int) (height * 0.01);
            width = (int) (width * 0.98);
            height = (int) (height * 0.97);
            return new Rectangle(left, top, width, height);
        }
    }

    private int guess1or2sided(GrayImage page1, GrayImage page2) {
        //It is assumed that the passed-in parameter is the
        //2nd page of the multi-page TIF. We will try to find
        //an ID box on this page. If we succeed, return 1,
        //meaning page 2 is a new test. If we fail, we return 2,
        //meaning page 2 is a continuation of the previous
        //test.
        int guess = 2;
        var bounds1 = idBoxDimensions(page1);
        var bounds2 = idBoxDimensions(page2);
        var area1 = bounds1.getWidth() * bounds1.getHeight();
        var area2 = bounds2.getWidth() * bounds2.getHeight();
        //System.out.println("Area of rect1=" + area1);
        //System.out.println("Area of rect2=" + area2);
        if (area2 > 10 && area1 > 10) {
            double ratio = area1/area2;
            //System.out.println("ratio=" + ratio);
            if (ratio > 0.9 && ratio < 1.1) {
                guess = 1;
            }
        }
        return guess;
    }

    private int calculateScore(int student, int n) {
        int score = 0;
        for (int i=0; i<n; i++) {
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
        //int w = img.X();
        //int h = img.Y();

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
            if (max > 0.06) { //threshold based on heuristic observation
                id += (char)(48 + maxIndex);
            } else {
                id += "-";
            }
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

    private char[] readFrontSide(GrayImage img, double top) {

        double[][] ratios = new double[ROWS_ON_PAGE1][COLUMNS];
        int w = img.X();
        int h = img.Y();

        //System.out.println(w + "," + h);
        //final double top = h*0.36;//0.37;//0.361;
        final double left1 = w*0.16;
        final double left2 = w*0.564;
        final double colWidth1 = w*0.036;
        final double colWidth2 = w*0.035;
        final double rowHeight = h*0.0196;

        //draw the lines for debugging
        for (int i=0; i<26; i++) {
            for (int x = 0; x < w; x++) {
                img.set(x, (int)(top+(i*rowHeight)), 0);
            }
        }
        for (int i=0; i<11; i++) {
            for (int y=0; y<h; y++) {
                img.set((int)(left1+i*colWidth1), y, 0);
                img.set((int)(left2+i*colWidth2), y, 0);
            }
        }

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
        char result = '-';
        double max = -1;
        int maxIndex = -1;
        for (int i=0; i<COLUMNS; i++) {
            if (max < ratios[q][i]) {
                max = ratios[q][i];
                maxIndex = i;
            }
        }
        //threshold based on heuristic observation
        //TODO make this a preference option
        if (max > 0.099) {
            result = (char)(65 + maxIndex);
        }
        //System.out.println("Question#"+(q+1) + ", maxratio="+max + ", letter="+result);
        return result;
    }

//    private void showInWindow(GrayImage img) throws ImageNotSupportedException {
//        JFrame window = new JFrame();
//        JImageCanvas jpanel = new JImageCanvas(img);
//        window.setContentPane(jpanel);
//        window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
//        window.setSize(img.X(), img.Y());
//        window.setVisible(true);
//    }

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
        //int sides;
        int n;
        if (args.length == 0) {
            System.out.println("USAGE: java ScantronReader <inputfile> <#questions>");
            System.out.println("where <inputfile> is a grayscale multi-page TIFF image,");
            //System.out.println("and <#sides> is either 1 or 2 depending on whether your bubble sheet scans are 1- or 2-sided,");
            System.out.println("and <#questions> is the number of questions in your test.");
            return;
        } else {
            in = args[0];
            //sides = Integer.parseInt(args[1]);
            if (args.length > 1) {
                n = Integer.parseInt(args[1]);
            } else {
                n = -1;
            }
        }
        new Main(in, n);
    }
}