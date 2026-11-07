package br.com.mikael8367.nexustech;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.BlockHorizontal;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.Mirror;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

abstract class BlockTechMachine extends BlockContainer {
    BlockTechMachine(String name, float hardness) {
        super(Material.IRON);
        setRegistryName(NexusTechMod.MOD_ID, name);
        setTranslationKey(NexusTechMod.MOD_ID + "." + name);
        setCreativeTab(NexusTechCreativeTab.INSTANCE);
        setHardness(hardness);
        setResistance(10.0F);
        setHarvestLevel("pickaxe", 1);
    }
    /**
     * Status screens are client-only (no server container). Opening a server Container with a plain
     * GuiScreen desynchronised the player's inventory window id until they walked away.
     */
    protected static boolean openMachineGui(World world, BlockPos pos, EntityPlayer player, int id) {
        if (world.isRemote) player.openGui(NexusTechMod.INSTANCE, id, world, pos.getX(), pos.getY(), pos.getZ());
        return true;
    }
    @Override public EnumBlockRenderType getRenderType(IBlockState state) { return EnumBlockRenderType.MODEL; }
}

/** Machines with a front face that turns toward the player on placement. */
abstract class BlockFacingMachine extends BlockTechMachine {
    static final PropertyDirection FACING = BlockHorizontal.FACING;
    // Meta 0 = north keeps blocks placed before facing existed looking exactly as before.
    private static final EnumFacing[] BY_META = {EnumFacing.NORTH, EnumFacing.EAST, EnumFacing.SOUTH, EnumFacing.WEST};
    BlockFacingMachine(String name, float hardness) {
        super(name, hardness);
        setDefaultState(blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH));
    }
    @Override protected BlockStateContainer createBlockState() { return new BlockStateContainer(this, FACING); }
    @Override public IBlockState getStateFromMeta(int meta) { return getDefaultState().withProperty(FACING, BY_META[meta & 3]); }
    @Override public int getMetaFromState(IBlockState state) {
        EnumFacing facing = state.getValue(FACING);
        for (int i = 0; i < BY_META.length; i++) if (BY_META[i] == facing) return i;
        return 0;
    }
    @Override public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing side, float hitX, float hitY,
            float hitZ, int meta, EntityLivingBase placer, EnumHand hand) {
        return getDefaultState().withProperty(FACING, placer.getHorizontalFacing().getOpposite());
    }
    @Override public IBlockState withRotation(IBlockState state, Rotation rotation) {
        return state.withProperty(FACING, rotation.rotate(state.getValue(FACING)));
    }
    @Override public IBlockState withMirror(IBlockState state, Mirror mirror) {
        return state.withRotation(mirror.toRotation(state.getValue(FACING)));
    }
}

final class BlockCoalGenerator extends BlockFacingMachine {
    BlockCoalGenerator() { super("coal_generator", 3.5F); }
    @Override public TileEntity createNewTileEntity(World world, int meta) { return new TileCoalGenerator(); }
    @Override public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
            EnumHand hand, EnumFacing side, float hitX, float hitY, float hitZ) {
        if (hand != EnumHand.MAIN_HAND) return false;
        ItemStack held = player.getHeldItem(hand);
        TileEntity tile = world.getTileEntity(pos);
        if (!(tile instanceof TileCoalGenerator)) return false;
        if (isFuel(held)) {
            if (!world.isRemote && ((TileCoalGenerator) tile).addFuel(held) && !player.capabilities.isCreativeMode) held.shrink(1);
            return true;
        }
        return openMachineGui(world, pos, player, TechGuiHandler.GUI_GENERATOR);
    }
    @Override public void breakBlock(World world, BlockPos pos, IBlockState state) {
        TileEntity tile = world.getTileEntity(pos);
        if (!world.isRemote && tile instanceof TileCoalGenerator) {
            ((TileCoalGenerator) tile).dropContents();
        }
        super.breakBlock(world, pos, state);
    }
    static boolean isFuel(ItemStack stack) { return TileCoalGenerator.fuelTicksFor(stack) > 0; }
}

final class BlockSolarPanel extends BlockTechMachine {
    private static final AxisAlignedBB BOUNDS = new AxisAlignedBB(0, 0, 0, 1, 0.3125, 1);
    BlockSolarPanel() { super("solar_panel", 1.5F); }
    @Override public TileEntity createNewTileEntity(World world, int meta) { return new TileSolarPanel(); }
    @Override public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand, EnumFacing side, float hitX, float hitY, float hitZ) {
        if (hand != EnumHand.MAIN_HAND) return false;
        return openMachineGui(world, pos, player, TechGuiHandler.GUI_SOLAR);
    }
    @Override public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) { return BOUNDS; }
    @Override public AxisAlignedBB getCollisionBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) { return BOUNDS; }
    @Override public boolean isFullCube(IBlockState state) { return false; }
    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public BlockFaceShape getBlockFaceShape(IBlockAccess world, IBlockState state, BlockPos pos, EnumFacing face) {
        return face == EnumFacing.DOWN ? BlockFaceShape.SOLID : BlockFaceShape.UNDEFINED;
    }
}

final class BlockEnergyCable extends BlockTechMachine {
    static final PropertyBool DOWN = PropertyBool.create("down");
    static final PropertyBool UP = PropertyBool.create("up");
    static final PropertyBool NORTH = PropertyBool.create("north");
    static final PropertyBool SOUTH = PropertyBool.create("south");
    static final PropertyBool WEST = PropertyBool.create("west");
    static final PropertyBool EAST = PropertyBool.create("east");
    /** Indexed by EnumFacing.getIndex(): down, up, north, south, west, east. */
    private static final PropertyBool[] CONNECTIONS = {DOWN, UP, NORTH, SOUTH, WEST, EAST};
    private static final double MIN = 5.0D / 16.0D, MAX = 11.0D / 16.0D;
    private static final AxisAlignedBB CORE = new AxisAlignedBB(MIN, MIN, MIN, MAX, MAX, MAX);
    private static final AxisAlignedBB[] ARMS = {
            new AxisAlignedBB(MIN, 0, MIN, MAX, MIN, MAX),   // down
            new AxisAlignedBB(MIN, MAX, MIN, MAX, 1, MAX),   // up
            new AxisAlignedBB(MIN, MIN, 0, MAX, MAX, MIN),   // north
            new AxisAlignedBB(MIN, MIN, MAX, MAX, MAX, 1),   // south
            new AxisAlignedBB(0, MIN, MIN, MIN, MAX, MAX),   // west
            new AxisAlignedBB(MAX, MIN, MIN, 1, MAX, MAX)};  // east

    BlockEnergyCable() {
        super("energy_cable", 1.0F);
        IBlockState state = blockState.getBaseState();
        for (PropertyBool property : CONNECTIONS) state = state.withProperty(property, false);
        setDefaultState(state);
    }
    @Override protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, new IProperty<?>[] {DOWN, UP, NORTH, SOUTH, WEST, EAST});
    }
    @Override public int getMetaFromState(IBlockState state) { return 0; }
    @Override public IBlockState getActualState(IBlockState state, IBlockAccess world, BlockPos pos) {
        for (EnumFacing side : EnumFacing.values()) {
            state = state.withProperty(CONNECTIONS[side.getIndex()], connectsTo(world, pos, side));
        }
        return state;
    }
    static boolean connectsTo(IBlockAccess world, BlockPos pos, EnumFacing side) {
        TileEntity self = world.getTileEntity(pos);
        if (self instanceof TileEnergyCable && !((TileEnergyCable) self).isFaceEnabled(side)) return false;
        TileEntity neighbour = world.getTileEntity(pos.offset(side));
        return neighbour != null && neighbour.hasCapability(CapabilityEnergy.ENERGY, side.getOpposite());
    }
    @Override public TileEntity createNewTileEntity(World world, int meta) { return new TileEnergyCable(); }
    @Override public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand, EnumFacing side, float hitX, float hitY, float hitZ) {
        if (hand != EnumHand.MAIN_HAND) return false;
        return openMachineGui(world, pos, player, TechGuiHandler.GUI_CABLE);
    }
    @Override public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        IBlockState actual = getActualState(state, world, pos);
        AxisAlignedBB box = CORE;
        for (EnumFacing side : EnumFacing.values()) {
            if (actual.getValue(CONNECTIONS[side.getIndex()])) box = box.union(ARMS[side.getIndex()]);
        }
        return box;
    }
    @Override @SuppressWarnings("deprecation")
    public void addCollisionBoxToList(IBlockState state, World world, BlockPos pos, AxisAlignedBB entityBox,
            List<AxisAlignedBB> boxes, @Nullable Entity entity, boolean actualState) {
        addCollisionBoxToList(pos, entityBox, boxes, CORE);
        for (EnumFacing side : EnumFacing.values()) {
            if (connectsTo(world, pos, side)) addCollisionBoxToList(pos, entityBox, boxes, ARMS[side.getIndex()]);
        }
    }
    @Override public boolean isFullCube(IBlockState state) { return false; }
    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public BlockFaceShape getBlockFaceShape(IBlockAccess world, IBlockState state, BlockPos pos, EnumFacing face) {
        return BlockFaceShape.UNDEFINED;
    }
}

final class BlockElectricFurnace extends BlockFacingMachine {
    BlockElectricFurnace() { super("electric_furnace", 3.5F); }
    @Override public TileEntity createNewTileEntity(World world, int meta) { return new TileElectricFurnace(); }
    @Override public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
            EnumHand hand, EnumFacing side, float hitX, float hitY, float hitZ) {
        if (hand != EnumHand.MAIN_HAND) return false;
        TileEntity tile = world.getTileEntity(pos);
        if (!(tile instanceof TileElectricFurnace)) return false;
        ItemStack held = player.getHeldItem(hand);
        // Both sides take the same branch so the client never opens a screen for an insert click.
        if (TileElectricFurnace.isSmeltable(held)) {
            if (!world.isRemote) {
                int inserted = ((TileElectricFurnace) tile).insertInput(held);
                if (inserted > 0 && !player.capabilities.isCreativeMode) held.shrink(inserted);
            }
            return true;
        }
        if (player.isSneaking()) {
            if (!world.isRemote) ((TileElectricFurnace) tile).takeOutput(player);
            return true;
        }
        return openMachineGui(world, pos, player, TechGuiHandler.GUI_FURNACE);
    }
    @Override public void breakBlock(World world, BlockPos pos, IBlockState state) {
        TileEntity tile = world.getTileEntity(pos);
        if (!world.isRemote && tile instanceof TileElectricFurnace) ((TileElectricFurnace) tile).dropContents();
        super.breakBlock(world, pos, state);
    }
}

final class BlockElectricAnvil extends BlockFacingMachine {
    private static final AxisAlignedBB BOUNDS_NS = new AxisAlignedBB(0.0625, 0, 0.125, 0.9375, 1, 0.875);
    private static final AxisAlignedBB BOUNDS_EW = new AxisAlignedBB(0.125, 0, 0.0625, 0.875, 1, 0.9375);
    BlockElectricAnvil() { super("electric_anvil", 4.0F); }
    @Override public TileEntity createNewTileEntity(World world, int meta) { return new TileElectricAnvil(); }
    private static AxisAlignedBB bounds(IBlockState state) {
        return state.getValue(FACING).getAxis() == EnumFacing.Axis.X ? BOUNDS_EW : BOUNDS_NS;
    }
    @Override public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) { return bounds(state); }
    @Override public AxisAlignedBB getCollisionBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) { return bounds(state); }
    @Override public boolean isFullCube(IBlockState state) { return false; }
    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public BlockFaceShape getBlockFaceShape(IBlockAccess world, IBlockState state, BlockPos pos, EnumFacing face) {
        return BlockFaceShape.UNDEFINED;
    }
    @Override public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
            EnumHand hand, EnumFacing side, float hitX, float hitY, float hitZ) {
        if (hand != EnumHand.MAIN_HAND) return false;
        TileEntity tile = world.getTileEntity(pos);
        if (!(tile instanceof TileElectricAnvil)) return false;
        TileElectricAnvil anvil = (TileElectricAnvil) tile;
        ItemStack held = player.getHeldItem(hand);
        if (TileElectricAnvil.isRepairable(held)) {
            if (!world.isRemote && anvil.insertItem(held) && !player.capabilities.isCreativeMode) held.shrink(1);
            return true;
        }
        if (player.isSneaking()) {
            if (!world.isRemote) anvil.takeOutput(player);
            return true;
        }
        return openMachineGui(world, pos, player, TechGuiHandler.GUI_ANVIL);
    }
    @Override public void breakBlock(World world, BlockPos pos, IBlockState state) {
        TileEntity tile = world.getTileEntity(pos);
        if (!world.isRemote && tile instanceof TileElectricAnvil) ((TileElectricAnvil) tile).dropContents();
        super.breakBlock(world, pos, state);
    }
}

final class TechRegistry {
    static BlockCoalGenerator COAL_GENERATOR;
    static BlockSolarPanel SOLAR_PANEL;
    static BlockEnergyCable ENERGY_CABLE;
    static BlockElectricFurnace ELECTRIC_FURNACE;
    static BlockElectricAnvil ELECTRIC_ANVIL;

    @SubscribeEvent public void registerBlocks(RegistryEvent.Register<Block> event) {
        COAL_GENERATOR = new BlockCoalGenerator();
        SOLAR_PANEL = new BlockSolarPanel();
        ENERGY_CABLE = new BlockEnergyCable();
        ELECTRIC_FURNACE = new BlockElectricFurnace();
        ELECTRIC_ANVIL = new BlockElectricAnvil();
        event.getRegistry().register(COAL_GENERATOR);
        event.getRegistry().register(SOLAR_PANEL);
        event.getRegistry().register(ENERGY_CABLE);
        event.getRegistry().register(ELECTRIC_FURNACE);
        event.getRegistry().register(ELECTRIC_ANVIL);
        event.getRegistry().register(TechOreRegistry.URANIUM_ORE);
        event.getRegistry().register(TechOreRegistry.COPPER_ORE);
        event.getRegistry().register(TechOreRegistry.TIN_ORE);
        GameRegistry.registerTileEntity(TileCoalGenerator.class, new net.minecraft.util.ResourceLocation(NexusTechMod.MOD_ID, "coal_generator"));
        GameRegistry.registerTileEntity(TileSolarPanel.class, new net.minecraft.util.ResourceLocation(NexusTechMod.MOD_ID, "solar_panel"));
        GameRegistry.registerTileEntity(TileEnergyCable.class, new net.minecraft.util.ResourceLocation(NexusTechMod.MOD_ID, "energy_cable"));
        GameRegistry.registerTileEntity(TileElectricFurnace.class, new net.minecraft.util.ResourceLocation(NexusTechMod.MOD_ID, "electric_furnace"));
        GameRegistry.registerTileEntity(TileElectricAnvil.class, new net.minecraft.util.ResourceLocation(NexusTechMod.MOD_ID, "electric_anvil"));
    }
    @SubscribeEvent public void registerItems(RegistryEvent.Register<Item> event) {
        Block[] blocks = {COAL_GENERATOR, SOLAR_PANEL, ENERGY_CABLE, ELECTRIC_FURNACE, ELECTRIC_ANVIL,
                TechOreRegistry.URANIUM_ORE, TechOreRegistry.COPPER_ORE, TechOreRegistry.TIN_ORE};
        for (Block block : blocks) {
            if (block == null || block.getRegistryName() == null) {
                throw new IllegalStateException("Block registration order or registry name invalid");
            }
            event.getRegistry().register(new ItemBlock(block).setRegistryName(block.getRegistryName()));
        }
        for (Item item : TechItems.ALL) {
            if (item == null || item.getRegistryName() == null) {
                throw new IllegalStateException("Tech item has no registry name");
            }
            event.getRegistry().register(item);
        }
    }
    @SideOnly(Side.CLIENT)
    @SubscribeEvent public void registerModels(ModelRegistryEvent event) {
        registerModel(COAL_GENERATOR); registerModel(SOLAR_PANEL); registerModel(ENERGY_CABLE);
        registerModel(ELECTRIC_FURNACE); registerModel(ELECTRIC_ANVIL);
        registerModel(TechOreRegistry.URANIUM_ORE); registerModel(TechOreRegistry.COPPER_ORE);
        registerModel(TechOreRegistry.TIN_ORE);
        for (Item item : TechItems.ALL) {
            ModelLoader.setCustomModelResourceLocation(item, 0,
                    new ModelResourceLocation(item.getRegistryName(), "inventory"));
        }
    }
    @SideOnly(Side.CLIENT)
    private static void registerModel(Block block) {
        if (block != null) ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(block), 0,
                new ModelResourceLocation(block.getRegistryName(), "inventory"));
    }
}
