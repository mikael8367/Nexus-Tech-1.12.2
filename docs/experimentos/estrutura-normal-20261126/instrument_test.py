from pathlib import Path
import shutil
root=Path('project')
def one(s,a,b):
    assert s.count(a)==1, repr(a)
    return s.replace(a,b)
p=root/'src/main/java/com/mikael8367/nexustech/NexusTech.java'
s=p.read_text()
s=one(s,'        ModRegistry.registerTiles();','        // ISOLATED CI ONLY: Never include in a release.\n        net.minecraftforge.fml.common.registry.GameRegistry.registerWorldGenerator(new com.mikael8367.nexustech.worldgen.ExperimentalStructureGenerator(), 100);\n        com.mikael8367.nexustech.worldgen.StructureForgeHarness.register();\n        ModRegistry.registerTiles();')
p.write_text(s)
p=root/'src/main/java/com/mikael8367/nexustech/worldgen/ExperimentalStructureGenerator.java'
s=p.read_text()
s=one(s,'        if(world.isRemote || world.provider.getDimension()!=0) return;','        StructureForgeHarness.enter();\n        try {\n        if(world.isRemote || world.provider.getDimension()!=0) return;')
s=one(s,'        StructurePlan.apply(new StructurePlan.Site() {','        int testSurface=255;\n        while(testSurface>=0 && chunk.getBlockState(new BlockPos((cx<<4)+candidate.x,testSurface,(cz<<4)+candidate.z)).getBlock()!=Blocks.GRASS) testSurface--;\n        boolean testPlaced=StructurePlan.apply(new StructurePlan.Site() {')
s=one(s,'        },candidate,schematic);\n    }','        },candidate,schematic);\n        if(testPlaced) StructureForgeHarness.record(cx,cz,candidate.x,candidate.z,testSurface);\n        } finally { StructureForgeHarness.exit(); }\n    }')
p.write_text(s)
shutil.copyfile('docs/experimentos/estrutura-normal-20261126/StructureForgeHarness.java',root/'src/main/java/com/mikael8367/nexustech/worldgen/StructureForgeHarness.java')
print('Applied exact TEST-ONLY registration and recursive-entry observer; production patch remains disabled')
