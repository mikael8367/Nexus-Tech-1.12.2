package com.mikael8367.nexustech.fluidnetwork.pipe;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Bootstrap;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;

/** Isolated test: uses the visual-state fake world; registers no mod blocks. */
public final class FluidPipeCollisionTest {
    private static final BlockPos P = FluidPipeVisualStateTest.ORIGIN;
    private static final EnumFacing[] SIDES = FluidPipeVisualStateTest.SIDES;
    private static void check(boolean ok, String detail) { if (!ok) throw new AssertionError(detail); }
    private static Vec3d v(double x, double y, double z) { return new Vec3d(P.getX()+x, P.getY()+y, P.getZ()+z); }
    private static Vec3d along(EnumFacing f, double d) { return v(.5 + f.getFrontOffsetX()*d, .5 + f.getFrontOffsetY()*d, .5 + f.getFrontOffsetZ()*d); }
    private static boolean eq(double a, double b) { return Math.abs(a-b) < 1e-8; }
    private static void point(Vec3d a, Vec3d b, String msg) { check(eq(a.x,b.x) && eq(a.y,b.y) && eq(a.z,b.z), msg + ": " + a + " != " + b); }
    private static void box(AxisAlignedBB a, AxisAlignedBB b, String msg) { check(a.equals(b), msg + ": " + a + " != " + b); }
    private static List<AxisAlignedBB> collisions(FluidPipeBlock pipe, IBlockState state, FluidPipeVisualStateTest.W w, AxisAlignedBB query, boolean actual) {
        List<AxisAlignedBB> out = new ArrayList<>();
        pipe.addCollisionBoxToList(state, w, P, query.offset(P), out, null, actual); return out;
    }
    private static boolean contains(List<AxisAlignedBB> boxes, Vec3d point) {
        for (AxisAlignedBB box : boxes) if (box.contains(point)) return true;
        return false;
    }
    // Independent visual oracle: read the staged Minecraft model elements,
    // not constants or private fields from the collision implementation.
    private static AxisAlignedBB modelBox(Path models, String suffix) throws Exception {
        Path file = models.resolve("fluid_pipe" + suffix + ".json");
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject model = new JsonParser().parse(reader).getAsJsonObject();
            JsonArray elements = model.getAsJsonArray("elements");
            check(elements != null && elements.size() == 1, "model must have exactly one element: " + file);
            JsonObject element = elements.get(0).getAsJsonObject();
            check(!element.has("rotation"), "rotated model requires a new oracle: " + file);
            JsonArray from = element.getAsJsonArray("from"), to = element.getAsJsonArray("to");
            check(from.size() == 3 && to.size() == 3, "three model coordinates: " + file);
            return new AxisAlignedBB(from.get(0).getAsDouble()/16, from.get(1).getAsDouble()/16,
                    from.get(2).getAsDouble()/16, to.get(0).getAsDouble()/16,
                    to.get(1).getAsDouble()/16, to.get(2).getAsDouble()/16).offset(P);
        }
    }
    private static void hit(FluidPipeBlock pipe, IBlockState state, FluidPipeVisualStateTest.W w,
            Vec3d start, Vec3d end, Vec3d expected, EnumFacing face, String label) {
        RayTraceResult got = pipe.collisionRayTrace(state, w, P, start, end);
        check(got != null, label + " missing hit");
        point(got.hitVec, expected, label);
        check(got.sideHit == face, label + " face " + got.sideHit + " != " + face);
    }
    private static void boundaryRays(FluidPipeBlock pipe, IBlockState state, FluidPipeVisualStateTest.W w) {
        w.blocks.clear();
        // Unique entry axis avoids the inherently ambiguous face at edges.
        hit(pipe,state,w,v(-1,.25,.5),v(1,.25,.5),v(.25,.25,.5),EnumFacing.WEST,"face tangent");
        hit(pipe,state,w,v(-1,.25,.25),v(1,.25,.25),v(.25,.25,.25),EnumFacing.WEST,"edge tangent");
        hit(pipe,state,w,v(0,.5,.5),v(.5,0,.5),v(.25,.25,.5),EnumFacing.WEST,"single-point edge tangent");
        check(pipe.collisionRayTrace(state,w,P,v(-1,.249,.5),v(1,.249,.5)) == null,"near-tangent miss");
        hit(pipe,state,w,v(.5,.5,.5),v(1,.5,.5),v(.75,.5,.5),EnumFacing.EAST,"inside exit");
        check(pipe.collisionRayTrace(state,w,P,v(.5,.5,.5),v(.6,.5,.5)) == null,"segment wholly inside");
        hit(pipe,state,w,v(.25,.5,.5),v(1,.5,.5),v(.25,.5,.5),EnumFacing.WEST,"start on face inward");
        hit(pipe,state,w,v(.25,.5,.5),v(-1,.5,.5),v(.25,.5,.5),EnumFacing.WEST,"start on face outward");
        w.blocks.put(P.east(), pipe.getDefaultState());
        hit(pipe,state,w,v(.5,.5,.5),v(2,.5,.5),v(1,.5,.5),EnumFacing.EAST,"core through arm");
        hit(pipe,state,w,v(.875,.5,.5),v(-1,.5,.5),v(.25,.5,.5),EnumFacing.WEST,"arm through core");
        hit(pipe,state,w,v(.75,.5,.5),v(2,.5,.5),v(1,.5,.5),EnumFacing.EAST,"seam start outward");
        hit(pipe,state,w,v(.75,.5,.5),v(-1,.5,.5),v(.25,.5,.5),EnumFacing.WEST,"seam start inward");
        hit(pipe,state,w,v(.5,.3,.5),v(2,.3,.5),v(.75,.3,.5),EnumFacing.EAST,"uncovered core face");
    }
    // Independent oracle: Forge AABB interception on every collision component,
    // selecting the closest hit for rays beginning outside the block.
    private static void gridRays(FluidPipeBlock pipe, IBlockState base, FluidPipeVisualStateTest.W w,
            List<AxisAlignedBB> boxes, int mask) {
        for (int axis=0; axis<3; axis++) for (int a=1; a<=9; a++) for (int b=1; b<=9; b++) for (int sign : new int[]{-1,1}) {
            double[] s={a/10.0,b/10.0,a/10.0}, e=s.clone();
            if (axis==0) { s[1]=e[1]=a/10.0; s[2]=e[2]=b/10.0; }
            if (axis==1) { s[0]=e[0]=a/10.0; s[2]=e[2]=b/10.0; }
            if (axis==2) { s[0]=e[0]=a/10.0; s[1]=e[1]=b/10.0; }
            s[axis]=sign<0?-1:2; e[axis]=sign<0?2:-1;
            Vec3d start=v(s[0],s[1],s[2]), end=v(e[0],e[1],e[2]);
            RayTraceResult expected=null; double distance=Double.POSITIVE_INFINITY;
            for (AxisAlignedBB component:boxes) {
                RayTraceResult candidate=component.calculateIntercept(start,end);
                if (candidate!=null && candidate.hitVec.squareDistanceTo(start)<distance) {
                    expected=candidate; distance=candidate.hitVec.squareDistanceTo(start);
                }
            }
            RayTraceResult got=pipe.collisionRayTrace(base,w,P,start,end);
            check((got==null)==(expected==null), "grid miss " + mask + " " + axis + " " + a + " " + b);
            if (got!=null) {
                point(got.hitVec,expected.hitVec,"grid hit " + mask);
                check(got.sideHit==expected.sideHit,"grid face " + mask);
            }
        }
    }
    private static void diagonalRays(FluidPipeBlock pipe, IBlockState base, FluidPipeVisualStateTest.W w,
            List<AxisAlignedBB> boxes, int mask) {
        Random random = new Random(92341L + mask);
        for (int i=0;i<200;i++) {
            Vec3d start=v(-.1-random.nextDouble(), random.nextDouble(), random.nextDouble());
            Vec3d end=v(1.1+random.nextDouble(), random.nextDouble(), random.nextDouble());
            RayTraceResult expected=null; double distance=Double.POSITIVE_INFINITY;
            for (AxisAlignedBB component:boxes) {
                RayTraceResult candidate=component.calculateIntercept(start,end);
                if (candidate!=null && candidate.hitVec.squareDistanceTo(start)<distance) {
                    expected=candidate; distance=candidate.hitVec.squareDistanceTo(start);
                }
            }
            RayTraceResult got=pipe.collisionRayTrace(base,w,P,start,end);
            check((got==null)==(expected==null), "diagonal hit/miss " + mask);
            if (got!=null) { point(got.hitVec,expected.hitVec,"diagonal " + mask); check(got.sideHit==expected.sideHit,"diagonal face"); }
        }
    }
    public static void main(String[] args) throws Exception {
        Bootstrap.register();
        FluidPipeBlock pipe = new FluidPipeBlock(); FluidPumpBlock pump = new FluidPumpBlock();
        FluidPipeVisualStateTest.W w = FluidPipeVisualStateTest.world();
        IBlockState base = pipe.getDefaultState();
        AxisAlignedBB query = new AxisAlignedBB(0, 0, 0, 1, 1, 1);
        check(args.length == 1, "pass the staged block-model directory as the sole argument");
        Path models = Paths.get(args[0]);
        AxisAlignedBB[] visual = new AxisAlignedBB[7];
        visual[0] = modelBox(models, "");
        String[] suffixes = {"_north", "_south", "_east", "_west", "_up", "_down"};
        for (int i=0;i<6;i++) visual[i+1] = modelBox(models, suffixes[i]);
        for (int mask = 0; mask < 64; mask++) {
            w.blocks.clear(); w.unloaded.clear();
            for (int i=0;i<6;i++) if ((mask & (1<<i)) != 0)
                w.blocks.put(P.offset(SIDES[i]), (i%2 == 0 ? pipe : pump).getDefaultState());
            List<AxisAlignedBB> list = collisions(pipe, base, w, query, false);
            check(list.size() == 1 + Integer.bitCount(mask), "box count " + mask);
            box(list.get(0), visual[0], "JSON core " + mask);
            int component = 1;
            for (int i=0;i<6;i++) if ((mask & (1<<i)) != 0)
                box(list.get(component++), visual[i+1], "JSON arm " + SIDES[i] + " mask " + mask);
            check(contains(list, v(.5,.5,.5)), "core " + mask);
            for (int i=0;i<6;i++) {
                EnumFacing side=SIDES[i]; boolean connected=(mask & (1<<i)) != 0;
                check(contains(list, along(side,.375)) == connected, "arm " + mask + " " + side);
                RayTraceResult hit=pipe.collisionRayTrace(base,w,P,along(side,1),along(side,0));
                check(hit != null && hit.sideHit == side && hit.getBlockPos().equals(P), "outside face " + mask + " " + side);
                point(hit.hitVec, along(side,connected ? .5 : .25), "outside distance");
                hit=pipe.collisionRayTrace(base,w,P,along(side,0),along(side,1));
                check(hit != null && hit.sideHit == side, "inside face " + mask + " " + side);
                point(hit.hitVec, along(side,connected ? .5 : .25), "inside exit, no internal seam");
            }
            gridRays(pipe, base, w, list, mask);
            diagonalRays(pipe, base, w, list, mask);
            check(!contains(list, v(.1,.1,.1)), "empty corner " + mask);
            check(pipe.collisionRayTrace(base,w,P,v(-1,.1,.1),v(2,.1,.1)) == null, "corner ray misses " + mask);
            AxisAlignedBB bounds = new AxisAlignedBB((mask&8)!=0?0:.25,(mask&32)!=0?0:.25,(mask&1)!=0?0:.25,
                    (mask&4)!=0?1:.75,(mask&16)!=0?1:.75,(mask&2)!=0?1:.75);
            box(pipe.getBoundingBox(base,w,P),bounds,"envelope " + mask);
            box(pipe.getSelectedBoundingBox(base,w,P),bounds.offset(P),"selection envelope " + mask);
            check(collisions(pipe,base,w,new AxisAlignedBB(0,0,0,.1,.1,.1),false).isEmpty(),"query filter " + mask);
            IBlockState actual=pipe.getActualState(base,w,P);
            w.reads=0; List<AxisAlignedBB> direct=collisions(pipe,actual,w,query,true);
            check(w.reads==0 && direct.equals(list),"actual state flag " + mask);
        }
        w.blocks.clear(); w.blocks.put(P.east(),pipe.getDefaultState());
        IBlockState stale=pipe.getActualState(base,w,P);
        w.unloaded.add(P.east()); w.reads=0;
        check(collisions(pipe,stale,w,query,false).size()==1 && w.reads==5 && w.tiles==0,"unloaded neighbor and stale state");
        check(pipe.collisionRayTrace(stale,w,P,along(EnumFacing.EAST,1),along(EnumFacing.EAST,0)).hitVec.x == P.getX()+.75,"stale ray state");
        w.unloaded.clear(); w.missingChunk=true; w.reads=0;
        check(collisions(pipe,stale,w,query,false).size()==1 && w.reads==0,"missing loaded chunk");
        check(pipe.collisionRayTrace(base,w,P,v(.5,.5,.5),v(.5,.5,.5))==null,"zero ray");
        check(pipe.collisionRayTrace(base,w,P,v(0,.1,.5),v(1,.1,.5))==null,"parallel miss");
        w.missingChunk=false;
        boundaryRays(pipe,base,w);
        System.out.println("PASS 64 masks: JSON box comparison, entry/exit and boundary rays, bounds, queries, unloaded neighbors, stale state");
    }
}
