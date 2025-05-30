import jigl.image.GrayImage;
import jigl.image.io.ImageOutputStream;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
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

public class TestScorer {
    public static final int COLUMNS = 10;
    public static final int ROWS_ON_PAGE1 = 50;
    public static final int ROWS_ON_PAGE2 = 80;
    private String statusReport;
    private final boolean DEBUG_MODE = false;

    private String[] ids;
    private TestMetaData meta;
    private List<BufferedImage> pics;
    private Set<Point> idBoxPixels;
    private File workingDir;
    private char[][] responses;//1st dimension=number of students; 2nd dim=number of questions

    public TestScorer(File inputFile) {
        idBoxPixels = new HashSet<>();
        try {
            //read in image data
            var fis = new FileInputStream(inputFile);
            workingDir = inputFile.getParentFile();
            pics = extractImages(fis);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null,
                    "Sorry, could not read test file. Please only submit black and white TIFF scans.", "Oops", JOptionPane.ERROR_MESSAGE);
        }
    }

    public void readScores(JProgressBar progressBarListener) {
        statusReport = "";
        GrayImage image;
        if (meta.doubleSided) {
            ids = new String[meta.numTests+1];

            for (int i = 1; i <= meta.numTests; i++) {
                image = new GrayImage(pics.get(i * 2));
                Rectangle idBox = idBoxDimensions(image);
                //FIXME YOU NEED THE ROTATE LOGIC FOR ONE-SIDED TESTS TOO!!
                var rotated = rotateIfNecessary(idBox, pics.get(i * 2));
                if (rotated != null) {
                    image = rotated;
                    idBox = idBoxDimensions(image);
                }
                ids[i] = getIDNumber(idBox, image);
                progressBarListener.setValue(i);
                progressBarListener.setString(i + "/" + meta.numTests);
                System.out.println("READING TEST#" + i + "(ID#" + ids[i] + ")");
                char[] tmp = readFrontSide(image, idBox, false);
                if (DEBUG_MODE) {
                    try {
                        String filepath = workingDir.getAbsolutePath() + "/debug" + i + "_" + ids[i] + "_side1.pgm";
                        ImageOutputStream os = new ImageOutputStream(filepath);
                        os.write(image);
                        System.out.println("Just wrote to " + filepath);
                        os.close();
                    } catch (Exception e) {
                        System.out.println("whatever...");
                    }
                }
                for (int j = 0; j < ROWS_ON_PAGE1; j++) {
                    responses[i][j] = tmp[j];
                }
                image = new GrayImage(pics.get(i * 2 + 1));
                tmp = readBackSide(image, idBox, false);
                if (DEBUG_MODE) {
                    try {
                        String filepath = workingDir.getAbsolutePath() + "/debug" + i + "_" + ids[i] + "_side2.pgm";
                        ImageOutputStream os = new ImageOutputStream(filepath);
                        os.write(image);
                        System.out.println("Just wrote to " + filepath);
                        os.close();
                    } catch (Exception e) {
                        System.out.println("whatever...");
                    }
                }

                for (int j = 0; j < ROWS_ON_PAGE2; j++) {
                    responses[i][j + ROWS_ON_PAGE1] = tmp[j];
                }
            }
        } else {
            //just read one side
            ids = new String[meta.numTests+1];
            for (int i = 1; i <= meta.numTests; i++) {
                progressBarListener.setValue(i);
                progressBarListener.setString(i + "/" + meta.numTests);
                image = new GrayImage(pics.get(i));
                Rectangle idBox = idBoxDimensions(image);
                var rotated = rotateIfNecessary(idBox, pics.get(i));
                if (rotated != null) {
                    image = rotated;
                    idBox = idBoxDimensions(image);
                    if (DEBUG_MODE) System.out.println("Just rotated test#"+i);
                }
                ids[i] = getIDNumber(idBox, image);
                System.out.println("READING TEST#" + i + "(ID#"+ids[i]+")");
                responses[i] = readFrontSide(image, idBox, false);
                if (DEBUG_MODE) {
                    try {
                        String filepath = workingDir.getAbsolutePath() + "/debugging" + i + "_" + ids[i] + ".pgm";
                        ImageOutputStream os = new ImageOutputStream(filepath);
                        os.write(image);
                        System.out.println("Just wrote to " + filepath);
                        os.close();
                    } catch (Exception e) {
                        System.out.println("whatever...");
                    }
                }
            }
        }
    }

    public void generateTeachersReport() {
        final int KEY = 0;
        //generate teacher's report
        File teacherReport = new File(workingDir, "teacher_report.csv");
        try (PrintWriter out = new PrintWriter(new FileWriter(teacherReport))) {
            statusReport += "Saved teacher's report to\n" + teacherReport.getAbsolutePath();
            out.println("Student ID,Score,Out of,%");
            for (int i = 1; i <= meta.numTests; i++) {
                int score = calculateScore(i, meta.numQuestions);
                String percent = String.format("%.2f", ((double) score / meta.numQuestions) * 100);
                out.println(ids[i] + "," + score + "," + meta.numQuestions + "," + percent);
            }
            out.println();
            out.println("PER-QUESTION PERFORMANCE");
            out.println("Question #,Key,# correct,/,# tests");
            for (int i=0; i<meta.numQuestions; i++) {
                int numCorrect = questionScore(i, meta.numTests);
                out.println((i+1) + "," + responses[KEY][i] + ","+ numCorrect + ",/," + meta.numTests);
            }
        } catch (IOException e) {
            System.out.println("Error writing teacher report");
        }
    }

    public void generateStudentReports() {
        final int KEY = 0;
        //generate individual student reports
        File studentFolder = new File(workingDir, "students");
        studentFolder.mkdir();
        statusReport += "\nSaved students' reports to\n" + studentFolder.getAbsolutePath() + "/";
        for (int i=1; i<=meta.numTests; i++) {
            File outfile = new File(studentFolder, "student" + i + "_" + ids[i] + ".csv");
            try (PrintWriter out = new PrintWriter(new FileWriter(outfile))) {
                out.println("Student ID: " + ids[i] + " Score: " + calculateScore(i,meta.numQuestions) + " / " + meta.numQuestions);
                //print column labels
                out.println("QUESTION,YOUR ANSWER,CORRECT ANSWER,MATCH");
                //print results
                for (int j=0; j<meta.numQuestions; j++) {
                    out.println((j+1) + "," + responses[i][j] + "," + responses[KEY][j] + "," + (isCorrect(i,j) ? "YES" : "NO"));
                }
            } catch (IOException e) {
                System.out.println("Error writing report for student " + i + "(" + ids[i] + ")");
            }
        }
    }

    public TestMetaData extractMetaData() {
        final int KEY = 0;
        meta = new TestMetaData();
        int guess = guess1or2sided(new GrayImage(pics.get(0)), new GrayImage(pics.get(1)));
        meta.doubleSided = (guess == 2);

        //count number of tests and number of questions
        if (meta.doubleSided) {
            meta.numTests = (pics.size()-1) / 2;
            responses = new char[meta.numTests+1][ROWS_ON_PAGE1 + ROWS_ON_PAGE2];

            GrayImage image = new GrayImage(pics.get(0));
            Rectangle idBox = idBoxDimensions(image);
            char[] tmp = readFrontSide(image, idBox, true);
            for (int j = 0; j < ROWS_ON_PAGE1; j++) {
                responses[KEY][j] = tmp[j];
            }
//            try {
//                String filepath = workingDir.getAbsolutePath() + "/debugging1.pgm";
//                ImageOutputStream os = new ImageOutputStream(filepath);
//                os.write(image);
//                System.out.println("Just wrote to "+filepath);
//                os.close();
//            } catch (Exception e) {
//                System.out.println("whatever...");
//            }

            image = new GrayImage(pics.get(1));
            tmp = readBackSide(image, idBox, true);
            for (int j = 0; j < ROWS_ON_PAGE2; j++) {
                responses[KEY][j + ROWS_ON_PAGE1] = tmp[j];
            }
        } else {
            //just read one side
            meta.numTests = pics.size()-1;
            responses = new char[meta.numTests+1][ROWS_ON_PAGE1];
            GrayImage image = new GrayImage(pics.get(0));
            Rectangle idBox = idBoxDimensions(image);
            responses[KEY] = readFrontSide(image, idBox, true);
        }
        meta.numQuestions = guessNumberOfQuestions();

        return meta;
    }

    private double getTopRowOfAnswers(Rectangle idBox) {
        return idBox.getMaxY()+idBox.height*0.28;
    }

    private double getRowHeight(Rectangle idBox) {
        return idBox.getHeight()*0.092;
    }

    private double getColumn1Width(Rectangle idBox) {
        return idBox.getWidth()*0.135;
    }

    private double getColumn2Width(Rectangle idBox) {
        return getColumn1Width(idBox)*0.97;
    }

    private double checkForRotation(Rectangle box) {
//        System.out.println("ID BOX DIMENSIONS:");
//        Point bottommostPoint = idBoxPixels.stream().max(Comparator.comparingInt(p -> p.y)).get();
//        Point topmostPoint = idBoxPixels.stream().min(Comparator.comparingInt(p -> p.y)).get();
//        Point leftmostPoint = idBoxPixels.stream().min(Comparator.comparingInt(p -> p.x)).get();
//        Point rightmostPoint = idBoxPixels.stream().max(Comparator.comparingInt(p -> p.x)).get();

        Point center = new Point((int)box.getCenterX(), (int)box.getCenterY());
        double tlDist, blDist, trDist, brDist;
        tlDist = blDist = trDist = brDist = Double.MIN_VALUE;
        Point topLeft = new Point(),
                bottomLeft = new Point(),
                topRight = new Point(),
                bottomRight = new Point();
        for (var p : idBoxPixels) {
            final double dist = Point2D.distanceSq(p.x, p.y, center.x, center.y);
            if (p.x < center.x && p.y < center.y) {
                //top left
                if (dist > tlDist) {
                    tlDist = dist;
                    topLeft.setLocation(p);
                }
            } else if (p.x < center.x && p.y > center.y) {
                //bottom left
                if (dist > blDist) {
                    blDist = dist;
                    bottomLeft.setLocation(p);
                }
            } else if (p.x > center.x && p.y < center.y) {
                //top right
                if (dist > trDist) {
                    trDist = dist;
                    topRight.setLocation(p);
                }
            } else {
                //bottom right
                if (dist > brDist) {
                    brDist = dist;
                    bottomRight.setLocation(p);
                }
            }
        }
        if (DEBUG_MODE) {
            System.out.println("TL=" + topLeft);
            System.out.println("BL=" + bottomLeft);
            System.out.println("BR=" + bottomRight);
            System.out.println("TR=" + topRight);
        }
        double angle;
        if (topLeft.x < bottomLeft.x) {
            //leaning counterclockwise
            angle = Math.atan2(bottomLeft.y-topLeft.y, bottomLeft.x-topLeft.x);
        } else {
            //TODO what to do if the paper leans too far clockwise?
            angle = 0;
        }
        return angle;
    }

    private int guessNumberOfQuestions_helper(final int MAX) {
        final char BLANK = '-';
        final int KEY = 0;
        int n = MAX;
        for (int i=MAX-1; i>=0; i--) {
            if (responses[KEY][i] == BLANK) {
                n--;
            } else {
                break;
            }
        }
        return n;
    }

    private int guessNumberOfQuestions() {
        if (meta.doubleSided) {
            return guessNumberOfQuestions_helper(130);
        } else {
            return guessNumberOfQuestions_helper(50);
        }
    }

    private int questionScore(int q, int n) {
        int total = 0;
        for (int i=1; i<=n; i++) {
            if (isCorrect(i, q)) {
                total++;
            }
        }
        return total;
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

    private Rectangle idBoxDimensions(GrayImage img) {
        idBoxPixels.clear();
        final int BLACK = 0;
        int w = img.X();
        int h = img.Y();
        int guessX = w/20;
        int guessY = h/8;
        int y = guessY;
        for (int x=guessX; x<w; x++) {
            int tmp = img.get(x,y);
            if (tmp == BLACK) {
                //System.out.println("found a black pixel at " + x +","+y);
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
        //right neighbor
        if (img.get(x+1, y) == BLACK) {
            img.set(x+1, y, 200);
            if (idBoxPixels.add(new Point(x+1, y))) {
                searchNeighbors(img, x+1, y);
            }
        }
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
            if (max > 0.05) { //threshold based on heuristic observation
                id += (char)(48 + maxIndex);
            } else {
                id += "-";
            }
        }

        //draw the lines for debugging
//        for (int i=0; i<11; i++) {
//            for (int x = 0; x < bounds.getMaxX(); x++) {
//                img.set(x, (int)(top+(i*rowHeight)), 0);
//            }
//        }
//        for (int i=0; i<8; i++) {
//            for (int y=0; y<bounds.getMaxY(); y++) {
//                img.set((int)(left1+i*colWidth1), y, 0);
//            }
//        }
        return id;
    }

    /**
     * returns the Y coordinate where the top row of answers starts
     * @param img backside of a test paper
     * @return the Y coordinate where the top row of answer bubbles starts
     */
    private int findTopOfBackPage(GrayImage img) {
        final int BLACK = 0;
        int w = img.X();
        int h = img.Y();
        int guess = (int)(h*0.05);
        int guess2 = (int)(h*0.15);
        for (int y=guess; y<guess2; y++) {
            int sum = 0;
            for (int x=0; x<w; x++) {
                if (img.get(x,y) == BLACK) {
                    sum++;
                }
            }
            //System.out.println("At y="+y+ ", dark pixels="+sum);
            if (sum > 100) {
                return y;
            }
        }
        //fail? then return a guess
        return (int)(h*0.105);
    }

    public char[] readBackSide(GrayImage img, Rectangle idBox, boolean answerKey) {
        double[][] ratios = new double[ROWS_ON_PAGE2][COLUMNS];
        int w = img.X();
        int h = img.Y();

        //System.out.println(w + "," + h);
        //final double top = h*0.105;
        final int top = findTopOfBackPage(img);
        final double left1 = w*0.17;
        final double left2 = w*0.571;
        final double colWidth1 = getColumn1Width(idBox);
        final double colWidth2 = getColumn2Width(idBox);
        final double rowHeight = getRowHeight(idBox);

        //draw the lines for debugging
        if (DEBUG_MODE) {
            for (int i = 0; i < 41; i++) {
                for (int x = 0; x < w; x++) {
                    img.set(x, (int) (top + (i * rowHeight)), 0);
                }
            }
            for (int i = 0; i < 11; i++) {
                for (int y = 0; y < h; y++) {
                    img.set((int) (left1 + i * colWidth1), y, 0);
                    img.set((int) (left2 + i * colWidth2), y, 0);
                }
            }
        }

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
            studentAnswers[i] = getResponseLetter(i, ratios, answerKey);
            //System.out.println("Question " + (i+1) + ", answer " + studentAnswers[i]);
        }

        return studentAnswers;
    }

    public char[] readFrontSide(GrayImage img, /*double top*/Rectangle idBox, boolean answerKey) {

        double[][] ratios = new double[ROWS_ON_PAGE1][COLUMNS];
        int w = img.X();
        int h = img.Y();

        final double top = getTopRowOfAnswers(idBox);
        final double left1 = idBox.getMinX()+idBox.getWidth()/7;
//        final double left1 = w*0.16;
        final double left2 = w*0.564;
        final double colWidth1 = getColumn1Width(idBox);
        final double colWidth2 = getColumn2Width(idBox);
        final double rowHeight = getRowHeight(idBox);

          //draw the lines for debugging
        if (DEBUG_MODE) {
            for (int i = 0; i < 26; i++) {
                for (int x = 0; x < w; x++) {
                    img.set(x, (int) (top + (i * rowHeight)), 0);
                }
            }
            for (int i = 0; i < 11; i++) {
                for (int y = 0; y < h; y++) {
                    img.set((int) (left1 + i * colWidth1), y, 0);
                    img.set((int) (left2 + i * colWidth2), y, 0);
                }
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
            studentAnswers[i] = getResponseLetter(i, ratios, answerKey);
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

    private char getResponseLetter(int q, double[][] ratios, boolean useThreshold) {
        char result = '-';
        double max = -1;
        //double min = 20000;
        double sum = 0;
        int maxIndex = -1;
        //System.out.print("Q:"+(q+1));
        for (int i=0; i<COLUMNS; i++) {
            //System.out.print("col"+(char)(65+i)+"="+ratios[q][i]+",");
            sum += ratios[q][i];
            if (max < ratios[q][i]) {
                max = ratios[q][i];
                maxIndex = i;
            }
        }
        //TODO maybe find the 2nd biggest number
        //and compare it against the biggest?
        sum -= max;
        double average = sum/(COLUMNS-1);
        //threshold based on heuristic observation

        //this is a logical implication A->B, aka A'\/B
        if (!useThreshold || max/average > 1.3) {
            result = (char)(65 + maxIndex);
        }
        //System.out.println("Question#"+(q+1) + ", maxratio="+max + ", letter="+result +",max/avg="+(max/average));
        return result;
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

    public void notifyUser(JPanel pane) {
        JOptionPane.showMessageDialog(pane, statusReport, "SCORED!", JOptionPane.INFORMATION_MESSAGE);
    }

    private GrayImage rotateIfNecessary(Rectangle box, BufferedImage bimg) {
        double tilt = checkForRotation(box);
        if (tilt > 1.0) {
            //Thank you, https://cloudinary.com/guides/image-effects/how-to-rotate-an-image-with-java
            //for the tutorial on image rotation!
            if (DEBUG_MODE) System.out.println("TILT: " + tilt);
            var angle = Math.PI/2 - tilt;
            int width = bimg.getWidth();
            int height = bimg.getHeight();
            int newWidth = (int) Math.abs(width * Math.cos(angle)) + (int) Math.abs(height * Math.sin(angle));
            int newHeight = (int) Math.abs(height * Math.cos(angle)) + (int) Math.abs(width * Math.sin(angle));
            BufferedImage outputImage = new BufferedImage(newWidth, newHeight, bimg.getType());
            AffineTransform transform = new AffineTransform();
            transform.rotate(angle, newWidth / 2, newHeight / 2);
            transform.translate((newWidth - width) / 2, (newHeight - height) / 2);
            Graphics2D g2d = outputImage.createGraphics();
            g2d.setTransform(transform);
            g2d.drawImage(bimg, 0, 0, null);
            g2d.dispose();
            return new GrayImage(outputImage);
            //idBox = idBoxDimensions(image);
        } else {
            return null;
        }
    }


//    private void showInWindow(GrayImage img) throws ImageNotSupportedException {
//        JFrame window = new JFrame();
//        JImageCanvas jpanel = new JImageCanvas(img);
//        window.setContentPane(jpanel);
//        window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
//        window.setSize(img.X(), img.Y());
//        window.setVisible(true);
//    }


}