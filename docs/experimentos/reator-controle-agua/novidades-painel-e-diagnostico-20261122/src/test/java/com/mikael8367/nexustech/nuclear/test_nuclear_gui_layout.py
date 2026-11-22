"""Static GUI geometry regressions; no Minecraft client or Gradle required."""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[7]
SRC = ROOT / 'src/main/java/com/mikael8367/nexustech/nuclear'
GUI = (SRC / 'NuclearGui.java').read_text(encoding='utf-8')
CONTAINER = (SRC / 'NuclearContainer.java').read_text(encoding='utf-8')
LAYOUT = (SRC / 'NuclearGuiLayout.java').read_text(encoding='utf-8')


def intersects(a, b):
    return a[0] < b[2] and b[0] < a[2] and a[1] < b[3] and b[1] < a[3]


def lang(locale):
    result = {}
    for line in (ROOT / ('src/main/resources/assets/nexustech/lang/' + locale + '.lang')).read_text(encoding='utf-8').splitlines():
        if '=' in line and not line.startswith('#'):
            key, value = line.split('=', 1)
            result[key] = value
    return result


class NuclearGuiLayoutTest(unittest.TestCase):
    def test_coordinate_constants_and_wiring(self):
        for fragment in ('WIDTH=176, HEIGHT=221', 'MACHINE_SLOT_Y=58', 'INVENTORY_TOP=125',
                         'PLAYER_SLOT_Y=INVENTORY_TOP+14', 'HOTBAR_Y=INVENTORY_TOP+72',
                         'BUTTON_Y=102, BUTTON_WIDTH=78, BUTTON_HEIGHT=20'):
            self.assertIn(fragment, LAYOUT)
        for x in (8, 30, 52):
            self.assertRegex(CONTAINER, r'new SafeSlot\(tile,\d,' + str(x) + r',NuclearGuiLayout.MACHINE_SLOT_Y\)')
        self.assertIn('NuclearGuiLayout.PLAYER_SLOT_Y+r*18', CONTAINER)
        self.assertIn('NuclearGuiLayout.HOTBAR_Y', CONTAINER)
        self.assertIn('NuclearGuiLayout.MACHINE_SLOT_Y-1', GUI)
        self.assertIn('guiTop+NuclearGuiLayout.INVENTORY_TOP,0,126,176,96', GUI)
        self.assertEqual(GUI.count('GlStateManager.color(1.0F,1.0F,1.0F,1.0F)'), 2)
        for x in (8, 90):
            self.assertIn('guiLeft+'+str(x)+',guiTop+NuclearGuiLayout.BUTTON_Y', GUI)
        self.assertEqual(GUI.count('NuclearGuiLayout.BUTTON_WIDTH,NuclearGuiLayout.BUTTON_HEIGHT'), 2)
        self.assertIn('sendEnchantPacket(inventorySlots.windowId,button.id)', GUI)
        self.assertIn('new GuiButton(2,guiLeft+90,guiTop+58,78,18', GUI)
        self.assertIn('id!=0 && id!=1 && id!=2', CONTAINER)

    def test_text_slots_and_buttons_do_not_overlap(self):
        # Minecraft's standard font occupies 9 vertical pixels. Slots and buttons
        # include their frame; full 160 px width is reserved for all text lines.
        lines = re.findall(r'^  text\(.*,(\d+)\);$', GUI, re.M)
        self.assertEqual([int(y) for y in lines], [5,16,26,36,46,80,90])
        text_boxes = [(8,int(y),168,int(y)+9) for y in lines]
        machine_slots = [(x-1,57,x+17,75) for x in (8,30,52)]
        player_slots = [(x-1,y-1,x+17,y+17) for y in (139,157,175,197) for x in range(8,153,18)]
        buttons = [(8,102,86,122),(90,102,168,122),(90,58,168,76)]
        boxes = text_boxes + machine_slots + player_slots + buttons
        for i, a in enumerate(boxes):
            self.assertTrue(0 <= a[0] < a[2] <= 176 and 0 <= a[1] < a[3] <= 221, a)
            for b in boxes[i+1:]:
                self.assertFalse(intersects(a,b), (a,b))
        self.assertLessEqual(221, 240)  # usual minimum scaled 1.12.2 screen height

    def test_meters_geometry_wiring_and_contrast(self):
        self.assertIn('METER_X=8, METER_WIDTH=160, METER_HEIGHT=9', LAYOUT)
        self.assertIn('ENERGY_Y=16, HEAT_Y=36, WATER_Y=90', LAYOUT)
        # Background rectangles coincide with their own text rows only.
        rows = [(8,y,168,y+9) for y in (5,16,26,36,46,80,90)]
        controls = [(x-1,57,x+17,75) for x in (8,30,52)]
        controls += [(8,102,86,122),(90,102,168,122),(90,58,168,76)]
        controls += [(x-1,y-1,x+17,y+17) for y in (139,157,175,197) for x in range(8,153,18)]
        for y in (16,36,90):
            box=(8,y,168,y+9)
            self.assertEqual([r for r in rows if intersects(box,r)], [box])
            self.assertFalse(any(intersects(box,c) for c in controls))
        for expected in ('meter(NuclearGuiLayout.ENERGY_Y,tile.displayField(0),NuclearController.CAPACITY',
                         'meter(NuclearGuiLayout.HEAT_Y,tile.displayField(2),NuclearController.SCRAM',
                         'meter(NuclearGuiLayout.WATER_Y,tile.displayField(4),NuclearController.WATER_CAPACITY',
                         'percent("FE",0,NuclearController.CAPACITY)',
                         'percent(I18n.format("nuclear.heat"),2,NuclearController.SCRAM)',
                         'percent(I18n.format("nuclear.water"),4,NuclearController.WATER_CAPACITY)'):
            self.assertIn(expected, GUI)
        self.assertLess(GUI.index('meter(NuclearGuiLayout.ENERGY_Y'), GUI.index('bindTexture(TEX)'))
        # Standard dark foreground text against each fill, all above WCAG 4.5:1.
        def lum(rgb):
            def linear(c):
                c=c/255
                return c/12.92 if c<=0.04045 else ((c+0.055)/1.055)**2.4
            return sum(linear((rgb >> shift)&255)*weight for shift,weight in ((16,.2126),(8,.7152),(0,.0722)))
        ink=lum(4210752)
        for bg in (0xe4e4e4,0xffe2a8,0xb8dcff,0xc7e6c2,0xffdcab,0xffb8b8,0xd6d6d6):
            self.assertGreater((lum(bg)+.05)/(ink+.05),4.5,hex(bg))

    def test_translations_maxima_and_scaling(self):
        self.assertIn('fontRenderer.getStringWidth(value)', GUI)
        self.assertIn('width>160?160.0F/width:1.0F', GUI)
        self.assertIn('GlStateManager.popMatrix()', GUI)
        controller=(SRC/'NuclearController.java').read_text(encoding='utf-8')
        for limit in ('CAPACITY=1000000','FE_PER_TICK=200','FUEL_TICKS=2400','WATER_CAPACITY=8000','SCRAM=1000'):
            self.assertIn(limit,controller)
        for locale in ('en_us','pt_br'):
            t=lang(locale)
            for key in ('tile.nexustech.nuclear_controller.name','nuclear.production','nuclear.heat',
                        'nuclear.fuel','nuclear.water','nuclear.status.scram','nuclear.status.unformed',
                        'nuclear.status.on','nuclear.status.off','nuclear.button.on','nuclear.button.off'):
                self.assertTrue(t.get(key), (locale,key))
            rows=[t['tile.nexustech.nuclear_controller.name'], 'FE: 1000000 / 1000000',
                  t['nuclear.production']+': 200 FE/t', t['nuclear.heat']+': 1000 / 1000',
                  t['nuclear.fuel']+': 2400 / 2400', t['nuclear.water']+': 8000 mB']
            rows += [t['nuclear.status.'+s] for s in ('scram','unformed','on','off')]
            # Conservative 8 px per glyph: even under this pessimistic bound,
            # normal max values fit or require at most a small reduction.
            for row in rows:
                estimated=len(row)*8
                self.assertGreaterEqual(min(1,160/estimated), .80, (locale,row,estimated))
            for key in ('nuclear.button.on','nuclear.button.off','nuclear.guard.on','nuclear.guard.off'):
                self.assertLessEqual(len(t[key])*8, 76, (locale,key))
            for key in ('nuclear.guard.hint1','nuclear.guard.hint2','nuclear.heat.hint1','nuclear.heat.hint2','nuclear.water.hint1','nuclear.water.hint2','nuclear.energy.hint','nuclear.status.no_water','nuclear.status.full'):
                self.assertTrue(t.get(key), (locale,key))
            for row in ('FE: 2147483647 / 1000000', t['nuclear.production']+': 2147483647 FE/t',
                        t['nuclear.heat']+': 2147483647 / 1000',
                        t['nuclear.fuel']+': 2147483647 / 2400', t['nuclear.water']+': 2147483647 mB',
                        t['nuclear.production']+': -2147483648 FE/t'):
                width=len(row)*8
                self.assertLessEqual(width*min(1,160/width),160.00001)

if __name__ == '__main__':
    unittest.main()
