package com.bentonian.framework.ui;

import static com.bentonian.framework.ui.ShaderUtil.clearGlError;
import static com.bentonian.framework.ui.ShaderUtil.compileProgram;
import static com.bentonian.framework.ui.ShaderUtil.loadShader;
import static com.bentonian.framework.ui.ShaderUtil.testGlError;
import static com.bentonian.framework.ui.ShaderUtil.validateLocation;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.util.Stack;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

import com.bentonian.framework.math.M3d;
import com.bentonian.framework.math.M4x4;
import com.bentonian.framework.math.MatrixStack;
import com.bentonian.framework.scene.Camera;

/**
 * Context for OpenGL adapter layer
 *
 * @author Alex Benton
 */
public class GLCanvas {

  // Caution!  This 'constant' is lazy-initialized in the first call to initGl().
  public static int DEFAULT_SHADER_PROGRAM = -1;

  protected final Camera camera;
  protected final MatrixStack projection;
  protected final MatrixStack modelStack;

  private int program;
  private Stack<Integer> programStack;

  public GLCanvas() {
    this.modelStack = new MatrixStack();
    this.projection = new MatrixStack();
    this.programStack = new Stack<Integer>();
    this.camera = new Camera();

    projection.peek().setData(M4x4.perspective(1));
  }

  protected void initGl() {
    testGlError();

    if (DEFAULT_SHADER_PROGRAM == -1) {
      DEFAULT_SHADER_PROGRAM = loadDefaultShaderProgram();
    }
    useProgram(DEFAULT_SHADER_PROGRAM);

    GL11.glClearColor(0.2f, 0.4f, 0.6f, 0.0f);
    GL11.glClearDepth(1.0f);
    GL11.glEnable(GL11.GL_DEPTH_TEST);
    GL11.glEnable(GL11.GL_BLEND);
    GL11.glDepthFunc(GL11.GL_LEQUAL);
    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

    testGlError();
  }

  public void push(M4x4 T) {
    modelStack.push(T);
    updateUniforms();
  }

  public void pop() {
    modelStack.pop();
    updateUniforms();
  }

  public M4x4 peek() {
    return modelStack.peek();
  }

  public void pushLineOffset() {
    projection.pushReversed(M4x4.translationMatrix(new M3d(0, 0, -0.00001)));
    updateProjectionMatrix();
  }

  public void popLineOffset() {
    projection.pop();
    updateProjectionMatrix();
  }
  
  public void pushFrameBuffer(GLFrameBuffer frameBuffer) {
    projection.add(M4x4.perspective(frameBuffer.getAspectRatio()));
    updateProjectionMatrix();
    frameBuffer.activate();
  }

  public void popFrameBuffer(GLFrameBuffer frameBuffer) {
    frameBuffer.deactivate();
    projection.pop();
    updateProjectionMatrix();
  }
  
  /**
   * Initializes and copies an ARGB BufferedImage into an RGBA texture.
   */
  public static int setupTexture(BufferedImage texture) {
    int textureName = GL11.glGenTextures();
    updateTexture(textureName, texture);
    return textureName;
  }

  /**
   * Copies an ARGB BufferedImage into an RGBA texture.
   */
  public static void updateTexture(int textureName, BufferedImage texture) {
    int width = texture.getWidth();
    int height = texture.getHeight();
    
    // Get the image into a known byte alignment.
    // Could this be done more efficiently?  Heck yes!
    ByteBuffer buffer = ByteBuffer.allocateDirect(texture.getWidth() * texture.getHeight() * 4);
    for (int x = 0; x < texture.getWidth(); x++) {
      for (int y = 0; y < texture.getHeight(); y++) {
        int argb = texture.getRGB(x, y);
        buffer.put((x + y * texture.getWidth()) * 4 + 0, (byte) ((argb >> 16) & 0xFF));
        buffer.put((x + y * texture.getWidth()) * 4 + 1, (byte) ((argb >>  8) & 0xFF));
        buffer.put((x + y * texture.getWidth()) * 4 + 2, (byte) ((argb >>  0) & 0xFF));
        buffer.put((x + y * texture.getWidth()) * 4 + 3, (byte) ((argb >> 24) & 0xFF));
      }
    }
    
    updateTextureBuffer(textureName, buffer, width, height, GL11.GL_RGBA);
  }

  public static void updateTextureBuffer(
      int textureId, ByteBuffer buffer, int width, int height, int glFormat) {
    testGlError();
    GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
    GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, 
        width, height, 0, glFormat, GL11.GL_UNSIGNED_BYTE, buffer);
    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
    testGlError();
  }

  public void useProgram(int program) {
    if (program != this.program) {
      this.program = program;
      GL20.glUseProgram(program);
      testGlError();
    }
  }
  
  public void pushProgram(int program) {
    programStack.push(this.program);
    useProgram(program);
  }

  public void popProgram() {
    useProgram(programStack.pop());
  }

  public int getProgram() {
    return program;
  }

  public MatrixStack getProjection() {
    return projection;
  }

  public MatrixStack getModelView() {
    return modelStack;
  }

  public Camera getCamera() {
    return camera;
  }

  public int getUniformLocation(String name) {
    return validateLocation(GL20.glGetUniformLocation(program, name), name);
  }

  public int getInputLocation(String name) {
    return validateLocation(GL20.glGetAttribLocation(program, name), name);
  }

  protected void updateUniforms() {
    Camera cameraForModelView = getCameraForModelview();
    M4x4 modelToWorld = peek();
    M4x4 modelToCamera = cameraForModelView.getParentToLocal().times(modelToWorld);

    testGlError();
    updateUniformM4x4("modelToWorld", modelToWorld);
    updateUniformM4x4("modelToCamera", modelToCamera);
    updateUniformM3x3("normalToWorld", modelToWorld.extract3x3().inverted().transposed());
    updateUniformM3x3("normalToCamera", modelToCamera.times(modelToWorld).extract3x3().inverted().transposed());
    updateUniformM4x4("modelToScreen", projection.peek().times(modelToCamera));
    updateUniformVec3("eyePosition", cameraForModelView.getPosition());
    updateUniformVec3("lightPosition", getLightPosition());
  }

  protected Camera getCameraForModelview() {
    return camera;
  }
  
  protected void updateProjectionMatrix() {
    testGlError();
    updateUniformM4x4("modelToScreen", projection.peek().times(camera.getParentToLocal().times(peek())));
  }

  protected M3d getLightPosition() {
    return camera.getPosition().normalized().times(1000);
  }

  public void updateUniformM4x4(String uniformName, M4x4 T) {
    int uniform = GL20.glGetUniformLocation(program, uniformName);
    if (uniform != -1) {
      GL20.glUniformMatrix4(uniform, false, T.asFloats());
      testGlError();
    } else {
      clearGlError();
    }
  }

  public void updateUniformM3x3(String uniformName, M4x4 T) {
    int uniform = GL20.glGetUniformLocation(program, uniformName);
    if (uniform != -1) {
      GL20.glUniformMatrix3(uniform, false, T.as3x3Floats());
      testGlError();
    } else {
      clearGlError();
    }
  }

  public void updateUniformVec3(String uniformName, M3d v) {
    int uniform = GL20.glGetUniformLocation(program, uniformName);
    if (uniform != -1) {
      GL20.glUniform3f(uniform, (float) v.getX(), (float) v.getY(), (float) v.getZ());
      testGlError();
    } else {
      clearGlError();
    }
  }

  public void updateUniformVec2(String uniformName, float a, float b) {
    int uniform = GL20.glGetUniformLocation(program, uniformName);
    if (uniform != -1) {
      GL20.glUniform2f(uniform, a, b);
      testGlError();
    } else {
      clearGlError();
    }
  }

  public void updateUniformFloat(String uniformName, float f) {
    int uniform = GL20.glGetUniformLocation(program, uniformName);
    if (uniform != -1) {
      GL20.glUniform1f(uniform, f);
      testGlError();
    } else {
      clearGlError();
    }
  }

  public void updateUniformInt(String uniformName, int i) {
    int uniform = GL20.glGetUniformLocation(program, uniformName);
    if (uniform != -1) {
      GL20.glUniform1i(uniform, i);
      testGlError();
    } else {
      clearGlError();
    }
  }

  public void updateUniformBoolean(String uniformName, boolean b) {
    updateUniformInt(uniformName, b ? 1 : 0);
  }
  
  private static int loadDefaultShaderProgram() {
    int vsName = loadShader(GL20.GL_VERTEX_SHADER, GLCanvas.class, "default.vsh");
    int fsName = loadShader(GL20.GL_FRAGMENT_SHADER, GLCanvas.class, "default.fsh");
    int program = compileProgram(vsName, fsName);
    testGlError();
    return program;
  }
}
