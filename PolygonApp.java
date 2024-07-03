import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;

public class PolygonApp extends JFrame {
    private JPanel drawingPanel;
    private List<Point> originalPolygon = new ArrayList<>();
    private List<Point> originalCoordinates = new ArrayList<>();
    private List<Point> cutPolygon1 = new ArrayList<>();
    private List<Point> cutPolygon2 = new ArrayList<>();
    private List<Point> cutPolygon1Original = new ArrayList<>();
    private List<Point> cutPolygon2Original = new ArrayList<>();
    private double xDownscaleFactor, yDownscaleFactor;
    private boolean isCutting = false;
    private Point cutStart, cutEnd;
    private final int PANEL_WIDTH = 800;
    private final int PANEL_HEIGHT = 600;
    private final int X_MIN = 350000;
    private final int X_MAX = 650000;
    private final int Y_MIN = 2900000;
    private final int Y_MAX = 3400000;
    private final String OUTPUT_DIR = "outputs";

    public PolygonApp() {
        setTitle("Polygon Drawing and Cutting");
        setSize(PANEL_WIDTH, PANEL_HEIGHT);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        drawingPanel = new DrawingPanel();
        drawingPanel.setPreferredSize(new Dimension(PANEL_WIDTH, PANEL_HEIGHT));
        add(drawingPanel, BorderLayout.CENTER);

        JPanel controlPanel = new JPanel();
        controlPanel.setLayout(new GridLayout(3, 2, 10, 10));

        JLabel titleLabel = new JLabel("Parcel Drawing and Cutting Tool with UTM cordinates (Pragyan,Shisir,Rishav)", JLabel.CENTER);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 16));
        add(titleLabel, BorderLayout.NORTH);

        JButton generateButton = new JButton("Generate Polygon");
        JButton cutButton = new JButton("Cut Polygon");
        JButton loadFigBButton = new JButton("Load Fig B (Green)");
        JButton loadFigCButton = new JButton("Load Fig C (Blue)");

        controlPanel.add(generateButton);
        controlPanel.add(cutButton);
        controlPanel.add(loadFigBButton);
        controlPanel.add(loadFigCButton);

        add(controlPanel, BorderLayout.SOUTH);

        generateButton.addActionListener(e -> generatePolygon());
        cutButton.addActionListener(e -> startCutting());
        loadFigBButton.addActionListener(e -> {
            loadFigure("cut_polygon_1.txt", "cut_polygon_1_original.txt");
            JOptionPane.showMessageDialog(this, "Polygon 1 is displayed in GREEN 💚.\nYou can see the original coordinates in outputs/cut_polygon_1_original.txt", "Information", JOptionPane.INFORMATION_MESSAGE);
        });
        loadFigCButton.addActionListener(e -> {
            loadFigure("cut_polygon_2.txt", "cut_polygon_2_original.txt");
            JOptionPane.showMessageDialog(this, "Polygon 2 is displayed in BLUE 💙.\nYou can see the original coordinates in outputs/cut_polygon_2_original.txt", "Information", JOptionPane.INFORMATION_MESSAGE);
        });

        ensureOutputDirectory();
    }

    private void generatePolygon() {
        String input = JOptionPane.showInputDialog(this, "Enter coordinates (x1,y1 x2,y2 ...):");
        if (input != null && !input.isEmpty()) {
            originalPolygon.clear();
            originalCoordinates.clear();
            String[] coords = input.split(" ");
            for (String coord : coords) {
                String[] xy = coord.split(",");
                int x = Integer.parseInt(xy[0]);
                int y = Integer.parseInt(xy[1]);
                Point point = new Point(x, y);
                originalCoordinates.add(point);
                originalPolygon.add(new Point(x, y));
            }
            downscalePolygon();
            drawingPanel.repaint();
            saveCoordinates(originalPolygon, "original_polygon.txt");
            saveOriginalCoordinates(originalCoordinates, "original_coordinates.txt");
        }
    }

    private void downscalePolygon() {
        xDownscaleFactor = (double) PANEL_WIDTH / (X_MAX - X_MIN);
        yDownscaleFactor = (double) PANEL_HEIGHT / (Y_MAX - Y_MIN);

        for (Point p : originalPolygon) {
            int scaledX = (int) ((p.x - X_MIN) * xDownscaleFactor);
            int scaledY = (int) ((p.y - Y_MIN) * yDownscaleFactor);
            p.setLocation(scaledX, scaledY);
        }
    }

    private void startCutting() {
        isCutting = true;
    }

    private void cutPolygon(Point lineStart, Point lineEnd) {
        cutPolygon1.clear();
        cutPolygon2.clear();
        cutPolygon1Original.clear();
        cutPolygon2Original.clear();

        List<Point> leftPolygon = new ArrayList<>();
        List<Point> rightPolygon = new ArrayList<>();
        List<Point> leftPolygonOriginal = new ArrayList<>();
        List<Point> rightPolygonOriginal = new ArrayList<>();

        int n = originalPolygon.size();
        for (int i = 0; i < n; i++) {
            Point p1 = originalPolygon.get(i);
            Point p2 = originalPolygon.get((i + 1) % n);
            Point p1Original = originalCoordinates.get(i);
            Point p2Original = originalCoordinates.get((i + 1) % n);

            if (isLeft(lineStart, lineEnd, p1)) {
                leftPolygon.add(p1);
                leftPolygonOriginal.add(p1Original);
            } else {
                rightPolygon.add(p1);
                rightPolygonOriginal.add(p1Original);
            }

            if (doIntersect(lineStart, lineEnd, p1, p2)) {
                Point intersection = getIntersectionPoint(lineStart, lineEnd, p1, p2);
                Point intersectionOriginal = getIntersectionPoint(lineStart, lineEnd, p1Original, p2Original);
                leftPolygon.add(intersection);
                rightPolygon.add(intersection);
                leftPolygonOriginal.add(intersectionOriginal);
                rightPolygonOriginal.add(intersectionOriginal);
            }
        }

        cutPolygon1.addAll(leftPolygon);
        cutPolygon2.addAll(rightPolygon);
        cutPolygon1Original.addAll(leftPolygonOriginal);
        cutPolygon2Original.addAll(rightPolygonOriginal);

        drawingPanel.repaint();
        saveCoordinates(cutPolygon1, "cut_polygon_1.txt");
        saveCoordinates(cutPolygon2, "cut_polygon_2.txt");
        saveOriginalCoordinates(cutPolygon1Original, "cut_polygon_1_original.txt");
        saveOriginalCoordinates(cutPolygon2Original, "cut_polygon_2_original.txt");
    }

    
    private boolean isLeft(Point a, Point b, Point c) {
        return (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x) > 0;
    }

    private boolean doIntersect(Point a, Point b, Point c, Point d) {
        int o1 = orientation(a, b, c);
        int o2 = orientation(a, b, d);
        int o3 = orientation(c, d, a);
        int o4 = orientation(c, d, b);

        if (o1 != o2 && o3 != o4) {
            return true;
        }

        return false;
    }

    private int orientation(Point a, Point b, Point c) {
        int val = (b.y - a.y) * (c.x - b.x) - (b.x - a.x) * (c.y - b.y);
        if (val == 0) return 0;
        return (val > 0) ? 1 : 2;
    }

    private Point getIntersectionPoint(Point a, Point b, Point c, Point d) {
        double a1 = b.y - a.y;
        double b1 = a.x - b.x;
        double c1 = a1 * a.x + b1 * a.y;

        double a2 = d.y - c.y;
        double b2 = c.x - d.x;
        double c2 = a2 * c.x + b2 * c.y;

        double determinant = a1 * b2 - a2 * b1;
        if (determinant == 0) {
            return new Point(0, 0);
        } else {
            int x = (int) ((b2 * c1 - b1 * c2) / determinant);
            int y = (int) ((a1 * c2 - a2 * c1) / determinant);
            return new Point(x, y);
        }
    }

    private void saveCoordinates(List<Point> polygon, String filename) {
        ensureOutputDirectory();
        try (PrintWriter out = new PrintWriter(new FileWriter(OUTPUT_DIR + "/" + filename))) {
            for (Point p : polygon) {
                out.println(p.x + "," + p.y);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void saveOriginalCoordinates(List<Point> polygon, String filename) {
        ensureOutputDirectory();
        try (PrintWriter out = new PrintWriter(new FileWriter(OUTPUT_DIR + "/" + filename))) {
            for (Point p : polygon) {
                out.println(p.x + "," + p.y);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadFigure(String filename, String originalFilename) {
        List<Point> polygonToLoad = filename.equals("cut_polygon_1.txt") ? cutPolygon1 : cutPolygon2;
        List<Point> originalPolygonToLoad = filename.equals("cut_polygon_1.txt") ? cutPolygon1Original : cutPolygon2Original;
        polygonToLoad.clear();
        originalPolygonToLoad.clear();
        try (Scanner scanner = new Scanner(new File(OUTPUT_DIR + "/" + filename))) {
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                String[] xy = line.split(",");
                int x = Integer.parseInt(xy[0]);
                int y = Integer.parseInt(xy[1]);
                polygonToLoad.add(new Point(x, y));
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        try (Scanner scanner = new Scanner(new File(OUTPUT_DIR + "/" + originalFilename))) {
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                String[] xy = line.split(",");
                int x = Integer.parseInt(xy[0]);
                int y = Integer.parseInt(xy[1]);
                originalPolygonToLoad.add(new Point(x, y));
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        drawingPanel.repaint();
    }

    private void ensureOutputDirectory() {
        Path path = Paths.get(OUTPUT_DIR);
        if (!Files.exists(path)) {
            try {
                Files.createDirectories(path);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private class DrawingPanel extends JPanel {
        public DrawingPanel() {
            addMouseListener(new MouseAdapter() {
                public void mouseClicked(MouseEvent e) {
                    if (isCutting) {
                        if (cutStart == null) {
                            cutStart = e.getPoint();
                        } else {
                            cutEnd = e.getPoint();
                            cutPolygon(cutStart, cutEnd);
                            isCutting = false;
                            cutStart = null;
                            cutEnd = null;
                        }
                    }
                }

                public void mouseMoved(MouseEvent e) {
                    for (Point p : originalPolygon) {
                        if (p.distance(e.getPoint()) < 5) {
                            setToolTipText("Coordinates: " + (int) (p.x / xDownscaleFactor + X_MIN) + "," + (int) (p.y / yDownscaleFactor + Y_MIN));
                            return;
                        }
                    }
                    setToolTipText(null);
                }
            });
        }

        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            g.setColor(Color.BLACK);
            drawPolygon(g, originalPolygon);

            g.setColor(Color.GREEN);
            drawPolygon(g, cutPolygon1);

            g.setColor(Color.BLUE);
            drawPolygon(g, cutPolygon2);
        }

        private void drawPolygon(Graphics g, List<Point> polygon) {
            if (polygon.size() < 2) return;
            for (int i = 0; i < polygon.size() - 1; i++) {
                Point p1 = polygon.get(i);
                Point p2 = polygon.get(i + 1);
                g.drawLine(p1.x, p1.y, p2.x, p2.y);
                g.fillOval(p1.x - 2, p1.y - 2, 4, 4);
                g.fillOval(p2.x - 2, p2.y - 2, 4, 4);
            }
            Point first = polygon.get(0);
            Point last = polygon.get(polygon.size() - 1);
            g.drawLine(first.x, first.y, last.x, last.y);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                e.printStackTrace();
            }
            PolygonApp app = new PolygonApp();
            app.setVisible(true);
        });
    }
}
