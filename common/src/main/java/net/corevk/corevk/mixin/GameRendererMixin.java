package net.corevk.corevk.mixin;

import net.corevk.corevk.render.CameraBob;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

	@WrapOperation(method = "renderLevel",
			at = @At(value = "INVOKE", target = "Lorg/joml/Matrix4f;mul(Lorg/joml/Matrix4fc;)Lorg/joml/Matrix4f;"))
	private Matrix4f corevk$bob(Matrix4f projection, Matrix4fc bob, Operation<Matrix4f> original) {
		CameraBob.take(bob);

		return original.call(projection, bob);
	}

	@WrapOperation(method = "renderLevel", require = 2,
			at = @At(value = "INVOKE", target = "Lorg/joml/Matrix4f;rotate(FLorg/joml/Vector3fc;)Lorg/joml/Matrix4f;"))
	private Matrix4f corevk$spin(Matrix4f projection, float angle, Vector3fc axis,
			Operation<Matrix4f> original) {
		CameraBob.rotate(angle, axis);

		return original.call(projection, angle, axis);
	}

	@WrapOperation(method = "renderLevel",
			at = @At(value = "INVOKE", target = "Lorg/joml/Matrix4f;scale(FFF)Lorg/joml/Matrix4f;"))
	private Matrix4f corevk$skew(Matrix4f projection, float x, float y, float z,
			Operation<Matrix4f> original) {
		CameraBob.scale(x, y, z);

		return original.call(projection, x, y, z);
	}
}
