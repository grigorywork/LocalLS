#!/usr/bin/env python3
"""Emergency SDK-only Android build. Does not replace or claim a successful Gradle gate."""
import argparse,json,os,pathlib,shutil,subprocess,zipfile,xml.etree.ElementTree as ET
from verify_apk import verify

def main():
 p=argparse.ArgumentParser();p.add_argument('--sdk',default=os.environ.get('ANDROID_SDK_ROOT'));p.add_argument('--jdk',default=os.environ.get('JAVA_HOME'));p.add_argument('--dependency',required=True,action='append');p.add_argument('--keystore',required=True);p.add_argument('--d8-jar');a=p.parse_args()
 root=pathlib.Path(__file__).resolve().parents[1];sdk=pathlib.Path(a.sdk);jdk=pathlib.Path(a.jdk);bt=sdk/'build-tools/36.0.0';android=sdk/'platforms/android-37.0/android.jar'
 out=root/'app/build/sdk-offline';shutil.rmtree(out,ignore_errors=True);out.mkdir(parents=True)
 for name in ['generated','classes','dex']:(out/name).mkdir()
 def run(*args):
  print('Running:',pathlib.Path(str(args[0])).name,flush=True)
  subprocess.run([str(x) for x in args],check=True,cwd=root)
 ns='http://schemas.android.com/apk/res/android';ET.register_namespace('android',ns)
 manifest=ET.parse(root/'app/src/main/AndroidManifest.xml');m=manifest.getroot();m.set('package','com.bitpoint.homeservercontrol')
 uses=ET.Element('uses-sdk');m.insert(0,uses);uses.set('{'+ns+'}minSdkVersion','24');uses.set('{'+ns+'}targetSdkVersion','37')
 m.set('{'+ns+'}versionCode','12');m.set('{'+ns+'}versionName','0.9.3');m.find('application').set('{'+ns+'}debuggable','true')
 manifest.write(out/'AndroidManifest.xml',encoding='utf-8',xml_declaration=True)
 shutil.copytree(root/'ui/src/main/res',out/'merged-res')
 shutil.copytree(root/'app/src/main/res',out/'merged-res',dirs_exist_ok=True)
 run(bt/'aapt2','compile','--dir',out/'merged-res','-o',out/'compiled-res.zip')
 run(bt/'aapt2','link','-I',android,'--manifest',out/'AndroidManifest.xml','--java',out/'generated','--extra-packages','com.bitpoint.homeservercontrol.ui','--min-sdk-version','24','--target-sdk-version','37','--compile-sdk-version-code','37','--compile-sdk-version-name','17','-o',out/'resources.apk',out/'compiled-res.zip')
 sources=[p for module in ['app','core','data','transport','ui'] for p in sorted((root/module/'src/main/java').rglob('*.java'))]+sorted((out/'generated').rglob('*.java'))
 source_list=out/'sources.txt';source_list.write_text('\n'.join(str(x) for x in sources)+'\n')
 run(jdk/'bin/javac','-J-Xmx512m','-encoding','UTF-8','-source','17','-target','17','-classpath',os.pathsep.join([str(android)]+a.dependency),'-d',out/'classes','@'+str(source_list))
 run(jdk/'bin/jar','cf',out/'classes.jar','-C',out/'classes','.')
 run(jdk/'bin/java','-Xmx512m','-cp',pathlib.Path(a.d8_jar) if a.d8_jar else bt/'lib/d8.jar','com.android.tools.r8.D8','--debug','--min-api','24','--lib',android,'--output',out/'dex',out/'classes.jar',*a.dependency)
 with zipfile.ZipFile(out/'unsigned.apk','w',compression=zipfile.ZIP_DEFLATED) as result:
  with zipfile.ZipFile(out/'resources.apk') as resources:
   for entry in resources.infolist(): result.writestr(entry,resources.read(entry.filename))
  written=set(result.namelist())
  for dependency in a.dependency:
   with zipfile.ZipFile(dependency) as archive:
    for name in archive.namelist():
     if name.endswith(('.class','/','.SF','.RSA','.DSA')) or name=='META-INF/MANIFEST.MF' or name.startswith('META-INF/versions/') or name in written:continue
     result.writestr(name,archive.read(name));written.add(name)
  for dex in sorted((out/'dex').glob('*.dex')):result.write(dex,dex.name)
 run(bt/'zipalign','-P','16','-f','4',out/'unsigned.apk',out/'aligned.apk')
 destination=root/'artifacts/LocalLS-v0.9.3-preview.apk'
 run(bt/'apksigner','sign','--ks',a.keystore,'--ks-key-alias','androiddebugkey','--ks-pass','pass:android','--key-pass','pass:android','--out',destination,out/'aligned.apk')
 metadata=verify(destination,sdk);metadata['buildMethod']='Official Android SDK AAPT2 + javac + D8 + zipalign + apksigner; Gradle gate remains blocked';metadata['d8Jar']=str(a.d8_jar or bt/'lib/d8.jar')
 (root/'artifacts/apk-preview-metadata.json').write_text(json.dumps(metadata,indent=2,ensure_ascii=False)+'\n')
 print('Official Android SDK build complete. APK:',destination,flush=True)
 print('Bytes:',metadata['bytes'],'SHA-256:',metadata['sha256'],flush=True)
if __name__=='__main__':main()
