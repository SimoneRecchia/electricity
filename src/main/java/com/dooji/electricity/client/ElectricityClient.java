package com.dooji.electricity.client;

import com.dooji.electricity.block.PowerBoxBlockEntity;
import com.dooji.electricity.client.render.block.ElectricCabinRenderer;
import com.dooji.electricity.client.render.block.MetStationRenderer;
import com.dooji.electricity.client.render.block.SwitchgearRenderer;
import com.dooji.electricity.client.render.block.LatticeTowerRenderer;
import com.dooji.electricity.client.render.block.PowerBoxRenderer;
import com.dooji.electricity.client.render.block.TransformerRenderer;
import com.dooji.electricity.client.render.block.PvArrayRenderer;
import com.dooji.electricity.client.render.block.PvCombinerRenderer;
import com.dooji.electricity.client.render.block.PvInverterRenderer;
import com.dooji.electricity.client.render.block.UtilityPoleRenderer;
import com.dooji.electricity.client.render.block.WindTurbineRenderer;
import com.dooji.electricity.client.wire.InsulatorLookup;
import com.dooji.electricity.client.wire.WireManagerClient;
import com.dooji.electricity.main.Electricity;
import com.dooji.electricity.wire.InsulatorHost;
import com.dooji.electricity.main.network.payloads.PowerUpdatePayload;
import com.dooji.electricity.main.network.payloads.SyncWiresPayload;
import com.dooji.electricity.main.network.payloads.WireConnectionPayload;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = Electricity.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ElectricityClient {
	static {
		ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ElectricityClientConfig.spec(), "Electricity/config.toml");
	}

	@SubscribeEvent
	public static void onClientSetup(FMLClientSetupEvent event) {
		MinecraftForge.EVENT_BUS.addListener(ElectricityClient::onLevelUnload);
		MinecraftForge.EVENT_BUS.addListener(ElectricityClient::onClientDisconnect);

		setupRenderers();
	}

	private static void setupRenderers() {
		UtilityPoleRenderer.init();
		ElectricCabinRenderer.init();
		PowerBoxRenderer.init();
		LatticeTowerRenderer.init();
		TransformerRenderer.init();
		WindTurbineRenderer.init();
		PvArrayRenderer.init();
		PvInverterRenderer.init();
		PvCombinerRenderer.init();
		MetStationRenderer.init();
		SwitchgearRenderer.init();
	}

	private static void onClientDisconnect(ClientPlayerNetworkEvent.LoggingOut event) {
		WireManagerClient.removeAll();
		TrackedBlockEntities.clear();
		InsulatorLookup.clear();
	}

	private static void onLevelUnload(LevelEvent.Unload event) {
		if (event.getLevel().isClientSide()) {
			WireManagerClient.removeAll();
			TrackedBlockEntities.clear();
			InsulatorLookup.clear();
		}
	}

	public static void handleSyncPacket(SyncWiresPayload payload) {
		WireManagerClient.sync(payload.connections());
	}

	public static void handleWireConnectionPacket(WireConnectionPayload payload) {
		if (payload.isCreation()) {
			WireManagerClient.addWireConnection(payload.connection());
		} else {
			WireManagerClient.removeWireConnection(payload.connection());
		}
	}

	public static void handlePowerUpdatePacket(PowerUpdatePayload payload) {
		var level = Minecraft.getInstance().level;
		if (level == null) return;

		var blockEntity = level.getBlockEntity(payload.blockPos());
		// the same figure the server delivered, so what a wrench reads on the client is what flowed
		if (blockEntity instanceof InsulatorHost host) host.deliverPower(payload.power());
		if (blockEntity instanceof PowerBoxBlockEntity powerBox) powerBox.setCurrentPower(payload.power());
	}
}
