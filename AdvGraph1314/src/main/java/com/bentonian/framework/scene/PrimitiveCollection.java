package com.bentonian.framework.scene;

import java.util.List;

import com.bentonian.framework.ui.GLRenderingContext;
import com.google.common.collect.Lists;

public class PrimitiveCollection extends Primitive {

  protected List<Primitive> primitives;

  public PrimitiveCollection() {
    primitives = Lists.newArrayList();
  }

  public Primitive add(Primitive sceneElement) {
    primitives.add(sceneElement);
    return sceneElement;
  }

  public void addAll(Primitive... sceneElements) {
    for (Primitive element : sceneElements) {
      add(element);
    }
  }

  public Primitive remove(Primitive sceneElement) {
    primitives.remove(sceneElement);
    return sceneElement;
  }

  public List<Primitive> getPrimitives() {
    return primitives;
  }

  @Override
  public void render(GLRenderingContext glCanvas) {
    glCanvas.push(getLocalToParent());
    for (Primitive sceneElement : getPrimitives()) {
      sceneElement.render(glCanvas);
    }
    glCanvas.pop();
  }

  @Override
  protected void renderLocal(GLRenderingContext glCanvas) {
  }
}
