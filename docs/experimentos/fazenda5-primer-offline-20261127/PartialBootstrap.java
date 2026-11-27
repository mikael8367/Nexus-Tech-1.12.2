package com.mikael8367.nexustech.worldgen;

import java.lang.reflect.Field;
import net.minecraft.init.Bootstrap;
import net.minecraft.util.SoundEvent;
import net.minecraft.block.Block;

/** Offline experiment only: partial block bootstrap; not a replacement for Forge lifecycle. */
public final class PartialBootstrap {
 public static void main(String[] args) throws Exception {
  System.err.println("PARTIAL: starting sound and block registration");
  Field f=Bootstrap.class.getDeclaredField("alreadyRegistered"); f.setAccessible(true); f.setBoolean(null,true);
  SoundEvent.registerSounds();
  Block.registerBlocks();
  System.err.println("PARTIAL: blocks registered; invoking test");
  if (args.length==1 && args[0].equals("deep")) Fazenda5DeepPrimerTest.main(new String[0]);
  else Fazenda5ForgePrimerTest.main(args);
 }
}
