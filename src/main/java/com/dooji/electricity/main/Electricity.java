package com.dooji.electricity.main;

import com.dooji.electricity.api.power.ElectricityCapabilities;
import com.dooji.electricity.api.power.CombinerSpec;
import com.dooji.electricity.api.power.DcCableSpec;
import com.dooji.electricity.api.power.InverterSpec;
import com.dooji.electricity.api.power.PvArraySpec;
import com.dooji.electricity.api.power.TowerSpec;
import com.dooji.electricity.api.power.TransformerSpec;
import com.dooji.electricity.api.power.TurbineSpec;
import com.dooji.electricity.block.DcCableBlock;
import com.dooji.electricity.block.ElectricCabinBlock;
import com.dooji.electricity.block.ElectricCabinBlockEntity;
import com.dooji.electricity.block.MachineShellBlock;
import com.dooji.electricity.block.MetStationBlock;
import com.dooji.electricity.block.MetStationBlockEntity;
import com.dooji.electricity.block.PowerBoxBlock;
import com.dooji.electricity.block.PvArrayBlock;
import com.dooji.electricity.block.PvCombinerBlock;
import com.dooji.electricity.block.PvCombinerBlockEntity;
import com.dooji.electricity.block.PvArrayBlockEntity;
import com.dooji.electricity.block.PvInverterBlock;
import com.dooji.electricity.block.PvInverterBlockEntity;
import com.dooji.electricity.block.PowerBoxBlockEntity;
import com.dooji.electricity.block.LatticeTowerBlock;
import com.dooji.electricity.block.TransformerBlock;
import com.dooji.electricity.block.TransformerBlockEntity;
import com.dooji.electricity.block.LatticeTowerBlockEntity;
import com.dooji.electricity.block.UtilityPoleBlock;
import com.dooji.electricity.block.UtilityPoleBlockEntity;
import com.dooji.electricity.block.TowerCollapse;
import com.dooji.electricity.block.TurbineTowerBlock;
import com.dooji.electricity.block.TurbineTowerBlockEntity;
import com.dooji.electricity.block.WindTurbineBlock;
import com.dooji.electricity.block.WindTurbineBlockEntity;
import com.dooji.electricity.compat.computercraft.ComputerCraftBridge;
import com.dooji.electricity.item.DcCableItem;
import com.dooji.electricity.item.PvCombinerBlockItem;
import com.dooji.electricity.item.ConductorItem;
import com.dooji.electricity.item.PowerWrenchItem;
import com.dooji.electricity.item.PvArrayBlockItem;
import com.dooji.electricity.item.PvInverterBlockItem;
import com.dooji.electricity.item.TooltipBlockItem;
import com.dooji.electricity.item.TooltipItem;
import com.dooji.electricity.item.TurbineBlockItem;
import com.dooji.electricity.main.registry.CableCatalog;
import com.dooji.electricity.main.registry.ConductorCatalog;
import com.dooji.electricity.main.registry.CombinerCatalog;
import com.dooji.electricity.main.registry.InverterCatalog;
import com.dooji.electricity.main.registry.ObjDefinitions;
import com.dooji.electricity.main.registry.PartCatalog;
import com.dooji.electricity.main.registry.PvCatalog;
import com.dooji.electricity.main.registry.TowerCatalog;
import com.dooji.electricity.main.registry.TransformerCatalog;
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
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.common.MinecraftForge;
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

	public static final RegistryObject<Block> UTILITY_POLE_BLOCK = BLOCKS.register("utility_pole", () -> new UtilityPoleBlock(Block.Properties.of().strength(2.0f, 10.0f).requiresCorrectToolForDrops().noOcclusion()));
	public static final RegistryObject<Block> ELECTRIC_CABIN_BLOCK = BLOCKS.register("electric_cabin", () -> new ElectricCabinBlock(Block.Properties.of().strength(2.0f, 10.0f).requiresCorrectToolForDrops().noOcclusion()));
	public static final RegistryObject<Block> POWER_BOX_BLOCK = BLOCKS.register("power_box", () -> new PowerBoxBlock(Block.Properties.of().strength(2.0f, 10.0f).requiresCorrectToolForDrops().noOcclusion()));
	// The original block keeps its "wind_turbine" registry name so existing worlds load,
	public static final RegistryObject<Block> WIND_TURBINE_BLOCK = BLOCKS.register("wind_turbine", () -> new WindTurbineBlock(Block.Properties.of().strength(2.0f, 10.0f).requiresCorrectToolForDrops().noOcclusion(), TurbineCatalog.C130_40));

	/** The rest of a machine that is bigger than its own block: invisible, solid, and never an item. */
	public static final RegistryObject<Block> MACHINE_SHELL_BLOCK = BLOCKS.register("machine_shell",
			() -> new MachineShellBlock(Block.Properties.of().strength(2.0f, 10.0f).requiresCorrectToolForDrops()
					.noOcclusion().noLootTable().pushReaction(PushReaction.BLOCK)));

	/**
	 * The three overhead line conductors, one item a class.
	  *
	 * See {@link ConductorCatalog}.
	 */
	public static final Map<ResourceLocation, RegistryObject<Item>> CONDUCTOR_ITEMS = conductorItems();

	private static Map<ResourceLocation, RegistryObject<Item>> conductorItems() {
		Map<ResourceLocation, RegistryObject<Item>> out = new LinkedHashMap<>();
		for (var spec : ConductorCatalog.all()) {
			out.put(spec.id(), ITEMS.register(spec.id().getPath(), () -> new ConductorItem(spec)));
		}

		return Map.copyOf(out);
	}
	public static final RegistryObject<Item> POWER_WRENCH_ITEM = ITEMS.register("power_wrench", PowerWrenchItem::new);
	public static final RegistryObject<Item> UTILITY_POLE_ITEM = ITEMS.register("utility_pole", () -> new TooltipBlockItem(UTILITY_POLE_BLOCK.get(), new Item.Properties(), "tooltip.electricity.utility_pole"));
	public static final RegistryObject<Item> ELECTRIC_CABIN_ITEM = ITEMS.register("electric_cabin", () -> new TooltipBlockItem(ELECTRIC_CABIN_BLOCK.get(), new Item.Properties(), "tooltip.electricity.electric_cabin"));
	public static final RegistryObject<Item> POWER_BOX_ITEM = ITEMS.register("power_box", () -> new TooltipBlockItem(POWER_BOX_BLOCK.get(), new Item.Properties(), "tooltip.electricity.power_box"));
	public static final RegistryObject<Item> WIND_TURBINE_ITEM = ITEMS.register("wind_turbine", () -> new TurbineBlockItem(WIND_TURBINE_BLOCK.get(), new Item.Properties(), TurbineCatalog.C130_40));
	public static final RegistryObject<Item> WEATHER_TABLET_ITEM = ITEMS.register("weather_tablet", () -> new TooltipItem(new Item.Properties().stacksTo(1), "tooltip.electricity.weather_tablet"));
	public static final RegistryObject<Item> CIRCUIT_BOARD_ITEM = ITEMS.register("circuit_board", () -> new TooltipItem(new Item.Properties(), "tooltip.electricity.circuit_board"));
	public static final RegistryObject<Item> CPU_ITEM = ITEMS.register("cpu", () -> new TooltipItem(new Item.Properties(), "tooltip.electricity.cpu"));
	public static final RegistryObject<Item> SCREEN_ITEM = ITEMS.register("screen", () -> new TooltipItem(new Item.Properties(), "tooltip.electricity.screen"));
	public static final RegistryObject<Item> INSULATOR_ITEM = ITEMS.register("insulator", () -> new TooltipItem(new Item.Properties(), "tooltip.electricity.insulator"));
	public static final RegistryObject<Item> METAL_CASING_ITEM = ITEMS.register("metal_casing", () -> new TooltipItem(new Item.Properties(), "tooltip.electricity.metal_casing"));
	public static final RegistryObject<Item> MOTOR_CORE_ITEM = ITEMS.register("motor_core", () -> new TooltipItem(new Item.Properties(), "tooltip.electricity.motor_core"));

	/** Every part in {@link PartCatalog} */
	public static final Map<String, RegistryObject<Item>> PART_ITEMS = registerParts();

	private static Map<String, RegistryObject<Item>> registerParts() {
		Map<String, RegistryObject<Item>> items = new LinkedHashMap<>();
		for (PartCatalog.Part part : PartCatalog.all()) {
			items.put(part.id(), ITEMS.register(part.id(),
					() -> new TooltipItem(new Item.Properties(), "tooltip." + MOD_ID + "." + part.id())));
		}

		return items;
	}

	/** One block of turbine tower. */
	public static final RegistryObject<Block> TURBINE_TOWER_BLOCK = BLOCKS.register("turbine_tower",
			() -> new TurbineTowerBlock(Block.Properties.of().strength(2.0f, 10.0f).requiresCorrectToolForDrops().noOcclusion()));
	public static final RegistryObject<Item> TURBINE_TOWER_ITEM = ITEMS.register("turbine_tower",
			() -> new TooltipBlockItem(TURBINE_TOWER_BLOCK.get(), new Item.Properties(), "tooltip.electricity.turbine_tower"));

	/** A block for every array product, keyed by spec id. */
	public static final Map<ResourceLocation, RegistryObject<Block>> PV_ARRAY_BLOCKS = registerPvArrayBlocks();
	public static final Map<ResourceLocation, RegistryObject<Item>> PV_ARRAY_ITEMS = registerPvArrayItems();

	/** One block per inverter in the catalogue. */
	public static final Map<ResourceLocation, RegistryObject<Block>> PV_INVERTER_BLOCKS = registerInverterBlocks();
	public static final Map<ResourceLocation, RegistryObject<Item>> PV_INVERTER_ITEMS = registerInverterItems();

	/** A block of cable per gauge */
	public static final Map<ResourceLocation, RegistryObject<Block>> DC_CABLE_BLOCKS = registerCableBlocks();
	public static final Map<ResourceLocation, RegistryObject<Item>> DC_CABLE_ITEMS = registerCableItems();

	/** A combiner box per product: the switchgear between a field of strings and one cabinet. */
	public static final Map<ResourceLocation, RegistryObject<Block>> PV_COMBINER_BLOCKS = registerCombinerBlocks();
	public static final Map<ResourceLocation, RegistryObject<Item>> PV_COMBINER_ITEMS = registerCombinerItems();

	/** One block per lattice tower duty: a line is suspension towers with tension and terminal ones. */
	public static final Map<ResourceLocation, RegistryObject<Block>> LATTICE_TOWER_BLOCKS = registerTowerBlocks();
	public static final Map<ResourceLocation, RegistryObject<Item>> LATTICE_TOWER_ITEMS = registerTowerItems();

	/** The two transformers: a machine unit at every generator's foot, a substation unit at the grid. */
	public static final Map<ResourceLocation, RegistryObject<Block>> TRANSFORMER_BLOCKS = registerTransformerBlocks();
	public static final Map<ResourceLocation, RegistryObject<Item>> TRANSFORMER_ITEMS = registerTransformerItems();

	/** A meteorological mast: the seven instruments a plant measures the sky with. */
	public static final RegistryObject<Block> MET_STATION_BLOCK = BLOCKS.register("met_station",
			() -> new MetStationBlock(Block.Properties.of().strength(1.5f, 3.0f).requiresCorrectToolForDrops().noOcclusion()));
	public static final RegistryObject<Item> MET_STATION_ITEM = ITEMS.register("met_station",
			() -> new TooltipBlockItem(MET_STATION_BLOCK.get(), new Item.Properties(), "tooltip.electricity.met_station"));

	private static Map<ResourceLocation, RegistryObject<Block>> registerCableBlocks() {
		Map<ResourceLocation, RegistryObject<Block>> blocks = new LinkedHashMap<>();
		for (DcCableSpec spec : CableCatalog.all()) {
			blocks.put(spec.id(), BLOCKS.register(spec.id().getPath(), () -> new DcCableBlock(DcCableBlock.properties(), spec)));
		}

		return blocks;
	}

	private static Map<ResourceLocation, RegistryObject<Item>> registerCableItems() {
		Map<ResourceLocation, RegistryObject<Item>> items = new LinkedHashMap<>();
		for (DcCableSpec spec : CableCatalog.all()) {
			RegistryObject<Block> block = DC_CABLE_BLOCKS.get(spec.id());
			items.put(spec.id(), ITEMS.register(spec.id().getPath(),
					() -> new DcCableItem((DcCableBlock) block.get(), new Item.Properties())));
		}

		return items;
	}

	private static Map<ResourceLocation, RegistryObject<Block>> registerCombinerBlocks() {
		Map<ResourceLocation, RegistryObject<Block>> blocks = new LinkedHashMap<>();
		for (CombinerSpec spec : CombinerCatalog.all()) {
			blocks.put(spec.id(), BLOCKS.register(spec.id().getPath(), () -> new PvCombinerBlock(combinerProperties(), spec)));
		}

		return blocks;
	}

	private static Map<ResourceLocation, RegistryObject<Item>> registerCombinerItems() {
		Map<ResourceLocation, RegistryObject<Item>> items = new LinkedHashMap<>();
		for (CombinerSpec spec : CombinerCatalog.all()) {
			RegistryObject<Block> block = PV_COMBINER_BLOCKS.get(spec.id());
			items.put(spec.id(), ITEMS.register(spec.id().getPath(),
					() -> new PvCombinerBlockItem((PvCombinerBlock) block.get(), new Item.Properties())));
		}

		return items;
	}

	private static BlockBehaviour.Properties combinerProperties() {
		return Block.Properties.of().strength(1.5f, 6.0f).requiresCorrectToolForDrops().noOcclusion();
	}

	/** Every combiner block, so one block entity type serves the whole catalogue. */
	private static Block[] pvCombinerBlocks() {
		return PV_COMBINER_BLOCKS.values().stream().map(RegistryObject::get).toArray(Block[]::new);
	}

	private static BlockBehaviour.Properties arrayProperties() {
		return Block.Properties.of().strength(1.0f, 2.0f).requiresCorrectToolForDrops().noOcclusion();
	}

	private static BlockBehaviour.Properties inverterProperties() {
		return Block.Properties.of().strength(2.5f, 10.0f).requiresCorrectToolForDrops().noOcclusion();
	}

	private static Map<ResourceLocation, RegistryObject<Block>> registerPvArrayBlocks() {
		Map<ResourceLocation, RegistryObject<Block>> blocks = new LinkedHashMap<>();
		for (PvArraySpec spec : PvCatalog.all()) {
			blocks.put(spec.id(), BLOCKS.register(spec.id().getPath(), () -> new PvArrayBlock(arrayProperties(), spec)));
		}

		return blocks;
	}

	private static Map<ResourceLocation, RegistryObject<Item>> registerPvArrayItems() {
		Map<ResourceLocation, RegistryObject<Item>> items = new LinkedHashMap<>();
		for (PvArraySpec spec : PvCatalog.all()) {
			RegistryObject<Block> block = PV_ARRAY_BLOCKS.get(spec.id());
			items.put(spec.id(), ITEMS.register(spec.id().getPath(), () -> new PvArrayBlockItem(block.get(), new Item.Properties(), spec)));
		}

		return items;
	}

	private static Map<ResourceLocation, RegistryObject<Block>> registerTransformerBlocks() {
		Map<ResourceLocation, RegistryObject<Block>> blocks = new LinkedHashMap<>();
		for (TransformerSpec spec : TransformerCatalog.all()) {
			blocks.put(spec.id(), BLOCKS.register(spec.id().getPath(),
					() -> new TransformerBlock(Block.Properties.of().strength(3.0f, 12.0f).requiresCorrectToolForDrops().noOcclusion(), spec)));
		}

		return blocks;
	}

	private static Map<ResourceLocation, RegistryObject<Item>> registerTransformerItems() {
		Map<ResourceLocation, RegistryObject<Item>> items = new LinkedHashMap<>();
		for (TransformerSpec spec : TransformerCatalog.all()) {
			RegistryObject<Block> block = TRANSFORMER_BLOCKS.get(spec.id());
			items.put(spec.id(), ITEMS.register(spec.id().getPath(),
					() -> new TooltipBlockItem(block.get(), new Item.Properties(), "tooltip.electricity." + spec.id().getPath())));
		}

		return items;
	}

	private static Map<ResourceLocation, RegistryObject<Block>> registerTowerBlocks() {
		Map<ResourceLocation, RegistryObject<Block>> blocks = new LinkedHashMap<>();
		for (TowerSpec spec : TowerCatalog.all()) {
			blocks.put(spec.id(), BLOCKS.register(spec.id().getPath(),
					() -> new LatticeTowerBlock(Block.Properties.of().strength(3.0f, 12.0f).requiresCorrectToolForDrops().noOcclusion(), spec)));
		}

		return blocks;
	}

	private static Map<ResourceLocation, RegistryObject<Item>> registerTowerItems() {
		Map<ResourceLocation, RegistryObject<Item>> items = new LinkedHashMap<>();
		for (TowerSpec spec : TowerCatalog.all()) {
			RegistryObject<Block> block = LATTICE_TOWER_BLOCKS.get(spec.id());
			items.put(spec.id(), ITEMS.register(spec.id().getPath(),
					() -> new TooltipBlockItem(block.get(), new Item.Properties(), "tooltip.electricity." + spec.id().getPath())));
		}

		return items;
	}

	private static Map<ResourceLocation, RegistryObject<Block>> registerInverterBlocks() {
		Map<ResourceLocation, RegistryObject<Block>> blocks = new LinkedHashMap<>();
		for (InverterSpec spec : InverterCatalog.all()) {
			blocks.put(spec.id(), BLOCKS.register(spec.id().getPath(), () -> new PvInverterBlock(inverterProperties(), spec)));
		}

		return blocks;
	}

	private static Map<ResourceLocation, RegistryObject<Item>> registerInverterItems() {
		Map<ResourceLocation, RegistryObject<Item>> items = new LinkedHashMap<>();
		for (InverterSpec spec : InverterCatalog.all()) {
			RegistryObject<Block> block = PV_INVERTER_BLOCKS.get(spec.id());
			items.put(spec.id(), ITEMS.register(spec.id().getPath(), () -> new PvInverterBlockItem(block.get(), new Item.Properties(), spec)));
		}

		return items;
	}

	/** Every array block, so one block entity type serves the whole catalogue. */
	private static Block[] pvArrayBlocks() {
		return PV_ARRAY_BLOCKS.values().stream().map(RegistryObject::get).toArray(Block[]::new);
	}

	private static Block[] pvInverterBlocks() {
		return PV_INVERTER_BLOCKS.values().stream().map(RegistryObject::get).toArray(Block[]::new);
	}

	/** A block for every machine in the catalogue, keyed by spec id. */
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

	private static Block[] latticeTowerBlocks() {
		return LATTICE_TOWER_BLOCKS.values().stream().map(RegistryObject::get).toArray(Block[]::new);
	}

	private static Block[] transformerBlocks() {
		return TRANSFORMER_BLOCKS.values().stream().map(RegistryObject::get).toArray(Block[]::new);
	}


	public static final RegistryObject<CreativeModeTab> ELECTRICITY_TAB = CREATIVE_TABS.register("main",
			() -> CreativeModeTab.builder().title(Component.translatable("itemGroup." + MOD_ID + ".main")).icon(() -> new ItemStack(CONDUCTOR_ITEMS.get(ConductorCatalog.AAAC_228.id()).get())).displayItems((parameters, output) -> {
				output.accept(POWER_WRENCH_ITEM.get());
				for (var spec : ConductorCatalog.all()) {
					output.accept(CONDUCTOR_ITEMS.get(spec.id()).get());
				}
				// the two transformers, in the order the current passes through them
				for (TransformerSpec spec : TransformerCatalog.all()) {
					output.accept(TRANSFORMER_ITEMS.get(spec.id()).get());
				}

				output.accept(UTILITY_POLE_ITEM.get());
				// the transmission line: the towers a substation's output actually leaves on
				for (TowerSpec spec : TowerCatalog.all()) {
					output.accept(LATTICE_TOWER_ITEMS.get(spec.id()).get());
				}

				output.accept(ELECTRIC_CABIN_ITEM.get());
				output.accept(POWER_BOX_ITEM.get());
				// in catalogue order, which is the order a player builds them
				for (TurbineSpec spec : TurbineCatalog.all()) {
					output.accept(TURBINE_ITEMS.get(spec.id()).get());
				}

				output.accept(TURBINE_TOWER_ITEM.get());
				// the arrays, then the inverters they need
				// order a plant is actually built in
				for (PvArraySpec spec : PvCatalog.all()) {
					output.accept(PV_ARRAY_ITEMS.get(spec.id()).get());
				}

				// the cable between them, because that is the order it is built in: racking, then the copper,
				// then the machine the copper goes to
				for (DcCableSpec spec : CableCatalog.all()) {
					output.accept(DC_CABLE_ITEMS.get(spec.id()).get());
				}

				for (CombinerSpec spec : CombinerCatalog.all()) {
					output.accept(PV_COMBINER_ITEMS.get(spec.id()).get());
				}

				for (InverterSpec spec : InverterCatalog.all()) {
					output.accept(PV_INVERTER_ITEMS.get(spec.id()).get());
				}

				output.accept(MET_STATION_ITEM.get());
				output.accept(CIRCUIT_BOARD_ITEM.get());
				output.accept(CPU_ITEM.get());
				output.accept(SCREEN_ITEM.get());
				output.accept(INSULATOR_ITEM.get());
				output.accept(METAL_CASING_ITEM.get());
				output.accept(MOTOR_CORE_ITEM.get());
				// the parts, in the order they are made: stock, then components, then assemblies
				for (PartCatalog.Part part : PartCatalog.all()) {
					output.accept(PART_ITEMS.get(part.id()).get());
				}
			}).build());

	public static RegistryObject<BlockEntityType<UtilityPoleBlockEntity>> UTILITY_POLE_BLOCK_ENTITY;
	public static RegistryObject<BlockEntityType<ElectricCabinBlockEntity>> ELECTRIC_CABIN_BLOCK_ENTITY;
	public static RegistryObject<BlockEntityType<PowerBoxBlockEntity>> POWER_BOX_BLOCK_ENTITY;
	public static RegistryObject<BlockEntityType<WindTurbineBlockEntity>> WIND_TURBINE_BLOCK_ENTITY;
	public static RegistryObject<BlockEntityType<TurbineTowerBlockEntity>> TURBINE_TOWER_BLOCK_ENTITY;
	public static RegistryObject<BlockEntityType<PvArrayBlockEntity>> PV_ARRAY_BLOCK_ENTITY;
	public static RegistryObject<BlockEntityType<PvInverterBlockEntity>> PV_INVERTER_BLOCK_ENTITY;
	public static RegistryObject<BlockEntityType<PvCombinerBlockEntity>> PV_COMBINER_BLOCK_ENTITY;
	public static RegistryObject<BlockEntityType<MetStationBlockEntity>> MET_STATION_BLOCK_ENTITY;
	public static RegistryObject<BlockEntityType<LatticeTowerBlockEntity>> LATTICE_TOWER_BLOCK_ENTITY;
	public static RegistryObject<BlockEntityType<TransformerBlockEntity>> TRANSFORMER_BLOCK_ENTITY;

	public static final WireManager wireManager = new WireManager();
	public static PowerNetwork powerNetwork;

	public Electricity() {
		IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
		BLOCKS.register(modEventBus);
		ITEMS.register(modEventBus);
		CREATIVE_TABS.register(modEventBus);
		ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, ElectricityServerConfig.spec(), "Electricity/server.toml");
		// has to be now, before any level data is read: the rule set is deserialised with the
		// world, so a rule registered later would be missing from a world that had it set
		ElectricityGameRules.register();

		UTILITY_POLE_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register("utility_pole", () -> BlockEntityType.Builder.of(UtilityPoleBlockEntity::new, UTILITY_POLE_BLOCK.get()).build(null));

		ELECTRIC_CABIN_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register("electric_cabin", () -> BlockEntityType.Builder.of(ElectricCabinBlockEntity::new, ELECTRIC_CABIN_BLOCK.get()).build(null));

		POWER_BOX_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register("power_box", () -> BlockEntityType.Builder.of(PowerBoxBlockEntity::new, POWER_BOX_BLOCK.get()).build(null));

		// one type for all three duties: they differ by their spec, and the entity reads it back off
		// whichever block it is sitting in
		LATTICE_TOWER_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register("lattice_tower",
				() -> BlockEntityType.Builder.of(LatticeTowerBlockEntity::new, latticeTowerBlocks()).build(null));

		TRANSFORMER_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register("transformer",
				() -> BlockEntityType.Builder.of(TransformerBlockEntity::new, transformerBlocks()).build(null));

		// one type for the whole catalogue: the machines differ by their spec
		// block entity reads back off whichever block it is sitting in
		WIND_TURBINE_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register("wind_turbine", () -> BlockEntityType.Builder.of(WindTurbineBlockEntity::new, turbineBlocks()).build(null));

		// stateless and never ticked: it only exists so the renderer can find a tower that has
		// no machine on it yet, which is every tower while it is being stacked
		TURBINE_TOWER_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register("turbine_tower", () -> BlockEntityType.Builder.of(TurbineTowerBlockEntity::new, TURBINE_TOWER_BLOCK.get()).build(null));

		// one type for the whole array catalogue
		// products differ by their spec
		PV_ARRAY_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register("pv_array", () -> BlockEntityType.Builder.of(PvArrayBlockEntity::new, pvArrayBlocks()).build(null));

		PV_INVERTER_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register("pv_inverter", () -> BlockEntityType.Builder.of(PvInverterBlockEntity::new, pvInverterBlocks()).build(null));

		PV_COMBINER_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register("pv_combiner", () -> BlockEntityType.Builder.of(PvCombinerBlockEntity::new, pvCombinerBlocks()).build(null));

		MET_STATION_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register("met_station", () -> BlockEntityType.Builder.of(MetStationBlockEntity::new, MET_STATION_BLOCK.get()).build(null));

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
