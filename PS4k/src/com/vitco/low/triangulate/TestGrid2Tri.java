package com.vitco.low.triangulate;

import com.vitco.util.misc.StringIndexer;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LinearRing;
import com.vividsolutions.jts.geom.Polygon;
import com.vividsolutions.jts.geom.impl.CoordinateArraySequence;
import org.jaitools.imageutils.ImageUtils;
import org.junit.Test;
import org.poly2tri.geometry.polygon.PolygonPoint;
import org.poly2tri.triangulation.delaunay.DelaunayTriangle;

import javax.imageio.ImageIO;
import javax.media.jai.TiledImage;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;

/**
 * Some basic tests for the "outdated" triangulation algorithm.
 *
 * - Monotone Meshing
 * - Naive Greedy Meshing
 * - Poly2Tri with slow conversion "voxel -> polygon"
 * - "Manual" testing by outputting triangulation
 * - some other helper tests for slow conversion
 */
public class TestGrid2Tri {

    // helper - true if point c is in between point a and b
    private static boolean inBetween(Point a, Point b, Point c) {
        return (!a.equals(c) && !b.equals(c)) && // not the same points
                ((b.x - a.x) * (c.y - a.y) == (c.x - a.x) * (b.y - a.y)) && // on one line
                ((a.x < c.x == c.x < b.x) && (a.y < c.y == c.y < b.y)); // in between on that line
    }

    // test if the mono triangulation is correct and has t-junction problems
    // in the 2D plane
    @Test
    public void testMonoTriangulation() {
        for (int i = 7500; i < 20000; i++) {
            Random rand = new Random(i);
            // create image
            int sizex = rand.nextInt(100)+5;
            int sizey = rand.nextInt(100)+5;
            boolean[][] data = new boolean[sizex][sizey];
            TiledImage src = ImageUtils.createConstantImage(sizex, sizey, 0);

            // fill with random data
            int count = rand.nextInt(sizex * sizey * 2);
            for (int j = 0; j < count; j++) {
                int x = rand.nextInt(sizex);
                int y = rand.nextInt(sizey);
                data[x][y] = true;
                src.setSample(x, y, 0, 1);
            }

//            // save image (for checking)
//            BufferedImage bufferedImage = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
//            for (int x = 0; x < src.getWidth(); x++) {
//                for (int y = 0; y < src.getHeight(); y++) {
//                    bufferedImage.setRGB(x, y, src.getSample(x, y, 0) == 1 ? Color.BLACK.getRGB() : Color.WHITE.getRGB());
//                }
//            }
//            File outputfile = new File("image" + i + ".png");
//            try {
//                ImageIO.write(bufferedImage, "png", outputfile);
//            } catch (IOException e) {
//                e.printStackTrace();
//            }


            // print information
            System.out.print("Test " + i + " @ " + sizex + " x " + sizey + " :: ");
            Collection<Polygon> geometry = Grid2TriPolySlow.doVectorize(src);
            System.out.print(geometry.size());
            System.out.print(" :: ");
            ArrayList<DelaunayTriangle> tris = Grid2TriMono.triangulate(data, true);
            System.out.print(tris.size());

            // check that no points are overlapping
            HashSet<Point> points = new HashSet<Point>();
            for (DelaunayTriangle tri : tris) {
                points.add(new Point((int)Math.round(tri.points[0].getX()),(int)Math.round(tri.points[0].getY())));
                points.add(new Point((int)Math.round(tri.points[1].getX()),(int)Math.round(tri.points[1].getY())));
                points.add(new Point((int)Math.round(tri.points[2].getX()),(int)Math.round(tri.points[2].getY())));
            }
            for (Point p : points) {
                for (DelaunayTriangle tri : tris) {
                    //System.out.println(p.toString() + " " + tri.points[0] + " " + tri.points[1] + " " + tri.points[2]);
                    assert !inBetween(new Point((int)Math.round(tri.points[0].getX()),(int)Math.round(tri.points[0].getY())),
                            new Point((int)Math.round(tri.points[1].getX()),(int)Math.round(tri.points[1].getY())), p);
                    assert !inBetween(new Point((int)Math.round(tri.points[0].getX()),(int)Math.round(tri.points[0].getY())),
                            new Point((int)Math.round(tri.points[2].getX()),(int)Math.round(tri.points[2].getY())), p);
                    assert !inBetween(new Point((int)Math.round(tri.points[1].getX()),(int)Math.round(tri.points[1].getY())),
                            new Point((int)Math.round(tri.points[2].getX()),(int)Math.round(tri.points[2].getY())), p);
                }
            }

            // variables
            GeometryFactory geometryFactory = new GeometryFactory();

            // stores the area sum of all triangles
            double aTri = 0;


            int statusCount = 0;
            for (DelaunayTriangle tri: tris) {
                // handle triangle area
                double area = tri.area();
                aTri += area;
                assert area > 0.25;

                // print info
                if (statusCount%((tris.size()/100)+1) == 0) {
                    System.out.print(".");
                }
                statusCount++;

                // convert into geometry
                LinearRing ring = new LinearRing(new CoordinateArraySequence(
                        new Coordinate[]{
                                new Coordinate(tri.points[0].getX(), tri.points[0].getY()),
                                new Coordinate(tri.points[1].getX(), tri.points[1].getY()),
                                new Coordinate(tri.points[2].getX(), tri.points[2].getY()),
                                new Coordinate(tri.points[0].getX(), tri.points[0].getY())
                        }
                ), geometryFactory);
                Polygon triPoly = new Polygon(ring, new LinearRing[0], geometryFactory);
                // check that points are different (area exists)
                assert triPoly.getArea() > 0.25;

                // check containment
                boolean contain = false;
                for (Polygon poly : geometry) {
                    // check for containment
                    if (poly.contains(triPoly)) {
                        contain = true;
                        break;
                    }
                }
                assert contain;
            }
            // check that areas match
            double aPoly = 0;
            for (Polygon poly : geometry) {
                double area = poly.getArea();
                aPoly += area;
                assert area > 0.25;
            }
            assert Math.round(aTri) == Math.round(aPoly);

            System.out.println(" :: ");
        }
    }

    // test if the greedy triangulation is correct
    @Test
    public void testGreedyTriangulation() {
        for (int i = 0; i < 20000; i++) {
            Random rand = new Random(i);
            // create image
            int sizex = rand.nextInt(100)+5;
            int sizey = rand.nextInt(100)+5;
            boolean[][] data = new boolean[sizex][sizey];
            TiledImage src = ImageUtils.createConstantImage(sizex, sizey, 0);

            // fill with random data
            int count = rand.nextInt(sizex * sizey * 2);
            for (int j = 0; j < count; j++) {
                int x = rand.nextInt(sizex);
                int y = rand.nextInt(sizey);
                data[x][y] = true;
                src.setSample(x, y, 0, 1);
            }

//            // save image (for checking)
//            BufferedImage bufferedImage = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
//            for (int x = 0; x < src.getWidth(); x++) {
//                for (int y = 0; y < src.getHeight(); y++) {
//                    bufferedImage.setRGB(x, y, src.getSample(x, y, 0) == 1 ? Color.BLACK.getRGB() : Color.WHITE.getRGB());
//                }
//            }
//            File outputfile = new File("image" + i + ".png");
//            try {
//                ImageIO.write(bufferedImage, "png", outputfile);
//            } catch (IOException e) {
//                e.printStackTrace();
//            }


            // print information
            System.out.print("Test " + i + " @ " + sizex + " x " + sizey + " :: ");
            Collection<Polygon> geometry = Grid2TriPolySlow.doVectorize(src);
            System.out.print(geometry.size());
            System.out.print(" :: ");
            ArrayList<DelaunayTriangle> tris = Grid2TriNaiveGreedy.triangulate(data);
            System.out.print(tris.size());

            // variables
            GeometryFactory geometryFactory = new GeometryFactory();

            // stores the area sum of all triangles
            double aTri = 0;


            int statusCount = 0;
            for (DelaunayTriangle tri: tris) {
                // handle triangle area
                double area = tri.area();
                aTri += area;
                assert area > 0.25;

                // print info
                if (statusCount%((tris.size()/100)+1) == 0) {
                    System.out.print(".");
                }
                statusCount++;

                // convert into geometry
                LinearRing ring = new LinearRing(new CoordinateArraySequence(
                        new Coordinate[]{
                                new Coordinate(tri.points[0].getX(), tri.points[0].getY()),
                                new Coordinate(tri.points[1].getX(), tri.points[1].getY()),
                                new Coordinate(tri.points[2].getX(), tri.points[2].getY()),
                                new Coordinate(tri.points[0].getX(), tri.points[0].getY())
                        }
                ), geometryFactory);
                Polygon triPoly = new Polygon(ring, new LinearRing[0], geometryFactory);
                // check that points are different (area exists)
                assert triPoly.getArea() > 0.25;

                // check containment
                boolean contain = false;
                for (Polygon poly : geometry) {
                    // check for containment
                    if (poly.contains(triPoly)) {
                        contain = true;
                        break;
                    }
                }
                assert contain;
            }
            // check that areas match
            double aPoly = 0;
            for (Polygon poly : geometry) {
                double area = poly.getArea();
                aPoly += area;
                assert area > 0.25;
            }
            assert Math.round(aTri) == Math.round(aPoly);

            System.out.println(" :: ");
        }
    }

    // test if the poly2tri triangulation is correct, using a slow external library
    // to do the conversion from voxel data to polygon
    @Test
    public void testPolyTriangulation() {
        for (int i = 0; i < 20000; i++) {
            Random rand = new Random(i);
            // create image
            int sizex = rand.nextInt(100)+5;
            int sizey = rand.nextInt(100)+5;
            TiledImage src = ImageUtils.createConstantImage(sizex, sizey, 0);

            // fill with random data
            int count = rand.nextInt(sizex * sizey * 2);
            for (int j = 0; j < count; j++) {
                src.setSample(rand.nextInt(sizex), rand.nextInt(sizey), 0, 1);
            }

//            // save image (for checking)
//            BufferedImage bufferedImage = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
//            for (int x = 0; x < src.getWidth(); x++) {
//                for (int y = 0; y < src.getHeight(); y++) {
//                    bufferedImage.setRGB(x, y, src.getSample(x, y, 0) == 1 ? Color.BLACK.getRGB() : Color.WHITE.getRGB());
//                }
//            }
//            File outputfile = new File("image" + i + ".png");
//            try {
//                ImageIO.write(bufferedImage, "png", outputfile);
//            } catch (IOException e) {
//                e.printStackTrace();
//            }


            // print information
            System.out.print("Test " + i + " @ " + sizex + " x " + sizey + " :: ");
            Collection<Polygon> geometry = Grid2TriPolySlow.doVectorize(src);
            System.out.print(geometry.size());
            System.out.print(" :: ");
            ArrayList<DelaunayTriangle> tris = Grid2TriPolySlow.triangulate(geometry, false);
            System.out.print(tris.size());

            // check that no points are overlapping
            HashSet<Point> points = new HashSet<Point>();
            for (DelaunayTriangle tri : tris) {
                points.add(new Point((int)Math.round(tri.points[0].getX()),(int)Math.round(tri.points[0].getY())));
                points.add(new Point((int)Math.round(tri.points[1].getX()),(int)Math.round(tri.points[1].getY())));
                points.add(new Point((int)Math.round(tri.points[2].getX()),(int)Math.round(tri.points[2].getY())));
            }
            for (Point p : points) {
                for (DelaunayTriangle tri : tris) {
                    //System.out.println(p.toString() + " " + tri.points[0] + " " + tri.points[1] + " " + tri.points[2]);
                    assert !inBetween(new Point((int)Math.round(tri.points[0].getX()),(int)Math.round(tri.points[0].getY())),
                            new Point((int)Math.round(tri.points[1].getX()),(int)Math.round(tri.points[1].getY())), p);
                    assert !inBetween(new Point((int)Math.round(tri.points[0].getX()),(int)Math.round(tri.points[0].getY())),
                            new Point((int)Math.round(tri.points[2].getX()),(int)Math.round(tri.points[2].getY())), p);
                    assert !inBetween(new Point((int)Math.round(tri.points[1].getX()),(int)Math.round(tri.points[1].getY())),
                            new Point((int)Math.round(tri.points[2].getX()),(int)Math.round(tri.points[2].getY())), p);
                }
            }

            // variables
            GeometryFactory geometryFactory = new GeometryFactory();

            // stores the area sum of all triangles
            double aTri = 0;


            int statusCount = 0;
            for (DelaunayTriangle tri: tris) {
                // handle triangle area
                double area = tri.area();
                aTri += area;
                assert area > 0.25;

                // print info
                if (statusCount%((tris.size()/100)+1) == 0) {
                    System.out.print(".");
                }
                statusCount++;

                // convert into geometry
                LinearRing ring = new LinearRing(new CoordinateArraySequence(
                        new Coordinate[]{
                                new Coordinate(tri.points[0].getX(), tri.points[0].getY()),
                                new Coordinate(tri.points[1].getX(), tri.points[1].getY()),
                                new Coordinate(tri.points[2].getX(), tri.points[2].getY()),
                                new Coordinate(tri.points[0].getX(), tri.points[0].getY())
                        }
                ), geometryFactory);
                Polygon triPoly = new Polygon(ring, new LinearRing[0], geometryFactory);
                // check that points are different (area exists)
                assert triPoly.getArea() > 0.25;

                // check containment
                boolean contain = false;
                for (Polygon poly : geometry) {
                    // check for containment
                    if (poly.intersects(triPoly)) {
                        if (poly.contains(triPoly) || (poly.intersection(triPoly).getArea() - triPoly.getArea() < 0.00001)) {
                            contain = true;
                        }
                        break;
                    }
                }
                if (!contain) {
                    System.out.println(triPoly.toString());
                }
                assert contain;
            }
            // check that areas match
            double aPoly = 0;
            for (Polygon poly : geometry) {
                double area = poly.getArea();
                aPoly += area;
                assert area > 0.25;
            }
            assert Math.round(aTri) == Math.round(aPoly);

            System.out.println(" :: ");
        }
    }

    // test if the point tracker is working correctly (we need this to process
    // the output of the slow external library that converts voxel data to polygon
    // and make it ok for input to poly2tri)
    @Test
    public void testPointTracker() {
        StringIndexer pointTracker = new StringIndexer();

        pointTracker.index(new PolygonPoint(5.0,67.0).toString(), 0);
        pointTracker.index(new PolygonPoint(6.0,67.0).toString(), 0);
        pointTracker.index(new PolygonPoint(6.0,69.0).toString(), 0);
        pointTracker.index(new PolygonPoint(17.0,69.0).toString(), 0);
        pointTracker.index(new PolygonPoint(17.0,66.0).toString(), 0);
        pointTracker.index(new PolygonPoint(18.0,66.0).toString(), 0);
        pointTracker.index(new PolygonPoint(18.0,67.0).toString(), 0);
        pointTracker.index(new PolygonPoint(19.0,67.0).toString(), 0);
        pointTracker.index(new PolygonPoint(19.0,66.0).toString(), 0);
        pointTracker.index(new PolygonPoint(20.0,66.0).toString(), 0);
        pointTracker.index(new PolygonPoint(20.0,67.0).toString(), 0);
        pointTracker.index(new PolygonPoint(21.0,67.0).toString(), 0);
        pointTracker.index(new PolygonPoint(21.0,68.0).toString(), 0);
        pointTracker.index(new PolygonPoint(22.0,68.0).toString(), 0);
        pointTracker.index(new PolygonPoint(22.0,69.0).toString(), 0);
        pointTracker.index(new PolygonPoint(23.0,69.0).toString(), 0);
        pointTracker.index(new PolygonPoint(23.0,67.0).toString(), 0);
        pointTracker.index(new PolygonPoint(22.0,67.0).toString(), 0);
        pointTracker.index(new PolygonPoint(22.0,66.0).toString(), 0);
        pointTracker.index(new PolygonPoint(21.0,66.0).toString(), 0);
        pointTracker.index(new PolygonPoint(21.0,65.0).toString(), 0);
        pointTracker.index(new PolygonPoint(23.0,65.0).toString(), 0);
        pointTracker.index(new PolygonPoint(23.0,66.0).toString(), 0);
        pointTracker.index(new PolygonPoint(25.0,66.0).toString(), 0);
        pointTracker.index(new PolygonPoint(25.0,65.0).toString(), 0);
        pointTracker.index(new PolygonPoint(26.0,65.0).toString(), 0);
        pointTracker.index(new PolygonPoint(26.0,67.0).toString(), 0);
        pointTracker.index(new PolygonPoint(31.0,67.0).toString(), 0);
        pointTracker.index(new PolygonPoint(31.0,69.0).toString(), 0);
        pointTracker.index(new PolygonPoint(33.0,69.0).toString(), 0);
        pointTracker.index(new PolygonPoint(33.0,68.0).toString(), 0);
        pointTracker.index(new PolygonPoint(34.0,68.0).toString(), 0);
        pointTracker.index(new PolygonPoint(34.0,69.0).toString(), 0);
        pointTracker.index(new PolygonPoint(38.0,69.0).toString(), 0);
        pointTracker.index(new PolygonPoint(38.0,68.0).toString(), 0);
        pointTracker.index(new PolygonPoint(40.0,68.0).toString(), 0);
        pointTracker.index(new PolygonPoint(40.0,69.0).toString(), 0);
        pointTracker.index(new PolygonPoint(41.0,69.0).toString(), 0);
        pointTracker.index(new PolygonPoint(41.0,63.0).toString(), 0);
        pointTracker.index(new PolygonPoint(38.0,63.0).toString(), 0);
        pointTracker.index(new PolygonPoint(38.0,61.0).toString(), 0);
        pointTracker.index(new PolygonPoint(39.0,61.0).toString(), 0);
        pointTracker.index(new PolygonPoint(39.0,62.0).toString(), 0);
        pointTracker.index(new PolygonPoint(41.0,62.0).toString(), 0);
        pointTracker.index(new PolygonPoint(41.0,61.0).toString(), 0);
        pointTracker.index(new PolygonPoint(40.0,61.0).toString(), 0);
        pointTracker.index(new PolygonPoint(40.0,60.0).toString(), 0);
        pointTracker.index(new PolygonPoint(39.0,60.0).toString(), 0);
        pointTracker.index(new PolygonPoint(39.0,58.0).toString(), 0);
        pointTracker.index(new PolygonPoint(41.0,58.0).toString(), 0);
        pointTracker.index(new PolygonPoint(41.0,57.0).toString(), 0);
        pointTracker.index(new PolygonPoint(39.0,57.0).toString(), 0);
        pointTracker.index(new PolygonPoint(39.0,55.0).toString(), 0);
        pointTracker.index(new PolygonPoint(40.0,55.0).toString(), 0);
        pointTracker.index(new PolygonPoint(40.0,56.0).toString(), 0);
        pointTracker.index(new PolygonPoint(41.0,56.0).toString(), 0);
        pointTracker.index(new PolygonPoint(41.0,53.0).toString(), 0);
        pointTracker.index(new PolygonPoint(40.0,53.0).toString(), 0);
        pointTracker.index(new PolygonPoint(40.0,52.0).toString(), 0);
        pointTracker.index(new PolygonPoint(41.0,52.0).toString(), 0);
        pointTracker.index(new PolygonPoint(41.0,47.0).toString(), 0);
        pointTracker.index(new PolygonPoint(40.0,47.0).toString(), 0);
        pointTracker.index(new PolygonPoint(40.0,46.0).toString(), 0);
        pointTracker.index(new PolygonPoint(41.0,46.0).toString(), 0);
        pointTracker.index(new PolygonPoint(41.0,45.0).toString(), 0);
        pointTracker.index(new PolygonPoint(39.0,45.0).toString(), 0);
        pointTracker.index(new PolygonPoint(39.0,44.0).toString(), 0);
        pointTracker.index(new PolygonPoint(41.0,44.0).toString(), 0);
        pointTracker.index(new PolygonPoint(41.0,40.0).toString(), 0);
        pointTracker.index(new PolygonPoint(40.0,40.0).toString(), 0);
        pointTracker.index(new PolygonPoint(40.0,39.0).toString(), 0);
        pointTracker.index(new PolygonPoint(41.0,39.0).toString(), 0);
        pointTracker.index(new PolygonPoint(41.0,33.0).toString(), 0);
        pointTracker.index(new PolygonPoint(40.0,33.0).toString(), 0);
        pointTracker.index(new PolygonPoint(40.0,32.0).toString(), 0);
        pointTracker.index(new PolygonPoint(41.0,32.0).toString(), 0);
        pointTracker.index(new PolygonPoint(41.0,22.0).toString(), 0);
        pointTracker.index(new PolygonPoint(40.0,22.0).toString(), 0);
        pointTracker.index(new PolygonPoint(40.0,25.0).toString(), 0);
        pointTracker.index(new PolygonPoint(39.0,25.0).toString(), 0);
        pointTracker.index(new PolygonPoint(39.0,24.0).toString(), 0);
        pointTracker.index(new PolygonPoint(38.0,24.0).toString(), 0);
        pointTracker.index(new PolygonPoint(38.0,21.0).toString(), 0);
        pointTracker.index(new PolygonPoint(35.0,21.0).toString(), 0);
        pointTracker.index(new PolygonPoint(35.0,20.0).toString(), 0);
        pointTracker.index(new PolygonPoint(32.0,20.0).toString(), 0);
        pointTracker.index(new PolygonPoint(32.0,17.0).toString(), 0);
        pointTracker.index(new PolygonPoint(33.0,17.0).toString(), 0);
        pointTracker.index(new PolygonPoint(33.0,16.0).toString(), 0);
        pointTracker.index(new PolygonPoint(31.0,16.0).toString(), 0);
        pointTracker.index(new PolygonPoint(31.0,15.0).toString(), 0);
        pointTracker.index(new PolygonPoint(32.0,15.0).toString(), 0);
        pointTracker.index(new PolygonPoint(32.0,14.0).toString(), 0);
        pointTracker.index(new PolygonPoint(33.0,14.0).toString(), 0);
        pointTracker.index(new PolygonPoint(33.0,13.0).toString(), 0);
        pointTracker.index(new PolygonPoint(34.0,13.0).toString(), 0);
        pointTracker.index(new PolygonPoint(34.0,12.0).toString(), 0);
        pointTracker.index(new PolygonPoint(35.0,12.0).toString(), 0);
        pointTracker.index(new PolygonPoint(35.0,11.0).toString(), 0);
        pointTracker.index(new PolygonPoint(36.0,11.0).toString(), 0);
        pointTracker.index(new PolygonPoint(36.0,9.0).toString(), 0);
        pointTracker.index(new PolygonPoint(38.0,9.0).toString(), 0);
        pointTracker.index(new PolygonPoint(38.0,7.0).toString(), 0);
        pointTracker.index(new PolygonPoint(39.0,7.0).toString(), 0);
        pointTracker.index(new PolygonPoint(39.0,6.0).toString(), 0);
        pointTracker.index(new PolygonPoint(37.0,6.0).toString(), 0);
        pointTracker.index(new PolygonPoint(37.0,5.0).toString(), 0);
        pointTracker.index(new PolygonPoint(40.0,5.0).toString(), 0);
        pointTracker.index(new PolygonPoint(40.0,6.0).toString(), 0);
        pointTracker.index(new PolygonPoint(41.0,6.0).toString(), 0);
        pointTracker.index(new PolygonPoint(41.0,2.0).toString(), 0);
        pointTracker.index(new PolygonPoint(40.0,2.0).toString(), 0);
        pointTracker.index(new PolygonPoint(40.0,1.0).toString(), 0);
        pointTracker.index(new PolygonPoint(37.0,1.0).toString(), 0);
        pointTracker.index(new PolygonPoint(37.0,2.0).toString(), 0);
        pointTracker.index(new PolygonPoint(36.0,2.0).toString(), 0);
        pointTracker.index(new PolygonPoint(36.0,1.0).toString(), 0);
        pointTracker.index(new PolygonPoint(35.0,1.0).toString(), 0);
        pointTracker.index(new PolygonPoint(35.0,2.0).toString(), 0);
        pointTracker.index(new PolygonPoint(34.0,2.0).toString(), 0);
        pointTracker.index(new PolygonPoint(34.0,1.0).toString(), 0);
        pointTracker.index(new PolygonPoint(32.0,1.0).toString(), 0);
        pointTracker.index(new PolygonPoint(32.0,0.0).toString(), 0);
        pointTracker.index(new PolygonPoint(31.0,0.0).toString(), 0);
        pointTracker.index(new PolygonPoint(31.0,1.0).toString(), 0);
        pointTracker.index(new PolygonPoint(30.0,1.0).toString(), 0);
        pointTracker.index(new PolygonPoint(30.0,2.0).toString(), 0);
        pointTracker.index(new PolygonPoint(28.0,2.0).toString(), 0);
        pointTracker.index(new PolygonPoint(28.0,3.0).toString(), 0);
        pointTracker.index(new PolygonPoint(25.0,3.0).toString(), 0);
        pointTracker.index(new PolygonPoint(25.0,2.0).toString(), 0);
        pointTracker.index(new PolygonPoint(24.0,2.0).toString(), 0);
        pointTracker.index(new PolygonPoint(24.0,3.0).toString(), 0);
        pointTracker.index(new PolygonPoint(22.0,3.0).toString(), 0);
        pointTracker.index(new PolygonPoint(22.0,2.0).toString(), 0);
        pointTracker.index(new PolygonPoint(21.0,2.0).toString(), 0);
        pointTracker.index(new PolygonPoint(21.0,0.0).toString(), 0);
        pointTracker.index(new PolygonPoint(20.0,0.0).toString(), 0);
        pointTracker.index(new PolygonPoint(20.0,1.0).toString(), 0);
        pointTracker.index(new PolygonPoint(18.0,1.0).toString(), 0);
        pointTracker.index(new PolygonPoint(18.0,0.0).toString(), 0);
        pointTracker.index(new PolygonPoint(14.0,0.0).toString(), 0);
        pointTracker.index(new PolygonPoint(14.0,1.0).toString(), 0);
        pointTracker.index(new PolygonPoint(17.0,1.0).toString(), 0);
        pointTracker.index(new PolygonPoint(17.0,2.0).toString(), 0);
        pointTracker.index(new PolygonPoint(16.0,2.0).toString(), 0);
        pointTracker.index(new PolygonPoint(16.0,5.0).toString(), 0);
        pointTracker.index(new PolygonPoint(17.0,5.0).toString(), 0);
        pointTracker.index(new PolygonPoint(17.0,3.0).toString(), 0);
        pointTracker.index(new PolygonPoint(18.0,3.0).toString(), 0);
        pointTracker.index(new PolygonPoint(18.0,4.0).toString(), 0);
        pointTracker.index(new PolygonPoint(19.0,4.0).toString(), 0);
        pointTracker.index(new PolygonPoint(19.0,7.0).toString(), 0);
        pointTracker.index(new PolygonPoint(17.0,7.0).toString(), 0);
        pointTracker.index(new PolygonPoint(17.0,6.0).toString(), 0);
        pointTracker.index(new PolygonPoint(15.0,6.0).toString(), 0);
        pointTracker.index(new PolygonPoint(15.0,2.0).toString(), 0);
        pointTracker.index(new PolygonPoint(13.0,2.0).toString(), 0);
        pointTracker.index(new PolygonPoint(13.0,0.0).toString(), 0);
        pointTracker.index(new PolygonPoint(12.0,0.0).toString(), 0);
        pointTracker.index(new PolygonPoint(12.0,2.0).toString(), 0);
        pointTracker.index(new PolygonPoint(11.0,2.0).toString(), 0);
        pointTracker.index(new PolygonPoint(11.0,3.0).toString(), 0);
        pointTracker.index(new PolygonPoint(9.0,3.0).toString(), 0);
        pointTracker.index(new PolygonPoint(9.0,1.0).toString(), 0);
        pointTracker.index(new PolygonPoint(10.0,1.0).toString(), 0);
        pointTracker.index(new PolygonPoint(10.0,0.0).toString(), 0);
        pointTracker.index(new PolygonPoint(6.0,0.0).toString(), 0);
        pointTracker.index(new PolygonPoint(6.0,2.0).toString(), 0);
        pointTracker.index(new PolygonPoint(7.0,2.0).toString(), 0);
        pointTracker.index(new PolygonPoint(7.0,3.0).toString(), 0);
        pointTracker.index(new PolygonPoint(6.0,3.0).toString(), 0);
        pointTracker.index(new PolygonPoint(6.0,5.0).toString(), 0);
        pointTracker.index(new PolygonPoint(5.0,5.0).toString(), 0);
        pointTracker.index(new PolygonPoint(5.0,6.0).toString(), 0);
        pointTracker.index(new PolygonPoint(2.0,6.0).toString(), 0);
        pointTracker.index(new PolygonPoint(2.0,7.0).toString(), 0);
        pointTracker.index(new PolygonPoint(7.0,7.0).toString(), 0);
        pointTracker.index(new PolygonPoint(7.0,9.0).toString(), 0);
        pointTracker.index(new PolygonPoint(8.0,9.0).toString(), 0);
        pointTracker.index(new PolygonPoint(8.0,10.0).toString(), 0);
        pointTracker.index(new PolygonPoint(6.0,10.0).toString(), 0);
        pointTracker.index(new PolygonPoint(6.0,9.0).toString(), 0);
        pointTracker.index(new PolygonPoint(5.0,9.0).toString(), 0);
        pointTracker.index(new PolygonPoint(5.0,8.0).toString(), 0);
        pointTracker.index(new PolygonPoint(2.0,8.0).toString(), 0);
        pointTracker.index(new PolygonPoint(2.0,9.0).toString(), 0);
        pointTracker.index(new PolygonPoint(1.0,9.0).toString(), 0);
        pointTracker.index(new PolygonPoint(1.0,11.0).toString(), 0);
        pointTracker.index(new PolygonPoint(3.0,11.0).toString(), 0);
        pointTracker.index(new PolygonPoint(3.0,12.0).toString(), 0);
        pointTracker.index(new PolygonPoint(6.0,12.0).toString(), 0);
        pointTracker.index(new PolygonPoint(6.0,13.0).toString(), 0);
        pointTracker.index(new PolygonPoint(8.0,13.0).toString(), 0);
        pointTracker.index(new PolygonPoint(8.0,15.0).toString(), 0);
        pointTracker.index(new PolygonPoint(7.0,15.0).toString(), 0);
        pointTracker.index(new PolygonPoint(7.0,16.0).toString(), 0);
        pointTracker.index(new PolygonPoint(8.0,16.0).toString(), 0);
        pointTracker.index(new PolygonPoint(8.0,17.0).toString(), 0);
        pointTracker.index(new PolygonPoint(9.0,17.0).toString(), 0);
        pointTracker.index(new PolygonPoint(9.0,18.0).toString(), 0);
        pointTracker.index(new PolygonPoint(11.0,18.0).toString(), 0);
        pointTracker.index(new PolygonPoint(11.0,19.0).toString(), 0);
        pointTracker.index(new PolygonPoint(12.0,19.0).toString(), 0);
        pointTracker.index(new PolygonPoint(12.0,18.0).toString(), 0);
        pointTracker.index(new PolygonPoint(14.0,18.0).toString(), 0);
        pointTracker.index(new PolygonPoint(14.0,17.0).toString(), 0);
        pointTracker.index(new PolygonPoint(15.0,17.0).toString(), 0);
        pointTracker.index(new PolygonPoint(15.0,16.0).toString(), 0);
        pointTracker.index(new PolygonPoint(18.0,16.0).toString(), 0);
        pointTracker.index(new PolygonPoint(18.0,14.0).toString(), 0);
        pointTracker.index(new PolygonPoint(19.0,14.0).toString(), 0);
        pointTracker.index(new PolygonPoint(19.0,13.0).toString(), 0);
        pointTracker.index(new PolygonPoint(17.0,13.0).toString(), 0);
        pointTracker.index(new PolygonPoint(17.0,12.0).toString(), 0);
        pointTracker.index(new PolygonPoint(15.0,12.0).toString(), 0);
        pointTracker.index(new PolygonPoint(15.0,10.0).toString(), 0);
        pointTracker.index(new PolygonPoint(16.0,10.0).toString(), 0);
        pointTracker.index(new PolygonPoint(16.0,11.0).toString(), 0);
        pointTracker.index(new PolygonPoint(17.0,11.0).toString(), 0);
        pointTracker.index(new PolygonPoint(17.0,9.0).toString(), 0);
        pointTracker.index(new PolygonPoint(18.0,9.0).toString(), 0);
        pointTracker.index(new PolygonPoint(18.0,12.0).toString(), 0);
        pointTracker.index(new PolygonPoint(19.0,12.0).toString(), 0);
        pointTracker.index(new PolygonPoint(19.0,11.0).toString(), 0);
        pointTracker.index(new PolygonPoint(20.0,11.0).toString(), 0);
        pointTracker.index(new PolygonPoint(20.0,12.0).toString(), 0);
        pointTracker.index(new PolygonPoint(22.0,12.0).toString(), 0);
        pointTracker.index(new PolygonPoint(22.0,13.0).toString(), 0);
        pointTracker.index(new PolygonPoint(20.0,13.0).toString(), 0);
        pointTracker.index(new PolygonPoint(20.0,14.0).toString(), 0);
        pointTracker.index(new PolygonPoint(21.0,14.0).toString(), 0);
        pointTracker.index(new PolygonPoint(21.0,17.0).toString(), 0);
        pointTracker.index(new PolygonPoint(20.0,17.0).toString(), 0);
        pointTracker.index(new PolygonPoint(20.0,19.0).toString(), 0);
        pointTracker.index(new PolygonPoint(21.0,19.0).toString(), 0);
        pointTracker.index(new PolygonPoint(21.0,18.0).toString(), 0);
        pointTracker.index(new PolygonPoint(23.0,18.0).toString(), 0);
        pointTracker.index(new PolygonPoint(23.0,17.0).toString(), 0);
        pointTracker.index(new PolygonPoint(24.0,17.0).toString(), 0);
        pointTracker.index(new PolygonPoint(24.0,18.0).toString(), 0);
        pointTracker.index(new PolygonPoint(25.0,18.0).toString(), 0);
        pointTracker.index(new PolygonPoint(25.0,15.0).toString(), 0);
        pointTracker.index(new PolygonPoint(24.0,15.0).toString(), 0);
        pointTracker.index(new PolygonPoint(24.0,14.0).toString(), 0);
        pointTracker.index(new PolygonPoint(23.0,14.0).toString(), 0);
        pointTracker.index(new PolygonPoint(23.0,12.0).toString(), 0);
        pointTracker.index(new PolygonPoint(24.0,12.0).toString(), 0);
        pointTracker.index(new PolygonPoint(24.0,13.0).toString(), 0);
        pointTracker.index(new PolygonPoint(26.0,13.0).toString(), 0);
        pointTracker.index(new PolygonPoint(26.0,15.0).toString(), 0);
        pointTracker.index(new PolygonPoint(27.0,15.0).toString(), 0);
        pointTracker.index(new PolygonPoint(27.0,17.0).toString(), 0);
        pointTracker.index(new PolygonPoint(31.0,17.0).toString(), 0);
        pointTracker.index(new PolygonPoint(31.0,18.0).toString(), 0);
        pointTracker.index(new PolygonPoint(29.0,18.0).toString(), 0);
        pointTracker.index(new PolygonPoint(29.0,20.0).toString(), 0);
        pointTracker.index(new PolygonPoint(28.0,20.0).toString(), 0);
        pointTracker.index(new PolygonPoint(28.0,19.0).toString(), 0);
        pointTracker.index(new PolygonPoint(26.0,19.0).toString(), 0);
        pointTracker.index(new PolygonPoint(26.0,20.0).toString(), 0);
        pointTracker.index(new PolygonPoint(23.0,20.0).toString(), 0);
        pointTracker.index(new PolygonPoint(23.0,21.0).toString(), 0);
        pointTracker.index(new PolygonPoint(22.0,21.0).toString(), 0);
        pointTracker.index(new PolygonPoint(22.0,20.0).toString(), 0);
        pointTracker.index(new PolygonPoint(21.0,20.0).toString(), 0);
        pointTracker.index(new PolygonPoint(21.0,21.0).toString(), 0);
        pointTracker.index(new PolygonPoint(20.0,21.0).toString(), 0);
        pointTracker.index(new PolygonPoint(20.0,22.0).toString(), 0);
        pointTracker.index(new PolygonPoint(23.0,22.0).toString(), 0);
        pointTracker.index(new PolygonPoint(23.0,25.0).toString(), 0);
        pointTracker.index(new PolygonPoint(22.0,25.0).toString(), 0);
        pointTracker.index(new PolygonPoint(22.0,23.0).toString(), 0);
        pointTracker.index(new PolygonPoint(21.0,23.0).toString(), 0);
        pointTracker.index(new PolygonPoint(21.0,24.0).toString(), 0);
        pointTracker.index(new PolygonPoint(20.0,24.0).toString(), 0);
        pointTracker.index(new PolygonPoint(20.0,25.0).toString(), 0);
        pointTracker.index(new PolygonPoint(21.0,25.0).toString(), 0);
        pointTracker.index(new PolygonPoint(21.0,26.0).toString(), 0);
        pointTracker.index(new PolygonPoint(20.0,26.0).toString(), 0);
        pointTracker.index(new PolygonPoint(20.0,27.0).toString(), 0);
        pointTracker.index(new PolygonPoint(19.0,27.0).toString(), 0);
        pointTracker.index(new PolygonPoint(19.0,28.0).toString(), 0);
        pointTracker.index(new PolygonPoint(18.0,28.0).toString(), 0);
        pointTracker.index(new PolygonPoint(18.0,27.0).toString(), 0);
        pointTracker.index(new PolygonPoint(17.0,27.0).toString(), 0);
        pointTracker.index(new PolygonPoint(17.0,26.0).toString(), 0);
        pointTracker.index(new PolygonPoint(18.0,26.0).toString(), 0);
        pointTracker.index(new PolygonPoint(18.0,25.0).toString(), 0);
        pointTracker.index(new PolygonPoint(17.0,25.0).toString(), 0);
        pointTracker.index(new PolygonPoint(17.0,23.0).toString(), 0);
        pointTracker.index(new PolygonPoint(15.0,23.0).toString(), 0);
        pointTracker.index(new PolygonPoint(15.0,26.0).toString(), 0);
        pointTracker.index(new PolygonPoint(14.0,26.0).toString(), 0);
        pointTracker.index(new PolygonPoint(14.0,28.0).toString(), 0);
        pointTracker.index(new PolygonPoint(13.0,28.0).toString(), 0);
        pointTracker.index(new PolygonPoint(13.0,26.0).toString(), 0);
        pointTracker.index(new PolygonPoint(12.0,26.0).toString(), 0);
        pointTracker.index(new PolygonPoint(12.0,25.0).toString(), 0);
        pointTracker.index(new PolygonPoint(11.0,25.0).toString(), 0);
        pointTracker.index(new PolygonPoint(11.0,26.0).toString(), 0);
        pointTracker.index(new PolygonPoint(10.0,26.0).toString(), 0);
        pointTracker.index(new PolygonPoint(10.0,25.0).toString(), 0);
        pointTracker.index(new PolygonPoint(9.0,25.0).toString(), 0);
        pointTracker.index(new PolygonPoint(9.0,24.0).toString(), 0);
        pointTracker.index(new PolygonPoint(8.0,24.0).toString(), 0);
        pointTracker.index(new PolygonPoint(8.0,26.0).toString(), 0);
        pointTracker.index(new PolygonPoint(7.0,26.0).toString(), 0);
        pointTracker.index(new PolygonPoint(7.0,24.0).toString(), 0);
        pointTracker.index(new PolygonPoint(6.0,24.0).toString(), 0);
        pointTracker.index(new PolygonPoint(6.0,26.0).toString(), 0);
        pointTracker.index(new PolygonPoint(5.0,26.0).toString(), 0);
        pointTracker.index(new PolygonPoint(5.0,23.0).toString(), 0);
        pointTracker.index(new PolygonPoint(7.0,23.0).toString(), 0);
        pointTracker.index(new PolygonPoint(7.0,21.0).toString(), 0);
        pointTracker.index(new PolygonPoint(6.0,21.0).toString(), 0);
        pointTracker.index(new PolygonPoint(6.0,20.0).toString(), 0);
        pointTracker.index(new PolygonPoint(5.0,20.0).toString(), 0);
        pointTracker.index(new PolygonPoint(5.0,19.0).toString(), 0);
        pointTracker.index(new PolygonPoint(3.0,19.0).toString(), 0);
        pointTracker.index(new PolygonPoint(3.0,20.0).toString(), 0);
        pointTracker.index(new PolygonPoint(1.0,20.0).toString(), 0);
        pointTracker.index(new PolygonPoint(1.0,21.0).toString(), 0);
        pointTracker.index(new PolygonPoint(0.0,21.0).toString(), 0);
        pointTracker.index(new PolygonPoint(0.0,24.0).toString(), 0);
        pointTracker.index(new PolygonPoint(1.0,24.0).toString(), 0);
        pointTracker.index(new PolygonPoint(1.0,22.0).toString(), 0);
        pointTracker.index(new PolygonPoint(2.0,22.0).toString(), 0);
        pointTracker.index(new PolygonPoint(2.0,25.0).toString(), 0);
        pointTracker.index(new PolygonPoint(0.0,25.0).toString(), 0);
        pointTracker.index(new PolygonPoint(0.0,27.0).toString(), 0);
        pointTracker.index(new PolygonPoint(1.0,27.0).toString(), 0);
        pointTracker.index(new PolygonPoint(1.0,28.0).toString(), 0);
        pointTracker.index(new PolygonPoint(0.0,28.0).toString(), 0);
        pointTracker.index(new PolygonPoint(0.0,29.0).toString(), 0);
        pointTracker.index(new PolygonPoint(1.0,29.0).toString(), 0);
        pointTracker.index(new PolygonPoint(1.0,30.0).toString(), 0);
        pointTracker.index(new PolygonPoint(0.0,30.0).toString(), 0);
        pointTracker.index(new PolygonPoint(0.0,35.0).toString(), 0);
        pointTracker.index(new PolygonPoint(2.0,35.0).toString(), 0);
        pointTracker.index(new PolygonPoint(2.0,36.0).toString(), 0);
        pointTracker.index(new PolygonPoint(3.0,36.0).toString(), 0);
        pointTracker.index(new PolygonPoint(3.0,37.0).toString(), 0);
        pointTracker.index(new PolygonPoint(1.0,37.0).toString(), 0);
        pointTracker.index(new PolygonPoint(1.0,36.0).toString(), 0);
        pointTracker.index(new PolygonPoint(0.0,36.0).toString(), 0);
        pointTracker.index(new PolygonPoint(0.0,39.0).toString(), 0);
        pointTracker.index(new PolygonPoint(1.0,39.0).toString(), 0);
        pointTracker.index(new PolygonPoint(1.0,38.0).toString(), 0);
        pointTracker.index(new PolygonPoint(2.0,38.0).toString(), 0);
        pointTracker.index(new PolygonPoint(2.0,39.0).toString(), 0);
        pointTracker.index(new PolygonPoint(3.0,39.0).toString(), 0);
        pointTracker.index(new PolygonPoint(3.0,43.0).toString(), 0);
        pointTracker.index(new PolygonPoint(2.0,43.0).toString(), 0);
        pointTracker.index(new PolygonPoint(2.0,44.0).toString(), 0);
        pointTracker.index(new PolygonPoint(5.0,44.0).toString(), 0);
        pointTracker.index(new PolygonPoint(5.0,45.0).toString(), 0);
        pointTracker.index(new PolygonPoint(4.0,45.0).toString(), 0);
        pointTracker.index(new PolygonPoint(4.0,46.0).toString(), 0);
        pointTracker.index(new PolygonPoint(3.0,46.0).toString(), 0);
        pointTracker.index(new PolygonPoint(3.0,47.0).toString(), 0);
        pointTracker.index(new PolygonPoint(2.0,47.0).toString(), 0);
        pointTracker.index(new PolygonPoint(2.0,45.0).toString(), 0);
        pointTracker.index(new PolygonPoint(1.0,45.0).toString(), 0);
        pointTracker.index(new PolygonPoint(1.0,44.0).toString(), 0);
        pointTracker.index(new PolygonPoint(0.0,44.0).toString(), 0);
        pointTracker.index(new PolygonPoint(0.0,48.0).toString(), 0);
        pointTracker.index(new PolygonPoint(2.0,48.0).toString(), 0);
        pointTracker.index(new PolygonPoint(2.0,49.0).toString(), 0);
        pointTracker.index(new PolygonPoint(3.0,49.0).toString(), 0);
        pointTracker.index(new PolygonPoint(3.0,51.0).toString(), 0);
        pointTracker.index(new PolygonPoint(2.0,51.0).toString(), 0);
        pointTracker.index(new PolygonPoint(2.0,52.0).toString(), 0);
        pointTracker.index(new PolygonPoint(1.0,52.0).toString(), 0);
        pointTracker.index(new PolygonPoint(1.0,53.0).toString(), 0);
        pointTracker.index(new PolygonPoint(0.0,53.0).toString(), 0);
        pointTracker.index(new PolygonPoint(0.0,54.0).toString(), 0);
        pointTracker.index(new PolygonPoint(1.0,54.0).toString(), 0);
        pointTracker.index(new PolygonPoint(1.0,58.0).toString(), 0);
        pointTracker.index(new PolygonPoint(0.0,58.0).toString(), 0);
        pointTracker.index(new PolygonPoint(0.0,60.0).toString(), 0);
        pointTracker.index(new PolygonPoint(2.0,60.0).toString(), 0);
        pointTracker.index(new PolygonPoint(2.0,62.0).toString(), 0);
        pointTracker.index(new PolygonPoint(1.0,62.0).toString(), 0);
        pointTracker.index(new PolygonPoint(1.0,61.0).toString(), 0);
        pointTracker.index(new PolygonPoint(0.0,61.0).toString(), 0);
        pointTracker.index(new PolygonPoint(0.0,64.0).toString(), 0);
        pointTracker.index(new PolygonPoint(1.0,64.0).toString(), 0);
        pointTracker.index(new PolygonPoint(1.0,65.0).toString(), 0);
        pointTracker.index(new PolygonPoint(0.0,65.0).toString(), 0);
        pointTracker.index(new PolygonPoint(0.0,66.0).toString(), 0);
        pointTracker.index(new PolygonPoint(2.0,66.0).toString(), 0);
        pointTracker.index(new PolygonPoint(2.0,68.0).toString(), 0);
        pointTracker.index(new PolygonPoint(4.0,68.0).toString(), 0);
        pointTracker.index(new PolygonPoint(4.0,69.0).toString(), 0);
        pointTracker.index(new PolygonPoint(5.0,69.0).toString(), 0);
        pointTracker.getIndex(new PolygonPoint(17.0,59.0).toString());
        pointTracker.index(new PolygonPoint(17.0,59.0).toString(), 1);
        pointTracker.getIndex(new PolygonPoint(14.0,59.0).toString());
        pointTracker.index(new PolygonPoint(14.0,59.0).toString(), 1);
        pointTracker.getIndex(new PolygonPoint(14.0,58.0).toString());
        pointTracker.index(new PolygonPoint(14.0,58.0).toString(), 1);
        pointTracker.getIndex(new PolygonPoint(15.0,58.0).toString());
        pointTracker.index(new PolygonPoint(15.0,58.0).toString(), 1);
        pointTracker.getIndex(new PolygonPoint(15.0,57.0).toString());
        pointTracker.index(new PolygonPoint(15.0,57.0).toString(), 1);
        pointTracker.getIndex(new PolygonPoint(16.0,57.0).toString());
        pointTracker.index(new PolygonPoint(16.0,57.0).toString(), 1);
        pointTracker.getIndex(new PolygonPoint(16.0,58.0).toString());
        pointTracker.index(new PolygonPoint(16.0,58.0).toString(), 1);
        pointTracker.getIndex(new PolygonPoint(17.0,58.0).toString());
        pointTracker.index(new PolygonPoint(17.0,58.0).toString(), 1);
        pointTracker.getIndex(new PolygonPoint(26.0,12.0).toString());
        pointTracker.index(new PolygonPoint(26.0,12.0).toString(), 2);
        pointTracker.getIndex(new PolygonPoint(25.0,12.0).toString());
        pointTracker.index(new PolygonPoint(25.0,12.0).toString(), 2);
        pointTracker.getIndex(new PolygonPoint(25.0,11.0).toString());
        pointTracker.index(new PolygonPoint(25.0,11.0).toString(), 2);
        pointTracker.getIndex(new PolygonPoint(26.0,11.0).toString());
        pointTracker.index(new PolygonPoint(26.0,11.0).toString(), 2);
        pointTracker.getIndex(new PolygonPoint(15.0,15.0).toString());
        pointTracker.index(new PolygonPoint(15.0,15.0).toString(), 3);
        pointTracker.getIndex(new PolygonPoint(15.0,14.0).toString());
        pointTracker.index(new PolygonPoint(15.0,14.0).toString(), 3);
        pointTracker.getIndex(new PolygonPoint(16.0,14.0).toString());
        pointTracker.index(new PolygonPoint(16.0,14.0).toString(), 3);
        pointTracker.getIndex(new PolygonPoint(16.0,15.0).toString());
        pointTracker.index(new PolygonPoint(16.0,15.0).toString(), 3);
        pointTracker.getIndex(new PolygonPoint(11.0,51.0).toString());
        pointTracker.index(new PolygonPoint(11.0,51.0).toString(), 4);
        pointTracker.getIndex(new PolygonPoint(11.0,50.0).toString());
        pointTracker.index(new PolygonPoint(11.0,50.0).toString(), 4);
        pointTracker.getIndex(new PolygonPoint(12.0,50.0).toString());
        pointTracker.index(new PolygonPoint(12.0,50.0).toString(), 4);
        pointTracker.getIndex(new PolygonPoint(12.0,51.0).toString());
        pointTracker.index(new PolygonPoint(12.0,51.0).toString(), 4);
        pointTracker.getIndex(new PolygonPoint(13.0,51.0).toString());
        pointTracker.index(new PolygonPoint(13.0,51.0).toString(), 4);
        pointTracker.getIndex(new PolygonPoint(13.0,49.0).toString());
        pointTracker.index(new PolygonPoint(13.0,49.0).toString(), 4);
        pointTracker.getIndex(new PolygonPoint(15.0,49.0).toString());
        pointTracker.index(new PolygonPoint(15.0,49.0).toString(), 4);
        pointTracker.getIndex(new PolygonPoint(15.0,50.0).toString());
        pointTracker.index(new PolygonPoint(15.0,50.0).toString(), 4);
        pointTracker.getIndex(new PolygonPoint(16.0,50.0).toString());
        pointTracker.index(new PolygonPoint(16.0,50.0).toString(), 4);
        pointTracker.getIndex(new PolygonPoint(16.0,51.0).toString());
        pointTracker.index(new PolygonPoint(16.0,51.0).toString(), 4);
        pointTracker.getIndex(new PolygonPoint(17.0,51.0).toString());
        pointTracker.index(new PolygonPoint(17.0,51.0).toString(), 4);
        pointTracker.getIndex(new PolygonPoint(17.0,52.0).toString());
        pointTracker.index(new PolygonPoint(17.0,52.0).toString(), 4);
        pointTracker.getIndex(new PolygonPoint(19.0,52.0).toString());
        pointTracker.index(new PolygonPoint(19.0,52.0).toString(), 4);
        pointTracker.getIndex(new PolygonPoint(19.0,53.0).toString());
        pointTracker.index(new PolygonPoint(19.0,53.0).toString(), 4);
        pointTracker.getIndex(new PolygonPoint(17.0,53.0).toString());
        pointTracker.index(new PolygonPoint(17.0,53.0).toString(), 4);
        pointTracker.getIndex(new PolygonPoint(17.0,54.0).toString());
        pointTracker.index(new PolygonPoint(17.0,54.0).toString(), 4);
        pointTracker.getIndex(new PolygonPoint(14.0,54.0).toString());
        pointTracker.index(new PolygonPoint(14.0,54.0).toString(), 4);
        pointTracker.getIndex(new PolygonPoint(14.0,53.0).toString());
        pointTracker.index(new PolygonPoint(14.0,53.0).toString(), 4);
        pointTracker.getIndex(new PolygonPoint(11.0,53.0).toString());
        pointTracker.index(new PolygonPoint(11.0,53.0).toString(), 4);
        pointTracker.getIndex(new PolygonPoint(11.0,55.0).toString());
        pointTracker.index(new PolygonPoint(11.0,55.0).toString(), 4);
        pointTracker.getIndex(new PolygonPoint(10.0,55.0).toString());
        pointTracker.index(new PolygonPoint(10.0,55.0).toString(), 4);
        pointTracker.getIndex(new PolygonPoint(10.0,54.0).toString());
        pointTracker.index(new PolygonPoint(10.0,54.0).toString(), 4);
        pointTracker.getIndex(new PolygonPoint(9.0,54.0).toString());
        pointTracker.index(new PolygonPoint(9.0,54.0).toString(), 4);
        pointTracker.getIndex(new PolygonPoint(9.0,52.0).toString());
        pointTracker.index(new PolygonPoint(9.0,52.0).toString(), 4);
        pointTracker.getIndex(new PolygonPoint(10.0,52.0).toString());
        pointTracker.index(new PolygonPoint(10.0,52.0).toString(), 4);
        pointTracker.getIndex(new PolygonPoint(10.0,51.0).toString());
        pointTracker.index(new PolygonPoint(10.0,51.0).toString(), 4);
        pointTracker.getIndex(new PolygonPoint(7.0,58.0).toString());
        pointTracker.index(new PolygonPoint(7.0,58.0).toString(), 5);
        pointTracker.getIndex(new PolygonPoint(7.0,57.0).toString());
        pointTracker.index(new PolygonPoint(7.0,57.0).toString(), 5);
        pointTracker.getIndex(new PolygonPoint(8.0,57.0).toString());
        pointTracker.index(new PolygonPoint(8.0,57.0).toString(), 5);
        pointTracker.getIndex(new PolygonPoint(8.0,58.0).toString());
        pointTracker.index(new PolygonPoint(8.0,58.0).toString(), 5);
        pointTracker.getIndex(new PolygonPoint(14.0,8.0).toString());
        pointTracker.index(new PolygonPoint(14.0,8.0).toString(), 6);
        pointTracker.getIndex(new PolygonPoint(12.0,8.0).toString());
        pointTracker.index(new PolygonPoint(12.0,8.0).toString(), 6);
        pointTracker.getIndex(new PolygonPoint(12.0,7.0).toString());
        pointTracker.index(new PolygonPoint(12.0,7.0).toString(), 6);
        pointTracker.getIndex(new PolygonPoint(14.0,7.0).toString());
        pointTracker.index(new PolygonPoint(14.0,7.0).toString(), 6);
        pointTracker.getIndex(new PolygonPoint(9.0,64.0).toString());
        pointTracker.index(new PolygonPoint(9.0,64.0).toString(), 7);
        pointTracker.getIndex(new PolygonPoint(9.0,63.0).toString());
        pointTracker.index(new PolygonPoint(9.0,63.0).toString(), 7);
        pointTracker.getIndex(new PolygonPoint(10.0,63.0).toString());
        pointTracker.index(new PolygonPoint(10.0,63.0).toString(), 7);
        pointTracker.getIndex(new PolygonPoint(10.0,62.0).toString());
        pointTracker.index(new PolygonPoint(10.0,62.0).toString(), 7);
        pointTracker.getIndex(new PolygonPoint(9.0,62.0).toString());
        pointTracker.index(new PolygonPoint(9.0,62.0).toString(), 7);
        pointTracker.getIndex(new PolygonPoint(9.0,61.0).toString());
        pointTracker.index(new PolygonPoint(9.0,61.0).toString(), 7);
        pointTracker.getIndex(new PolygonPoint(10.0,61.0).toString());
        pointTracker.index(new PolygonPoint(10.0,61.0).toString(), 7);
        pointTracker.getIndex(new PolygonPoint(10.0,60.0).toString());
        pointTracker.index(new PolygonPoint(10.0,60.0).toString(), 7);
        pointTracker.getIndex(new PolygonPoint(13.0,60.0).toString());
        pointTracker.index(new PolygonPoint(13.0,60.0).toString(), 7);
        pointTracker.getIndex(new PolygonPoint(13.0,61.0).toString());
        pointTracker.index(new PolygonPoint(13.0,61.0).toString(), 7);
        pointTracker.getIndex(new PolygonPoint(14.0,61.0).toString());
        pointTracker.index(new PolygonPoint(14.0,61.0).toString(), 7);
        pointTracker.getIndex(new PolygonPoint(14.0,62.0).toString());
        pointTracker.index(new PolygonPoint(14.0,62.0).toString(), 7);
        pointTracker.getIndex(new PolygonPoint(16.0,62.0).toString());
        pointTracker.index(new PolygonPoint(16.0,62.0).toString(), 7);
        pointTracker.getIndex(new PolygonPoint(16.0,61.0).toString());
        pointTracker.index(new PolygonPoint(16.0,61.0).toString(), 7);
        pointTracker.getIndex(new PolygonPoint(18.0,61.0).toString());
        pointTracker.index(new PolygonPoint(18.0,61.0).toString(), 7);
        pointTracker.getIndex(new PolygonPoint(18.0,63.0).toString());
        pointTracker.index(new PolygonPoint(18.0,63.0).toString(), 7);
        pointTracker.getIndex(new PolygonPoint(17.0,63.0).toString());
        pointTracker.index(new PolygonPoint(17.0,63.0).toString(), 7);
        pointTracker.getIndex(new PolygonPoint(17.0,64.0).toString());
        pointTracker.index(new PolygonPoint(17.0,64.0).toString(), 7);
        pointTracker.getIndex(new PolygonPoint(16.0,64.0).toString());
        pointTracker.index(new PolygonPoint(16.0,64.0).toString(), 7);
        pointTracker.getIndex(new PolygonPoint(16.0,63.0).toString());
        pointTracker.index(new PolygonPoint(16.0,63.0).toString(), 7);
        pointTracker.getIndex(new PolygonPoint(15.0,63.0).toString());
        pointTracker.index(new PolygonPoint(15.0,63.0).toString(), 7);
        pointTracker.getIndex(new PolygonPoint(15.0,67.0).toString());
        pointTracker.index(new PolygonPoint(15.0,67.0).toString(), 7);
        pointTracker.getIndex(new PolygonPoint(14.0,67.0).toString());
        pointTracker.index(new PolygonPoint(14.0,67.0).toString(), 7);
        pointTracker.getIndex(new PolygonPoint(14.0,66.0).toString());
        pointTracker.index(new PolygonPoint(14.0,66.0).toString(), 7);
        pointTracker.getIndex(new PolygonPoint(13.0,66.0).toString());
        pointTracker.index(new PolygonPoint(13.0,66.0).toString(), 7);
        pointTracker.getIndex(new PolygonPoint(13.0,67.0).toString());
        pointTracker.index(new PolygonPoint(13.0,67.0).toString(), 7);
        pointTracker.getIndex(new PolygonPoint(12.0,67.0).toString());
        pointTracker.index(new PolygonPoint(12.0,67.0).toString(), 7);
        pointTracker.getIndex(new PolygonPoint(12.0,66.0).toString());
        pointTracker.index(new PolygonPoint(12.0,66.0).toString(), 7);
        pointTracker.getIndex(new PolygonPoint(10.0,66.0).toString());
        pointTracker.index(new PolygonPoint(10.0,66.0).toString(), 7);
        pointTracker.getIndex(new PolygonPoint(10.0,67.0).toString());
        pointTracker.index(new PolygonPoint(10.0,67.0).toString(), 7);
        pointTracker.getIndex(new PolygonPoint(11.0,67.0).toString());
        pointTracker.index(new PolygonPoint(11.0,67.0).toString(), 7);
        pointTracker.getIndex(new PolygonPoint(11.0,68.0).toString());
        pointTracker.index(new PolygonPoint(11.0,68.0).toString(), 7);
        pointTracker.getIndex(new PolygonPoint(9.0,68.0).toString());
        pointTracker.index(new PolygonPoint(9.0,68.0).toString(), 7);
        pointTracker.getIndex(new PolygonPoint(9.0,65.0).toString());
        pointTracker.index(new PolygonPoint(9.0,65.0).toString(), 7);
        pointTracker.getIndex(new PolygonPoint(8.0,65.0).toString());
        pointTracker.index(new PolygonPoint(8.0,65.0).toString(), 7);
        pointTracker.getIndex(new PolygonPoint(8.0,66.0).toString());
        pointTracker.index(new PolygonPoint(8.0,66.0).toString(), 7);
        pointTracker.getIndex(new PolygonPoint(7.0,66.0).toString());
        pointTracker.index(new PolygonPoint(7.0,66.0).toString(), 7);
        pointTracker.getIndex(new PolygonPoint(7.0,67.0).toString());
        pointTracker.index(new PolygonPoint(7.0,67.0).toString(), 7);
        pointTracker.getIndex(new PolygonPoint(6.0,67.0).toString());
        pointTracker.getIndex(new PolygonPoint(6.0,66.0).toString());
        pointTracker.index(new PolygonPoint(6.0,66.0).toString(), 7);
        pointTracker.getIndex(new PolygonPoint(5.0,66.0).toString());
        pointTracker.index(new PolygonPoint(5.0,66.0).toString(), 7);
        pointTracker.getIndex(new PolygonPoint(5.0,65.0).toString());
        pointTracker.index(new PolygonPoint(5.0,65.0).toString(), 7);
        pointTracker.getIndex(new PolygonPoint(4.0,65.0).toString());
        pointTracker.index(new PolygonPoint(4.0,65.0).toString(), 7);
        pointTracker.getIndex(new PolygonPoint(4.0,64.0).toString());
        pointTracker.index(new PolygonPoint(4.0,64.0).toString(), 7);
        pointTracker.getIndex(new PolygonPoint(6.0,64.0).toString());
        pointTracker.index(new PolygonPoint(6.0,64.0).toString(), 7);
        pointTracker.getIndex(new PolygonPoint(6.0,63.0).toString());
        pointTracker.index(new PolygonPoint(6.0,63.0).toString(), 7);
        pointTracker.getIndex(new PolygonPoint(8.0,63.0).toString());
        pointTracker.index(new PolygonPoint(8.0,63.0).toString(), 7);
        pointTracker.getIndex(new PolygonPoint(8.0,64.0).toString());
        pointTracker.index(new PolygonPoint(8.0,64.0).toString(), 7);
        pointTracker.changeIndex(0, 7);
        pointTracker.getIndex(new PolygonPoint(29.0,54.0).toString());
        pointTracker.index(new PolygonPoint(29.0,54.0).toString(), 8);
        pointTracker.getIndex(new PolygonPoint(29.0,55.0).toString());
        pointTracker.index(new PolygonPoint(29.0,55.0).toString(), 8);
        pointTracker.getIndex(new PolygonPoint(27.0,55.0).toString());
        pointTracker.index(new PolygonPoint(27.0,55.0).toString(), 8);
        pointTracker.getIndex(new PolygonPoint(27.0,54.0).toString());
        pointTracker.index(new PolygonPoint(27.0,54.0).toString(), 8);
        pointTracker.getIndex(new PolygonPoint(28.0,54.0).toString());
        pointTracker.index(new PolygonPoint(28.0,54.0).toString(), 8);
        pointTracker.getIndex(new PolygonPoint(28.0,53.0).toString());
        pointTracker.index(new PolygonPoint(28.0,53.0).toString(), 8);
        pointTracker.getIndex(new PolygonPoint(30.0,53.0).toString());
        pointTracker.index(new PolygonPoint(30.0,53.0).toString(), 8);
        pointTracker.getIndex(new PolygonPoint(30.0,54.0).toString());
        pointTracker.index(new PolygonPoint(30.0,54.0).toString(), 8);
        pointTracker.getIndex(new PolygonPoint(35.0,58.0).toString());
        pointTracker.index(new PolygonPoint(35.0,58.0).toString(), 9);
        pointTracker.getIndex(new PolygonPoint(34.0,58.0).toString());
        pointTracker.index(new PolygonPoint(34.0,58.0).toString(), 9);
        pointTracker.getIndex(new PolygonPoint(34.0,57.0).toString());
        pointTracker.index(new PolygonPoint(34.0,57.0).toString(), 9);
        pointTracker.getIndex(new PolygonPoint(35.0,57.0).toString());
        pointTracker.index(new PolygonPoint(35.0,57.0).toString(), 9);
        pointTracker.getIndex(new PolygonPoint(28.0,45.0).toString());
        pointTracker.index(new PolygonPoint(28.0,45.0).toString(), 10);
        pointTracker.getIndex(new PolygonPoint(28.0,44.0).toString());
        pointTracker.index(new PolygonPoint(28.0,44.0).toString(), 10);
        pointTracker.getIndex(new PolygonPoint(26.0,44.0).toString());
        pointTracker.index(new PolygonPoint(26.0,44.0).toString(), 10);
        pointTracker.getIndex(new PolygonPoint(26.0,43.0).toString());
        pointTracker.index(new PolygonPoint(26.0,43.0).toString(), 10);
        pointTracker.getIndex(new PolygonPoint(28.0,43.0).toString());
        pointTracker.index(new PolygonPoint(28.0,43.0).toString(), 10);
        pointTracker.getIndex(new PolygonPoint(28.0,42.0).toString());
        pointTracker.index(new PolygonPoint(28.0,42.0).toString(), 10);
        pointTracker.getIndex(new PolygonPoint(24.0,42.0).toString());
        pointTracker.index(new PolygonPoint(24.0,42.0).toString(), 10);
        pointTracker.getIndex(new PolygonPoint(24.0,41.0).toString());
        pointTracker.index(new PolygonPoint(24.0,41.0).toString(), 10);
        pointTracker.getIndex(new PolygonPoint(27.0,41.0).toString());
        pointTracker.index(new PolygonPoint(27.0,41.0).toString(), 10);
        pointTracker.getIndex(new PolygonPoint(27.0,40.0).toString());
        pointTracker.index(new PolygonPoint(27.0,40.0).toString(), 10);
        pointTracker.getIndex(new PolygonPoint(25.0,40.0).toString());
        pointTracker.index(new PolygonPoint(25.0,40.0).toString(), 10);
        pointTracker.getIndex(new PolygonPoint(25.0,39.0).toString());
        pointTracker.index(new PolygonPoint(25.0,39.0).toString(), 10);
        pointTracker.getIndex(new PolygonPoint(24.0,39.0).toString());
        pointTracker.index(new PolygonPoint(24.0,39.0).toString(), 10);
        pointTracker.getIndex(new PolygonPoint(24.0,37.0).toString());
        pointTracker.index(new PolygonPoint(24.0,37.0).toString(), 10);
        pointTracker.getIndex(new PolygonPoint(23.0,37.0).toString());
        pointTracker.index(new PolygonPoint(23.0,37.0).toString(), 10);
        pointTracker.getIndex(new PolygonPoint(23.0,36.0).toString());
        pointTracker.index(new PolygonPoint(23.0,36.0).toString(), 10);
        pointTracker.getIndex(new PolygonPoint(22.0,36.0).toString());
        pointTracker.index(new PolygonPoint(22.0,36.0).toString(), 10);
        pointTracker.getIndex(new PolygonPoint(22.0,32.0).toString());
        pointTracker.index(new PolygonPoint(22.0,32.0).toString(), 10);
        pointTracker.getIndex(new PolygonPoint(24.0,32.0).toString());
        pointTracker.index(new PolygonPoint(24.0,32.0).toString(), 10);
        pointTracker.getIndex(new PolygonPoint(24.0,33.0).toString());
        pointTracker.index(new PolygonPoint(24.0,33.0).toString(), 10);
        pointTracker.getIndex(new PolygonPoint(26.0,33.0).toString());
        pointTracker.index(new PolygonPoint(26.0,33.0).toString(), 10);
        pointTracker.getIndex(new PolygonPoint(26.0,32.0).toString());
        pointTracker.index(new PolygonPoint(26.0,32.0).toString(), 10);
        pointTracker.getIndex(new PolygonPoint(27.0,32.0).toString());
        pointTracker.index(new PolygonPoint(27.0,32.0).toString(), 10);
        pointTracker.getIndex(new PolygonPoint(27.0,30.0).toString());
        pointTracker.index(new PolygonPoint(27.0,30.0).toString(), 10);
        pointTracker.getIndex(new PolygonPoint(26.0,30.0).toString());
        pointTracker.index(new PolygonPoint(26.0,30.0).toString(), 10);
        pointTracker.getIndex(new PolygonPoint(26.0,29.0).toString());
        pointTracker.index(new PolygonPoint(26.0,29.0).toString(), 10);
        pointTracker.getIndex(new PolygonPoint(28.0,29.0).toString());
        pointTracker.index(new PolygonPoint(28.0,29.0).toString(), 10);
        pointTracker.getIndex(new PolygonPoint(28.0,27.0).toString());
        pointTracker.index(new PolygonPoint(28.0,27.0).toString(), 10);
        pointTracker.getIndex(new PolygonPoint(29.0,27.0).toString());
        pointTracker.index(new PolygonPoint(29.0,27.0).toString(), 10);
        pointTracker.getIndex(new PolygonPoint(29.0,25.0).toString());
        pointTracker.index(new PolygonPoint(29.0,25.0).toString(), 10);
        pointTracker.getIndex(new PolygonPoint(31.0,25.0).toString());
        pointTracker.index(new PolygonPoint(31.0,25.0).toString(), 10);
        pointTracker.getIndex(new PolygonPoint(31.0,24.0).toString());
        pointTracker.index(new PolygonPoint(31.0,24.0).toString(), 10);
        pointTracker.getIndex(new PolygonPoint(32.0,24.0).toString());
        pointTracker.index(new PolygonPoint(32.0,24.0).toString(), 10);
        pointTracker.getIndex(new PolygonPoint(32.0,27.0).toString());
        pointTracker.index(new PolygonPoint(32.0,27.0).toString(), 10);
        pointTracker.getIndex(new PolygonPoint(33.0,27.0).toString());
        pointTracker.index(new PolygonPoint(33.0,27.0).toString(), 10);
        pointTracker.getIndex(new PolygonPoint(33.0,26.0).toString());
        pointTracker.index(new PolygonPoint(33.0,26.0).toString(), 10);
        pointTracker.getIndex(new PolygonPoint(34.0,26.0).toString());
        pointTracker.index(new PolygonPoint(34.0,26.0).toString(), 10);
        pointTracker.getIndex(new PolygonPoint(34.0,28.0).toString());
        pointTracker.index(new PolygonPoint(34.0,28.0).toString(), 10);
        pointTracker.getIndex(new PolygonPoint(35.0,28.0).toString());
        pointTracker.index(new PolygonPoint(35.0,28.0).toString(), 10);
        pointTracker.getIndex(new PolygonPoint(35.0,24.0).toString());
        pointTracker.index(new PolygonPoint(35.0,24.0).toString(), 10);
        pointTracker.getIndex(new PolygonPoint(36.0,24.0).toString());
        pointTracker.index(new PolygonPoint(36.0,24.0).toString(), 10);
        pointTracker.getIndex(new PolygonPoint(36.0,29.0).toString());
        pointTracker.index(new PolygonPoint(36.0,29.0).toString(), 10);
        pointTracker.getIndex(new PolygonPoint(38.0,29.0).toString());
        pointTracker.index(new PolygonPoint(38.0,29.0).toString(), 10);
        pointTracker.getIndex(new PolygonPoint(38.0,30.0).toString());
        pointTracker.index(new PolygonPoint(38.0,30.0).toString(), 10);
        pointTracker.getIndex(new PolygonPoint(37.0,30.0).toString());
        pointTracker.index(new PolygonPoint(37.0,30.0).toString(), 10);
        pointTracker.getIndex(new PolygonPoint(37.0,31.0).toString());
        pointTracker.index(new PolygonPoint(37.0,31.0).toString(), 10);
        pointTracker.getIndex(new PolygonPoint(36.0,31.0).toString());
        pointTracker.index(new PolygonPoint(36.0,31.0).toString(), 10);
        pointTracker.getIndex(new PolygonPoint(36.0,33.0).toString());
        pointTracker.index(new PolygonPoint(36.0,33.0).toString(), 10);
        pointTracker.getIndex(new PolygonPoint(35.0,33.0).toString());
        pointTracker.index(new PolygonPoint(35.0,33.0).toString(), 10);
        pointTracker.getIndex(new PolygonPoint(35.0,34.0).toString());
        pointTracker.index(new PolygonPoint(35.0,34.0).toString(), 10);
        pointTracker.getIndex(new PolygonPoint(34.0,34.0).toString());
        pointTracker.index(new PolygonPoint(34.0,34.0).toString(), 10);
        pointTracker.getIndex(new PolygonPoint(34.0,30.0).toString());
        pointTracker.index(new PolygonPoint(34.0,30.0).toString(), 10);
        pointTracker.getIndex(new PolygonPoint(32.0,30.0).toString());
        pointTracker.index(new PolygonPoint(32.0,30.0).toString(), 10);
        pointTracker.getIndex(new PolygonPoint(32.0,34.0).toString());
        pointTracker.index(new PolygonPoint(32.0,34.0).toString(), 10);
        pointTracker.getIndex(new PolygonPoint(33.0,34.0).toString());
        pointTracker.index(new PolygonPoint(33.0,34.0).toString(), 10);
        pointTracker.getIndex(new PolygonPoint(33.0,36.0).toString());
        pointTracker.index(new PolygonPoint(33.0,36.0).toString(), 10);
        pointTracker.getIndex(new PolygonPoint(35.0,36.0).toString());
        pointTracker.index(new PolygonPoint(35.0,36.0).toString(), 10);
        pointTracker.getIndex(new PolygonPoint(35.0,37.0).toString());
        pointTracker.index(new PolygonPoint(35.0,37.0).toString(), 10);
        pointTracker.getIndex(new PolygonPoint(36.0,37.0).toString());
        pointTracker.index(new PolygonPoint(36.0,37.0).toString(), 10);
        pointTracker.getIndex(new PolygonPoint(36.0,38.0).toString());
        pointTracker.index(new PolygonPoint(36.0,38.0).toString(), 10);
        pointTracker.getIndex(new PolygonPoint(33.0,38.0).toString());
        pointTracker.index(new PolygonPoint(33.0,38.0).toString(), 10);
        pointTracker.getIndex(new PolygonPoint(33.0,39.0).toString());
        pointTracker.index(new PolygonPoint(33.0,39.0).toString(), 10);
        pointTracker.getIndex(new PolygonPoint(34.0,39.0).toString());
        pointTracker.index(new PolygonPoint(34.0,39.0).toString(), 10);
        pointTracker.getIndex(new PolygonPoint(34.0,41.0).toString());
        pointTracker.index(new PolygonPoint(34.0,41.0).toString(), 10);
        pointTracker.getIndex(new PolygonPoint(35.0,41.0).toString());
        pointTracker.index(new PolygonPoint(35.0,41.0).toString(), 10);
        pointTracker.getIndex(new PolygonPoint(35.0,43.0).toString());
        pointTracker.index(new PolygonPoint(35.0,43.0).toString(), 10);
        pointTracker.getIndex(new PolygonPoint(36.0,43.0).toString());
        pointTracker.index(new PolygonPoint(36.0,43.0).toString(), 10);
        pointTracker.getIndex(new PolygonPoint(36.0,44.0).toString());
        pointTracker.index(new PolygonPoint(36.0,44.0).toString(), 10);
        pointTracker.getIndex(new PolygonPoint(34.0,44.0).toString());
        pointTracker.index(new PolygonPoint(34.0,44.0).toString(), 10);
        pointTracker.getIndex(new PolygonPoint(34.0,48.0).toString());
        pointTracker.index(new PolygonPoint(34.0,48.0).toString(), 10);
        pointTracker.getIndex(new PolygonPoint(35.0,48.0).toString());
        pointTracker.index(new PolygonPoint(35.0,48.0).toString(), 10);
        pointTracker.getIndex(new PolygonPoint(35.0,49.0).toString());
        pointTracker.index(new PolygonPoint(35.0,49.0).toString(), 10);
        pointTracker.getIndex(new PolygonPoint(33.0,49.0).toString());
        pointTracker.index(new PolygonPoint(33.0,49.0).toString(), 10);
        pointTracker.getIndex(new PolygonPoint(33.0,50.0).toString());
        pointTracker.index(new PolygonPoint(33.0,50.0).toString(), 10);
        pointTracker.getIndex(new PolygonPoint(31.0,50.0).toString());
        pointTracker.index(new PolygonPoint(31.0,50.0).toString(), 10);
        pointTracker.getIndex(new PolygonPoint(31.0,47.0).toString());
        pointTracker.index(new PolygonPoint(31.0,47.0).toString(), 10);
        pointTracker.getIndex(new PolygonPoint(30.0,47.0).toString());
        pointTracker.index(new PolygonPoint(30.0,47.0).toString(), 10);
        pointTracker.getIndex(new PolygonPoint(30.0,48.0).toString());
        pointTracker.index(new PolygonPoint(30.0,48.0).toString(), 10);
        pointTracker.getIndex(new PolygonPoint(29.0,48.0).toString());
        pointTracker.index(new PolygonPoint(29.0,48.0).toString(), 10);
        pointTracker.getIndex(new PolygonPoint(29.0,47.0).toString());
        pointTracker.index(new PolygonPoint(29.0,47.0).toString(), 10);
        pointTracker.getIndex(new PolygonPoint(28.0,47.0).toString());
        pointTracker.index(new PolygonPoint(28.0,47.0).toString(), 10);
        pointTracker.getIndex(new PolygonPoint(28.0,48.0).toString());
        pointTracker.index(new PolygonPoint(28.0,48.0).toString(), 10);
        pointTracker.getIndex(new PolygonPoint(27.0,48.0).toString());
        pointTracker.index(new PolygonPoint(27.0,48.0).toString(), 10);
        pointTracker.getIndex(new PolygonPoint(27.0,47.0).toString());
        pointTracker.index(new PolygonPoint(27.0,47.0).toString(), 10);
        pointTracker.getIndex(new PolygonPoint(26.0,47.0).toString());
        pointTracker.index(new PolygonPoint(26.0,47.0).toString(), 10);
        pointTracker.getIndex(new PolygonPoint(26.0,45.0).toString());
        pointTracker.index(new PolygonPoint(26.0,45.0).toString(), 10);
        pointTracker.getIndex(new PolygonPoint(23.0,31.0).toString());
        pointTracker.index(new PolygonPoint(23.0,31.0).toString(), 11);
        pointTracker.getIndex(new PolygonPoint(23.0,30.0).toString());
        pointTracker.index(new PolygonPoint(23.0,30.0).toString(), 11);
        pointTracker.getIndex(new PolygonPoint(22.0,30.0).toString());
        pointTracker.index(new PolygonPoint(22.0,30.0).toString(), 11);
        pointTracker.getIndex(new PolygonPoint(22.0,31.0).toString());
        pointTracker.index(new PolygonPoint(22.0,31.0).toString(), 11);
        pointTracker.getIndex(new PolygonPoint(21.0,31.0).toString());
        pointTracker.index(new PolygonPoint(21.0,31.0).toString(), 11);
        pointTracker.getIndex(new PolygonPoint(21.0,29.0).toString());
        pointTracker.index(new PolygonPoint(21.0,29.0).toString(), 11);
        pointTracker.getIndex(new PolygonPoint(22.0,29.0).toString());
        pointTracker.index(new PolygonPoint(22.0,29.0).toString(), 11);
        pointTracker.getIndex(new PolygonPoint(22.0,28.0).toString());
        pointTracker.index(new PolygonPoint(22.0,28.0).toString(), 11);
        pointTracker.getIndex(new PolygonPoint(21.0,28.0).toString());
        pointTracker.index(new PolygonPoint(21.0,28.0).toString(), 11);
        pointTracker.getIndex(new PolygonPoint(21.0,27.0).toString());
        pointTracker.index(new PolygonPoint(21.0,27.0).toString(), 11);
        pointTracker.getIndex(new PolygonPoint(23.0,27.0).toString());
        pointTracker.index(new PolygonPoint(23.0,27.0).toString(), 11);
        pointTracker.getIndex(new PolygonPoint(23.0,26.0).toString());
        pointTracker.index(new PolygonPoint(23.0,26.0).toString(), 11);
        pointTracker.getIndex(new PolygonPoint(24.0,26.0).toString());
        pointTracker.index(new PolygonPoint(24.0,26.0).toString(), 11);
        pointTracker.getIndex(new PolygonPoint(24.0,24.0).toString());
        pointTracker.index(new PolygonPoint(24.0,24.0).toString(), 11);
        pointTracker.getIndex(new PolygonPoint(25.0,24.0).toString());
        pointTracker.index(new PolygonPoint(25.0,24.0).toString(), 11);
        pointTracker.getIndex(new PolygonPoint(25.0,25.0).toString());
        pointTracker.index(new PolygonPoint(25.0,25.0).toString(), 11);
        pointTracker.getIndex(new PolygonPoint(26.0,25.0).toString());
        pointTracker.index(new PolygonPoint(26.0,25.0).toString(), 11);
        pointTracker.getIndex(new PolygonPoint(26.0,26.0).toString());
        pointTracker.index(new PolygonPoint(26.0,26.0).toString(), 11);
        pointTracker.getIndex(new PolygonPoint(27.0,26.0).toString());
        pointTracker.index(new PolygonPoint(27.0,26.0).toString(), 11);
        pointTracker.getIndex(new PolygonPoint(27.0,27.0).toString());
        pointTracker.index(new PolygonPoint(27.0,27.0).toString(), 11);
        pointTracker.getIndex(new PolygonPoint(24.0,27.0).toString());
        pointTracker.index(new PolygonPoint(24.0,27.0).toString(), 11);
        pointTracker.getIndex(new PolygonPoint(24.0,28.0).toString());
        pointTracker.index(new PolygonPoint(24.0,28.0).toString(), 11);
        pointTracker.getIndex(new PolygonPoint(25.0,28.0).toString());
        pointTracker.index(new PolygonPoint(25.0,28.0).toString(), 11);
        pointTracker.getIndex(new PolygonPoint(25.0,29.0).toString());
        pointTracker.index(new PolygonPoint(25.0,29.0).toString(), 11);
        pointTracker.getIndex(new PolygonPoint(24.0,29.0).toString());
        pointTracker.index(new PolygonPoint(24.0,29.0).toString(), 11);
        pointTracker.getIndex(new PolygonPoint(24.0,31.0).toString());
        pointTracker.index(new PolygonPoint(24.0,31.0).toString(), 11);
        pointTracker.getIndex(new PolygonPoint(4.0,22.0).toString());
        pointTracker.index(new PolygonPoint(4.0,22.0).toString(), 12);
        pointTracker.getIndex(new PolygonPoint(3.0,22.0).toString());
        pointTracker.index(new PolygonPoint(3.0,22.0).toString(), 12);
        pointTracker.getIndex(new PolygonPoint(3.0,20.0).toString());
        pointTracker.getIndex(new PolygonPoint(4.0,20.0).toString());
        pointTracker.index(new PolygonPoint(4.0,20.0).toString(), 12);
        pointTracker.changeIndex(0, 12);
        pointTracker.getIndex(new PolygonPoint(13.0,43.0).toString());
        pointTracker.index(new PolygonPoint(13.0,43.0).toString(), 13);
        pointTracker.getIndex(new PolygonPoint(14.0,43.0).toString());
        pointTracker.index(new PolygonPoint(14.0,43.0).toString(), 13);
        pointTracker.getIndex(new PolygonPoint(14.0,45.0).toString());
        pointTracker.index(new PolygonPoint(14.0,45.0).toString(), 13);
        pointTracker.getIndex(new PolygonPoint(13.0,45.0).toString());
        pointTracker.index(new PolygonPoint(13.0,45.0).toString(), 13);
        pointTracker.getIndex(new PolygonPoint(13.0,46.0).toString());
        pointTracker.index(new PolygonPoint(13.0,46.0).toString(), 13);
        pointTracker.getIndex(new PolygonPoint(11.0,46.0).toString());
        pointTracker.index(new PolygonPoint(11.0,46.0).toString(), 13);
        pointTracker.getIndex(new PolygonPoint(11.0,48.0).toString());
        pointTracker.index(new PolygonPoint(11.0,48.0).toString(), 13);
        pointTracker.getIndex(new PolygonPoint(10.0,48.0).toString());
        pointTracker.index(new PolygonPoint(10.0,48.0).toString(), 13);
        pointTracker.getIndex(new PolygonPoint(10.0,46.0).toString());
        pointTracker.index(new PolygonPoint(10.0,46.0).toString(), 13);
        pointTracker.getIndex(new PolygonPoint(9.0,46.0).toString());
        pointTracker.index(new PolygonPoint(9.0,46.0).toString(), 13);
        pointTracker.getIndex(new PolygonPoint(9.0,45.0).toString());
        pointTracker.index(new PolygonPoint(9.0,45.0).toString(), 13);
        pointTracker.getIndex(new PolygonPoint(10.0,45.0).toString());
        pointTracker.index(new PolygonPoint(10.0,45.0).toString(), 13);
        pointTracker.getIndex(new PolygonPoint(10.0,44.0).toString());
        pointTracker.index(new PolygonPoint(10.0,44.0).toString(), 13);
        pointTracker.getIndex(new PolygonPoint(11.0,44.0).toString());
        pointTracker.index(new PolygonPoint(11.0,44.0).toString(), 13);
        pointTracker.getIndex(new PolygonPoint(11.0,45.0).toString());
        pointTracker.index(new PolygonPoint(11.0,45.0).toString(), 13);
        pointTracker.getIndex(new PolygonPoint(12.0,45.0).toString());
        pointTracker.index(new PolygonPoint(12.0,45.0).toString(), 13);
        pointTracker.getIndex(new PolygonPoint(12.0,44.0).toString());
        pointTracker.index(new PolygonPoint(12.0,44.0).toString(), 13);
        pointTracker.getIndex(new PolygonPoint(13.0,44.0).toString());
        pointTracker.index(new PolygonPoint(13.0,44.0).toString(), 13);
        pointTracker.getIndex(new PolygonPoint(30.0,53.0).toString());
        pointTracker.getIndex(new PolygonPoint(30.0,52.0).toString());
        pointTracker.index(new PolygonPoint(30.0,52.0).toString(), 14);
        pointTracker.getIndex(new PolygonPoint(29.0,52.0).toString());
        pointTracker.index(new PolygonPoint(29.0,52.0).toString(), 14);
        pointTracker.getIndex(new PolygonPoint(29.0,51.0).toString());
        pointTracker.index(new PolygonPoint(29.0,51.0).toString(), 14);
        pointTracker.getIndex(new PolygonPoint(28.0,51.0).toString());
        pointTracker.index(new PolygonPoint(28.0,51.0).toString(), 14);
        pointTracker.getIndex(new PolygonPoint(28.0,50.0).toString());
        pointTracker.index(new PolygonPoint(28.0,50.0).toString(), 14);
        pointTracker.getIndex(new PolygonPoint(27.0,50.0).toString());
        pointTracker.index(new PolygonPoint(27.0,50.0).toString(), 14);
        pointTracker.getIndex(new PolygonPoint(27.0,49.0).toString());
        pointTracker.index(new PolygonPoint(27.0,49.0).toString(), 14);
        pointTracker.getIndex(new PolygonPoint(29.0,49.0).toString());
        pointTracker.index(new PolygonPoint(29.0,49.0).toString(), 14);
        pointTracker.getIndex(new PolygonPoint(29.0,50.0).toString());
        pointTracker.index(new PolygonPoint(29.0,50.0).toString(), 14);
        pointTracker.getIndex(new PolygonPoint(30.0,50.0).toString());
        pointTracker.index(new PolygonPoint(30.0,50.0).toString(), 14);
        pointTracker.getIndex(new PolygonPoint(30.0,51.0).toString());
        pointTracker.index(new PolygonPoint(30.0,51.0).toString(), 14);
        pointTracker.getIndex(new PolygonPoint(32.0,51.0).toString());
        pointTracker.index(new PolygonPoint(32.0,51.0).toString(), 14);
        pointTracker.getIndex(new PolygonPoint(32.0,53.0).toString());
        pointTracker.index(new PolygonPoint(32.0,53.0).toString(), 14);
        pointTracker.changeIndex(8, 14);
        pointTracker.getIndex(new PolygonPoint(27.0,52.0).toString());
        pointTracker.index(new PolygonPoint(27.0,52.0).toString(), 15);
        pointTracker.getIndex(new PolygonPoint(27.0,51.0).toString());
        pointTracker.index(new PolygonPoint(27.0,51.0).toString(), 15);
        pointTracker.getIndex(new PolygonPoint(28.0,51.0).toString());
        pointTracker.getIndex(new PolygonPoint(28.0,52.0).toString());
        pointTracker.index(new PolygonPoint(28.0,52.0).toString(), 15);
        pointTracker.changeIndex(8, 15);
        pointTracker.getIndex(new PolygonPoint(36.0,6.0).toString());
        pointTracker.index(new PolygonPoint(36.0,6.0).toString(), 16);
        pointTracker.getIndex(new PolygonPoint(37.0,6.0).toString());
        pointTracker.getIndex(new PolygonPoint(37.0,8.0).toString());
        pointTracker.index(new PolygonPoint(37.0,8.0).toString(), 16);
        pointTracker.getIndex(new PolygonPoint(36.0,8.0).toString());
        pointTracker.index(new PolygonPoint(36.0,8.0).toString(), 16);
        pointTracker.changeIndex(0, 16);
        pointTracker.getIndex(new PolygonPoint(15.0,55.0).toString());
        pointTracker.index(new PolygonPoint(15.0,55.0).toString(), 17);
        pointTracker.getIndex(new PolygonPoint(15.0,56.0).toString());
        pointTracker.index(new PolygonPoint(15.0,56.0).toString(), 17);
        pointTracker.getIndex(new PolygonPoint(12.0,56.0).toString());
        pointTracker.index(new PolygonPoint(12.0,56.0).toString(), 17);
        pointTracker.getIndex(new PolygonPoint(12.0,54.0).toString());
        pointTracker.index(new PolygonPoint(12.0,54.0).toString(), 17);
        pointTracker.getIndex(new PolygonPoint(13.0,54.0).toString());
        pointTracker.index(new PolygonPoint(13.0,54.0).toString(), 17);
        pointTracker.getIndex(new PolygonPoint(13.0,55.0).toString());
        pointTracker.index(new PolygonPoint(13.0,55.0).toString(), 17);
        pointTracker.getIndex(new PolygonPoint(39.0,54.0).toString());
        pointTracker.index(new PolygonPoint(39.0,54.0).toString(), 18);
        pointTracker.getIndex(new PolygonPoint(39.0,55.0).toString());
        pointTracker.getIndex(new PolygonPoint(37.0,55.0).toString());
        pointTracker.index(new PolygonPoint(37.0,55.0).toString(), 18);
        pointTracker.getIndex(new PolygonPoint(37.0,56.0).toString());
        pointTracker.index(new PolygonPoint(37.0,56.0).toString(), 18);
        pointTracker.getIndex(new PolygonPoint(35.0,56.0).toString());
        pointTracker.index(new PolygonPoint(35.0,56.0).toString(), 18);
        pointTracker.getIndex(new PolygonPoint(35.0,53.0).toString());
        pointTracker.index(new PolygonPoint(35.0,53.0).toString(), 18);
        pointTracker.getIndex(new PolygonPoint(36.0,53.0).toString());
        pointTracker.index(new PolygonPoint(36.0,53.0).toString(), 18);
        pointTracker.getIndex(new PolygonPoint(36.0,52.0).toString());
        pointTracker.index(new PolygonPoint(36.0,52.0).toString(), 18);
        pointTracker.getIndex(new PolygonPoint(37.0,52.0).toString());
        pointTracker.index(new PolygonPoint(37.0,52.0).toString(), 18);
        pointTracker.getIndex(new PolygonPoint(37.0,53.0).toString());
        pointTracker.index(new PolygonPoint(37.0,53.0).toString(), 18);
        pointTracker.getIndex(new PolygonPoint(38.0,53.0).toString());
        pointTracker.index(new PolygonPoint(38.0,53.0).toString(), 18);
        pointTracker.getIndex(new PolygonPoint(38.0,54.0).toString());
        pointTracker.index(new PolygonPoint(38.0,54.0).toString(), 18);
        pointTracker.changeIndex(0, 18);
        pointTracker.getIndex(new PolygonPoint(17.0,9.0).toString());
        pointTracker.getIndex(new PolygonPoint(16.0,9.0).toString());
        pointTracker.index(new PolygonPoint(16.0,9.0).toString(), 19);
        pointTracker.getIndex(new PolygonPoint(16.0,8.0).toString());
        pointTracker.index(new PolygonPoint(16.0,8.0).toString(), 19);
        pointTracker.getIndex(new PolygonPoint(17.0,8.0).toString());
        pointTracker.index(new PolygonPoint(17.0,8.0).toString(), 19);
        pointTracker.changeIndex(0, 19);
        pointTracker.getIndex(new PolygonPoint(38.0,37.0).toString());
        pointTracker.index(new PolygonPoint(38.0,37.0).toString(), 20);
        pointTracker.getIndex(new PolygonPoint(37.0,37.0).toString());
        pointTracker.index(new PolygonPoint(37.0,37.0).toString(), 20);
        pointTracker.getIndex(new PolygonPoint(37.0,36.0).toString());
        pointTracker.index(new PolygonPoint(37.0,36.0).toString(), 20);
        pointTracker.getIndex(new PolygonPoint(38.0,36.0).toString());
        pointTracker.index(new PolygonPoint(38.0,36.0).toString(), 20);
        pointTracker.getIndex(new PolygonPoint(30.0,57.0).toString());
        pointTracker.index(new PolygonPoint(30.0,57.0).toString(), 21);
        pointTracker.getIndex(new PolygonPoint(30.0,56.0).toString());
        pointTracker.index(new PolygonPoint(30.0,56.0).toString(), 21);
        pointTracker.getIndex(new PolygonPoint(31.0,56.0).toString());
        pointTracker.index(new PolygonPoint(31.0,56.0).toString(), 21);
        pointTracker.getIndex(new PolygonPoint(31.0,57.0).toString());
        pointTracker.index(new PolygonPoint(31.0,57.0).toString(), 21);
        pointTracker.getIndex(new PolygonPoint(34.0,10.0).toString());
        pointTracker.index(new PolygonPoint(34.0,10.0).toString(), 22);
        pointTracker.getIndex(new PolygonPoint(34.0,9.0).toString());
        pointTracker.index(new PolygonPoint(34.0,9.0).toString(), 22);
        pointTracker.getIndex(new PolygonPoint(31.0,9.0).toString());
        pointTracker.index(new PolygonPoint(31.0,9.0).toString(), 22);
        pointTracker.getIndex(new PolygonPoint(31.0,8.0).toString());
        pointTracker.index(new PolygonPoint(31.0,8.0).toString(), 22);
        pointTracker.getIndex(new PolygonPoint(35.0,8.0).toString());
        pointTracker.index(new PolygonPoint(35.0,8.0).toString(), 22);
        pointTracker.getIndex(new PolygonPoint(35.0,11.0).toString());
        pointTracker.getIndex(new PolygonPoint(33.0,11.0).toString());
        pointTracker.index(new PolygonPoint(33.0,11.0).toString(), 22);
        pointTracker.getIndex(new PolygonPoint(33.0,10.0).toString());
        pointTracker.index(new PolygonPoint(33.0,10.0).toString(), 22);
        pointTracker.changeIndex(0, 22);
        pointTracker.getIndex(new PolygonPoint(38.0,65.0).toString());
        pointTracker.index(new PolygonPoint(38.0,65.0).toString(), 23);
        pointTracker.getIndex(new PolygonPoint(39.0,65.0).toString());
        pointTracker.index(new PolygonPoint(39.0,65.0).toString(), 23);
        pointTracker.getIndex(new PolygonPoint(39.0,64.0).toString());
        pointTracker.index(new PolygonPoint(39.0,64.0).toString(), 23);
        pointTracker.getIndex(new PolygonPoint(40.0,64.0).toString());
        pointTracker.index(new PolygonPoint(40.0,64.0).toString(), 23);
        pointTracker.getIndex(new PolygonPoint(40.0,67.0).toString());
        pointTracker.index(new PolygonPoint(40.0,67.0).toString(), 23);
        pointTracker.getIndex(new PolygonPoint(39.0,67.0).toString());
        pointTracker.index(new PolygonPoint(39.0,67.0).toString(), 23);
        pointTracker.getIndex(new PolygonPoint(39.0,66.0).toString());
        pointTracker.index(new PolygonPoint(39.0,66.0).toString(), 23);
        pointTracker.getIndex(new PolygonPoint(37.0,66.0).toString());
        pointTracker.index(new PolygonPoint(37.0,66.0).toString(), 23);
        pointTracker.getIndex(new PolygonPoint(37.0,64.0).toString());
        pointTracker.index(new PolygonPoint(37.0,64.0).toString(), 23);
        pointTracker.getIndex(new PolygonPoint(36.0,64.0).toString());
        pointTracker.index(new PolygonPoint(36.0,64.0).toString(), 23);
        pointTracker.getIndex(new PolygonPoint(36.0,63.0).toString());
        pointTracker.index(new PolygonPoint(36.0,63.0).toString(), 23);
        pointTracker.getIndex(new PolygonPoint(38.0,63.0).toString());
        pointTracker.changeIndex(0, 23);
        pointTracker.getIndex(new PolygonPoint(21.0,57.0).toString());
        pointTracker.index(new PolygonPoint(21.0,57.0).toString(), 24);
        pointTracker.getIndex(new PolygonPoint(20.0,57.0).toString());
        pointTracker.index(new PolygonPoint(20.0,57.0).toString(), 24);
        pointTracker.getIndex(new PolygonPoint(20.0,56.0).toString());
        pointTracker.index(new PolygonPoint(20.0,56.0).toString(), 24);
        pointTracker.getIndex(new PolygonPoint(21.0,56.0).toString());
        pointTracker.index(new PolygonPoint(21.0,56.0).toString(), 24);
        pointTracker.getIndex(new PolygonPoint(33.0,54.0).toString());
        pointTracker.index(new PolygonPoint(33.0,54.0).toString(), 25);
        pointTracker.getIndex(new PolygonPoint(33.0,51.0).toString());
        pointTracker.index(new PolygonPoint(33.0,51.0).toString(), 25);
        pointTracker.getIndex(new PolygonPoint(34.0,51.0).toString());
        pointTracker.index(new PolygonPoint(34.0,51.0).toString(), 25);
        pointTracker.getIndex(new PolygonPoint(34.0,52.0).toString());
        pointTracker.index(new PolygonPoint(34.0,52.0).toString(), 25);
        pointTracker.getIndex(new PolygonPoint(35.0,52.0).toString());
        pointTracker.index(new PolygonPoint(35.0,52.0).toString(), 25);
        pointTracker.getIndex(new PolygonPoint(35.0,53.0).toString());
        pointTracker.getIndex(new PolygonPoint(34.0,53.0).toString());
        pointTracker.index(new PolygonPoint(34.0,53.0).toString(), 25);
        pointTracker.getIndex(new PolygonPoint(34.0,54.0).toString());
        pointTracker.index(new PolygonPoint(34.0,54.0).toString(), 25);
        pointTracker.changeIndex(0, 25);
        pointTracker.getIndex(new PolygonPoint(13.0,13.0).toString());
        pointTracker.index(new PolygonPoint(13.0,13.0).toString(), 26);
        pointTracker.getIndex(new PolygonPoint(13.0,14.0).toString());
        pointTracker.index(new PolygonPoint(13.0,14.0).toString(), 26);
        pointTracker.getIndex(new PolygonPoint(12.0,14.0).toString());
        pointTracker.index(new PolygonPoint(12.0,14.0).toString(), 26);
        pointTracker.getIndex(new PolygonPoint(12.0,12.0).toString());
        pointTracker.index(new PolygonPoint(12.0,12.0).toString(), 26);
        pointTracker.getIndex(new PolygonPoint(11.0,12.0).toString());
        pointTracker.index(new PolygonPoint(11.0,12.0).toString(), 26);
        pointTracker.getIndex(new PolygonPoint(11.0,10.0).toString());
        pointTracker.index(new PolygonPoint(11.0,10.0).toString(), 26);
        pointTracker.getIndex(new PolygonPoint(12.0,10.0).toString());
        pointTracker.index(new PolygonPoint(12.0,10.0).toString(), 26);
        pointTracker.getIndex(new PolygonPoint(12.0,11.0).toString());
        pointTracker.index(new PolygonPoint(12.0,11.0).toString(), 26);
        pointTracker.getIndex(new PolygonPoint(13.0,11.0).toString());
        pointTracker.index(new PolygonPoint(13.0,11.0).toString(), 26);
        pointTracker.getIndex(new PolygonPoint(13.0,9.0).toString());
        pointTracker.index(new PolygonPoint(13.0,9.0).toString(), 26);
        pointTracker.getIndex(new PolygonPoint(15.0,9.0).toString());
        pointTracker.index(new PolygonPoint(15.0,9.0).toString(), 26);
        pointTracker.getIndex(new PolygonPoint(15.0,10.0).toString());
        pointTracker.getIndex(new PolygonPoint(14.0,10.0).toString());
        pointTracker.index(new PolygonPoint(14.0,10.0).toString(), 26);
        pointTracker.getIndex(new PolygonPoint(14.0,13.0).toString());
        pointTracker.index(new PolygonPoint(14.0,13.0).toString(), 26);
        pointTracker.changeIndex(0, 26);
        pointTracker.getIndex(new PolygonPoint(32.0,56.0).toString());
        pointTracker.index(new PolygonPoint(32.0,56.0).toString(), 27);
        pointTracker.getIndex(new PolygonPoint(33.0,56.0).toString());
        pointTracker.index(new PolygonPoint(33.0,56.0).toString(), 27);
        pointTracker.getIndex(new PolygonPoint(33.0,58.0).toString());
        pointTracker.index(new PolygonPoint(33.0,58.0).toString(), 27);
        pointTracker.getIndex(new PolygonPoint(34.0,58.0).toString());
        pointTracker.getIndex(new PolygonPoint(34.0,59.0).toString());
        pointTracker.index(new PolygonPoint(34.0,59.0).toString(), 27);
        pointTracker.getIndex(new PolygonPoint(32.0,59.0).toString());
        pointTracker.index(new PolygonPoint(32.0,59.0).toString(), 27);
        pointTracker.getIndex(new PolygonPoint(32.0,60.0).toString());
        pointTracker.index(new PolygonPoint(32.0,60.0).toString(), 27);
        pointTracker.getIndex(new PolygonPoint(31.0,60.0).toString());
        pointTracker.index(new PolygonPoint(31.0,60.0).toString(), 27);
        pointTracker.getIndex(new PolygonPoint(31.0,61.0).toString());
        pointTracker.index(new PolygonPoint(31.0,61.0).toString(), 27);
        pointTracker.getIndex(new PolygonPoint(29.0,61.0).toString());
        pointTracker.index(new PolygonPoint(29.0,61.0).toString(), 27);
        pointTracker.getIndex(new PolygonPoint(29.0,60.0).toString());
        pointTracker.index(new PolygonPoint(29.0,60.0).toString(), 27);
        pointTracker.getIndex(new PolygonPoint(30.0,60.0).toString());
        pointTracker.index(new PolygonPoint(30.0,60.0).toString(), 27);
        pointTracker.getIndex(new PolygonPoint(30.0,58.0).toString());
        pointTracker.index(new PolygonPoint(30.0,58.0).toString(), 27);
        pointTracker.getIndex(new PolygonPoint(31.0,58.0).toString());
        pointTracker.index(new PolygonPoint(31.0,58.0).toString(), 27);
        pointTracker.getIndex(new PolygonPoint(31.0,57.0).toString());
        pointTracker.getIndex(new PolygonPoint(32.0,57.0).toString());
        pointTracker.index(new PolygonPoint(32.0,57.0).toString(), 27);
        pointTracker.changeIndex(9, 27);
        pointTracker.changeIndex(9, 21);
        pointTracker.getIndex(new PolygonPoint(36.0,44.0).toString());
        pointTracker.getIndex(new PolygonPoint(37.0,44.0).toString());
        pointTracker.index(new PolygonPoint(37.0,44.0).toString(), 28);
        pointTracker.getIndex(new PolygonPoint(37.0,45.0).toString());
        pointTracker.index(new PolygonPoint(37.0,45.0).toString(), 28);
        pointTracker.getIndex(new PolygonPoint(36.0,45.0).toString());
        pointTracker.index(new PolygonPoint(36.0,45.0).toString(), 28);
        pointTracker.changeIndex(10, 28);
        pointTracker.getIndex(new PolygonPoint(19.0,54.0).toString());
        pointTracker.index(new PolygonPoint(19.0,54.0).toString(), 29);
        pointTracker.getIndex(new PolygonPoint(20.0,54.0).toString());
        pointTracker.index(new PolygonPoint(20.0,54.0).toString(), 29);
        pointTracker.getIndex(new PolygonPoint(20.0,56.0).toString());
        pointTracker.getIndex(new PolygonPoint(19.0,56.0).toString());
        pointTracker.index(new PolygonPoint(19.0,56.0).toString(), 29);
        pointTracker.changeIndex(24, 29);
        pointTracker.getIndex(new PolygonPoint(35.0,46.0).toString());
        pointTracker.index(new PolygonPoint(35.0,46.0).toString(), 30);
        pointTracker.getIndex(new PolygonPoint(35.0,45.0).toString());
        pointTracker.index(new PolygonPoint(35.0,45.0).toString(), 30);
        pointTracker.getIndex(new PolygonPoint(36.0,45.0).toString());
        pointTracker.getIndex(new PolygonPoint(36.0,46.0).toString());
        pointTracker.index(new PolygonPoint(36.0,46.0).toString(), 30);
        pointTracker.changeIndex(10, 30);
        pointTracker.getIndex(new PolygonPoint(9.0,43.0).toString());
        pointTracker.index(new PolygonPoint(9.0,43.0).toString(), 31);
        pointTracker.getIndex(new PolygonPoint(8.0,43.0).toString());
        pointTracker.index(new PolygonPoint(8.0,43.0).toString(), 31);
        pointTracker.getIndex(new PolygonPoint(8.0,42.0).toString());
        pointTracker.index(new PolygonPoint(8.0,42.0).toString(), 31);
        pointTracker.getIndex(new PolygonPoint(7.0,42.0).toString());
        pointTracker.index(new PolygonPoint(7.0,42.0).toString(), 31);
        pointTracker.getIndex(new PolygonPoint(7.0,41.0).toString());
        pointTracker.index(new PolygonPoint(7.0,41.0).toString(), 31);
        pointTracker.getIndex(new PolygonPoint(9.0,41.0).toString());
        pointTracker.index(new PolygonPoint(9.0,41.0).toString(), 31);
        pointTracker.getIndex(new PolygonPoint(34.0,65.0).toString());
        pointTracker.index(new PolygonPoint(34.0,65.0).toString(), 32);
        pointTracker.getIndex(new PolygonPoint(34.0,66.0).toString());
        pointTracker.index(new PolygonPoint(34.0,66.0).toString(), 32);
        pointTracker.getIndex(new PolygonPoint(33.0,66.0).toString());
        pointTracker.index(new PolygonPoint(33.0,66.0).toString(), 32);
        pointTracker.getIndex(new PolygonPoint(33.0,65.0).toString());
        pointTracker.index(new PolygonPoint(33.0,65.0).toString(), 32);
        pointTracker.getIndex(new PolygonPoint(26.0,61.0).toString());
        pointTracker.index(new PolygonPoint(26.0,61.0).toString(), 33);
        pointTracker.getIndex(new PolygonPoint(27.0,61.0).toString());
        pointTracker.index(new PolygonPoint(27.0,61.0).toString(), 33);
        pointTracker.getIndex(new PolygonPoint(27.0,62.0).toString());
        pointTracker.index(new PolygonPoint(27.0,62.0).toString(), 33);
        pointTracker.getIndex(new PolygonPoint(28.0,62.0).toString());
        pointTracker.index(new PolygonPoint(28.0,62.0).toString(), 33);
        pointTracker.getIndex(new PolygonPoint(28.0,61.0).toString());
        pointTracker.index(new PolygonPoint(28.0,61.0).toString(), 33);
        pointTracker.getIndex(new PolygonPoint(29.0,61.0).toString());
        pointTracker.getIndex(new PolygonPoint(29.0,63.0).toString());
        pointTracker.index(new PolygonPoint(29.0,63.0).toString(), 33);
        pointTracker.getIndex(new PolygonPoint(28.0,63.0).toString());
        pointTracker.index(new PolygonPoint(28.0,63.0).toString(), 33);
        pointTracker.getIndex(new PolygonPoint(28.0,65.0).toString());
        pointTracker.index(new PolygonPoint(28.0,65.0).toString(), 33);
        pointTracker.getIndex(new PolygonPoint(27.0,65.0).toString());
        pointTracker.index(new PolygonPoint(27.0,65.0).toString(), 33);
        pointTracker.getIndex(new PolygonPoint(27.0,64.0).toString());
        pointTracker.index(new PolygonPoint(27.0,64.0).toString(), 33);
        pointTracker.getIndex(new PolygonPoint(26.0,64.0).toString());
        pointTracker.index(new PolygonPoint(26.0,64.0).toString(), 33);
        pointTracker.getIndex(new PolygonPoint(26.0,63.0).toString());
        pointTracker.index(new PolygonPoint(26.0,63.0).toString(), 33);
        pointTracker.getIndex(new PolygonPoint(25.0,63.0).toString());
        pointTracker.index(new PolygonPoint(25.0,63.0).toString(), 33);
        pointTracker.getIndex(new PolygonPoint(25.0,62.0).toString());
        pointTracker.index(new PolygonPoint(25.0,62.0).toString(), 33);
        pointTracker.getIndex(new PolygonPoint(26.0,62.0).toString());
        pointTracker.index(new PolygonPoint(26.0,62.0).toString(), 33);
        pointTracker.changeIndex(9, 33);
        pointTracker.getIndex(new PolygonPoint(10.0,15.0).toString());
        pointTracker.index(new PolygonPoint(10.0,15.0).toString(), 34);
        pointTracker.getIndex(new PolygonPoint(10.0,16.0).toString());
        pointTracker.index(new PolygonPoint(10.0,16.0).toString(), 34);
        pointTracker.getIndex(new PolygonPoint(9.0,16.0).toString());
        pointTracker.index(new PolygonPoint(9.0,16.0).toString(), 34);
        pointTracker.getIndex(new PolygonPoint(9.0,15.0).toString());
        pointTracker.index(new PolygonPoint(9.0,15.0).toString(), 34);
        pointTracker.getIndex(new PolygonPoint(26.0,48.0).toString());
        pointTracker.index(new PolygonPoint(26.0,48.0).toString(), 35);
        pointTracker.getIndex(new PolygonPoint(25.0,48.0).toString());
        pointTracker.index(new PolygonPoint(25.0,48.0).toString(), 35);
        pointTracker.getIndex(new PolygonPoint(25.0,47.0).toString());
        pointTracker.index(new PolygonPoint(25.0,47.0).toString(), 35);
        pointTracker.getIndex(new PolygonPoint(26.0,47.0).toString());
        pointTracker.changeIndex(10, 35);
        pointTracker.getIndex(new PolygonPoint(8.0,60.0).toString());
        pointTracker.index(new PolygonPoint(8.0,60.0).toString(), 36);
        pointTracker.getIndex(new PolygonPoint(8.0,58.0).toString());
        pointTracker.getIndex(new PolygonPoint(9.0,58.0).toString());
        pointTracker.index(new PolygonPoint(9.0,58.0).toString(), 36);
        pointTracker.getIndex(new PolygonPoint(9.0,60.0).toString());
        pointTracker.index(new PolygonPoint(9.0,60.0).toString(), 36);
        pointTracker.changeIndex(5, 36);
        pointTracker.getIndex(new PolygonPoint(29.0,13.0).toString());
        pointTracker.index(new PolygonPoint(29.0,13.0).toString(), 37);
        pointTracker.getIndex(new PolygonPoint(30.0,13.0).toString());
        pointTracker.index(new PolygonPoint(30.0,13.0).toString(), 37);
        pointTracker.getIndex(new PolygonPoint(30.0,14.0).toString());
        pointTracker.index(new PolygonPoint(30.0,14.0).toString(), 37);
        pointTracker.getIndex(new PolygonPoint(28.0,14.0).toString());
        pointTracker.index(new PolygonPoint(28.0,14.0).toString(), 37);
        pointTracker.getIndex(new PolygonPoint(28.0,13.0).toString());
        pointTracker.index(new PolygonPoint(28.0,13.0).toString(), 37);
        pointTracker.getIndex(new PolygonPoint(27.0,13.0).toString());
        pointTracker.index(new PolygonPoint(27.0,13.0).toString(), 37);
        pointTracker.getIndex(new PolygonPoint(27.0,12.0).toString());
        pointTracker.index(new PolygonPoint(27.0,12.0).toString(), 37);
        pointTracker.getIndex(new PolygonPoint(29.0,12.0).toString());
        pointTracker.index(new PolygonPoint(29.0,12.0).toString(), 37);
        pointTracker.getIndex(new PolygonPoint(5.0,35.0).toString());
        pointTracker.index(new PolygonPoint(5.0,35.0).toString(), 38);
        pointTracker.getIndex(new PolygonPoint(9.0,35.0).toString());
        pointTracker.index(new PolygonPoint(9.0,35.0).toString(), 38);
        pointTracker.getIndex(new PolygonPoint(9.0,36.0).toString());
        pointTracker.index(new PolygonPoint(9.0,36.0).toString(), 38);
        pointTracker.getIndex(new PolygonPoint(5.0,36.0).toString());
        pointTracker.index(new PolygonPoint(5.0,36.0).toString(), 38);
        pointTracker.getIndex(new PolygonPoint(29.0,5.0).toString());
        pointTracker.index(new PolygonPoint(29.0,5.0).toString(), 39);
        pointTracker.getIndex(new PolygonPoint(31.0,5.0).toString());
        pointTracker.index(new PolygonPoint(31.0,5.0).toString(), 39);
        pointTracker.getIndex(new PolygonPoint(31.0,4.0).toString());
        pointTracker.index(new PolygonPoint(31.0,4.0).toString(), 39);
        pointTracker.getIndex(new PolygonPoint(30.0,4.0).toString());
        pointTracker.index(new PolygonPoint(30.0,4.0).toString(), 39);
        pointTracker.getIndex(new PolygonPoint(30.0,3.0).toString());
        pointTracker.index(new PolygonPoint(30.0,3.0).toString(), 39);
        pointTracker.getIndex(new PolygonPoint(31.0,3.0).toString());
        pointTracker.index(new PolygonPoint(31.0,3.0).toString(), 39);
        pointTracker.getIndex(new PolygonPoint(31.0,2.0).toString());
        pointTracker.index(new PolygonPoint(31.0,2.0).toString(), 39);
        pointTracker.getIndex(new PolygonPoint(33.0,2.0).toString());
        pointTracker.index(new PolygonPoint(33.0,2.0).toString(), 39);
        pointTracker.getIndex(new PolygonPoint(33.0,3.0).toString());
        pointTracker.index(new PolygonPoint(33.0,3.0).toString(), 39);
        pointTracker.getIndex(new PolygonPoint(34.0,3.0).toString());
        pointTracker.index(new PolygonPoint(34.0,3.0).toString(), 39);
        pointTracker.getIndex(new PolygonPoint(34.0,4.0).toString());
        pointTracker.index(new PolygonPoint(34.0,4.0).toString(), 39);
        pointTracker.getIndex(new PolygonPoint(35.0,4.0).toString());
        pointTracker.index(new PolygonPoint(35.0,4.0).toString(), 39);
        pointTracker.getIndex(new PolygonPoint(35.0,7.0).toString());
        pointTracker.index(new PolygonPoint(35.0,7.0).toString(), 39);
        pointTracker.getIndex(new PolygonPoint(32.0,7.0).toString());
        pointTracker.index(new PolygonPoint(32.0,7.0).toString(), 39);
        pointTracker.getIndex(new PolygonPoint(32.0,6.0).toString());
        pointTracker.index(new PolygonPoint(32.0,6.0).toString(), 39);
        pointTracker.getIndex(new PolygonPoint(29.0,6.0).toString());
        pointTracker.index(new PolygonPoint(29.0,6.0).toString(), 39);
        pointTracker.getIndex(new PolygonPoint(29.0,7.0).toString());
        pointTracker.index(new PolygonPoint(29.0,7.0).toString(), 39);
        pointTracker.getIndex(new PolygonPoint(28.0,7.0).toString());
        pointTracker.index(new PolygonPoint(28.0,7.0).toString(), 39);
        pointTracker.getIndex(new PolygonPoint(28.0,8.0).toString());
        pointTracker.index(new PolygonPoint(28.0,8.0).toString(), 39);
        pointTracker.getIndex(new PolygonPoint(27.0,8.0).toString());
        pointTracker.index(new PolygonPoint(27.0,8.0).toString(), 39);
        pointTracker.getIndex(new PolygonPoint(27.0,6.0).toString());
        pointTracker.index(new PolygonPoint(27.0,6.0).toString(), 39);
        pointTracker.getIndex(new PolygonPoint(26.0,6.0).toString());
        pointTracker.index(new PolygonPoint(26.0,6.0).toString(), 39);
        pointTracker.getIndex(new PolygonPoint(26.0,5.0).toString());
        pointTracker.index(new PolygonPoint(26.0,5.0).toString(), 39);
        pointTracker.getIndex(new PolygonPoint(27.0,5.0).toString());
        pointTracker.index(new PolygonPoint(27.0,5.0).toString(), 39);
        pointTracker.getIndex(new PolygonPoint(27.0,4.0).toString());
        pointTracker.index(new PolygonPoint(27.0,4.0).toString(), 39);
        pointTracker.getIndex(new PolygonPoint(29.0,4.0).toString());
        pointTracker.index(new PolygonPoint(29.0,4.0).toString(), 39);
        pointTracker.getIndex(new PolygonPoint(37.0,2.0).toString());
        pointTracker.getIndex(new PolygonPoint(38.0,2.0).toString());
        pointTracker.index(new PolygonPoint(38.0,2.0).toString(), 40);
        pointTracker.getIndex(new PolygonPoint(38.0,3.0).toString());
        pointTracker.index(new PolygonPoint(38.0,3.0).toString(), 40);
        pointTracker.getIndex(new PolygonPoint(37.0,3.0).toString());
        pointTracker.index(new PolygonPoint(37.0,3.0).toString(), 40);
        pointTracker.changeIndex(0, 40);
        pointTracker.getIndex(new PolygonPoint(37.0,43.0).toString());
        pointTracker.index(new PolygonPoint(37.0,43.0).toString(), 41);
        pointTracker.getIndex(new PolygonPoint(37.0,42.0).toString());
        pointTracker.index(new PolygonPoint(37.0,42.0).toString(), 41);
        pointTracker.getIndex(new PolygonPoint(39.0,42.0).toString());
        pointTracker.index(new PolygonPoint(39.0,42.0).toString(), 41);
        pointTracker.getIndex(new PolygonPoint(39.0,44.0).toString());
        pointTracker.getIndex(new PolygonPoint(38.0,44.0).toString());
        pointTracker.index(new PolygonPoint(38.0,44.0).toString(), 41);
        pointTracker.getIndex(new PolygonPoint(38.0,43.0).toString());
        pointTracker.index(new PolygonPoint(38.0,43.0).toString(), 41);
        pointTracker.changeIndex(0, 41);
        pointTracker.getIndex(new PolygonPoint(25.0,43.0).toString());
        pointTracker.index(new PolygonPoint(25.0,43.0).toString(), 42);
        pointTracker.getIndex(new PolygonPoint(25.0,45.0).toString());
        pointTracker.index(new PolygonPoint(25.0,45.0).toString(), 42);
        pointTracker.getIndex(new PolygonPoint(24.0,45.0).toString());
        pointTracker.index(new PolygonPoint(24.0,45.0).toString(), 42);
        pointTracker.getIndex(new PolygonPoint(24.0,46.0).toString());
        pointTracker.index(new PolygonPoint(24.0,46.0).toString(), 42);
        pointTracker.getIndex(new PolygonPoint(22.0,46.0).toString());
        pointTracker.index(new PolygonPoint(22.0,46.0).toString(), 42);
        pointTracker.getIndex(new PolygonPoint(22.0,47.0).toString());
        pointTracker.index(new PolygonPoint(22.0,47.0).toString(), 42);
        pointTracker.getIndex(new PolygonPoint(23.0,47.0).toString());
        pointTracker.index(new PolygonPoint(23.0,47.0).toString(), 42);
        pointTracker.getIndex(new PolygonPoint(23.0,53.0).toString());
        pointTracker.index(new PolygonPoint(23.0,53.0).toString(), 42);
        pointTracker.getIndex(new PolygonPoint(21.0,53.0).toString());
        pointTracker.index(new PolygonPoint(21.0,53.0).toString(), 42);
        pointTracker.getIndex(new PolygonPoint(21.0,52.0).toString());
        pointTracker.index(new PolygonPoint(21.0,52.0).toString(), 42);
        pointTracker.getIndex(new PolygonPoint(22.0,52.0).toString());
        pointTracker.index(new PolygonPoint(22.0,52.0).toString(), 42);
        pointTracker.getIndex(new PolygonPoint(22.0,50.0).toString());
        pointTracker.index(new PolygonPoint(22.0,50.0).toString(), 42);
        pointTracker.getIndex(new PolygonPoint(21.0,50.0).toString());
        pointTracker.index(new PolygonPoint(21.0,50.0).toString(), 42);
        pointTracker.getIndex(new PolygonPoint(21.0,49.0).toString());
        pointTracker.index(new PolygonPoint(21.0,49.0).toString(), 42);
        pointTracker.getIndex(new PolygonPoint(22.0,49.0).toString());
        pointTracker.index(new PolygonPoint(22.0,49.0).toString(), 42);
        pointTracker.getIndex(new PolygonPoint(22.0,48.0).toString());
        pointTracker.index(new PolygonPoint(22.0,48.0).toString(), 42);
        pointTracker.getIndex(new PolygonPoint(21.0,48.0).toString());
        pointTracker.index(new PolygonPoint(21.0,48.0).toString(), 42);
        pointTracker.getIndex(new PolygonPoint(21.0,47.0).toString());
        pointTracker.index(new PolygonPoint(21.0,47.0).toString(), 42);
        pointTracker.getIndex(new PolygonPoint(20.0,47.0).toString());
        pointTracker.index(new PolygonPoint(20.0,47.0).toString(), 42);
        pointTracker.getIndex(new PolygonPoint(20.0,49.0).toString());
        pointTracker.index(new PolygonPoint(20.0,49.0).toString(), 42);
        pointTracker.getIndex(new PolygonPoint(19.0,49.0).toString());
        pointTracker.index(new PolygonPoint(19.0,49.0).toString(), 42);
        pointTracker.getIndex(new PolygonPoint(19.0,45.0).toString());
        pointTracker.index(new PolygonPoint(19.0,45.0).toString(), 42);
        pointTracker.getIndex(new PolygonPoint(18.0,45.0).toString());
        pointTracker.index(new PolygonPoint(18.0,45.0).toString(), 42);
        pointTracker.getIndex(new PolygonPoint(18.0,44.0).toString());
        pointTracker.index(new PolygonPoint(18.0,44.0).toString(), 42);
        pointTracker.getIndex(new PolygonPoint(20.0,44.0).toString());
        pointTracker.index(new PolygonPoint(20.0,44.0).toString(), 42);
        pointTracker.getIndex(new PolygonPoint(20.0,41.0).toString());
        pointTracker.index(new PolygonPoint(20.0,41.0).toString(), 42);
        pointTracker.getIndex(new PolygonPoint(22.0,41.0).toString());
        pointTracker.index(new PolygonPoint(22.0,41.0).toString(), 42);
        pointTracker.getIndex(new PolygonPoint(22.0,42.0).toString());
        pointTracker.index(new PolygonPoint(22.0,42.0).toString(), 42);
        pointTracker.getIndex(new PolygonPoint(21.0,42.0).toString());
        pointTracker.index(new PolygonPoint(21.0,42.0).toString(), 42);
        pointTracker.getIndex(new PolygonPoint(21.0,43.0).toString());
        pointTracker.index(new PolygonPoint(21.0,43.0).toString(), 42);
        pointTracker.getIndex(new PolygonPoint(22.0,43.0).toString());
        pointTracker.index(new PolygonPoint(22.0,43.0).toString(), 42);
        pointTracker.getIndex(new PolygonPoint(22.0,44.0).toString());
        pointTracker.index(new PolygonPoint(22.0,44.0).toString(), 42);
        pointTracker.getIndex(new PolygonPoint(23.0,44.0).toString());
        pointTracker.index(new PolygonPoint(23.0,44.0).toString(), 42);
        pointTracker.getIndex(new PolygonPoint(23.0,43.0).toString());
        pointTracker.index(new PolygonPoint(23.0,43.0).toString(), 42);
        pointTracker.getIndex(new PolygonPoint(33.0,60.0).toString());
        pointTracker.index(new PolygonPoint(33.0,60.0).toString(), 43);
        pointTracker.getIndex(new PolygonPoint(33.0,61.0).toString());
        pointTracker.index(new PolygonPoint(33.0,61.0).toString(), 43);
        pointTracker.getIndex(new PolygonPoint(32.0,61.0).toString());
        pointTracker.index(new PolygonPoint(32.0,61.0).toString(), 43);
        pointTracker.getIndex(new PolygonPoint(32.0,60.0).toString());
        pointTracker.changeIndex(9, 43);
        pointTracker.getIndex(new PolygonPoint(8.0,8.0).toString());
        pointTracker.index(new PolygonPoint(8.0,8.0).toString(), 44);
        pointTracker.getIndex(new PolygonPoint(9.0,8.0).toString());
        pointTracker.index(new PolygonPoint(9.0,8.0).toString(), 44);
        pointTracker.getIndex(new PolygonPoint(9.0,9.0).toString());
        pointTracker.index(new PolygonPoint(9.0,9.0).toString(), 44);
        pointTracker.getIndex(new PolygonPoint(8.0,9.0).toString());
        pointTracker.changeIndex(0, 44);
        pointTracker.getIndex(new PolygonPoint(9.0,14.0).toString());
        pointTracker.index(new PolygonPoint(9.0,14.0).toString(), 45);
        pointTracker.getIndex(new PolygonPoint(9.0,13.0).toString());
        pointTracker.index(new PolygonPoint(9.0,13.0).toString(), 45);
        pointTracker.getIndex(new PolygonPoint(10.0,13.0).toString());
        pointTracker.index(new PolygonPoint(10.0,13.0).toString(), 45);
        pointTracker.getIndex(new PolygonPoint(10.0,14.0).toString());
        pointTracker.index(new PolygonPoint(10.0,14.0).toString(), 45);
        pointTracker.getIndex(new PolygonPoint(16.0,56.0).toString());
        pointTracker.index(new PolygonPoint(16.0,56.0).toString(), 46);
        pointTracker.getIndex(new PolygonPoint(17.0,56.0).toString());
        pointTracker.index(new PolygonPoint(17.0,56.0).toString(), 46);
        pointTracker.getIndex(new PolygonPoint(17.0,57.0).toString());
        pointTracker.index(new PolygonPoint(17.0,57.0).toString(), 46);
        pointTracker.getIndex(new PolygonPoint(16.0,57.0).toString());
        pointTracker.changeIndex(1, 46);
        pointTracker.getIndex(new PolygonPoint(37.0,24.0).toString());
        pointTracker.index(new PolygonPoint(37.0,24.0).toString(), 47);
        pointTracker.getIndex(new PolygonPoint(36.0,24.0).toString());
        pointTracker.getIndex(new PolygonPoint(36.0,22.0).toString());
        pointTracker.index(new PolygonPoint(36.0,22.0).toString(), 47);
        pointTracker.getIndex(new PolygonPoint(37.0,22.0).toString());
        pointTracker.index(new PolygonPoint(37.0,22.0).toString(), 47);
        pointTracker.changeIndex(10, 47);
        pointTracker.getIndex(new PolygonPoint(9.0,37.0).toString());
        pointTracker.index(new PolygonPoint(9.0,37.0).toString(), 48);
        pointTracker.getIndex(new PolygonPoint(9.0,38.0).toString());
        pointTracker.index(new PolygonPoint(9.0,38.0).toString(), 48);
        pointTracker.getIndex(new PolygonPoint(8.0,38.0).toString());
        pointTracker.index(new PolygonPoint(8.0,38.0).toString(), 48);
        pointTracker.getIndex(new PolygonPoint(8.0,37.0).toString());
        pointTracker.index(new PolygonPoint(8.0,37.0).toString(), 48);
        pointTracker.getIndex(new PolygonPoint(20.0,61.0).toString());
        pointTracker.index(new PolygonPoint(20.0,61.0).toString(), 49);
        pointTracker.getIndex(new PolygonPoint(20.0,62.0).toString());
        pointTracker.index(new PolygonPoint(20.0,62.0).toString(), 49);
        pointTracker.getIndex(new PolygonPoint(19.0,62.0).toString());
        pointTracker.index(new PolygonPoint(19.0,62.0).toString(), 49);
        pointTracker.getIndex(new PolygonPoint(19.0,61.0).toString());
        pointTracker.index(new PolygonPoint(19.0,61.0).toString(), 49);
        pointTracker.getIndex(new PolygonPoint(33.0,61.0).toString());
        pointTracker.getIndex(new PolygonPoint(34.0,61.0).toString());
        pointTracker.index(new PolygonPoint(34.0,61.0).toString(), 50);
        pointTracker.getIndex(new PolygonPoint(34.0,64.0).toString());
        pointTracker.index(new PolygonPoint(34.0,64.0).toString(), 50);
        pointTracker.getIndex(new PolygonPoint(33.0,64.0).toString());
        pointTracker.index(new PolygonPoint(33.0,64.0).toString(), 50);
        pointTracker.changeIndex(9, 50);
        pointTracker.getIndex(new PolygonPoint(23.0,63.0).toString());
        pointTracker.index(new PolygonPoint(23.0,63.0).toString(), 51);
        pointTracker.getIndex(new PolygonPoint(23.0,64.0).toString());
        pointTracker.index(new PolygonPoint(23.0,64.0).toString(), 51);
        pointTracker.getIndex(new PolygonPoint(21.0,64.0).toString());
        pointTracker.index(new PolygonPoint(21.0,64.0).toString(), 51);
        pointTracker.getIndex(new PolygonPoint(21.0,60.0).toString());
        pointTracker.index(new PolygonPoint(21.0,60.0).toString(), 51);
        pointTracker.getIndex(new PolygonPoint(22.0,60.0).toString());
        pointTracker.index(new PolygonPoint(22.0,60.0).toString(), 51);
        pointTracker.getIndex(new PolygonPoint(22.0,63.0).toString());
        pointTracker.index(new PolygonPoint(22.0,63.0).toString(), 51);
        pointTracker.getIndex(new PolygonPoint(18.0,61.0).toString());
        pointTracker.getIndex(new PolygonPoint(18.0,60.0).toString());
        pointTracker.index(new PolygonPoint(18.0,60.0).toString(), 52);
        pointTracker.getIndex(new PolygonPoint(17.0,60.0).toString());
        pointTracker.index(new PolygonPoint(17.0,60.0).toString(), 52);
        pointTracker.getIndex(new PolygonPoint(17.0,59.0).toString());
        pointTracker.getIndex(new PolygonPoint(19.0,59.0).toString());
        pointTracker.index(new PolygonPoint(19.0,59.0).toString(), 52);
        pointTracker.getIndex(new PolygonPoint(19.0,61.0).toString());
        pointTracker.changeIndex(0, 52);
        pointTracker.changeIndex(0, 1);
        pointTracker.changeIndex(0, 49);
        pointTracker.getIndex(new PolygonPoint(37.0,56.0).toString());
        pointTracker.getIndex(new PolygonPoint(38.0,56.0).toString());
        pointTracker.index(new PolygonPoint(38.0,56.0).toString(), 53);
        pointTracker.getIndex(new PolygonPoint(38.0,58.0).toString());
        pointTracker.index(new PolygonPoint(38.0,58.0).toString(), 53);
        pointTracker.getIndex(new PolygonPoint(37.0,58.0).toString());
        pointTracker.index(new PolygonPoint(37.0,58.0).toString(), 53);
        pointTracker.changeIndex(0, 53);
        pointTracker.getIndex(new PolygonPoint(37.0,40.0).toString());
        pointTracker.index(new PolygonPoint(37.0,40.0).toString(), 54);
        pointTracker.getIndex(new PolygonPoint(37.0,39.0).toString());
        pointTracker.index(new PolygonPoint(37.0,39.0).toString(), 54);
        pointTracker.getIndex(new PolygonPoint(39.0,39.0).toString());
        pointTracker.index(new PolygonPoint(39.0,39.0).toString(), 54);
        pointTracker.getIndex(new PolygonPoint(39.0,40.0).toString());
        pointTracker.index(new PolygonPoint(39.0,40.0).toString(), 54);
        pointTracker.getIndex(new PolygonPoint(13.0,43.0).toString());
        pointTracker.getIndex(new PolygonPoint(11.0,43.0).toString());
        pointTracker.index(new PolygonPoint(11.0,43.0).toString(), 55);
        pointTracker.getIndex(new PolygonPoint(11.0,42.0).toString());
        pointTracker.index(new PolygonPoint(11.0,42.0).toString(), 55);
        pointTracker.getIndex(new PolygonPoint(12.0,42.0).toString());
        pointTracker.index(new PolygonPoint(12.0,42.0).toString(), 55);
        pointTracker.getIndex(new PolygonPoint(12.0,40.0).toString());
        pointTracker.index(new PolygonPoint(12.0,40.0).toString(), 55);
        pointTracker.getIndex(new PolygonPoint(13.0,40.0).toString());
        pointTracker.index(new PolygonPoint(13.0,40.0).toString(), 55);
        pointTracker.getIndex(new PolygonPoint(13.0,41.0).toString());
        pointTracker.index(new PolygonPoint(13.0,41.0).toString(), 55);
        pointTracker.getIndex(new PolygonPoint(14.0,41.0).toString());
        pointTracker.index(new PolygonPoint(14.0,41.0).toString(), 55);
        pointTracker.getIndex(new PolygonPoint(14.0,42.0).toString());
        pointTracker.index(new PolygonPoint(14.0,42.0).toString(), 55);
        pointTracker.getIndex(new PolygonPoint(13.0,42.0).toString());
        pointTracker.index(new PolygonPoint(13.0,42.0).toString(), 55);
        pointTracker.changeIndex(13, 55);
        pointTracker.getIndex(new PolygonPoint(20.0,31.0).toString());
        pointTracker.index(new PolygonPoint(20.0,31.0).toString(), 56);
        pointTracker.getIndex(new PolygonPoint(21.0,31.0).toString());
        pointTracker.getIndex(new PolygonPoint(21.0,32.0).toString());
        pointTracker.index(new PolygonPoint(21.0,32.0).toString(), 56);
        pointTracker.getIndex(new PolygonPoint(20.0,32.0).toString());
        pointTracker.index(new PolygonPoint(20.0,32.0).toString(), 56);
        pointTracker.changeIndex(11, 56);
        pointTracker.getIndex(new PolygonPoint(6.0,31.0).toString());
        pointTracker.index(new PolygonPoint(6.0,31.0).toString(), 57);
        pointTracker.getIndex(new PolygonPoint(6.0,33.0).toString());
        pointTracker.index(new PolygonPoint(6.0,33.0).toString(), 57);
        pointTracker.getIndex(new PolygonPoint(5.0,33.0).toString());
        pointTracker.index(new PolygonPoint(5.0,33.0).toString(), 57);
        pointTracker.getIndex(new PolygonPoint(5.0,31.0).toString());
        pointTracker.index(new PolygonPoint(5.0,31.0).toString(), 57);
        pointTracker.getIndex(new PolygonPoint(7.0,53.0).toString());
        pointTracker.index(new PolygonPoint(7.0,53.0).toString(), 58);
        pointTracker.getIndex(new PolygonPoint(6.0,53.0).toString());
        pointTracker.index(new PolygonPoint(6.0,53.0).toString(), 58);
        pointTracker.getIndex(new PolygonPoint(6.0,51.0).toString());
        pointTracker.index(new PolygonPoint(6.0,51.0).toString(), 58);
        pointTracker.getIndex(new PolygonPoint(5.0,51.0).toString());
        pointTracker.index(new PolygonPoint(5.0,51.0).toString(), 58);
        pointTracker.getIndex(new PolygonPoint(5.0,50.0).toString());
        pointTracker.index(new PolygonPoint(5.0,50.0).toString(), 58);
        pointTracker.getIndex(new PolygonPoint(4.0,50.0).toString());
        pointTracker.index(new PolygonPoint(4.0,50.0).toString(), 58);
        pointTracker.getIndex(new PolygonPoint(4.0,48.0).toString());
        pointTracker.index(new PolygonPoint(4.0,48.0).toString(), 58);
        pointTracker.getIndex(new PolygonPoint(5.0,48.0).toString());
        pointTracker.index(new PolygonPoint(5.0,48.0).toString(), 58);
        pointTracker.getIndex(new PolygonPoint(5.0,49.0).toString());
        pointTracker.index(new PolygonPoint(5.0,49.0).toString(), 58);
        pointTracker.getIndex(new PolygonPoint(6.0,49.0).toString());
        pointTracker.index(new PolygonPoint(6.0,49.0).toString(), 58);
        pointTracker.getIndex(new PolygonPoint(6.0,48.0).toString());
        pointTracker.index(new PolygonPoint(6.0,48.0).toString(), 58);
        pointTracker.getIndex(new PolygonPoint(8.0,48.0).toString());
        pointTracker.index(new PolygonPoint(8.0,48.0).toString(), 58);
        pointTracker.getIndex(new PolygonPoint(8.0,50.0).toString());
        pointTracker.index(new PolygonPoint(8.0,50.0).toString(), 58);
        pointTracker.getIndex(new PolygonPoint(9.0,50.0).toString());
        pointTracker.index(new PolygonPoint(9.0,50.0).toString(), 58);
        pointTracker.getIndex(new PolygonPoint(9.0,51.0).toString());
        pointTracker.index(new PolygonPoint(9.0,51.0).toString(), 58);
        pointTracker.getIndex(new PolygonPoint(7.0,51.0).toString());
        pointTracker.index(new PolygonPoint(7.0,51.0).toString(), 58);
        pointTracker.getIndex(new PolygonPoint(7.0,52.0).toString());
        pointTracker.index(new PolygonPoint(7.0,52.0).toString(), 58);
        pointTracker.getIndex(new PolygonPoint(8.0,52.0).toString());
        pointTracker.index(new PolygonPoint(8.0,52.0).toString(), 58);
        pointTracker.getIndex(new PolygonPoint(8.0,54.0).toString());
        pointTracker.index(new PolygonPoint(8.0,54.0).toString(), 58);
        pointTracker.getIndex(new PolygonPoint(7.0,54.0).toString());
        pointTracker.index(new PolygonPoint(7.0,54.0).toString(), 58);
        pointTracker.getIndex(new PolygonPoint(23.0,14.0).toString());
        pointTracker.getIndex(new PolygonPoint(23.0,15.0).toString());
        pointTracker.index(new PolygonPoint(23.0,15.0).toString(), 59);
        pointTracker.getIndex(new PolygonPoint(22.0,15.0).toString());
        pointTracker.index(new PolygonPoint(22.0,15.0).toString(), 59);
        pointTracker.getIndex(new PolygonPoint(22.0,14.0).toString());
        pointTracker.index(new PolygonPoint(22.0,14.0).toString(), 59);
        pointTracker.changeIndex(0, 59);
        pointTracker.getIndex(new PolygonPoint(12.0,31.0).toString());
        pointTracker.index(new PolygonPoint(12.0,31.0).toString(), 60);
        pointTracker.getIndex(new PolygonPoint(12.0,33.0).toString());
        pointTracker.index(new PolygonPoint(12.0,33.0).toString(), 60);
        pointTracker.getIndex(new PolygonPoint(11.0,33.0).toString());
        pointTracker.index(new PolygonPoint(11.0,33.0).toString(), 60);
        pointTracker.getIndex(new PolygonPoint(11.0,31.0).toString());
        pointTracker.index(new PolygonPoint(11.0,31.0).toString(), 60);
        pointTracker.getIndex(new PolygonPoint(22.0,58.0).toString());
        pointTracker.index(new PolygonPoint(22.0,58.0).toString(), 61);
        pointTracker.getIndex(new PolygonPoint(23.0,58.0).toString());
        pointTracker.index(new PolygonPoint(23.0,58.0).toString(), 61);
        pointTracker.getIndex(new PolygonPoint(23.0,55.0).toString());
        pointTracker.index(new PolygonPoint(23.0,55.0).toString(), 61);
        pointTracker.getIndex(new PolygonPoint(24.0,55.0).toString());
        pointTracker.index(new PolygonPoint(24.0,55.0).toString(), 61);
        pointTracker.getIndex(new PolygonPoint(24.0,60.0).toString());
        pointTracker.index(new PolygonPoint(24.0,60.0).toString(), 61);
        pointTracker.getIndex(new PolygonPoint(22.0,60.0).toString());
        pointTracker.changeIndex(51, 61);
        pointTracker.getIndex(new PolygonPoint(23.0,11.0).toString());
        pointTracker.index(new PolygonPoint(23.0,11.0).toString(), 62);
        pointTracker.getIndex(new PolygonPoint(22.0,11.0).toString());
        pointTracker.index(new PolygonPoint(22.0,11.0).toString(), 62);
        pointTracker.getIndex(new PolygonPoint(22.0,10.0).toString());
        pointTracker.index(new PolygonPoint(22.0,10.0).toString(), 62);
        pointTracker.getIndex(new PolygonPoint(21.0,10.0).toString());
        pointTracker.index(new PolygonPoint(21.0,10.0).toString(), 62);
        pointTracker.getIndex(new PolygonPoint(21.0,9.0).toString());
        pointTracker.index(new PolygonPoint(21.0,9.0).toString(), 62);
        pointTracker.getIndex(new PolygonPoint(22.0,9.0).toString());
        pointTracker.index(new PolygonPoint(22.0,9.0).toString(), 62);
        pointTracker.getIndex(new PolygonPoint(22.0,8.0).toString());
        pointTracker.index(new PolygonPoint(22.0,8.0).toString(), 62);
        pointTracker.getIndex(new PolygonPoint(23.0,8.0).toString());
        pointTracker.index(new PolygonPoint(23.0,8.0).toString(), 62);
        pointTracker.getIndex(new PolygonPoint(18.0,65.0).toString());
        pointTracker.index(new PolygonPoint(18.0,65.0).toString(), 63);
        pointTracker.getIndex(new PolygonPoint(18.0,64.0).toString());
        pointTracker.index(new PolygonPoint(18.0,64.0).toString(), 63);
        pointTracker.getIndex(new PolygonPoint(20.0,64.0).toString());
        pointTracker.index(new PolygonPoint(20.0,64.0).toString(), 63);
        pointTracker.getIndex(new PolygonPoint(20.0,65.0).toString());
        pointTracker.index(new PolygonPoint(20.0,65.0).toString(), 63);
        pointTracker.getIndex(new PolygonPoint(22.0,57.0).toString());
        pointTracker.index(new PolygonPoint(22.0,57.0).toString(), 64);
        pointTracker.getIndex(new PolygonPoint(22.0,58.0).toString());
        pointTracker.getIndex(new PolygonPoint(21.0,58.0).toString());
        pointTracker.index(new PolygonPoint(21.0,58.0).toString(), 64);
        pointTracker.getIndex(new PolygonPoint(21.0,57.0).toString());
        pointTracker.changeIndex(51, 64);
        pointTracker.changeIndex(24, 51);
        pointTracker.getIndex(new PolygonPoint(19.0,10.0).toString());
        pointTracker.index(new PolygonPoint(19.0,10.0).toString(), 65);
        pointTracker.getIndex(new PolygonPoint(19.0,7.0).toString());
        pointTracker.getIndex(new PolygonPoint(20.0,7.0).toString());
        pointTracker.index(new PolygonPoint(20.0,7.0).toString(), 65);
        pointTracker.getIndex(new PolygonPoint(20.0,10.0).toString());
        pointTracker.index(new PolygonPoint(20.0,10.0).toString(), 65);
        pointTracker.changeIndex(0, 65);
        pointTracker.getIndex(new PolygonPoint(24.0,46.0).toString());
        pointTracker.getIndex(new PolygonPoint(25.0,46.0).toString());
        pointTracker.index(new PolygonPoint(25.0,46.0).toString(), 66);
        pointTracker.getIndex(new PolygonPoint(25.0,47.0).toString());
        pointTracker.getIndex(new PolygonPoint(24.0,47.0).toString());
        pointTracker.index(new PolygonPoint(24.0,47.0).toString(), 66);
        pointTracker.changeIndex(42, 66);
        assert pointTracker.getIndex(new PolygonPoint(21.0,42.0).toString()) == 42;
        pointTracker.changeIndex(10, 42);
        assert pointTracker.getIndex(new PolygonPoint(21.0,42.0).toString()) == 10;
        pointTracker.getIndex(new PolygonPoint(38.0,68.0).toString());
        pointTracker.getIndex(new PolygonPoint(35.0,68.0).toString());
        pointTracker.index(new PolygonPoint(35.0,68.0).toString(), 67);
        pointTracker.getIndex(new PolygonPoint(35.0,66.0).toString());
        pointTracker.index(new PolygonPoint(35.0,66.0).toString(), 67);
        pointTracker.getIndex(new PolygonPoint(36.0,66.0).toString());
        pointTracker.index(new PolygonPoint(36.0,66.0).toString(), 67);
        pointTracker.getIndex(new PolygonPoint(36.0,67.0).toString());
        pointTracker.index(new PolygonPoint(36.0,67.0).toString(), 67);
        pointTracker.getIndex(new PolygonPoint(38.0,67.0).toString());
        pointTracker.index(new PolygonPoint(38.0,67.0).toString(), 67);
        pointTracker.changeIndex(0, 67);
        pointTracker.getIndex(new PolygonPoint(4.0,38.0).toString());
        pointTracker.index(new PolygonPoint(4.0,38.0).toString(), 68);
        pointTracker.getIndex(new PolygonPoint(6.0,38.0).toString());
        pointTracker.index(new PolygonPoint(6.0,38.0).toString(), 68);
        pointTracker.getIndex(new PolygonPoint(6.0,37.0).toString());
        pointTracker.index(new PolygonPoint(6.0,37.0).toString(), 68);
        pointTracker.getIndex(new PolygonPoint(7.0,37.0).toString());
        pointTracker.index(new PolygonPoint(7.0,37.0).toString(), 68);
        pointTracker.getIndex(new PolygonPoint(7.0,38.0).toString());
        pointTracker.index(new PolygonPoint(7.0,38.0).toString(), 68);
        pointTracker.getIndex(new PolygonPoint(8.0,38.0).toString());
        pointTracker.getIndex(new PolygonPoint(8.0,40.0).toString());
        pointTracker.index(new PolygonPoint(8.0,40.0).toString(), 68);
        pointTracker.getIndex(new PolygonPoint(7.0,40.0).toString());
        pointTracker.index(new PolygonPoint(7.0,40.0).toString(), 68);
        pointTracker.getIndex(new PolygonPoint(7.0,41.0).toString());
        pointTracker.getIndex(new PolygonPoint(6.0,41.0).toString());
        pointTracker.index(new PolygonPoint(6.0,41.0).toString(), 68);
        pointTracker.getIndex(new PolygonPoint(6.0,39.0).toString());
        pointTracker.index(new PolygonPoint(6.0,39.0).toString(), 68);
        pointTracker.getIndex(new PolygonPoint(4.0,39.0).toString());
        pointTracker.index(new PolygonPoint(4.0,39.0).toString(), 68);
        pointTracker.changeIndex(48, 68);
        pointTracker.changeIndex(31, 48);
        pointTracker.getIndex(new PolygonPoint(4.0,26.0).toString());
        pointTracker.index(new PolygonPoint(4.0,26.0).toString(), 69);
        pointTracker.getIndex(new PolygonPoint(2.0,26.0).toString());
        pointTracker.index(new PolygonPoint(2.0,26.0).toString(), 69);
        pointTracker.getIndex(new PolygonPoint(2.0,25.0).toString());
        pointTracker.getIndex(new PolygonPoint(3.0,25.0).toString());
        pointTracker.index(new PolygonPoint(3.0,25.0).toString(), 69);
        pointTracker.getIndex(new PolygonPoint(3.0,23.0).toString());
        pointTracker.index(new PolygonPoint(3.0,23.0).toString(), 69);
        pointTracker.getIndex(new PolygonPoint(4.0,23.0).toString());
        pointTracker.index(new PolygonPoint(4.0,23.0).toString(), 69);
        pointTracker.changeIndex(0, 69);
        pointTracker.getIndex(new PolygonPoint(11.0,49.0).toString());
        pointTracker.index(new PolygonPoint(11.0,49.0).toString(), 70);
        pointTracker.getIndex(new PolygonPoint(11.0,50.0).toString());
        pointTracker.getIndex(new PolygonPoint(10.0,50.0).toString());
        pointTracker.index(new PolygonPoint(10.0,50.0).toString(), 70);
        pointTracker.getIndex(new PolygonPoint(10.0,49.0).toString());
        pointTracker.index(new PolygonPoint(10.0,49.0).toString(), 70);
        pointTracker.changeIndex(4, 70);
        pointTracker.getIndex(new PolygonPoint(35.0,49.0).toString());
        pointTracker.getIndex(new PolygonPoint(36.0,49.0).toString());
        pointTracker.index(new PolygonPoint(36.0,49.0).toString(), 71);
        pointTracker.getIndex(new PolygonPoint(36.0,50.0).toString());
        pointTracker.index(new PolygonPoint(36.0,50.0).toString(), 71);
        pointTracker.getIndex(new PolygonPoint(35.0,50.0).toString());
        pointTracker.index(new PolygonPoint(35.0,50.0).toString(), 71);
        pointTracker.changeIndex(10, 71);
        pointTracker.getIndex(new PolygonPoint(24.0,23.0).toString());
        pointTracker.index(new PolygonPoint(24.0,23.0).toString(), 72);
        pointTracker.getIndex(new PolygonPoint(24.0,21.0).toString());
        pointTracker.index(new PolygonPoint(24.0,21.0).toString(), 72);
        pointTracker.getIndex(new PolygonPoint(25.0,21.0).toString());
        pointTracker.index(new PolygonPoint(25.0,21.0).toString(), 72);
        pointTracker.getIndex(new PolygonPoint(25.0,23.0).toString());
        pointTracker.index(new PolygonPoint(25.0,23.0).toString(), 72);
        pointTracker.getIndex(new PolygonPoint(17.0,39.0).toString());
        pointTracker.index(new PolygonPoint(17.0,39.0).toString(), 73);
        pointTracker.getIndex(new PolygonPoint(17.0,40.0).toString());
        pointTracker.index(new PolygonPoint(17.0,40.0).toString(), 73);
        pointTracker.getIndex(new PolygonPoint(20.0,40.0).toString());
        pointTracker.index(new PolygonPoint(20.0,40.0).toString(), 73);
        pointTracker.getIndex(new PolygonPoint(20.0,41.0).toString());
        pointTracker.getIndex(new PolygonPoint(18.0,41.0).toString());
        pointTracker.index(new PolygonPoint(18.0,41.0).toString(), 73);
        pointTracker.getIndex(new PolygonPoint(18.0,42.0).toString());
        pointTracker.index(new PolygonPoint(18.0,42.0).toString(), 73);
        pointTracker.getIndex(new PolygonPoint(17.0,42.0).toString());
        pointTracker.index(new PolygonPoint(17.0,42.0).toString(), 73);
        pointTracker.getIndex(new PolygonPoint(17.0,41.0).toString());
        pointTracker.index(new PolygonPoint(17.0,41.0).toString(), 73);
        pointTracker.getIndex(new PolygonPoint(16.0,41.0).toString());
        pointTracker.index(new PolygonPoint(16.0,41.0).toString(), 73);
        pointTracker.getIndex(new PolygonPoint(16.0,40.0).toString());
        pointTracker.index(new PolygonPoint(16.0,40.0).toString(), 73);
        pointTracker.getIndex(new PolygonPoint(15.0,40.0).toString());
        pointTracker.index(new PolygonPoint(15.0,40.0).toString(), 73);
        pointTracker.getIndex(new PolygonPoint(15.0,39.0).toString());
        pointTracker.index(new PolygonPoint(15.0,39.0).toString(), 73);
        pointTracker.getIndex(new PolygonPoint(13.0,39.0).toString());
        pointTracker.index(new PolygonPoint(13.0,39.0).toString(), 73);
        pointTracker.getIndex(new PolygonPoint(13.0,38.0).toString());
        pointTracker.index(new PolygonPoint(13.0,38.0).toString(), 73);
        pointTracker.getIndex(new PolygonPoint(18.0,38.0).toString());
        pointTracker.index(new PolygonPoint(18.0,38.0).toString(), 73);
        pointTracker.getIndex(new PolygonPoint(18.0,39.0).toString());
        pointTracker.index(new PolygonPoint(18.0,39.0).toString(), 73);
        pointTracker.changeIndex(42, 73);

    }

    // test list merging (we need this to process
    // the output of the slow external library that converts voxel data to polygon
    // and make it ok for input to poly2tri)
    @Test
    public void testListMerging() throws Exception {
        ArrayList<PolygonPoint> listA = new ArrayList<PolygonPoint>();
        ArrayList<PolygonPoint> listB = new ArrayList<PolygonPoint>();

        Random rand = new Random();

        // list of known points
        HashSet<String> seen = new HashSet<String>();

        int count = 5;

        // populate lists
        for (int i = 0; i < count; i++) {
            PolygonPoint point = new PolygonPoint(rand.nextInt(100), rand.nextInt(100));
            String key = point.getX() + ", " + point.getY();
            if (!seen.contains(key)) {
                listA.add(point);
                seen.add(key);
            }
            point = new PolygonPoint(rand.nextInt(100), rand.nextInt(100));
            key = point.getX() + ", " + point.getY();
            if (!seen.contains(key)) {
                listB.add(point);
                seen.add(key);
            }
        }

        PolygonPoint point;
        if (listA.isEmpty()) {
            point = listA.get(0);
            listB.add(point);
        } else {
            point = listB.get(0);
            listA.add(point);
        }

        System.out.println(point);

        Collections.shuffle(listA);
        Collections.shuffle(listB);

        for (PolygonPoint p : listA) {
            System.out.print(p + ", ");
        }
        System.out.println();

        for (PolygonPoint p : listB) {
            System.out.print(p + ", ");
        }
        System.out.println();

        ArrayList<PolygonPoint> result = Grid2TriPolySlow.mergeInterp(listA, listB, point);

        for (PolygonPoint p : result) {
            System.out.print(p + ", ");
        }
        System.out.println();

    }

    // do a test case for mono triangulation (this prints the found triangles)
    // -> very helpful for debuggin triangulation algorithms
    @Test
    public void testMonoTriangulationCase() throws IOException {

        BufferedImage imgIn = ImageIO.read(new File("test.png"));
        boolean[][] data = new boolean[imgIn.getWidth()][imgIn.getHeight()];
        for (int x = 0; x < imgIn.getWidth(); x++) {
            for (int y = 0; y < imgIn.getHeight(); y++) {
                //System.out.println(img.getRGB(x,y));
                data[x][y] = imgIn.getRGB(x,y) != -1;
            }
        }

        // ------------------
//        List<DelaunayTriangle> tris = Grid2TriMono.triangulate(data, true);
//        List<DelaunayTriangle> tris = Grid2TriNaiveGreedy.triangulate(data);

        // ------------------
        // test original algorithm
        TiledImage src = ImageUtils.createConstantImage(imgIn.getWidth(), imgIn.getHeight(), 0);

        // fill with data
        for (int x = 0; x < imgIn.getWidth(); x++) {
            for (int y = 0; y < imgIn.getHeight(); y++) {
                src.setSample(x, y, 0, data[x][y] ? 1 : 0);
            }
        }

        // create polygons
        List<DelaunayTriangle> tris = Grid2TriPolySlow.triangulate(Grid2TriPolySlow.doVectorize(src), false);
        // ---------------


        for (DelaunayTriangle tri : tris) {
            System.out.println(tri.points[0] + " " + tri.points[1] + " " + tri.points[2]);
        }

        int zoom = 40;

        BufferedImage img = new BufferedImage(zoom*imgIn.getWidth(), zoom*imgIn.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D gr = (Graphics2D) img.getGraphics();
        gr.setColor(Color.WHITE);
        gr.fillRect(0, 0, img.getWidth(), img.getHeight());
        gr.setFont(gr.getFont().deriveFont(Font.BOLD,15f));

        gr.drawImage(imgIn,0,1,img.getWidth(),img.getHeight(), null);

        gr.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        gr.setRenderingHint(
                RenderingHints.KEY_FRACTIONALMETRICS,
                RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        gr.setRenderingHint(
                RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);

        int i = 0;
        for (DelaunayTriangle tri : tris) {
            gr.setColor(Color.GRAY);
            gr.drawLine((int)Math.round(tri.points[0].getX()*zoom), (int)Math.round(tri.points[0].getY()*zoom), (int)Math.round(tri.points[1].getX()*zoom), (int)Math.round(tri.points[1].getY()*zoom));
            gr.drawLine((int)Math.round(tri.points[1].getX()*zoom), (int)Math.round(tri.points[1].getY()*zoom), (int)Math.round(tri.points[2].getX()*zoom), (int)Math.round(tri.points[2].getY()*zoom));
            gr.drawLine((int)Math.round(tri.points[2].getX()*zoom), (int)Math.round(tri.points[2].getY()*zoom), (int)Math.round(tri.points[0].getX()*zoom), (int)Math.round(tri.points[0].getY()*zoom));
            gr.setColor(Color.BLACK);
            gr.drawString(String.valueOf(++i), (int)(tri.centroid().getX() * zoom) - 5, (int)(tri.centroid().getY() * zoom) + 5);
        }

        gr.setFont(gr.getFont().deriveFont(Font.PLAIN,15f));
        for (DelaunayTriangle tri : tris) {
            gr.setColor(Color.RED);
            gr.drawRect((int) Math.round(tri.points[0].getX() * zoom) - 2, (int) Math.round(tri.points[0].getY() * zoom) - 2, 4, 4);
            gr.drawRect((int) Math.round(tri.points[1].getX() * zoom) - 2, (int) Math.round(tri.points[1].getY() * zoom) - 2, 4, 4);
            gr.drawRect((int) Math.round(tri.points[2].getX() * zoom) - 2, (int) Math.round(tri.points[2].getY() * zoom) - 2, 4, 4);
//            gr.setColor(Color.GRAY);
//            gr.drawString("(" + (int) Math.round(tri.points[0].getX()) + "," + (int) Math.round(tri.points[0].getY()) + ")", (int) Math.round(tri.points[0].getX() * zoom), (int) Math.round(tri.points[0].getY() * zoom) + 5);
//            gr.drawString("(" + (int) Math.round(tri.points[1].getX()) + "," + (int) Math.round(tri.points[1].getY()) + ")", (int) Math.round(tri.points[1].getX() * zoom), (int) Math.round(tri.points[1].getY() * zoom) + 5);
//            gr.drawString("(" + (int) Math.round(tri.points[2].getX()) + "," + (int) Math.round(tri.points[2].getY()) + ")", (int) Math.round(tri.points[2].getX() * zoom), (int) Math.round(tri.points[2].getY() * zoom) + 5);
        }
        try {
            ImageIO.write(img, "png", new File("out.png"));
        } catch (IOException e) {
            e.printStackTrace();
        }

        // ----------
    }

}
