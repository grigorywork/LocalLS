"""Verify Java modules and Android resources when native Windows real-path lookup fails.
This is not Gradle packaging, lint, or an Android device test.
"""
from pathlib import Path
import argparse, json, os, shutil, subprocess, xml.etree.ElementTree as ET


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--root', type=Path, default=Path(__file__).absolute().parents[1])
    p.add_argument('--sdk', type=Path, required=True)
    p.add_argument('--java', required=True, help='Java executable from a JDK 17 installation')
    p.add_argument('--jsch', type=Path, required=True)
    p.add_argument('--junit', type=Path, required=True)
    p.add_argument('--hamcrest', type=Path, required=True)
    a=p.parse_args();root=a.root.absolute();sdk=a.sdk.absolute()
    out=root/'build/manual-verification';out.mkdir(parents=True,exist_ok=True)
    def run(*args):
        r=subprocess.run([str(x) for x in args],check=True,capture_output=True,text=True,errors='replace')
        print(r.stdout+r.stderr,flush=True);return r.stdout+r.stderr
    res=out/'res'
    shutil.copytree(root/'ui/src/main/res',res,dirs_exist_ok=True)
    shutil.copytree(root/'app/src/main/res',res,dirs_exist_ok=True)
    ns='http://schemas.android.com/apk/res/android';ET.register_namespace('android',ns)
    manifest=ET.parse(root/'app/src/main/AndroidManifest.xml')
    manifest.getroot().set('package','com.bitpoint.homeservercontrol')
    manifest.write(out/'AndroidManifest.xml',encoding='utf-8',xml_declaration=True)
    android=sdk/'platforms/android-37.0/android.jar';bt=sdk/'build-tools/36.0.0'
    aapt=bt/('aapt2.exe' if os.name=='nt' else 'aapt2')
    run(aapt,'compile','--dir',res,'-o',out/'compiled-res.zip')
    run(aapt,'link','-I',android,'--manifest',out/'AndroidManifest.xml','--java',out/'generated',
        '--extra-packages','com.bitpoint.homeservercontrol.ui','--min-sdk-version','24','--target-sdk-version','37',
        '-o',out/'resources.apk',out/'compiled-res.zip')
    compiler=root/'tools/MemoryCompiler.java';compiled={}
    dependencies={'core':[],'data':['core'],'transport':['core'],'ui':['core','data'],'app':['core','data','transport','ui']}
    for module,deps in dependencies.items():
        libs=([android] if module!='core' else [])+[compiled[x] for x in deps]
        if module=='transport':libs.append(a.jsch.absolute())
        sources=list((root/module/'src/main/java').rglob('*.java'))
        if module in {'ui','app'}:
            generated=out/'generated/com/bitpoint/homeservercontrol'
            if module=='ui':generated=generated/'ui'
            sources+=list(generated.glob('R.java'))
        if not libs:
            empty=out/'empty';empty.mkdir(exist_ok=True);libs=[empty]
        classes=out/module;classes.mkdir(exist_ok=True)
        run(a.java,compiler,os.pathsep.join(str(x) for x in libs),classes,*sources)
        compiled[module]=classes
    libs=[compiled['core'],compiled['transport'],android,a.jsch.absolute(),a.junit.absolute(),a.hamcrest.absolute()]
    tests=[p for m in ['core','transport'] for p in (root/m/'src/test/java').rglob('*.java')]
    test_classes=out/'tests';test_classes.mkdir(exist_ok=True)
    run(a.java,compiler,os.pathsep.join(str(x) for x in libs),test_classes,*tests)
    result=run(a.java,'-cp',os.pathsep.join(str(x) for x in [test_classes,*libs]),'org.junit.runner.JUnitCore',
        'com.bitpoint.homeservercontrol.SecretRedactorTest','com.bitpoint.homeservercontrol.HostKeyPinningTest')
    report={'modules':list(dependencies),'resourceLink':'passed','javaCompilation':'passed',
        'jvmTests':result.strip(),'gradlePackaging':'not verified','lint':'not verified','deviceTests':'not executed'}
    (out/'result.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
    print('Source verification passed. This does not declare a built APK.')

if __name__=='__main__':main()
