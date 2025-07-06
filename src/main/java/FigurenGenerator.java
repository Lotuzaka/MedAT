import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.Document;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.geom.util.AffineTransformation;
import org.locationtech.jts.geom.util.LineStringExtracter;
import org.locationtech.jts.noding.NodedSegmentString;
import org.locationtech.jts.noding.SegmentString;
import org.locationtech.jts.noding.snapround.SnapRoundingNoder;
import org.locationtech.jts.operation.polygonize.Polygonizer;

public class FigurenGenerator {
    // zentrale Factory & Random
    private static final PrecisionModel PM = new PrecisionModel(1_000_000);
    private static final GeometryFactory GF = new GeometryFactory(PM, 0);
    private static final Random RAND = new Random();

    private final Geometry original;
    private final String shapeType;

    public FigurenGenerator(Geometry geometry, String shapeType) {
        this.original = geometry;
        this.shapeType = shapeType.toLowerCase();
    }

    public Geometry getGeometry() {
        return original;
    }

    public String getShapeType() {
        return shapeType;
    }

    /** Form-Erzeugung */
    public static Geometry createShape(String type, int cx, int cy, int r) {
        switch (type.toLowerCase()) {
            case "hexagon":
                return createRegular(cx, cy, r, 6);
            case "heptagon":
                return createRegular(cx, cy, r, 7);
            case "octagon":
                return createRegular(cx, cy, r, 8);
            case "pentagon":
                return createRegular(cx, cy, r, 5);
            case "circle":
                return createRegular(cx, cy, r, 64);
            case "half circle":
                return createArc(cx, cy, r, 0, 180);
            case "quarter circle":
                return createArc(cx, cy, r, 0, 90);
            case "three-quarter circle":
                return createArc(cx, cy, r, 0, 270);
            default:
                throw new IllegalArgumentException("Unsupported shape: " + type);
        }
    }

    private static Polygon createRegular(int cx, int cy, int r, int sides) {
        Coordinate[] pts = new Coordinate[sides + 1];
        for (int i = 0; i < sides; i++) {
            double ang = 2 * Math.PI * i / sides;
            pts[i] = new Coordinate(cx + r * Math.cos(ang), cy + r * Math.sin(ang));
        }
        pts[sides] = pts[0];
        LinearRing ring = GF.createLinearRing(pts);
        return GF.createPolygon(ring, null);
    }

    private static Polygon createArc(int cx, int cy, int r, double start, double extent) {
        int segs = 64;
        List<Coordinate> pts = new ArrayList<>(segs + 3);
        for (int i = 0; i <= segs; i++) {
            double ang = Math.toRadians(start + i * (extent / segs));
            pts.add(new Coordinate(cx + r * Math.cos(ang), cy + r * Math.sin(ang)));
        }
        pts.add(new Coordinate(cx, cy));
        pts.add(pts.get(0));
        LinearRing ring = GF.createLinearRing(pts.toArray(new Coordinate[0]));
        return GF.createPolygon(ring, null);
    }

    /** Dissector für Easy / Hard */
    public class PolygonDissector {
        private final Difficulty diff;

        public PolygonDissector(String type, String difficulty) {
            this.diff = "hard".equalsIgnoreCase(difficulty) ? Difficulty.HARD : Difficulty.EASY;
        }

        public DissectedPieces dissect(int count) {
            List<Geometry> pieces = (diff == Difficulty.EASY)
                    ? GeometryUtils.randomSplit(original, count)
                    : GeometryUtils.interlockingSplit(original, count);
            List<Geometry> rotated = rotateAll(pieces);
            return new DissectedPieces(pieces, rotated);
        }

        private List<Geometry> rotateAll(List<Geometry> list) {
            List<Geometry> out = new ArrayList<>(list.size());
            for (Geometry g : list) {
                double ang = Math.toRadians(RAND.nextInt(360));
                double dx = RAND.nextInt(11) - 5;
                double dy = RAND.nextInt(11) - 5;
                out.add(GeometryUtils.rotateTranslate(g, ang, dx, dy));
            }
            return out;
        }
    }

    /** einfache Enumeration */
    private enum Difficulty {
        EASY, HARD
    }

    /** DissectedPieces */
    public static class DissectedPieces {
        public List<Geometry> originalPieces;
        public List<Geometry> assembledPieces;
        public List<Geometry> rotatedPieces;

        public DissectedPieces(List<Geometry> o, List<Geometry> r, List<Geometry> a) {
            this.originalPieces = o;
            this.rotatedPieces = r;
            this.assembledPieces = a;
        }

        public DissectedPieces(List<Geometry> o, List<Geometry> r) {
            this.originalPieces = o;
            this.rotatedPieces = r;
        }

        public List<Geometry> getAssembledPieces() {
            return assembledPieces;
        }
    }

    /** Utility-Klasse für Geometry-Helfer */
    private static class GeometryUtils {
        public static List<Geometry> randomSplit(Geometry poly, int target) {
            return genericSplit(poly, target);
        }

        public static List<Geometry> interlockingSplit(Geometry poly, int target) {
            return genericSplit(poly, target);
        }

        private static List<Geometry> genericSplit(Geometry seed, int target) {
            List<Geometry> parts = new ArrayList<>();
            parts.add(seed);
            double minArea = seed.getArea() * 0.02;
            int attempts = 0;
            while (parts.size() < target && attempts++ < 500) {
                parts.sort(Comparator.comparingDouble(Geometry::getArea).reversed());
                Geometry g = parts.remove(0);
                LineString cut = generateCut(g);
                List<Geometry> split = splitWithLine(g, cut);
                if (split != null
                        && split.size() >= 2
                        && split.stream().allMatch(x -> x.getArea() >= minArea)) {
                    parts.addAll(split);
                } else {
                    parts.add(g);
                }
            }
            if (parts.size() != target) {
                return radialFallback(seed, target);
            }
            return parts;
        }

        private static List<Geometry> radialFallback(Geometry poly, int n) {
            Coordinate c = poly.getCentroid().getCoordinate();
            double maxR = 0;
            for (Coordinate pc : poly.getCoordinates()) {
                maxR = Math.max(maxR, c.distance(pc));
            }
            List<Geometry> out = new ArrayList<>();
            double step = 2 * Math.PI / n;
            for (int i = 0; i < n; i++) {
                double a0 = i * step, a1 = a0 + step;
                List<Coordinate> pts = new ArrayList<>(34);
                pts.add(c);
                for (int j = 0; j <= 32; j++) {
                    double a = a0 + (a1 - a0) * j / 32;
                    pts.add(new Coordinate(c.x + Math.cos(a) * maxR,
                            c.y + Math.sin(a) * maxR));
                }
                pts.add(c);
                LinearRing ring = GF.createLinearRing(pts.toArray(new Coordinate[0]));
                Polygon wedge = GF.createPolygon(ring, null);
                Geometry inter = wedge.intersection(poly);
                if (inter instanceof Polygon)
                    out.add(inter);
            }
            return out;
        }

        private static LineString generateCut(Geometry poly) {
            double r = RAND.nextDouble();
            if (r < 0.5)
                return straightCut(poly);
            else if (r < 0.8)
                return notchCut(poly);
            else
                return centroidCut(poly);
        }

        private static LineString straightCut(Geometry poly) {
            Coordinate p1 = randomInside(poly), p2 = randomInside(poly);
            int i = 0;
            while ((p1 == null || p2 == null || p1.equals2D(p2)) && i++ < 10) {
                p1 = randomInside(poly);
                p2 = randomInside(poly);
            }
            if (p1 != null && p2 != null && !p1.equals2D(p2)) {
                return GF.createLineString(new Coordinate[] { p1, p2 });
            }
            return centroidCut(poly);
        }

        private static LineString notchCut(Geometry poly) {
            Coordinate a = randomOnBoundary(poly),
                    b = randomOnBoundary(poly);
            if (a == null || b == null || a.equals2D(b))
                return centroidCut(poly);
            int notches = 1 + RAND.nextInt(3);
            List<Coordinate> cs = new ArrayList<>();
            cs.add(a);
            double dx = b.x - a.x, dy = b.y - a.y, L = Math.hypot(dx, dy);
            double ux = dx / L, uy = dy / L, px = -uy, py = ux;
            for (int i = 0; i < notches; i++) {
                double t = 0.1 + 0.8 * RAND.nextDouble();
                double bx = a.x + dx * t, by = a.y + dy * t;
                double depth = L * (0.05 + 0.1 * RAND.nextDouble());
                int dir = RAND.nextBoolean() ? 1 : -1;
                double ang = Math.toRadians(RAND.nextDouble() * 30 - 15);
                double cos = Math.cos(ang), sin = Math.sin(ang);
                double nx = px * cos - py * sin, ny = px * sin + py * cos;
                cs.add(new Coordinate(bx, by));
                cs.add(new Coordinate(bx + nx * depth * dir,
                        by + ny * depth * dir));
            }
            cs.add(b);
            return GF.createLineString(cs.toArray(new Coordinate[0]));
        }

        private static LineString centroidCut(Geometry poly) {
            Coordinate c = poly.getCentroid().getCoordinate();
            double ang = 2 * Math.PI * RAND.nextDouble();
            double d = Math.max(poly.getEnvelopeInternal().getWidth(),
                    poly.getEnvelopeInternal().getHeight()) * 2;
            Coordinate a = new Coordinate(c.x - d * Math.cos(ang),
                    c.y - d * Math.sin(ang));
            Coordinate b = new Coordinate(c.x + d * Math.cos(ang),
                    c.y + d * Math.sin(ang));
            Geometry inter = poly.getBoundary()
                    .intersection(GF.createLineString(new Coordinate[] { a, b }));
            Coordinate[] pts = inter.getCoordinates();
            if (pts.length >= 2)
                return GF.createLineString(new Coordinate[] { pts[0], pts[1] });
            return GF.createLineString(new Coordinate[] { a, b });
        }

        private static Coordinate randomInside(Geometry poly) {
            Envelope e = poly.getEnvelopeInternal();
            for (int i = 0; i < 1000; i++) {
                double x = e.getMinX() + RAND.nextDouble() * e.getWidth();
                double y = e.getMinY() + RAND.nextDouble() * e.getHeight();
                Point p = GF.createPoint(new Coordinate(x, y));
                if (poly.contains(p))
                    return p.getCoordinate();
            }
            return null;
        }

        private static Coordinate randomOnBoundary(Geometry poly) {
            Coordinate[] cs = poly.getBoundary().getCoordinates();
            return cs.length > 0 ? cs[RAND.nextInt(cs.length)] : null;
        }

        /** Noding + Polygonizer split */
        private static List<Geometry> splitWithLine(Geometry poly, LineString cut) {
            if (!poly.intersects(cut))
                return null;

            // 1) SegmentStrings aus Boundary und cut erzeugen
            List<SegmentString> segs = new ArrayList<>();
            // LineStringExtracter liefert leider eine rohe Liste, daher als Object
            // iterieren:
            for (Object o : LineStringExtracter.getLines(poly.getBoundary())) {
                LineString ls = (LineString) o;
                segs.add(new NodedSegmentString(ls.getCoordinates(), null));
            }
            segs.add(new NodedSegmentString(cut.getCoordinates(), null));

            // 2) SnapRounding noder
            SnapRoundingNoder noder = new SnapRoundingNoder(PM);
            try {
                noder.computeNodes(segs);
            } catch (Exception ex) {
                return null;
            }

            // 3) Polygonizer befüllen
            Polygonizer polyz = new Polygonizer();
            // getNodedSubstrings() ist ebenfalls roh typisiert
            for (Object obj : noder.getNodedSubstrings()) {
                SegmentString ss = (SegmentString) obj;
                Coordinate[] coords = ss.getCoordinates();
                if (coords.length > 1) {
                    polyz.add(GF.createLineString(coords));
                }
            }

            // 4) Ergebnisse sammeln
            List<Geometry> result = new ArrayList<>();
            for (Object o : polyz.getPolygons()) {
                Polygon p = (Polygon) o;
                if (poly.contains(p.getInteriorPoint()) && p.isValid()) {
                    result.add(p);
                }
            }

            return result.size() >= 2 ? result : null;
        }

        public static Geometry rotateTranslate(Geometry g, double ang, double dx, double dy) {
            Coordinate c = g.getCentroid().getCoordinate();
            AffineTransformation at = AffineTransformation
                    .translationInstance(-c.x, -c.y)
                    .rotate(ang)
                    .translate(c.x + dx, c.y + dy);
            Geometry r = at.transform(g);
            return r.isValid() ? r : r.buffer(0);
        }
    }

    /** Distraktoren (andere Formen) */
    public List<Geometry> generateDistractorShapes(int num) {
        // Nur die passenden Pools je nach Ausgangsform
        List<String> polygonShapes = List.of("hexagon", "heptagon", "octagon", "pentagon");
        List<String> circleShapes = List.of("circle", "half circle", "quarter circle", "three-quarter circle");

        // Wähle Pool basierend auf shapeType
        List<String> pool;
        if (polygonShapes.contains(shapeType)) {
            pool = new ArrayList<>(polygonShapes);
        } else if (circleShapes.contains(shapeType)) {
            pool = new ArrayList<>(circleShapes);
        } else {
            // Fallback: im Zweifel alle Formen
            pool = new ArrayList<>();
            pool.addAll(polygonShapes);
            pool.addAll(circleShapes);
        }

        // Entferne die Ursprungsshape selbst
        pool.remove(shapeType);

        List<Geometry> out = new ArrayList<>(num);
        for (int i = 0; i < num; i++) {
            String candidate;
            boolean used;
            do {
                // 1) neuen Kandidaten ziehen
                candidate = pool.get(RAND.nextInt(pool.size()));
                // 2) prüfen, ob er in einem existing-Shape vorkommt
                used = false;
                for (Geometry g : out) {
                    if (g.toString().contains(candidate)) {
                        used = true;
                        break;
                    }
                }
            } while (used);
            // 3) akzeptierten Kandidaten hinzufügen
            out.add(createShape(candidate, 200, 200, 100));
        }
        Collections.shuffle(out);
        return out;
    }

    /** Panel → Bild */
    public static BufferedImage getPanelImage(JPanel panel) {
        Dimension d = panel.getSize();
        if (d.width <= 0 || d.height <= 0) {
            d = panel.getPreferredSize();
            panel.setSize(d.width > 0 ? d.width : 400,
                    d.height > 0 ? d.height : 400);
        }
        BufferedImage img = new BufferedImage(
                panel.getWidth(), panel.getHeight(),
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setColor(Color.WHITE);
        g2.fillRect(0, 0, panel.getWidth(), panel.getHeight());
        if (panel.isVisible())
            panel.printAll(g2);
        g2.dispose();
        return img;
    }

    /** DOCX-Export */
    public void exportToWord(BufferedImage q, List<BufferedImage> opts, BufferedImage sol)
            throws IOException, InvalidFormatException {
        try (XWPFDocument doc = new XWPFDocument()) {
            addImage(doc, "Question:", q);
            for (int i = 0; i < opts.size(); i++) {
                addImage(doc, "Option " + (char) ('A' + i) + ":", opts.get(i));
            }
            XWPFParagraph pE = doc.createParagraph();
            pE.createRun().setText("Option E: X");
            addImage(doc, "Solution:", sol);
            try (FileOutputStream out = new FileOutputStream("FigurenQuestion.docx")) {
                doc.write(out);
            }
        }
    }

    private void addImage(XWPFDocument doc, String label, BufferedImage img)
            throws IOException, InvalidFormatException {
        XWPFParagraph p = doc.createParagraph();
        XWPFRun r = p.createRun();
        r.setText(label);
        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            ImageIO.write(img, "png", os);
            try (InputStream is = new ByteArrayInputStream(os.toByteArray())) {
                r.addPicture(is,
                        Document.PICTURE_TYPE_PNG,
                        label + ".png",
                        Units.toEMU(200),
                        Units.toEMU(200));
            }
        }
    }

    /** Hauptprogramm */
    public static void main(String[] args) throws Exception {
        String[] shapes = {
                "Hexagon", "Octagon", "Heptagon", "Pentagon",
                "circle", "half circle", "quarter circle", "three-quarter circle"
        };
        String sel = shapes[RAND.nextInt(shapes.length)];
        int cnt = 5 + RAND.nextInt(3);
        String diff = RAND.nextBoolean() ? "hard" : "easy";
        System.out.printf("Shape=%s, Pieces=%d, Diff=%s%n", sel, cnt, diff);

        final Geometry orig = createShape(sel, 200, 200, 100);
        final FigurenGenerator gen = new FigurenGenerator(orig, sel);
        final PolygonDissector dis = gen.new PolygonDissector(sel, diff);
        final DissectedPieces dp = dis.dissect(cnt);

        final PolygonPanel questionPanel = new PolygonPanel(dp.rotatedPieces);
        questionPanel.setPreferredSize(new Dimension(200, 200));

        final List<PolygonPanel> options = new ArrayList<>();
        PolygonPanel correct = new PolygonPanel(dp.originalPieces);
        correct.setAssembled(true);
        correct.setFillColor(new Color(200, 200, 200));
        correct.setOutlineColor(Color.BLACK);
        options.add(correct);
        for (Geometry d : gen.generateDistractorShapes(3)) {
            options.add(new PolygonPanel(List.of(d)));
        }
        Collections.shuffle(options);
        options.get(options.indexOf(correct)).setFillColor(Color.GREEN);

        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Figuren Zusammensetzen");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setLayout(new java.awt.BorderLayout());

            JLabel lbl = new JLabel("Difficulty: " + diff, SwingConstants.CENTER);
            frame.add(lbl, java.awt.BorderLayout.NORTH);
            frame.add(questionPanel, java.awt.BorderLayout.CENTER);

            JPanel optsPanel = new JPanel(new java.awt.GridLayout(1, 5));
            for (int i = 0; i < options.size(); i++) {
                JPanel ctr = new JPanel(new java.awt.BorderLayout());
                ctr.add(options.get(i), java.awt.BorderLayout.CENTER);
                ctr.add(new JLabel("Option " + (char) ('A' + i)),
                        java.awt.BorderLayout.SOUTH);
                optsPanel.add(ctr);
            }
            JPanel ePanel = new JPanel(new java.awt.BorderLayout());
            ePanel.add(new JLabel("Option E: X", SwingConstants.CENTER),
                    java.awt.BorderLayout.CENTER);
            optsPanel.add(ePanel);

            frame.add(optsPanel, java.awt.BorderLayout.SOUTH);
            frame.pack();
            frame.setVisible(true);
        });
    }
}
