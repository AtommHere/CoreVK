package net.corevk.corevk.uniform;

import org.joml.Matrix4fc;
import org.joml.Vector2f;
import org.joml.Vector4fc;

public interface ViewSource {

	Matrix4fc gbufferModelView();

	Matrix4fc gbufferModelViewInverse();

	Matrix4fc passModelView();

	Matrix4fc passModelViewInverse();

	Matrix4fc cameraBob();

	Matrix4fc passProjection();

	Matrix4fc passProjectionInverse();

	Vector4fc passColour();

	float passAlphaTest();

	Matrix4fc gbufferProjection();

	Matrix4fc gbufferProjectionInverse();

	Matrix4fc gbufferPreviousModelView();

	Matrix4fc gbufferPreviousProjection();

	Matrix4fc shadowModelView();

	Matrix4fc shadowModelViewInverse();

	Matrix4fc shadowProjection();

	Matrix4fc shadowProjectionInverse();

	Matrix4fc drawnShadowModelView();

	Matrix4fc drawnShadowModelViewInverse();

	Matrix4fc drawnShadowProjection();

	Matrix4fc drawnShadowProjectionInverse();

	Matrix4fc dhProjection();

	Matrix4fc dhProjectionInverse();

	Matrix4fc drawnDistantProjection();

	boolean distantDepthPair(Vector2f dest);

	Matrix4fc dhPreviousProjection();

	float dhNearPlane();

	float dhFarPlane();

	int dhRenderDistance();

	float near();

	float far();

	Vector4fc depthConvention();
}
