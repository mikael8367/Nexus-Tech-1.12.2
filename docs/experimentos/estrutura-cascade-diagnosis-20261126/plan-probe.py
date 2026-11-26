from pathlib import Path
p=Path('project/src/main/java/com/mikael8367/nexustech/worldgen/StructurePlan.java')
s=p.read_text()
needle='    private StructurePlan() {}'
assert s.count(needle)==1
s=s.replace(needle,needle+'\n    private static boolean reject(String why) { System.out.println("STRUCTURE_REJECT "+why); return false; }')
replacements={
'if(c==null || schematic==null || c.x<3 || c.z<3 || c.x+5>12 || c.z+5>12) return false;':'if(c==null || schematic==null || c.x<3 || c.z<3 || c.x+5>12 || c.z+5>12) return reject("invalid candidate");',
'if(site.hasTile(x,y,z)) return false;':'if(site.hasTile(x,y,z)) return reject("sky tile");',
'if(y<60 || y>100 || site.id(x,y,z)!=2 || site.meta(x,y,z)!=0 || site.hasTile(x,y,z)) return false;':'if(y<60 || y>100 || site.id(x,y,z)!=2 || site.meta(x,y,z)!=0 || site.hasTile(x,y,z)) return reject("surface y="+y+" id="+(y>=0?site.id(x,y,z):-999));',
'if(surface==-1) surface=y; else if(surface!=y) return false;':'if(surface==-1) surface=y; else if(surface!=y) return reject("uneven footprint");',
'if(!natural(id) || (id==3 && site.meta(x,d,z)!=0) || site.hasTile(x,d,z)) return false;':'if(!natural(id) || (id==3 && site.meta(x,d,z)!=0) || site.hasTile(x,d,z)) return reject("shallow id="+id+" y="+d);',
'if(id!=0 && !natural(id)) return false;':'if(id!=0 && !natural(id)) return reject("deep id="+id+" y="+d);',
'if(site.hasTile(x,d,z)) return false;':'if(site.hasTile(x,d,z)) return reject("deep tile");',
'if(total==0 || solid*2<total) return false;':'if(total==0 || solid*2<total) return reject("grounding");'
}
for a,b in replacements.items():
    assert s.count(a)==1, a
    s=s.replace(a,b)
p.write_text(s)
print('Applied diagnostic-only StructurePlan rejection markers; logic unchanged')
