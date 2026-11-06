package br.com.mikael8367.nexustech;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import org.junit.Test;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Catches broken textures, missing models/textures and untranslated names before they reach the game. */
public class ResourceIntegrityTest {
    private static final File ASSETS = new File("src/main/resources/assets");
    private static final File JAVA = new File("src/main/java");

    private static List<File> files(File dir, String suffix) {
        List<File> out = new ArrayList<File>();
        File[] children = dir.listFiles();
        if (children == null) return out;
        for (File child : children) {
            if (child.isDirectory()) out.addAll(files(child, suffix));
            else if (child.getName().endsWith(suffix)) out.add(child);
        }
        return out;
    }

    private static JsonObject json(File file) throws IOException {
        Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8);
        try {
            return new JsonParser().parse(reader).getAsJsonObject();
        } finally {
            reader.close();
        }
    }

    private static File asset(String location, String folder, String suffix) {
        String domain = location.contains(":") ? location.substring(0, location.indexOf(':')) : "minecraft";
        String path = location.contains(":") ? location.substring(location.indexOf(':') + 1) : location;
        return new File(ASSETS, domain + "/" + folder + "/" + path + suffix);
    }

    @Test public void everyPngDecodesAndIsAPowerOfTwoSquare() throws IOException {
        List<File> pngs = files(ASSETS, ".png");
        assertTrue("no textures found", pngs.size() > 0);
        for (File png : pngs) {
            BufferedImage image;
            try {
                image = ImageIO.read(png);
                if (image != null) image.getRGB(image.getWidth() - 1, image.getHeight() - 1);
            } catch (Exception e) {
                throw new AssertionError("Corrupted texture " + png + ": " + e);
            }
            assertNotNull("Unreadable texture " + png, image);
            int w = image.getWidth();
            assertTrue("Texture width must be a power of two: " + png, w > 0 && (w & (w - 1)) == 0);
            assertEquals("Texture must be square: " + png, w, image.getHeight());
        }
    }

    @Test public void modelTexturesAndParentsExist() throws IOException {
        for (File model : files(ASSETS, ".json")) {
            if (!model.getPath().contains("/models/")) continue;
            JsonObject root = json(model);
            if (root.has("parent")) {
                String parent = root.get("parent").getAsString();
                if (parent.contains(":") && !parent.startsWith("minecraft:")) {
                    assertTrue("Missing parent " + parent + " in " + model, asset(parent, "models", ".json").isFile());
                }
            }
            if (root.has("textures")) {
                for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("textures").entrySet()) {
                    String texture = entry.getValue().getAsString();
                    if (texture.startsWith("#") || !texture.contains(":") || texture.startsWith("minecraft:")) continue;
                    assertTrue("Missing texture " + texture + " in " + model, asset(texture, "textures", ".png").isFile());
                }
            }
        }
    }

    private static void collectModels(JsonElement element, Set<String> out) {
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) collectModels(child, out);
        } else if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            if (object.has("model") && object.get("model").isJsonPrimitive()) out.add(object.get("model").getAsString());
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) collectModels(entry.getValue(), out);
        }
    }

    @Test public void blockstateModelsExistAndEveryBlockHasAnItemModel() throws IOException {
        for (File state : files(ASSETS, ".json")) {
            if (!state.getPath().contains("/blockstates/")) continue;
            Set<String> models = new TreeSet<String>();
            collectModels(json(state), models);
            assertTrue("Blockstate without models: " + state, !models.isEmpty());
            for (String model : models) {
                String domain = model.substring(0, model.indexOf(':'));
                File file = new File(ASSETS, domain + "/models/block/" + model.substring(model.indexOf(':') + 1) + ".json");
                assertTrue("Missing block model " + model + " in " + state, file.isFile());
            }
            String domain = state.getParentFile().getParentFile().getName();
            File item = new File(ASSETS, domain + "/models/item/" + state.getName());
            assertTrue("Missing item model for " + state, item.isFile());
        }
    }

    private static Properties lang(File file) throws IOException {
        Properties properties = new Properties();
        Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8);
        try {
            properties.load(reader);
        } finally {
            reader.close();
        }
        return properties;
    }

    @Test public void everyBlockAndGuiKeyIsTranslatedInBothLanguages() throws IOException {
        for (File domain : ASSETS.listFiles()) {
            File blockstates = new File(domain, "blockstates");
            File langDir = new File(domain, "lang");
            if (!blockstates.isDirectory()) continue;
            Set<String> required = new TreeSet<String>();
            for (File state : blockstates.listFiles()) {
                String name = state.getName().replace(".json", "");
                required.add("tile." + domain.getName() + "." + name + ".name");
            }
            required.add("itemGroup." + domain.getName());
            for (File source : files(JAVA, ".java")) {
                Matcher matcher = Pattern.compile("\"(gui\\." + Pattern.quote(domain.getName()) + "\\.[a-z0-9_.]+)\"")
                        .matcher(new String(Files.readAllBytes(source.toPath()), StandardCharsets.UTF_8));
                while (matcher.find()) required.add(matcher.group(1));
            }
            for (String language : new String[] {"en_us.lang", "pt_br.lang"}) {
                Properties properties = lang(new File(langDir, language));
                for (String key : required) {
                    String value = properties.getProperty(key);
                    if (value == null || value.trim().isEmpty()) fail("Missing " + key + " in " + domain.getName() + "/" + language);
                }
            }
        }
    }

    /** Vanilla items with subtypes must declare "data" or Forge rejects the whole recipe. */
    @Test public void recipesDeclareDataForItemsWithSubtypes() throws IOException {
        String[] subtyped = {"minecraft:anvil", "minecraft:coal", "minecraft:dye", "minecraft:wool",
                "minecraft:planks", "minecraft:log", "minecraft:stone", "minecraft:stained_glass"};
        for (File recipe : files(ASSETS, ".json")) {
            if (!recipe.getPath().contains("/recipes/")) continue;
            JsonObject root = json(recipe);
            assertTrue("Recipe without result: " + recipe, root.has("result"));
            for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("key").entrySet()) {
                JsonObject ingredient = entry.getValue().getAsJsonObject();
                String item = ingredient.get("item").getAsString();
                for (String s : subtyped) {
                    if (s.equals(item)) assertTrue("Ingredient " + item + " needs \"data\" in " + recipe, ingredient.has("data"));
                }
            }
        }
    }
}
