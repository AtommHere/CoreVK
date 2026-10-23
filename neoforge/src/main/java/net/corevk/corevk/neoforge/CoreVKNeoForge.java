package net.corevk.corevk.neoforge;

import net.corevk.corevk.platform.EngineStages;
import net.corevk.corevk.render.pbr.PbrAtlases;
import net.corevk.corevk.screen.PackScreens;
import net.corevk.corevk.CoreVK;

import net.minecraft.client.renderer.texture.TextureAtlas;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.FlipFrameEvent;
import net.neoforged.neoforge.client.event.FrameGraphSetupEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.TextureAtlasStitchedEvent;
import net.neoforged.neoforge.client.event.lifecycle.ClientStoppingEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = CoreVK.MOD_ID, dist = Dist.CLIENT)
public final class CoreVKNeoForge {

	public CoreVKNeoForge(IEventBus modBus, ModContainer container) {
		CoreVK.initClient(new NeoForgePlatform());

		container.registerExtensionPoint(IConfigScreenFactory.class,
				(_, modListScreen) -> PackScreens.open(modListScreen));

		CoreVKKeys.register(modBus);

		NeoForge.EVENT_BUS.addListener(FlipFrameEvent.class, _ -> EarlyWindow.close());

		modBus.addListener(FMLClientSetupEvent.class, _ -> EngineStages.clientSetup());

		NeoForge.EVENT_BUS.addListener(ClientTickEvent.Pre.class, _ -> EngineStages.clientTickStart());
		NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, _ -> EngineStages.clientTick());

		modBus.addListener(TextureAtlasStitchedEvent.class, this::onAtlasStitched);

		NeoForge.EVENT_BUS.addListener(FrameGraphSetupEvent.class, event ->
				EngineStages.frameGraphSetup(event.getModelViewMatrix(), event.getCameraState().pos));

		NeoForge.EVENT_BUS.addListener(RenderLevelStageEvent.AfterOpaqueBlocks.class,
				_ -> EngineStages.afterOpaqueBlocks());

		NeoForge.EVENT_BUS.addListener(RenderLevelStageEvent.AfterOpaqueFeatures.class,
				_ -> EngineStages.afterOpaqueFeatures());

		NeoForge.EVENT_BUS.addListener(RenderLevelStageEvent.AfterTranslucentFeatures.class,
				_ -> EngineStages.afterTranslucentFeatures());

		NeoForge.EVENT_BUS.addListener(RenderLevelStageEvent.AfterLevel.class,
				_ -> EngineStages.afterLevel());

		NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class,
				_ -> EngineStages.leaveWorld());

		NeoForge.EVENT_BUS.addListener(ClientStoppingEvent.class, _ -> EngineStages.closeClient());
	}

	private void onAtlasStitched(TextureAtlasStitchedEvent event) {
		TextureAtlas atlas = event.getAtlas();
		PbrAtlases.stitched(atlas.location(), atlas.getTexture(), atlas.getTextures().values());
	}
}
