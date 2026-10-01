#!/usr/bin/env python3
import hashlib,io,json,os,re,subprocess,tempfile,zipfile
from pathlib import Path
R=Path(__file__).resolve().parents[1];sdk=Path(os.environ['ANDROID_HOME']);readelf=sdk/'ndk/27.2.12479018/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-readelf'
reports=[]
for relative in ['engine/build/outputs/aar/engine-debug.aar','smoke-host/build/outputs/apk/debug/smoke-host-debug.apk','artifacts/independent-consumer/app-debug.apk']:
 p=R/relative;z=zipfile.ZipFile(p);row={'path':relative,'sha256':hashlib.sha256(p.read_bytes()).hexdigest(),'native':[]}
 libraries=[n for n in z.namelist() if n.endswith('.so')]
 assert len(libraries)==3,libraries
 for name in libraries:
  assert name.endswith('/libtelegram_photo_enhancement.so'),name
  with tempfile.NamedTemporaryFile(suffix='.so') as f:
   f.write(z.read(name));f.flush();elf=subprocess.check_output([str(readelf),'-d','-Ws',f.name],text=True)
  needed=re.findall(r'Shared library: \[([^]]+)\]',elf)
  assert set(needed)=={'libm.so','libdl.so','libc.so'},needed
  assert not re.search(r'tgnet|tmessages|ssl|socket|connect|sendto|recvfrom|Account',elf,re.I),elf
  assert 'Java_dev_oritwig_telegram_photo_NativeEnhancement_calculate' in elf
  row['native'].append({'entry':name,'needed':needed,'only_engine_jni':True})
 excluded=rb'ToneCurve|YUCIHighPass|drawSkinSmoothPass|greenAndBlueChannelOverlay|stillImageHighPass|highpassSkinSmoothing'
 forbidden=rb'org[/.]telegram|tgnet|AccountInstance|UserConfig|MessagesController|MessagesStorage|ConnectionsManager|FileLoader|java[/.]net[/.]|okhttp|android/permission/INTERNET'
 for name in z.namelist():
  if name.endswith('.dex'):
   assert not re.search(forbidden,z.read(name)),name
   assert not re.search(excluded,z.read(name)),name
  if name=='classes.jar':
   jar=zipfile.ZipFile(io.BytesIO(z.read(name)))
   for c in jar.namelist():
    if c.endswith('.class'):
     assert not re.search(forbidden,jar.read(c)),c
     assert not re.search(excluded,jar.read(c)),c
     assert 'ToneCurve' not in c,c
 if p.suffix=='.apk':
  permissions=subprocess.check_output([str(sdk/'build-tools/35.0.0/aapt2'),'dump','permissions',str(p)],text=True)
  assert 'uses-permission' not in permissions,permissions
  row['permissions']=[]
 reports.append(row)
print(json.dumps({'verified_artifacts':reports},indent=2))
