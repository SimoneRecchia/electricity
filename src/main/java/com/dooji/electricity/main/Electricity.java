package com.dooji.electricity.main;

import com.dooji.electricity.api.power.ElectricityCapabilities;
import com.dooji.electricity.api.power.CombinerSpec;
import com.dooji.electricity.api.power.DcCableSpec;
import com.dooji.electricity.api.power.InverterSpec;
import com.dooji.electricity.api.power.PvArraySpec;
import com.dooji.electricity.api.power.ConductorSpec;
import com.dooji.electricity.api.power.TowerSpec;
import com.dooji.electricity.api.power.SwitchgearSpec;
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
import com.dooji.electricity.block.GroundConductorBlock;
import com.dooji.electricity.block.GroundConductorBlockEntity;
import com.dooji.electricity.block.LatticeTowerBlock;
import com.dooji.electricity.block.TransformerBlock;
import com.dooji.electricity.block.SwitchgearBlock;
import com.dooji.electricity.block.SwitchgearBlockEntity;
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
import com.dooji.electricity.main.registry.SwitchgearCatalog;
import com.dooji.electricity.main.registry.TransformerCatalog;
import com.dooji.electricity.main.registry.TurbineCatalog;
import com.dooji.electricity.main.network.ElectricityNetworking;
import com.dooji.electricity.main.power.PowerNetwork;
import com.dooji.electricity.main.wire.WireManager;
import com.dooji.electricity.main.weather.GlobalWeatherManager;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

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
	public static final Map<ResourceLocation, RegistryObject<Block>> PV_ARRAY_BLOCKS =
			blockFamily(PvCatalog.all(), PvArraySpec::id, spec -> new PvArrayBlock(machine(1.0f, 2.0f), spec));
	public static final Map<ResourceLocation, RegistryObject<Item>> PV_ARRAY_ITEMS =
			itemFamily(PvCatalog.all(), PvArraySpec::id, PV_ARRAY_BLOCKS,
					(spec, block) -> new PvArrayBlockItem(block, new Item.Properties(), spec));

	/** One block per inverter in the catalogue. */
	public static final Map<ResourceLocation, RegistryObject<Block>> PV_INVERTER_BLOCKS =
			blockFamily(InverterCatalog.all(), InverterSpec::id, spec -> new PvInverterBlock(machine(2.5f, 10.0f), spec));
	public static final Map<ResourceLocation, RegistryObject<Item>> PV_INVERTER_ITEMS =
			itemFamily(InverterCatalog.all(), InverterSpec::id, PV_INVERTER_BLOCKS,
					(spec, block) -> new PvInverterBlockItem(block, new Item.Properties(), spec));

	/** A block of cable per gauge */
	public static final Map<ResourceLocation, RegistryObject<Block>> DC_CABLE_BLOCKS =
			blockFamily(CableCatalog.all(), DcCableSpec::id, spec -> new DcCableBlock(DcCableBlock.properties(), spec));
	public static final Map<ResourceLocation, RegistryObject<Item>> DC_CABLE_ITEMS =
			itemFamily(CableCatalog.all(), DcCableSpec::id, DC_CABLE_BLOCKS,
					(spec, block) -> new DcCableItem((DcCableBlock) block, new Item.Properties()));

	/** A combiner box per product: the switchgear between a field of strings and one cabinet. */
	public static final Map<ResourceLocation, RegistryObject<Block>> PV_COMBINER_BLOCKS =
			blockFamily(CombinerCatalog.all(), CombinerSpec::id, spec -> new PvCombinerBlock(machine(1.5f, 6.0f), spec));
	public static final Map<ResourceLocation, RegistryObject<Item>> PV_COMBINER_ITEMS =
			itemFamily(CombinerCatalog.all(), CombinerSpec::id, PV_COMBINER_BLOCKS,
					(spec, block) -> new PvCombinerBlockItem((PvCombinerBlock) block, new Item.Properties()));

	/** One block per lattice tower duty: a line is suspension towers with tension and terminal ones. */
	public static final Map<ResourceLocation, RegistryObject<Block>> LATTICE_TOWER_BLOCKS =
			blockFamily(TowerCatalog.all(), TowerSpec::id, spec -> new LatticeTowerBlock(machine(3.0f, 12.0f), spec));
	public static final Map<ResourceLocation, RegistryObject<Item>> LATTICE_TOWER_ITEMS =
			itemFamily(TowerCatalog.all(), TowerSpec::id, LATTICE_TOWER_BLOCKS, described(TowerSpec::id));

	/**
	 * A block of conductor lying on the ground, per conductor: the reel lays it and the reel strings it.
	 *
	 * Registered as "<id>_run", because the conductor's own id belongs to the reel and a block cannot share
	 * it - the one family whose registry name is not its spec's path.
	 */
	public static final Map<ResourceLocation, RegistryObject<Block>> GROUND_CONDUCTOR_BLOCKS =
			blockFamily(ConductorCatalog.all(), ConductorSpec::id, spec -> spec.id().getPath() + "_run",
					spec -> new GroundConductorBlock(Block.Properties.of(), spec), Map.of());

	/** The two transformers: a machine unit at every generator's foot, a substation unit at the grid. */
	public static final Map<ResourceLocation, RegistryObject<Block>> TRANSFORMER_BLOCKS =
			blockFamily(TransformerCatalog.all(), TransformerSpec::id, spec -> new TransformerBlock(machine(3.0f, 12.0f), spec));
	public static final Map<ResourceLocation, RegistryObject<Item>> TRANSFORMER_ITEMS =
			itemFamily(TransformerCatalog.all(), TransformerSpec::id, TRANSFORMER_BLOCKS, described(TransformerSpec::id));

	/** The two switches: a disconnector to make a gap you can see, a breaker to break load. */
	public static final Map<ResourceLocation, RegistryObject<Block>> SWITCHGEAR_BLOCKS =
			blockFamily(SwitchgearCatalog.all(), SwitchgearSpec::id, spec -> new SwitchgearBlock(machine(2.0f, 8.0f), spec));
	public static final Map<ResourceLocation, RegistryObject<Item>> SWITCHGEAR_ITEMS =
			itemFamily(SwitchgearCatalog.all(), SwitchgearSpec::id, SWITCHGEAR_BLOCKS, described(SwitchgearSpec::id));

	/** A meteorological mast: the seven instruments a plant measures the sky with. */
	public static final RegistryObject<Block> MET_STATION_BLOCK = BLOCKS.register("met_station",
			() -> new MetStationBlock(machine(1.5f, 3.0f)));
	public static final RegistryObject<Item> MET_STATION_ITEM = ITEMS.register("met_station",
			() -> new TooltipBlockItem(MET_STATION_BLOCK.get(), new Item.Properties(), "tooltip.electricity.met_station"));

	/**
	 * A block for every machine in the catalogue, keyed by spec id.
	 *
	 * The C130-4.0 is the machine "wind_turbine" already was, and its registry name is the one every existing
	 * world holds - so it keeps the older registration rather than getting a second one of its own.
	 */
	public static final Map<ResourceLocation, RegistryObject<Block>> TURBINE_BLOCKS =
			blockFamily(TurbineCatalog.all(), TurbineSpec::id, spec -> spec.id().getPath(),
					spec -> new WindTurbineBlock(machine(2.0f, 10.0f), spec),
					Map.of(TurbineCatalog.C130_40.id(), WIND_TURBINE_BLOCK));
	public static final Map<ResourceLocation, RegistryObject<Item>> TURBINE_ITEMS =
			itemFamily(TurbineCatalog.all(), TurbineSpec::id, TURBINE_BLOCKS,
					(spec, block) -> new TurbineBlockItem(block, new Item.Properties(), spec),
					Map.of(TurbineCatalog.C130_40.id(), WIND_TURBINE_ITEM));

	/** What every machine in this mod is built of, differing only in how hard it is to break. */
	private static BlockBehaviour.Properties machine(float hardness, float blastResistance) {
		return Block.Properties.of().strength(hardness, blastResistance).requiresCorrectToolForDrops().noOcclusion();
	}

	/** The item a machine with nothing to say beyond its own tooltip gets. */
	private static <S> BiFunction<S, Block, Item> described(Function<S, ResourceLocation> id) {
		return (spec, block) -> new TooltipBlockItem(block, new Item.Properties(),
				"tooltip.electricity." + id.apply(spec).getPath());
	}

	/**
	 * One block per spec in a catalogue, keyed by its id.
	 *
	 * Nine copies of this loop was nine places to keep in step, and the differences between them were the
	 * factory and, for two families, the registry name and an older registration to reuse. The factory is
	 * deferred because a RegistryObject's supplier has to be.
	 */
	private static <S> Map<ResourceLocation, RegistryObject<Block>> blockFamily(Collection<S> catalogue,
			Function<S, ResourceLocation> id, Function<S, String> name, Function<S, Block> factory,
			Map<ResourceLocation, RegistryObject<Block>> already) {
		Map<ResourceLocation, RegistryObject<Block>> blocks = new LinkedHashMap<>();
		for (S spec : catalogue) {
			ResourceLocation key = id.apply(spec);
			blocks.put(key, already.containsKey(key) ? already.get(key)
					: BLOCKS.register(name.apply(spec), () -> factory.apply(spec)));
		}

		return blocks;
	}

	private static <S> Map<ResourceLocation, RegistryObject<Block>> blockFamily(Collection<S> catalogue,
			Function<S, ResourceLocation> id, Function<S, Block> factory) {
		return blockFamily(catalogue, id, spec -> id.apply(spec).getPath(), factory, Map.of());
	}

	/** And one item per block of a family, which is how a machine gets into a hand. */
	private static <S> Map<ResourceLocation, RegistryObject<Item>> itemFamily(Collection<S> catalogue,
			Function<S, ResourceLocation> id, Map<ResourceLocation, RegistryObject<Block>> blocks,
			BiFunction<S, Block, Item> factory, Map<ResourceLocation, RegistryObject<Item>> already) {
		Map<ResourceLocation, RegistryObject<Item>> items = new LinkedHashMap<>();
		for (S spec : catalogue) {
			ResourceLocation key = id.apply(spec);
			RegistryObject<Block> block = blocks.get(key);
			items.put(key, already.containsKey(key) ? already.get(key)
					: ITEMS.register(key.getPath(), () -> factory.apply(spec, block.get())));
		}

		return items;
	}

	private static <S> Map<ResourceLocation, RegistryObject<Item>> itemFamily(Collection<S> catalogue,
			Function<S, ResourceLocation> id, Map<ResourceLocation, RegistryObject<Block>> blocks,
			BiFunction<S, Block, Item> factory) {
		return itemFamily(catalogue, id, blocks, factory, Map.of());
	}

	/** Every block of a family, so one block entity type can serve the whole catalogue. */
	private static Block[] blocksOf(Map<ResourceLocation, RegistryObject<Block>> family) {
		return family.values().stream().map(RegistryObject::get).toArray(Block[]::new);
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

				// the switches, which is what a player puts between the cabin and the line
				for (SwitchgearSpec spec : SwitchgearCatalog.all()) {
					output.accept(SWITCHGEAR_ITEMS.get(spec.id()).get());
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
	public static RegistryObject<BlockEntityType<GroundConductorBlockEntity>> GROUND_CONDUCTOR_BLOCK_ENTITY;
	public static RegistryObject<BlockEntityType<SwitchgearBlockEntity>> SWITCHGEAR_BLOCK_ENTITY;

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
				() -> BlockEntityType.Builder.of(LatticeTowerBlockEntity::new, blocksOf(LATTICE_TOWER_BLOCKS)).build(null));

		TRANSFORMER_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register("transformer",
				() -> BlockEntityType.Builder.of(TransformerBlockEntity::new, blocksOf(TRANSFORMER_BLOCKS)).build(null));

		GROUND_CONDUCTOR_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register("ground_conductor",
				() -> BlockEntityType.Builder.of(GroundConductorBlockEntity::new, blocksOf(GROUND_CONDUCTOR_BLOCKS)).build(null));

		// one type for the whole catalogue: the machines differ by their spec
		// block entity reads back off whichever block it is sitting in
		WIND_TURBINE_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register("wind_turbine", () -> BlockEntityType.Builder.of(WindTurbineBlockEntity::new, blocksOf(TURBINE_BLOCKS)).build(null));

		// stateless and never ticked: it only exists so the renderer can find a tower that has
		// no machine on it yet, which is every tower while it is being stacked
		TURBINE_TOWER_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register("turbine_tower", () -> BlockEntityType.Builder.of(TurbineTowerBlockEntity::new, TURBINE_TOWER_BLOCK.get()).build(null));

		// one type for the whole array catalogue
		// products differ by their spec
		PV_ARRAY_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register("pv_array", () -> BlockEntityType.Builder.of(PvArrayBlockEntity::new, blocksOf(PV_ARRAY_BLOCKS)).build(null));

		PV_INVERTER_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register("pv_inverter", () -> BlockEntityType.Builder.of(PvInverterBlockEntity::new, blocksOf(PV_INVERTER_BLOCKS)).build(null));

		PV_COMBINER_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register("pv_combiner", () -> BlockEntityType.Builder.of(PvCombinerBlockEntity::new, blocksOf(PV_COMBINER_BLOCKS)).build(null));

		MET_STATION_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register("met_station", () -> BlockEntityType.Builder.of(MetStationBlockEntity::new, MET_STATION_BLOCK.get()).build(null));

		SWITCHGEAR_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register("switchgear", () -> BlockEntityType.Builder.of(SwitchgearBlockEntity::new, blocksOf(SWITCHGEAR_BLOCKS)).build(null));

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
