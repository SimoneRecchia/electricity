package com.dooji.electricity.block;

import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * One block of turbine tower.
 *
 * A tower is stacked by hand, which is what makes hub height something a player
 * builds rather than a number they set. The machine goes on top and refuses to mount
 * outside the range of tower heights it is certified for.
 *
 * <h2>What you see and what you walk into</h2>
 *
 * The tower is drawn from the authored model's own tube, so it looks exactly as it always
 * did. Collision cannot follow that geometry exactly - a VoxelShape is a union of
 * axis-aligned boxes confined to the block's own cube, so neither a circle nor a cone is
 * on offer - and where it cannot, it errs inwards. Everything you can walk into is inside
 * something you can see.
 *
 * It is round rather than square, by {@link #STRIPS} strips laid each way, and it narrows
 * with the tube: each block takes the radius at its own top, the narrowest the tube gets
 * within it, so the collision is inside the steel over that block's whole height.
 *
 * That taper carries a cost worth naming. A collision that narrows leaves an outward-facing
 * ledge at every block boundary, and in Minecraft a ledge of any width supports a player, so
 * a tapered tower can be climbed. The ledges are a block's worth of taper each - 0.085 pixels
 * on a thirteen-block tower, 0.28 on a four-block one - and nothing made of axis-aligned
 * boxes can taper and be unclimbable at once. One radius for the whole tower is the only
 * profile without a ledge, and it is 1.1 pixels out at the foot instead.
 *
 * The 0.622 that looks like the tower's width is only the base plinth, eight centimetres
 * tall, and is not collided with at all.
 *
 * {@link #THICKNESS} carries how wide this tower is, from the machine standing on it, and
 * {@link #TAPER} where in the tube each block falls. {@link #refreshThickness} settles both
 * whenever the structure changes, so {@link #getShape} stays an array lookup - it is asked
 * far more often than a tower is ever built.
 */
public class TurbineTowerBlock extends Block implements EntityBlock {
	/**
	 * Blocks a tower carries without complaint.
	 *
	 * Sixteen is 160 m at this mod's ten-metres-a-block scale, which is about the tallest
	 * unguyed tube tower ever raised onshore, and comfortably above the 130 m the tallest
	 * machine in the catalogue is certified for. Anything past it is a pillar rather than a
	 * turbine tower.
	 */
	public static final int SAFE_HEIGHT = 16;
	/**
	 * Blocks a tower cannot hold up at all, even undisturbed.
	 *
	 * 200 m, past anything ever built. Between here and {@link #SAFE_HEIGHT} a tower stands
	 * but carries nothing: step on it and the excess comes down.
	 */
	public static final int MAX_HEIGHT = 20;
	/**
	 * Where a walk up or down a tower gives up.
	 *
	 * Clear of {@link #MAX_HEIGHT} rather than level with it, because this is a guard against
	 * an unbounded scan and not a limit anything should reach. When it was set at the height a
	 * player could actually build to, the renderer stopped drawing tube past it and towers went
	 * invisible from there up.
	 */
	private static final int SCAN_LIMIT = MAX_HEIGHT + 4;
	/**
	 * The authored tube's radius where it leaves the base plinth, in blocks, at C130 scale.
	 *
	 * The plinth itself is wider than anything else on the tower - 0.622, twice the tube - but it
	 * is only eight centimetres tall and nothing collides with it. The tube proper is one cone
	 * from here to {@link #TOP_RADIUS}, and it is the tube that the collision traces.
	 */
	private static final double FOOT_RADIUS = 0.381;
	/** And at the top, which is the narrowest the tube ever gets. */
	private static final double TOP_RADIUS = 0.312;
	/**
	 * Strips per quadrant used to round the collision off.
	 *
	 * A VoxelShape is a union of axis-aligned boxes, so a circle has to be approximated, and the
	 * two crossed boxes this used before were the wrong approximation. Their union is a plus, and
	 * its four corners stand 22% outside the tube they were meant to trace: right on the axes and
	 * badly wrong on the diagonals, which is exactly what reads as square when you walk round a
	 * tower.
	 *
	 * Six strips each way instead - across the shallow arcs and down the steep ones, since neither
	 * direction alone can follow a circle where it turns away from that direction. Twelve boxes,
	 * and each one is cut to the chord at its far edge so that no box anywhere reaches past the
	 * radius it was built for. That is by construction rather than by measurement, which is
	 * what makes it safe to say the collision never stands outside the tube: a balanced fit
	 * looks tighter on paper but its boxes' corners do reach past the circle, and past the tube
	 * with them.
	 */
	private static final int STRIPS = 6;

	/**
	 * How thick this tower is drawn and collided at, as a step on a ladder from
	 * {@link #THINNEST} to full size.
	 *
	 * A tower has to narrow with its machine or the proportions come apart: the authored tube
	 * is right for a C130, whose nacelle is nearly a block tall, and nearly three times too
	 * wide for an SW-10, whose nacelle is a quarter of one. Since the blocks are generic there
	 * is nothing on them to say which machine they belong to, so the answer is held in the
	 * state - which also keeps {@link #getShape} an array lookup, and it is asked far more
	 * often than a tower changes.
	 */
	public static final IntegerProperty THICKNESS = IntegerProperty.create("thickness", 0, 7);
	/**
	 * How far up its tower this block sits, in sixteenths.
	 *
	 * One radius for a whole tower left the collision hidden well inside the flare at the foot -
	 * 1.1 pixels of it - while sitting flush at the top, which is visible the moment an F3+B
	 * wireframe goes on. Each block now traces the tube over its own span instead.
	 *
	 * Held as a fraction of the way up rather than as a segment index because the tube is
	 * stretched over whatever height the tower is, so an index alone would not say where in the
	 * taper a block falls; the fraction does, and needs no third number to read it.
	 *
	 * This is the trade that comes with it: a collision that narrows has an outward-facing ledge
	 * at every block boundary, and in Minecraft a ledge of any width supports a player. They are
	 * small - a block's worth of taper is 0.085 pixels on a thirteen-block tower and 0.28 on a
	 * four-block one, against the 0.32 to 0.47 that were noticed before - but they are there.
	 * Nothing made of axis-aligned boxes can taper and be unclimbable at once.
	 */
	public static final IntegerProperty TAPER = IntegerProperty.create("taper", 0, 15);
	private static final int TAPER_STEPS = 16;
	/** Thinnest a tower is drawn, matching the smallest nacelle in the catalogue. */
	private static final double THINNEST = 0.25;
	private static final int STEPS = 8;

	private static final VoxelShape[][] SHAPES = buildShapes();
	/**
	 * Thickness step a tower with nothing on it is drawn at, by its height.
	 *
	 * A bare tower has no machine to take its proportion from, and its height cannot supply one
	 * either: the certified bands overlap so heavily that at nine or ten blocks a C80, C90, C112
	 * and C130 could all be coming, wanting steps 3, 4, 6 and 7. A spread of four means no
	 * function of height can land within better than two steps of every machine, whatever shape
	 * it takes.
	 *
	 * So this is not a curve but the midpoint of that ambiguity at each height, which reaches
	 * that floor of two. A tower therefore changes by at most two steps when its machine finally
	 * goes on - and a short tower, where the ambiguity is small, barely changes at all.
	 */
	private static final int[] BARE_STEP_BY_HEIGHT = {0, 0, 0, 0, 1, 1, 2, 2, 4, 5, 5, 6, 7, 7, 7, 7, 7, 7, 7, 7, 7};

	/** Guards the state writes in {@link #refreshThickness} from re-entering through their own updates. */
	private static boolean refreshing;

	public TurbineTowerBlock(Properties properties) {
		super(properties);
		// full size by default, so a tower saved before this property existed keeps the look it
		// had until the next thing a player does to it settles the question
		this.registerDefaultState(this.stateDefinition.any().setValue(THICKNESS, STEPS - 1).setValue(TAPER, 0));
	}

	private static VoxelShape[][] buildShapes() {
		VoxelShape[][] shapes = new VoxelShape[STEPS][TAPER_STEPS];
		for (int thickness = 0; thickness < STEPS; thickness++) {
			for (int taper = 0; taper < TAPER_STEPS; taper++) {
				shapes[thickness][taper] = roundColumn(radiusAt(thickness, taper));
			}
		}

		return shapes;
	}

	/** Tube radius for a tower this thick, at this fraction of the way up it. */
	private static double radiusAt(int thickness, int taper) {
		return Mth.lerp(taper / (TAPER_STEPS - 1.0), FOOT_RADIUS, TOP_RADIUS) * scaleOf(thickness);
	}

	/**
	 * A column as round as axis-aligned boxes get.
	 *
	 * Each strip is cut to the chord at its far edge, so the column is inscribed: it falls short
	 * of the circle between the strips and never reaches past it. Being inside is the whole point
	 * here, since anything outside is collision a player meets where there is nothing drawn.
	 */
	private static VoxelShape roundColumn(double radius) {
		double span = radius / Math.sqrt(2.0);
		VoxelShape shape = Shapes.empty();

		for (int strip = 0; strip < STRIPS; strip++) {
			double near = -span + 2.0 * span * strip / STRIPS;
			double far = -span + 2.0 * span * (strip + 1) / STRIPS;
			double half = chord(radius, Math.max(Math.abs(near), Math.abs(far)));

			// laid both ways: across the shallow arcs, and down the steep ones
			shape = Shapes.or(shape,
					Shapes.box(0.5 + near, 0.0, 0.5 - half, 0.5 + far, 1.0, 0.5 + half),
					Shapes.box(0.5 - half, 0.0, 0.5 + near, 0.5 + half, 1.0, 0.5 + far));
		}

		return shape;
	}

	/** Half-width of a circle at a given distance from its centre. */
	private static double chord(double radius, double offset) {
		double clamped = Math.min(offset, radius);
		return Math.sqrt(Math.max(0.0, radius * radius - clamped * clamped));
	}

	/** The radial scale a step on the thickness ladder stands for. */
	public static double scaleOf(int step) {
		return THINNEST + (1.0 - THINNEST) * Mth.clamp(step, 0, STEPS - 1) / (STEPS - 1.0);
	}

	/**
	 * The nearest step to a machine's own nacelle scale.
	 *
	 * The renderer quantises through here too, so what is drawn and what is collided with come
	 * off the same ladder rather than off two numbers that agree to within a rounding.
	 */
	public static int stepFor(double scale) {
		return Mth.clamp((int) Math.round((scale - THINNEST) / (1.0 - THINNEST) * (STEPS - 1)), 0, STEPS - 1);
	}

	public static double scaleFor(double scale) {
		return scaleOf(stepFor(scale));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(THICKNESS, TAPER);
	}

	@Override
	public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPES[state.getValue(THICKNESS)][state.getValue(TAPER)];
	}

	/**
	 * Settles how thick a whole tower is, and writes it to every block in it.
	 *
	 * Driven from the machine when there is one, since that is the proportion the thickness
	 * exists to match. A bare stack has no machine to ask, so its height stands in for one:
	 * every model is sold on a band of heights, so a short tower is a small machine's tower
	 * and a tall one is not. The two agree closely enough that capping a tower barely changes
	 * it - which is the point of deriving the bare case from height rather than defaulting it.
	 *
	 * Called when a tower or a machine is placed or broken, which is every way the answer can
	 * change. The writes skip neighbour updates and are guarded against re-entry, or setting
	 * one block's state would send us round again through its own update.
	 */
	public static void refreshThickness(Level level, BlockPos anywhere) {
		if (level.isClientSide() || refreshing) return;

		int below = countBelow(level, anywhere);
		BlockPos foot = anywhere.below(below);
		int height = below + 1 + countAbove(level, anywhere);
		int step = stepFor(targetScale(level, foot, height));

		refreshing = true;
		try {
			for (int segment = 0; segment < height; segment++) {
				BlockPos pos = foot.above(segment);
				BlockState state = level.getBlockState(pos);
				if (!(state.getBlock() instanceof TurbineTowerBlock)) continue;

				// The top of this block, which is the narrowest the tube gets within it, so the
				// collision stays inside the steel over the block's whole height. Rounded up rather
				// than to nearest, because a higher step is a smaller radius: rounding to nearest let
				// it out past the tube by a hundredth of a pixel, and never-outside is worth more as
				// something true by construction than as something true by a rounding.
				int taper = Math.min((int) Math.ceil((segment + 1.0) / height * (TAPER_STEPS - 1)), TAPER_STEPS - 1);
				if (state.getValue(THICKNESS) == step && state.getValue(TAPER) == taper) continue;

				level.setBlock(pos, state.setValue(THICKNESS, step).setValue(TAPER, taper), Block.UPDATE_CLIENTS);
			}
		} finally {
			refreshing = false;
		}
	}

	private static double targetScale(Level level, BlockPos foot, int height) {
		BlockPos machine = findTurbineAbove(level, foot);
		if (machine != null && level.getBlockState(machine).getBlock() instanceof WindTurbineBlock turbine) {
			return turbine.spec().nacelleRenderScale();
		}

		return scaleOf(BARE_STEP_BY_HEIGHT[Mth.clamp(height, 0, BARE_STEP_BY_HEIGHT.length - 1)]);
	}

	/**
	 * Drawn by the mod's own OBJ pipeline, like every other machine here, so the tube is
	 * the authored one rather than an approximation in cuboids.
	 */
	@Override
	public RenderShape getRenderShape(BlockState state) {
		return RenderShape.INVISIBLE;
	}

	/**
	 * Empty, and only present so the renderer can find the tower.
	 *
	 * This mod draws through {@code RenderLevelStageEvent} over tracked block entities
	 * rather than through the chunk mesh, and a tower with nothing to be found by would
	 * be invisible until a machine was mounted on it - which is no way to stack thirteen
	 * of them. It never ticks and holds no state.
	 */
	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new TurbineTowerBlockEntity(pos, state);
	}

	/**
	 * How many tower blocks stand under this position.
	 *
	 * Counted from the world rather than stored anywhere, so the height a turbine sees and
	 * the tower a player can walk up are one fact with one source and cannot come apart.
	 */
	public static int countBelow(BlockGetter level, BlockPos pos) {
		return count(level, pos, Direction.DOWN);
	}

	/** How many tower blocks stand above this position, for a stack with nothing on it yet. */
	public static int countAbove(BlockGetter level, BlockPos pos) {
		return count(level, pos, Direction.UP);
	}

	private static int count(BlockGetter level, BlockPos pos, Direction direction) {
		int found = 0;
		BlockPos.MutableBlockPos cursor = pos.mutable().move(direction);
		while (found <= SCAN_LIMIT && level.getBlockState(cursor).getBlock() instanceof TurbineTowerBlock) {
			found++;
			cursor.move(direction);
		}

		return found;
	}

	/**
	 * A tower needs a tower under it, or something with substance to it.
	 *
	 * Anything with a collision shape counts, rather than a sturdy face. A sturdy face means
	 * a full square block underneath - the rule vanilla uses for doors and rails - and it
	 * refused the machines and pipes of other mods, whose shapes have cutouts in them.
	 * Those are precisely what a turbine gets built next to, so a tower would not seat on an
	 * energy cube or a length of transmitter. A foot rests on what is under it; it does not
	 * ask for a flat square.
	 *
	 * Still a support rule, though, and the collapse comes out of it for free: take away
	 * what a tower stands on and every block above fails this same check in turn. That is
	 * how ladders and torches already behave, so nobody has to be told, and it is how a real
	 * tower fails too.
	 */
	@Override
	public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		BlockPos below = pos.below();
		BlockState support = level.getBlockState(below);
		if (support.getBlock() instanceof TurbineTowerBlock) return true;

		return !support.getCollisionShape(level, below).isEmpty();
	}

	@Override
	public BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
		if (!canSurvive(state, level, pos)) return Blocks.AIR.defaultBlockState();

		return state;
	}

	/**
	 * Weight on top of an over-tall tower brings the excess down.
	 *
	 * Only the top block of a stack carries anything, and one lookup rules everything else
	 * out, so this stays cheap despite being called on every movement tick of whatever is
	 * standing there. Any entity will do it - a wandering mob is weight too.
	 */
	@Override
	public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
		super.stepOn(level, pos, state, entity);
		if (level.isClientSide() || countAbove(level, pos) > 0) return;

		collapse(level, pos, entity);
	}

	/**
	 * A tower stacked past what it can hold up comes down as it is built.
	 *
	 * Placement rather than support, so it catches every way of reaching up there - flying,
	 * a scaffold beside it, a pillar - without needing to know which was used.
	 */
	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		if (level.isClientSide()) return;

		if (countBelow(level, pos) + 1 > MAX_HEIGHT) {
			collapse(level, pos, placer);
			return;
		}

		refreshThickness(level, pos);
	}

	@Override
	public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
		super.onRemove(state, level, pos, newState, movedByPiston);
		// the stack left underneath is shorter than it was, so its thickness may have changed
		if (!state.is(newState.getBlock()) && !level.isClientSide()) refreshThickness(level, pos.below());
	}

	/**
	 * Starts the whole tower failing, if it has been built past what it can hold.
	 *
	 * All of it, not only the part above the safe height: a tower that buckles does not shed
	 * its top few blocks and stand there, it comes down. {@link TowerCollapse} draws the
	 * fracture running from the top to the foot over about a second and a half, and the
	 * machine up there loses its support along with everything else.
	 */
	private static void collapse(Level level, BlockPos anywhere, @Nullable Entity cause) {
		if (!(level instanceof ServerLevel serverLevel)) return;

		int below = countBelow(level, anywhere);
		int height = below + 1 + countAbove(level, anywhere);
		if (height <= SAFE_HEIGHT) return;

		TowerCollapse.begin(serverLevel, anywhere.below(below), height);

		if (cause instanceof Player player) {
			player.displayClientMessage(Component.translatable("message.electricity.tower.collapsed").withStyle(ChatFormatting.RED), true);
		}
	}

	/** A tube is mostly air, so it neither blocks light nor stops the sky. */
	@Override
	public int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
		return 0;
	}

	@Override
	public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
		return true;
	}

	/**
	 * The turbine this tower carries, if it has one yet.
	 *
	 * Walks up, because the machine is always on top and the answer is wanted from
	 * anywhere on the tower: a wrench used at the foot should open the panel of the
	 * machine it belongs to, and the renderer needs to know whether the machine is
	 * already drawing the tower.
	 */
	@Nullable
	public static BlockPos findTurbineAbove(BlockGetter level, BlockPos pos) {
		BlockPos.MutableBlockPos cursor = pos.mutable().move(Direction.UP);
		for (int step = 0; step <= SCAN_LIMIT; step++) {
			BlockState state = level.getBlockState(cursor);
			if (state.getBlock() instanceof WindTurbineBlock) return cursor.immutable();
			if (!(state.getBlock() instanceof TurbineTowerBlock)) return null;
			cursor.move(Direction.UP);
		}

		return null;
	}

	/** Whether this block is the foot of its stack, which is the one that draws the tube. */
	public static boolean isFoot(BlockGetter level, BlockPos pos) {
		return countBelow(level, pos) == 0;
	}
}
