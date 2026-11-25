from pathlib import Path
import sys

source = Path('.github/workflows')
out = Path(sys.argv[1])
out.mkdir(parents=True, exist_ok=False)
original = (source / 'validate_jar.py').read_text(encoding='utf-8')
old = "                if not isinstance(logo, str) or not re.fullmatch(r'[a-zA-Z0-9_./-]+\\.png', logo) or '..' in logo.split('/') or logo.startswith('/'):\n                    error('mcmod.info logoFile missing or invalid')\n"
new = "                if not isinstance(logo, str):\n                    error('mcmod.info logoFile missing or invalid')\n                elif logo == '':\n                    if src_meta.get('logoFile') != '': error('mcmod.info logoFile differs from source')\n                elif not re.fullmatch(r'[a-zA-Z0-9_./-]+\\.png', logo) or '..' in logo.split('/') or logo.startswith('/'):\n                    error('mcmod.info logoFile missing or invalid')\n"
if original.count(old) != 1:
    raise SystemExit('Padrao do validador nao corresponde; recusando alteracao.')
(out / 'validate_jar.py').write_text(original.replace(old, new), encoding='utf-8')
for name in ('test_validate_jar.py', 'test_main_logo.py'):
    (out / name).write_bytes((source / name).read_bytes())
