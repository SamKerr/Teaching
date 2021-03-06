package com.bentonian.framework.mesh.implicits;

import com.bentonian.framework.math.Vec3;
import com.bentonian.framework.math.M4x4;

public class MetaStrip extends MetaCube {
  
  private final M4x4 squash;

  public MetaStrip(Vec3 scale) {
    this.squash = M4x4.scaleMatrix(scale);
  }
  
  @Override
  public double F(Vec3 v) {
    return super.F(squash.times(v));
  }
}
