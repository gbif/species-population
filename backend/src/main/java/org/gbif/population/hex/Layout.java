package org.gbif.population.hex;

import java.util.ArrayList;

public class Layout {
  public Layout(Orientation orientation, Point size, Point origin)
  {
    this.orientation = orientation;
    this.size = size;
    this.origin = origin;
  }
  public final Orientation orientation;
  public final Point size;
  public final Point origin;
  static public Orientation pointy = new Orientation(Math.sqrt(3.0), Math.sqrt(3.0) / 2.0, 0.0, 3.0 / 2.0, Math.sqrt(3.0) / 3.0, -1.0 / 3.0, 0.0, 2.0 / 3.0, 0.5);
  static public Orientation flat = new Orientation(3.0 / 2.0, 0.0, Math.sqrt(3.0) / 2.0, Math.sqrt(3.0), 2.0 / 3.0, 0.0, -1.0 / 3.0, Math.sqrt(3.0) / 3.0, 0.0);

  static public Point hexToPixel(Layout layout, Hex h)
  {
    Orientation M = layout.orientation;
    Point size = layout.size;
    Point origin = layout.origin;
    double x = (M.f0 * h.q + M.f1 * h.r) * size.x;
    double y = (M.f2 * h.q + M.f3 * h.r) * size.y;
    return new Point(x + origin.x, y + origin.y);
  }


  static public FractionalHex pixelToHex(Layout layout, Point p)
  {
    Orientation M = layout.orientation;
    Point size = layout.size;
    Point origin = layout.origin;
    Point pt = new Point((p.x - origin.x) / size.x, (p.y - origin.y) / size.y);
    double q = M.b0 * pt.x + M.b1 * pt.y;
    double r = M.b2 * pt.x + M.b3 * pt.y;
    return new FractionalHex(q, r, -q - r);
  }


  static public Point hexCornerOffset(Layout layout, int corner)
  {
    Orientation M = layout.orientation;
    Point size = layout.size;
    double angle = 2.0 * Math.PI * (corner + M.start_angle) / 6;
    return new Point(size.x * Math.cos(angle), size.y * Math.sin(angle));
  }


  static public ArrayList<Point> polygonCorners(Layout layout, Hex h)
  {
    ArrayList<Point> corners = new ArrayList<Point>(){{}};
    Point center = Layout.hexToPixel(layout, h);
    for (int i = 0; i < 6; i++)
    {
      Point offset = Layout.hexCornerOffset(layout, i);
      corners.add(new Point(center.x + offset.x, center.y + offset.y));
    }
    return corners;
  }

}
