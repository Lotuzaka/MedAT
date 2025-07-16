package svg;

import java.awt.Color;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.geom.util.GeometryCombiner;

public class SvgBuilder {
    private final StringBuilder sb = new StringBuilder();
    private final int width;
    private final int height;
    private final GeometryFactory gf = new GeometryFactory();
    private final List<Geometry> regions = new ArrayList<>();

    public SvgBuilder(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public void setupCircles(double ax, double ay, double ar,
                             double bx, double by, double br,
                             double cx, double cy, double cr) {
        try {
            Geometry A = circle(ax, ay, ar);
            Geometry B = circle(bx, by, br);
            Geometry C = circle(cx, cy, cr);
            regions.clear();
            
            // Use safer geometry operations with error handling
            regions.add(safeGeometryOperation(() -> A.difference(GeometryCombiner.combine(Arrays.asList(B, C))))); //0
            regions.add(safeGeometryOperation(() -> A.intersection(B).difference(C))); //1
            regions.add(safeGeometryOperation(() -> B.difference(GeometryCombiner.combine(Arrays.asList(A, C))))); //2
            regions.add(safeGeometryOperation(() -> A.intersection(B).intersection(C))); //3
            regions.add(safeGeometryOperation(() -> C.difference(GeometryCombiner.combine(Arrays.asList(A, B))))); //4
            regions.add(safeGeometryOperation(() -> A.intersection(C).difference(B))); //5
            regions.add(safeGeometryOperation(() -> B.intersection(C).difference(A))); //6
            regions.add(gf.createPolygon()); //7 empty

            sb.append(String.format("<circle id='A' cx='%.1f' cy='%.1f' r='%.1f' fill='none' stroke='black'/>", ax, ay, ar));
            sb.append(String.format("<circle id='B' cx='%.1f' cy='%.1f' r='%.1f' fill='none' stroke='black'/>", bx, by, br));
            sb.append(String.format("<circle id='C' cx='%.1f' cy='%.1f' r='%.1f' fill='none' stroke='black'/>", cx, cy, cr));
        } catch (Exception e) {
            // Fallback: create empty regions if geometry operations fail
            regions.clear();
            for (int i = 0; i < 8; i++) {
                regions.add(gf.createPolygon());
            }
            // Still add circle definitions for visual consistency
            sb.append(String.format("<circle id='A' cx='%.1f' cy='%.1f' r='%.1f' fill='none' stroke='black'/>", ax, ay, ar));
            sb.append(String.format("<circle id='B' cx='%.1f' cy='%.1f' r='%.1f' fill='none' stroke='black'/>", bx, by, br));
            sb.append(String.format("<circle id='C' cx='%.1f' cy='%.1f' r='%.1f' fill='none' stroke='black'/>", cx, cy, cr));
        }
    }

    private Geometry safeGeometryOperation(java.util.function.Supplier<Geometry> operation) {
        try {
            Geometry result = operation.get();
            return result != null ? result : gf.createPolygon();
        } catch (Exception e) {
            // Return empty geometry on any topology exception
            return gf.createPolygon();
        }
    }

    private Polygon circle(double x, double y, double r) {
        return (Polygon) gf.createPoint(new Coordinate(x, y)).buffer(r);
    }

    private String toPath(Geometry g) {
        if (g.isEmpty()) return "";
        StringBuilder path = new StringBuilder();
        for (int i = 0; i < g.getNumGeometries(); i++) {
            Polygon p = (Polygon) g.getGeometryN(i);
            Coordinate[] coords = p.getExteriorRing().getCoordinates();
            if (coords.length == 0) continue;
            path.append("M").append(coords[0].x).append(' ').append(coords[0].y);
            for (int j = 1; j < coords.length; j++) {
                path.append(" L").append(coords[j].x).append(' ').append(coords[j].y);
            }
            path.append('Z');
            for (int r = 0; r < p.getNumInteriorRing(); r++) {
                Coordinate[] inner = p.getInteriorRingN(r).getCoordinates();
                path.append(" M").append(inner[0].x).append(' ').append(inner[0].y);
                for (int j = 1; j < inner.length; j++) {
                    path.append(" L").append(inner[j].x).append(' ').append(inner[j].y);
                }
                path.append('Z');
            }
        }
        return path.toString();
    }

    public void fillRegion(int idx, Color color) {
        if (idx < 0 || idx >= regions.size()) return;
        Geometry g = regions.get(idx);
        if (g.isEmpty()) return;
        String path = toPath(g);
        String fill = String.format("rgb(%d,%d,%d)", color.getRed(), color.getGreen(), color.getBlue());
        sb.append(String.format("<path d='%s' fill='%s' stroke='none'/>", path, fill));
    }

    public void addText(String id, String text, double x, double y) {
        sb.append(String.format("<text id='%s' x='%.1f' y='%.1f' font-family='Arial' font-size='12' text-anchor='middle'>%s</text>",
                id, x, y, text));
    }

    /**
     * Adds a green dot marker to indicate existence claims in the specified region.
     */
    public void addExistenceMarker(int regionIndex) {
        // Define approximate center points for each region (matching EulerPanel)
        double[] regionCenters = {
            28, 45,   // Region 0: A only
            44, 45,   // Region 1: A ∩ B only  
            60, 45,   // Region 2: B only
            50, 38,   // Region 3: A ∩ B ∩ C
            50, 18,   // Region 4: C only
            40, 30,   // Region 5: A ∩ C only
            60, 30,   // Region 6: B ∩ C only
            80, 80    // Region 7: Outside (unused)
        };
        
        if (regionIndex < regionCenters.length / 2) {
            double x = regionCenters[regionIndex * 2];
            double y = regionCenters[regionIndex * 2 + 1];
            
            // Add a small green circle with white border
            sb.append(String.format("<circle cx='%.1f' cy='%.1f' r='3' fill='rgb(0,150,0)' stroke='white' stroke-width='1'/>", x, y));
        }
    }

    public Path saveSvg(Path dir, String fileName) throws IOException {
        Files.createDirectories(dir);
        Path out = dir.resolve(fileName);
        String content = "<svg xmlns='http://www.w3.org/2000/svg' width='"+width+"' height='"+height+"'>" + sb + "</svg>";
        Files.writeString(out, content);
        return out;
    }
}
