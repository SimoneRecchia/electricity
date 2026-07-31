package com.dooji.electricity.main;

import com.dooji.electricity.api.power.ElectricityCapabilities;
import com.dooji.electricity.api.power.TurbineSpec;
import com.dooji.electricity.block.ElectricCabinBlock;
import com.dooji.electricity.block.ElectricCabinBlockEntity;
import com.dooji.electricity.block.ElectricLampBlock;
import com.dooji.electricity.block.ElectricLampBlockEntity;
import com.dooji.electricity.block.PowerBoxBlock;
import com.dooji.electricity.block.SolarPanelBlock;
import com.dooji.electricity.block.SolarPanelBlockEntity;
import com.dooji.electricity.block.PowerBoxBlockEntity;
import com.dooji.electricity.block.UtilityPoleBlock;
import com.dooji.electricity.block.UtilityPoleBlockEntity;
import com.dooji.electricity.block.TowerCollapse;
import com.dooji.electricity.block.TurbineTowerBlock;
import com.dooji.electricity.block.TurbineTowerBlockEntity;
import com.dooji.electricity.block.WindTurbineBlock;
import com.dooji.electricity.block.WindTurbineBlockEntity;
import com.dooji.electricity.block.WorkbenchBlock;
import com.dooji.electricity.compat.computercraft.ComputerCraftBridge;
import com.dooji.electricity.item.ItemWire;
import com.dooji.electricity.item.PowerWrenchItem;
import com.dooji.electricity.item.TooltipBlockItem;
import com.dooji.electricity.item.TooltipItem;
import com.dooji.electricity.item.TurbineBlockItem;
import com.dooji.electricity.menu.WorkbenchMenu;
import com.dooji.electricity.recipe.WorkbenchRecipe;
import com.dooji.electricity.main.registry.ObjDefinitions;
import com.dooji.electricity.main.registry.TurbineCatalog;
import com.dooji.electricity.main.network.ElectricityNetworking;
import com.dooji.electricity.main.power.PowerNetwork;
import com.dooji.electricity.main.wire.WireManager;
import com.dooji.electricity.main.weather.GlobalWeatherManager;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(Electricity.MOD_ID)
public class Electricity {
	public static final String MOD_ID = "electricity";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);
	public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID);
	public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MOD_ID);
	public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MOD_ID);
	public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, MOD_ID);
	public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS = DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, MOD_ID);
	public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES = DeferredRegister.create(ForgeRegistries.RECIPE_TYPES, MOD_ID);

	public static final RegistryObject<Block> UTILITY_POLE_BLOCK = BLOCKS.register("utility_pole", () -> new UtilityPoleBlock(Block.Properties.of().strength(2.0f, 10.0f).requiresCorrectToolForDrops().noOcclusion()));
	public static final RegistryObject<Block> ELECTRIC_CABIN_BLOCK = BLOCKS.register("electric_cabin", () -> new ElectricCabinBlock(Block.Properties.of().strength(2.0f, 10.0f).requiresCorrectToolForDrops().noOcclusion()));
	public static final RegistryObject<Block> POWER_BOX_BLOCK = BLOCKS.register("power_box", () -> new PowerBoxBlock(Block.Properties.of().strength(2.0f, 10.0f).requiresCorrectToolForDrops().noOcclusion()));
	// The original block keeps its "wind_turbine" registry name so existing worlds load,
	// and is bound to the C130: the authored model's rotor measures 13.044 blocks, so
	// that is which machine it has always been geometrically.
	public static final RegistryObject<Block> WIND_TURBINE_BLOCK = BLOCKS.register("wind_turbine", () -> new WindTurbineBlock(Block.Properties.of().strength(2.0f, 10.0f).requiresCorrectToolForDrops().noOcclusion(), TurbineCatalog.C130_40));
	public static final RegistryObject<Block> ELECTRIC_LAMP_BLOCK = BLOCKS.register("electric_lamp", () -> new ElectricLampBlock(Block.Properties.of().strength(0.3f).requiresCorrectToolForDrops().noOcclusion()));
	public static final RegistryObject<Block> WORKBENCH_BLOCK = BLOCKS.register("workbench", () -> new WorkbenchBlock(Block.Properties.of().strength(2.0f).requiresCorrectToolForDrops().noOcclusion()));

	public static final RegistryObject<Item> WIRE_ITEM = ITEMS.register("wire", ItemWire::new);
	public static final RegistryObject<Item> POWER_WRENCH_ITEM = ITEMS.register("power_wrench", PowerWrenchItem::new);
	public static final RegistryObject<Item> UTILITY_POLE_ITEM = ITEMS.register("utility_pole", () -> new TooltipBlockItem(UTILITY_POLE_BLOCK.get(), new Item.Properties(), "tooltip.electricity.utility_pole"));
	public static final RegistryObject<Item> ELECTRIC_CABIN_ITEM = ITEMS.register("electric_cabin", () -> new TooltipBlockItem(ELECTRIC_CABIN_BLOCK.get(), new Item.Properties(), "tooltip.electricity.electric_cabin"));
	public static final RegistryObject<Item> POWER_BOX_ITEM = ITEMS.register("power_box", () -> new TooltipBlockItem(POWER_BOX_BLOCK.get(), new Item.Properties(), "tooltip.electricity.power_box"));
	public static final RegistryObject<Item> WIND_TURBINE_ITEM = ITEMS.register("wind_turbine", () -> new TurbineBlockItem(WIND_TURBINE_BLOCK.get(), new Item.Properties(), TurbineCatalog.C130_40));
	public static final RegistryObject<Item> ELECTRIC_LAMP_ITEM = ITEMS.register("electric_lamp", () -> new TooltipBlockItem(ELECTRIC_LAMP_BLOCK.get(), new Item.Properties(), "tooltip.electricity.electric_lamp"));
	public static final RegistryObject<Item> WEATHER_TABLET_ITEM = ITEMS.register("weather_tablet", () -> new TooltipItem(new Item.Properties().stacksTo(1), "tooltip.electricity.weather_tablet"));
	public static final RegistryObject<Item> WORKBENCH_ITEM = ITEMS.register("workbench", () -> new TooltipBlockItem(WORKBENCH_BLOCK.get(), new Item.Properties(), "tooltip.electricity.workbench"));
	public static final RegistryObject<Item> CIRCUIT_BOARD_ITEM = ITEMS.register("circuit_board", () -> new TooltipItem(new Item.Properties(), "tooltip.electricity.circuit_board"));
	public static final RegistryObject<Item> CPU_ITEM = ITEMS.register("cpu", () -> new TooltipItem(new Item.Properties(), "tooltip.electricity.cpu"));
	public static final RegistryObject<Item> SCREEN_ITEM = ITEMS.register("screen", () -> new TooltipItem(new Item.Properties(), "tooltip.electricity.screen"));
	public static final RegistryObject<Item> INSULATOR_ITEM = ITEMS.register("insulator", () -> new TooltipItem(new Item.Properties(), "tooltip.electricity.insulator"));
	public static final RegistryObject<Item> METAL_CASING_ITEM = ITEMS.register("metal_casing", () -> new TooltipItem(new Item.Properties(), "tooltip.electricity.metal_casing"));
	public static final RegistryObject<Item> MOTOR_CORE_ITEM = ITEMS.register("motor_core", () -> new TooltipItem(new Item.Properties(), "tooltip.electricity.motor_core"));

	/**
	 * One block of turbine tower.
	 *
	 * Stacked by hand, which is what makes hub height something a player builds rather than
	 * a number they set. The machine then goes on top and refuses to mount outside the range
	 * of tower heights it is certified for.
	 */
	public static final RegistryObject<Block> TURBINE_TOWER_BLOCK = BLOCKS.register("turbine_tower",
			() -> new TurbineTowerBlock(Block.Properties.of().strength(2.0f, 10.0f).requiresCorrectToolForDrops().noOcclusion()));
	public static final RegistryObject<Item> TURBINE_TOWER_ITEM = ITEMS.register("turbine_tower",
			() -> new TooltipBlockItem(TURBINE_TOWER_BLOCK.get(), new Item.Properties(), "tooltip.electricity.turbine_tower"));

	/**
	 * A block of photovoltaic array: twenty kilowatts of modules over a hundred square metres.
	 *
	 * Laid flat and walked over, so a farm of them is a field rather than a wall. Glass, so it
	 * breaks like glass.
	 */
	public static final RegistryObject<Block> SOLAR_PANEL_BLOCK = BLOCKS.register("solar_panel",
			() -> new SolarPanelBlock(Block.Properties.of().strength(1.0f, 2.0f).requiresCorrectToolForDrops().noOcclusion()));
	public static final RegistryObject<Item> SOLAR_PANEL_ITEM = ITEMS.register("solar_panel",
			() -> new TooltipBlockItem(SOLAR_PANEL_BLOCK.get(), new Item.Properties(), "tooltip.electricity.solar_panel"));

	/**
	 * A block for every machine in the catalogue, keyed by spec id.
	 *
	 * Registered from the catalogue in a loop rather than declared one by one, so a spec
	 * cannot come to exist without a block that places it. Each is named after its spec's
	 * own path except the C130, which keeps the original {@code wind_turbine} registry name
	 * because worlds already contain blocks under it.
	 */
	public static final Map<ResourceLocation, RegistryObject<Block>> TURBINE_BLOCKS = registerTurbineBlocks();
	public static final Map<ResourceLocation, RegistryObject<Item>> TURBINE_ITEMS = registerTurbineItems();

	private static BlockBehaviour.Properties turbineProperties() {
		return Block.Properties.of().strength(2.0f, 10.0f).requiresCorrectToolForDrops().noOcclusion();
	}

	private static Map<ResourceLocation, RegistryObject<Block>> registerTurbineBlocks() {
		Map<ResourceLocation, RegistryObject<Block>> blocks = new LinkedHashMap<>();
		for (TurbineSpec spec : TurbineCatalog.all()) {
			if (spec.id().equals(TurbineCatalog.C130_40.id())) {
				blocks.put(spec.id(), WIND_TURBINE_BLOCK);
				continue;
			}

			blocks.put(spec.id(), BLOCKS.register(spec.id().getPath(), () -> new WindTurbineBlock(turbineProperties(), spec)));
		}

		return blocks;
	}

	private static Map<ResourceLocation, RegistryObject<Item>> registerTurbineItems() {
		Map<ResourceLocation, RegistryObject<Item>> items = new LinkedHashMap<>();
		for (TurbineSpec spec : TurbineCatalog.all()) {
			if (spec.id().equals(TurbineCatalog.C130_40.id())) {
				items.put(spec.id(), WIND_TURBINE_ITEM);
				continue;
			}

			RegistryObject<Block> block = TURBINE_BLOCKS.get(spec.id());
			items.put(spec.id(), ITEMS.register(spec.id().getPath(), () -> new TurbineBlockItem(block.get(), new Item.Properties(), spec)));
		}

		return items;
	}

	/** Every turbine block, so one block entity type can serve the whole catalogue. */
	private static Block[] turbineBlocks() {
		return TURBINE_BLOCKS.values().stream().map(RegistryObject::get).toArray(Block[]::new);
	}

	public static final RegistryObject<MenuType<WorkbenchMenu>> WORKBENCH_MENU = MENUS.register("workbench", () -> IForgeMenuType.create(WorkbenchMenu::new));
	public static final RegistryObject<RecipeSerializer<WorkbenchRecipe>> WORKBENCH_RECIPE_SERIALIZER = RECIPE_SERIALIZERS.register("workbench", WorkbenchRecipe.Serializer::new);
	public static final RegistryObject<RecipeType<WorkbenchRecipe>> WORKBENCH_RECIPE_TYPE = RECIPE_TYPES.register("workbench", () -> WorkbenchRecipe.TYPE);

	public static final RegistryObject<CreativeModeTab> ELECTRICITY_TAB = CREATIVE_TABS.register("main",
			() -> CreativeModeTab.builder().title(Component.translatable("itemGroup." + MOD_ID + ".main")).icon(() -> new ItemStack(WIRE_ITEM.get())).displayItems((parameters, output) -> {
				output.accept(POWER_WRENCH_ITEM.get());
				output.accept(WIRE_ITEM.get());
				output.accept(UTILITY_POLE_ITEM.get());
				output.accept(ELECTRIC_CABIN_ITEM.get());
				output.accept(POWER_BOX_ITEM.get());
				// in catalogue order, which is the order a player builds them
				for (TurbineSpec spec : TurbineCatalog.all()) {
					output.accept(TURBINE_ITEMS.get(spec.id()).get());
				}

				output.accept(TURBINE_TOWER_ITEM.get());
				output.accept(SOLAR_PANEL_ITEM.get());
				output.accept(ELECTRIC_LAMP_ITEM.get());
				output.accept(WORKBENCH_ITEM.get());
				output.accept(CIRCUIT_BOARD_ITEM.get());
				output.accept(CPU_ITEM.get());
				output.accept(SCREEN_ITEM.get());
				output.accept(INSULATOR_ITEM.get());
				output.accept(METAL_CASING_ITEM.get());
				output.accept(MOTOR_CORE_ITEM.get());
			}).build());

	public static RegistryObject<BlockEntityType<UtilityPoleBlockEntity>> UTILITY_POLE_BLOCK_ENTITY;
	public static RegistryObject<BlockEntityType<ElectricCabinBlockEntity>> ELECTRIC_CABIN_BLOCK_ENTITY;
	public static RegistryObject<BlockEntityType<PowerBoxBlockEntity>> POWER_BOX_BLOCK_ENTITY;
	public static RegistryObject<BlockEntityType<WindTurbineBlockEntity>> WIND_TURBINE_BLOCK_ENTITY;
	public static RegistryObject<BlockEntityType<TurbineTowerBlockEntity>> TURBINE_TOWER_BLOCK_ENTITY;
	public static RegistryObject<BlockEntityType<ElectricLampBlockEntity>> ELECTRIC_LAMP_BLOCK_ENTITY;
	public static RegistryObject<BlockEntityType<SolarPanelBlockEntity>> SOLAR_PANEL_BLOCK_ENTITY;

	public static final WireManager wireManager = new WireManager();
	public static PowerNetwork powerNetwork;

	public Electricity() {
		IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
		BLOCKS.register(modEventBus);
		ITEMS.register(modEventBus);
		CREATIVE_TABS.register(modEventBus);
		MENUS.register(modEventBus);
		RECIPE_SERIALIZERS.register(modEventBus);
		// the type as well as the serializer: it was being created and filled but never handed
		// over, so the workbench's recipe type was missing from the registry other mods read
		RECIPE_TYPES.register(modEventBus);
		ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, ElectricityServerConfig.spec(), "Electricity/server.toml");
		// has to be now, before any level data is read: the rule set is deserialised with the
		// world, so a rule registered later would be missing from a world that had it set
		ElectricityGameRules.register();

		UTILITY_POLE_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register("utility_pole", () -> BlockEntityType.Builder.of(UtilityPoleBlockEntity::new, UTILITY_POLE_BLOCK.get()).build(null));

		ELECTRIC_CABIN_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register("electric_cabin", () -> BlockEntityType.Builder.of(ElectricCabinBlockEntity::new, ELECTRIC_CABIN_BLOCK.get()).build(null));

		POWER_BOX_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register("power_box", () -> BlockEntityType.Builder.of(PowerBoxBlockEntity::new, POWER_BOX_BLOCK.get()).build(null));

		// one type for the whole catalogue: the machines differ by their spec, which the
		// block entity reads back off whichever block it is sitting in
		WIND_TURBINE_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register("wind_turbine", () -> BlockEntityType.Builder.of(WindTurbineBlockEntity::new, turbineBlocks()).build(null));

		// stateless and never ticked: it only exists so the renderer can find a tower that has
		// no machine on it yet, which is every tower while it is being stacked
		TURBINE_TOWER_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register("turbine_tower", () -> BlockEntityType.Builder.of(TurbineTowerBlockEntity::new, TURBINE_TOWER_BLOCK.get()).build(null));

		ELECTRIC_LAMP_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register("electric_lamp", () -> BlockEntityType.Builder.of(ElectricLampBlockEntity::new, ELECTRIC_LAMP_BLOCK.get()).build(null));

		SOLAR_PANEL_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register("solar_panel", () -> BlockEntityType.Builder.of(SolarPanelBlockEntity::new, SOLAR_PANEL_BLOCK.get()).build(null));

		BLOCK_ENTITY_TYPES.register(modEventBus);
		LOGGER.info("Registered {} items, {} blocks, and {} block entity types", ITEMS.getEntries().size(), BLOCKS.getEntries().size(), BLOCK_ENTITY_TYPES.getEntries().size());

		modEventBus.addListener(this::commonSetup);
		modEventBus.addListener(ElectricityCapabilities::register);
		MinecraftForge.EVENT_BUS.register(this);
	}

	private void commonSetup(final FMLCommonSetupEvent event) {
		event.enqueueWork(() -> {
			ElectricityNetworking.init();
			ObjDefinitions.bootstrap();
			// no-op without CC:Tweaked installed
			ComputerCraftBridge.register();
		});
	}

	@SubscribeEvent
	public void onServerStarted(ServerStartedEvent event) {
		LOGGER.info("Electricity mod initialized on server");

		ServerLevel serverLevel = event.getServer().overworld();
		GlobalWeatherManager.get(serverLevel);
		wireManager.loadFromWorld(serverLevel);
		powerNetwork = new PowerNetwork(serverLevel, wireManager);
	}

	@SubscribeEvent
	public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
		if (event.getEntity() instanceof ServerPlayer player) {
			wireManager.sendAllWiresToPlayer(player);
		}
	}

	@SubscribeEvent
	public void onServerTick(TickEvent.ServerTickEvent event) {
		if (event.phase == TickEvent.Phase.START) {
			for (ServerLevel level : event.getServer().getAllLevels()) {
				// the clock first: everything sampled this tick, weather included, reads the day
				// time, and it should read this tick's rather than last tick's
				RealTimeClock.apply(level);
				GlobalWeatherManager.get(level).tick();
			}
			return;
		}

		if (powerNetwork != null) {
			powerNetwork.updatePowerNetwork();
		}

		// after the levels have ticked, so this is the last word on the time each client holds:
		// vanilla broadcasts its own during that tick and would otherwise start their clocks again
		for (ServerLevel level : event.getServer().getAllLevels()) {
			RealTimeClock.holdClientClocks(level);
		}

		TowerCollapse.tickAll();
	}

	@SubscribeEvent
	public void onServerStopping(ServerStoppingEvent event) {
		ServerLevel serverLevel = event.getServer().overworld();
		wireManager.forceSave(serverLevel);
		for (ServerLevel level : event.getServer().getAllLevels()) {
			GlobalWeatherManager.clear(level);
		}

		TowerCollapse.clear();
	}

	@SubscribeEvent
	public void onLevelUnload(LevelEvent.Unload event) {
		if (event.getLevel() instanceof ServerLevel serverLevel) {
			GlobalWeatherManager.clear(serverLevel);
		}
	}
}
