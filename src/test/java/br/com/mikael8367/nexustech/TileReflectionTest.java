package br.com.mikael8367.nexustech;

import static org.junit.Assert.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

import org.junit.Test;

/**
 * Minecraft recreates tile entities on chunk load with Class.newInstance(). A non-public class
 * or constructor throws IllegalAccessException and silently deletes the stored energy and items.
 */
public class TileReflectionTest {
    @Test public void everyTileCanBeRecreatedByMinecraft() throws Exception {
        Class<?>[] tiles = {TileCoalGenerator.class, TileSolarPanel.class, TileEnergyCable.class,
                TileElectricFurnace.class, TileElectricAnvil.class};
        for (Class<?> tile : tiles) {
            assertTrue(tile + " must be public", Modifier.isPublic(tile.getModifiers()));
            Constructor<?> constructor = tile.getConstructor();
            assertTrue(tile + " needs a public no-arg constructor", Modifier.isPublic(constructor.getModifiers()));
        }
    }
}
