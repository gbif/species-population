package org.gbif.population.spark;

import java.awt.geom.Point2D;

/**
 * Utilities when dealing with Mercator projected tiles.
 */
public class MercatorUtils {

  /**
   * For the given coordinates return the address of the tile on which they would appear at the zoom.
   *
   * @param lat  Decimal latitude
   * @param lng  Decimal longitude
   * @param zoom Zoom level of the target tile
   *
   * @return The x,y coordinate address of the target tile at the given zoom level or null if not plotable
   */
  public static Point2D toXY(Double lat, Double lng, int zoom) {

    if (!isPlottable(lat, lng)) {
      return null;
    }

    Point2D normalizedPixels = toNormalisedPixelCoords(lat, lng);
    int scale = 1 << zoom;
    // truncating to int removes the fractional pixel offset
    int x = (int) (normalizedPixels.getX() * scale);
    int y = (int) (normalizedPixels.getY() * scale);
    return new Point2D.Double(x, y);
  }

  /**
   * Google maps cover +/- 85 degrees only.
   *
   * @return true if the location is plottable on a map
   */
  static boolean isPlottable(Double lat, Double lng) {
    return lat != null && lng != null && lat >= -85d && lat <= 85d && lng >= -180 && lng <= 180;
  }

  /**
   * Returns the lat/lng as an "Offset Normalized Mercator" pixel coordinate.
   * This is a coordinate that runs from 0..1 in latitude and longitude with 0,0 being
   * top left. Normalizing means that this routine can be used at any zoom level and
   * then multiplied by a power of two to get actual pixel coordinates.
   */
  static Point2D toNormalisedPixelCoords(double lat, double lng) {
    if (lng > 180) {
      lng -= 360;
    }
    lng /= 360;
    lng += 0.5;
    lat = 0.5 - ((Math.log(Math.tan((Math.PI / 4) + ((0.5 * Math.PI * lat) / 180))) / Math.PI) / 2.0);
    return new Point2D.Double(lng, lat);
  }
}
