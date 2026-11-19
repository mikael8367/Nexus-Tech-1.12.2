package com.mikael8367.nexustech;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.init.Bootstrap;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.DimensionType;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.WorldType;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkPrimer;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.registries.ForgeRegistry;
import sun.misc.Unsafe;

/** Exercises the real 1.12.2 chunk height/skylight map and the actual solar tick. */
public final class NexusSolarOcclusionTest extends NexusEnergyTestSupport {
    static final class SkyProvider extends WorldProvider {
        SkyProvider() { hasSkyLight = true; }
        void setSkyLight(boolean value) { hasSkyLight = value; }
        @Override public DimensionType getDimensionType() { return DimensionType.OVERWORLD; }
    }
    static final class SolarWorld extends World {
        Chunk chunk;
        Map<BlockPos, TileEntity> tiles;
        Set<BlockPos> unloaded;
        boolean day = true, rain, thunder;
        // Not invoked: use the same Unsafe fixture approach as the other standalone tests.
        private SolarWorld() { super(null, null, null, null, false); }
        @Override protected IChunkProvider createChunkProvider() { return null; }
        @Override protected boolean isChunkLoaded(int x, int z, boolean allowEmpty) { return true; }
        @Override public boolean isBlockLoaded(BlockPos p) { return !unloaded.contains(p); }
        @Override public TileEntity getTileEntity(BlockPos p) { return tiles.get(p); }
        void put(BlockPos p, TileEntity tile) { tiles.put(p, tile); tile.setPos(p); tile.setWorld(this); }
        @Override public Chunk getChunkFromBlockCoords(BlockPos p) { return chunk; }
        @Override public WorldType getWorldType() { return WorldType.DEFAULT; }
        @Override public void notifyLightSet(BlockPos p) { }
        @Override public boolean isDaytime() { return day; }
        @Override public boolean isRaining() { return rain; }
        @Override public boolean isThundering() { return thunder; }
    }
    static final class Solar extends MachineTile.Solar { @Override public void markDirty() { } }

    static SolarWorld world() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        Unsafe unsafe = (Unsafe)field.get(null);
        SolarWorld w = (SolarWorld)unsafe.allocateInstance(SolarWorld.class);
        w.tiles = new HashMap<>();
        w.unloaded = new HashSet<>();
        w.day = true;
        unsafe.putObject(w, unsafe.objectFieldOffset(World.class.getField("provider")), new SkyProvider());
        return w;
    }
    static void makeChunk(SolarWorld w, Block roof, int roofY) {
        ChunkPrimer primer = new ChunkPrimer();
        if (roof != null) primer.setBlockState(0, roofY, 0, roof.getDefaultState());
        w.chunk = new Chunk(w, primer, 0, 0);
        w.chunk.generateSkylightMap();
    }
    static void roofCase(Block roof, int roofY, boolean expectSky, String label) throws Exception {
        SolarWorld w = world();
        makeChunk(w, roof, roofY);
        Solar solar = new Solar();
        Endpoint receiver = new Endpoint(0, true, false);
        w.put(O, solar);
        w.put(E, receiver);
        boolean visible = w.canSeeSky(O.up());
        int skylight = w.getLightFor(EnumSkyBlock.SKY, O.up());
        ok(visible == expectSky, label + " sky visibility");
        if (expectSky) eq(skylight, 15, label + " sky light");
        else ok(skylight < 15, label + " reduced sky light");
        solar.update();
        eq(solar.energy, 0, label + " output drained");
        eq(receiver.energy, expectSky ? 20 : 0, label + " emitted FE");
    }
    public static void main(String[] args) throws Exception {
        Bootstrap.register();
        setupCapability();
        // Standalone Bootstrap does not run Forge's mod registry events.
        ForgeRegistry<Block> registry = (ForgeRegistry<Block>)ForgeRegistries.BLOCKS;
        registry.unfreeze();
        for (MachineBlock block : ModRegistry.BLOCKS) registry.register(block);
        registry.freeze();
        roofCase(null, 0, true, "open sky");
        for (MachineBlock roof : new MachineBlock[] { ModRegistry.SOLAR, ModRegistry.GENERATOR, ModRegistry.TURBINE, ModRegistry.BATTERY }) {
            roofCase(roof, O.getY() + 1, false, roof.kind + " adjacent roof");
            roofCase(roof, O.getY() + 3, false, roof.kind + " elevated roof");
        }
        roofCase(ModRegistry.CABLE, O.getY() + 1, true, "adjacent cable");
        roofCase(ModRegistry.CABLE, O.getY() + 3, true, "elevated cable");
        SolarWorld w = world(); makeChunk(w, null, 0); Solar solar = new Solar(); w.put(O, solar);
        w.day = false; solar.update(); eq(solar.energy, 0, "night");
        w.day = true; w.rain = true; solar.update(); eq(solar.energy, 0, "rain");
        w.rain = false; w.thunder = true; solar.update(); eq(solar.energy, 0, "thunder");
        w.thunder = false; w.unloaded.add(O.up()); solar.update(); eq(solar.energy, 0, "unloaded above");
        w.unloaded.clear(); solar.update(); eq(solar.energy, 20, "sun restored");
        solar.energy = 0;
        ((SkyProvider)w.provider).setSkyLight(false); solar.update(); eq(solar.energy, 0, "dimension without skylight");
        ((SkyProvider)w.provider).setSkyLight(true);
        // Put a transparent block in this section so the real chunk stores light values.
        makeChunk(w, ModRegistry.CABLE, O.getY() + 1);
        w.chunk.setLightFor(EnumSkyBlock.SKY, O.up(), 14);
        ok(w.canSeeSky(O.up()), "sky visible for low-light threshold check");
        solar.update(); eq(solar.energy, 0, "sky light below threshold");
        w.chunk.setLightFor(EnumSkyBlock.SKY, O.up(), 15);
        solar.update(); eq(solar.energy, 20, "sky light reaches threshold");
        Bootstrap.printToSYSOUT("PASS real chunk skylight and solar emission: four roofs, open sky, cable, weather and light threshold");
    }
}
