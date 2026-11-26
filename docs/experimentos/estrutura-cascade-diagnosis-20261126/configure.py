from pathlib import Path
import os
mode=os.environ['MODE']
assert mode in ('baseline','fixed','noore')
root=Path('project/src/main/java/com/mikael8367/nexustech')
p=root/'ores/OreWorldGenerator.java'
s=p.read_text()
old='world.setBlockState(pos, OreRegistry.ORES.get(type).getDefaultState(), 2);'
assert s.count(old)==1, 'Unexpected ore generator; abort rather than testing wrong source'
if mode=='fixed':
    s=s.replace(old,'world.setBlockState(pos, OreRegistry.ORES.get(type).getDefaultState(), 2 | 16);')
# Test-only diagnostic, count entries and nested calls without changing control flow.
needle='        if (world.isRemote || world.provider.getDimension() != 0) return;'
assert s.count(needle)==1
s=s.replace(needle,'        com.mikael8367.nexustech.worldgen.StructureForgeHarness.enterOre(chunkX,chunkZ);\n        try {\n'+needle)
needle='        });\n    }\n}'
assert s.count(needle)==1
s=s.replace(needle,'        });\n        } finally { com.mikael8367.nexustech.worldgen.StructureForgeHarness.exitOre(); }\n    }\n}')
p.write_text(s)
p=root/'worldgen/StructureForgeHarness.java'
s=p.read_text()
needle='    private static int depth=0;'
assert s.count(needle)==1
s=s.replace(needle,needle+'''\n    private static int oreDepth=0, oreCalls=0, oreMaxDepth=0;\n    public static void enterOre(int cx,int cz) {\n        oreDepth++; oreCalls++; oreMaxDepth=Math.max(oreMaxDepth,oreDepth);\n        if(oreCalls<=20 || oreCalls%1000==0 || oreDepth>1)\n            System.out.println("ORE_DIAG calls="+oreCalls+" depth="+oreDepth+" max="+oreMaxDepth+" structureDepth="+depth+" chunk="+cx+","+cz);\n    }\n    public static void exitOre() { --oreDepth; }''')
p.write_text(s)
if mode=='noore':
    p=root/'NexusTech.java'
    s=p.read_text()
    reg='        com.mikael8367.nexustech.ores.OreRegistry.registerWorldGenerator();'
    assert s.count(reg)==1
    s=s.replace(reg,'        System.out.println("ORE_GENERATOR_DISABLED_FOR_CONTROL");')
    p.write_text(s)
print('DIAG_MODE='+mode+' (TEST ONLY, reactor remains disabled, no jar exported)')
