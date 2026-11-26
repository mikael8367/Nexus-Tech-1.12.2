#!/usr/bin/env python3
"""Static checks against an EXISTING mapped Forge jar; does not boot Forge or create archives."""
import hashlib, os, re, subprocess, sys
jar = sys.argv[1] if len(sys.argv)>1 else '/home/daytona/workspace/nexus-test/gradle/caches/forge_gradle/minecraft_user_repo/net/minecraftforge/forge/1.12.2-14.23.5.2860_mapped_snapshot_20171003-1.12/forge-1.12.2-14.23.5.2860_mapped_snapshot_20171003-1.12.jar'
javap = os.environ.get('JAVAP','/home/daytona/workspace/nexus-test/jdk/bin/javap')
def bc(cls):
    return subprocess.check_output([javap,'-classpath',jar,'-c','-p',cls], universal_newlines=True)
def method(text, sig):
    start=text.index(sig)
    end=text.find('\n  public ',start+len(sig))
    if end<0: end=len(text)
    return text[start:end]
w=bc('net.minecraft.world.World')
c=bc('net.minecraft.world.chunk.Chunk')
setstate=method(w,'public boolean setBlockState(net.minecraft.util.math.BlockPos, net.minecraft.block.state.IBlockState, int);')
notify=method(w,'public void markAndNotifyBlock(')
observe=method(w,'public void updateObservingBlocksAt(')
chunkset=method(c,'public net.minecraft.block.state.IBlockState setBlockState(')
pop=method(c,'protected void populate(')
assert 'getChunkFromBlockCoords' in setstate and 'Chunk.setBlockState' in setstate and 'checkLight' in setstate
assert 'iconst_1' in notify and re.search(r'bipush\s+16\s*\n\s*\d+: iand\s*\n\s*\d+: ifne',notify) and 'updateObservingBlocksAt' in notify
assert all(x in observe for x in ('BlockPos.west','BlockPos.east','BlockPos.north','BlockPos.south','BlockPos.up','BlockPos.down'))
assert 'Block.breakBlock' in chunkset and 'Block.onBlockAdded' in chunkset
assert 'logCascadingWorldGeneration' in pop and 'populating' in pop
assert 'loaded a new chunk' in c
with open(jar,'rb') as f: sha=hashlib.sha256(f.read()).hexdigest()
print('FORGE_MAPPED_SHA256='+sha)
print('PASS static: flags 1,16 gate observer notification; observer routine checks all 6 adjacent positions')
print('PASS static: World.setBlockState obtains chunk, calls Chunk.setBlockState and may check light')
print('PASS static: Chunk.setBlockState contains breakBlock and onBlockAdded callbacks')
print('PASS static: nested Chunk.populate detects populating and may log cascading worldgen')
print('LIMIT: bytecode inspection only, no runtime, no proof of origin of previous warnings')
