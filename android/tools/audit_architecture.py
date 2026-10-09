"""Enforce source dependency direction in the Gradle modules without Android tooling."""
from pathlib import Path
import re, sys
ROOT=Path(__file__).resolve().parents[1]
ALLOWED={'core':set(),'data':{'core'},'transport':{'core'},'ui':{'core','data'},'app':{'core','data','transport','ui'}}
errors=[]; classes={}; sources=[]
for module in ALLOWED:
    for p in (ROOT/module/'src/main/java').rglob('*.java'):
        if p.stem in classes: errors.append(f'Duplicate class: {p.stem}')
        classes[p.stem]=module;sources.append((module,p))
for module,p in sources:
    text=p.read_text(encoding='utf-8')
    text=re.sub(r'/\*.*?\*/|//[^\n]*','',text,flags=re.S)
    for name in set(re.findall(r'\b[A-Z]\w*\b',text)) & classes.keys():
        target=classes[name]
        if target != module and target not in ALLOWED[module]:
            errors.append(f'{p.relative_to(ROOT)} references forbidden {target} class {name}')
    if module=='core' and re.search(r'import\s+(android\.|com\.jcraft\.)',text):
        errors.append(f'Core depends on a platform/transport API: {p.name}')
# With non-transitive R, a reusable component cannot silently use a screen's IDs.
for module,p in sources:
    xml_text = "\n".join(x.read_text(encoding="utf-8") for x in (ROOT/module/"src/main/res").rglob("*.xml"))
    ids = set(re.findall(r"@\+id/(\w+)", xml_text))
    layouts = {x.stem for x in (ROOT/module/"src/main/res/layout").glob("*.xml")}
    text = p.read_text(encoding="utf-8")
    for kind,name in re.findall(r"(?<![\w.])R\.(id|layout)\.(\w+)", text):
        owned = ids if kind == "id" else layouts
        if name not in owned: errors.append(f"{p.relative_to(ROOT)} uses an unowned R.{kind}.{name}")
for module,allowed in ALLOWED.items():
    text=(ROOT/module/'build.gradle.kts').read_text(encoding='utf-8')
    declared=set(re.findall(r'implementation\(project\(":([^"]+)"\)\)',text))
    if declared != allowed: errors.append(f'{module} dependencies {declared} != {allowed}')
for e in errors:print('ERROR:',e)
print(f'Architecture audit: {len(sources)} classes, {len(ALLOWED)} modules, {len(errors)} errors')
sys.exit(bool(errors))
