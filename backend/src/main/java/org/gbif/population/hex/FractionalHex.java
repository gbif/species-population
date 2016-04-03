package org.gbif.population.hex;

import java.util.ArrayList;

public class FractionalHex {
  public FractionalHex(double q, double r, double s)
  {
    this.q = q;
    this.r = r;
    this.s = s;
  }
  public final double q;
  public final double r;
  public final double s;

  static public Hex hexRound(FractionalHex h)
  {
    int q = (int)(Math.round(h.q));
    int r = (int)(Math.round(h.r));
    int s = (int)(Math.round(h.s));
    double q_diff = Math.abs(q - h.q);
    double r_diff = Math.abs(r - h.r);
    double s_diff = Math.abs(s - h.s);
    if (q_diff > r_diff && q_diff > s_diff)
    {
      q = -r - s;
    }
    else
    if (r_diff > s_diff)
    {
      r = -q - s;
    }
    else
    {
      s = -q - r;
    }
    return new Hex(q, r, s);
  }


  static public FractionalHex hexLerp(Hex a, Hex b, double t)
  {
    return new FractionalHex(a.q + (b.q - a.q) * t, a.r + (b.r - a.r) * t, a.s + (b.s - a.s) * t);
  }


  static public ArrayList<Hex> hexLinedraw(Hex a, Hex b)
  {
    int N = Hex.distance(a, b);
    ArrayList<Hex> results = new ArrayList<Hex>(){{}};
    double step = 1.0 / Math.max(N, 1);
    for (int i = 0; i <= N; i++)
    {
      results.add(FractionalHex.hexRound(FractionalHex.hexLerp(a, b, step * i)));
    }
    return results;
  }

}
