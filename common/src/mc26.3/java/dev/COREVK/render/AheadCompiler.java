package net.corevk.corevk.render;

import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.ShaderSource;

import java.util.concurrent.CompletionException;

public final class AheadCompiler implements AutoCloseable {

	private final GpuDevice device;

	private AheadCompiler(GpuDevice device) {
		this.device = device;
	}

	public static boolean available(GpuDevice device) {
		return true;
	}

	public static AheadCompiler open(GpuDevice device) {
		return new AheadCompiler(device);
	}

	public Ahead build(RenderPipeline pipeline, ShaderSource source) throws Refused {
		CompiledRenderPipeline compiled;
		try {
			compiled = this.device.compilePipeline(pipeline, source, Runnable::run).join()
					.finishCompile();
		} catch (CompletionException e) {
			Throwable cause = e.getCause() == null ? e : e.getCause();
			throw new Refused(String.valueOf(cause.getMessage()));
		}

		if (compiled == null) {
			throw new Refused("the device refused it, as the line above says");
		}

		return new Ahead(compiled);
	}

	@Override
	public void close() {

	}

	public static final class Ahead {

		private final CompiledRenderPipeline built;

		private Ahead(CompiledRenderPipeline built) {
			this.built = built;
		}

		public boolean adopt(GpuDevice device, RenderPipeline pipeline) {
			return GraphicsApi.adopt(pipeline, this.built);
		}

		public void destroy() {
			this.built.close();
		}
	}

	public static final class Refused extends Exception {

		Refused(String message) {
			super(message);
		}
	}
}
