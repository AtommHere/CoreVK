package net.corevk.corevk.neoforge.mixin;

import net.corevk.corevk.neoforge.EarlyWindow;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.GpuBackend;
import net.neoforged.fml.loading.EarlyLoadingScreenController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Window.class)
public abstract class WindowEarlyScreenMixin {

	private static final String CURRENT =
			"Lnet/neoforged/fml/loading/EarlyLoadingScreenController;current()"
					+ "Lnet/neoforged/fml/loading/EarlyLoadingScreenController;";

	@WrapOperation(method = "createGlfwWindow", at = @At(value = "INVOKE", target = CURRENT),
			require = 1)
	private static EarlyLoadingScreenController corevk$refuseTheEarlyWindow(
			Operation<EarlyLoadingScreenController> original,
			@Local(argsOnly = true) GpuBackend backend) {
		return EarlyWindow.hand(backend, original.call(), true);
	}

	@WrapOperation(method = "<init>", at = @At(value = "INVOKE", target = CURRENT), require = 1)
	private static EarlyLoadingScreenController corevk$sayItAgainOnInit(
			Operation<EarlyLoadingScreenController> original,
			@Local(argsOnly = true) GpuBackend backend) {
		return EarlyWindow.hand(backend, original.call(), false);
	}
}
