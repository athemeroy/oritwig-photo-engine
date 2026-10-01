#!/usr/bin/env python3
"""Read-only provenance and actual-algorithm retention checks."""
import hashlib,json,re
from pathlib import Path
import os
R=Path(__file__).resolve().parents[1];U=Path(os.environ.get('TELEGRAM_REFERENCE_DIR',str(R.parent/'oritwig-photo-private-reference/upstream')));D=R/'engine/src/main/java/dev/oritwig/telegram/photo'
checks=[]
def check(ok,label):
 if not ok: raise AssertionError(label)
 checks.append(label)
def body(s,needle):
 a=s.index(needle);a=s.index('{',a);depth=0;quote=None;escape=False;comment=None;i=a
 while i<len(s):
  c=s[i];n=s[i:i+2]
  if comment=='line':
   if c=='\n':comment=None
  elif comment=='block':
   if n=='*/':comment=None;i+=1
  elif quote:
   if escape:escape=False
   elif c=='\\':escape=True
   elif c==quote:quote=None
  elif n=='//':comment='line';i+=1
  elif n=='/*':comment='block';i+=1
  elif c in '\"\'':quote=c
  elif c=='{':depth+=1
  elif c=='}':
   depth-=1
   if depth==0:return s[a:i+1]
  i+=1
 raise ValueError(needle)
def method_body(s,name):
 match=re.search(r'(?:public|private|protected)\s+(?:static\s+)?[\w<>\[\].]+\s+'+re.escape(name)+r'\(',s)
 if not match: raise AssertionError('Missing method '+name)
 return body(s,match[0])
def normalized(s): return '\n'.join(line.lstrip() for line in s.splitlines())
for row in json.loads((U/'manifest.json').read_text()):
 b=(U/row['path']).read_bytes(); sha=hashlib.sha1(b'blob '+str(len(b)).encode()+b'\0'+b).hexdigest()
 check(sha==row['sha'],'Exact upstream Git blob '+row['path'])
for row in json.loads((R/'docs/extraction-manifest.json').read_text())['outputs']:
 check(hashlib.sha256((R/row['output']).read_bytes()).hexdigest()==row['sha256'],'Extracted hash '+row['output'])
prefix='TMessagesProj/src/main/java/org/telegram/'
original=(U/(prefix+'ui/Components/FilterShaders.java')).read_text();current=(D/'FilterShaders.java').read_text()
# Each GLSL literal sequence is byte-preserved; compare declaration-to-end of assignment.
pattern=r'(?m)^\s*(?:private|public) static (?:final )?String (\w+)\s*=\s*'
def shaders(s):
 result={}
 for match in re.finditer(pattern,s):
  quote=False;escape=False;i=match.end()
  while i<len(s):
   c=s[i]
   if quote:
    if escape:escape=False
    elif c=='\\':escape=True
    elif c=='"':quote=False
   elif c=='"':quote=True
   elif c==';':break
   i+=1
  result[match[1]]=s[match.end():i]
 return result
check(all(shaders(original).get(k)==v for k,v in shaders(current).items()),'All '+str(len(shaders(current)))+' retained GLSL constant bodies unchanged')
check(not any(x in current for x in ['YUCIHighPass','ToneCurve','drawSkinSmoothPass','skinPassDrawn','greenAndBlueChannelOverlay','highpassSkinSmoothing']), 'Optional smoothing/private tone mapper absent')
for method in ['drawEnhancePass','drawSharpenPass','drawCustomParamsPass','drawBlurPass','getFilterShadersDelegate','vertexShaderForOptimizedBlurOfRadius','fragmentShaderForOptimizedBlurOfRadius']:
 left=method_body(original,method);right=method_body(current,method).replace('NativeEnhancement.calcCDT','Utilities.calcCDT')
 if method=='drawEnhancePass': left=left.replace('isVideo && !skinPassDrawn','isVideo').replace('skinPassDrawn ? renderTexture[1] : bitmapTextre[0]','bitmapTextre[0]')
 if method=='getFilterShadersDelegate': left=re.sub(r'            @Override\s+public float getSoftenSkinValue\(\)\s*\{[^}]*\}\s*', '', left)
 check(left==right,'Retained upstream pass/math unchanged: '+method)
thread=(U/(prefix+'ui/Components/FilterGLThread.java')).read_text();current=(D/'FilterGLThread.java').read_text()
check(body(thread,'private Bitmap getRenderBufferBitmap(')==body(current,'private Bitmap getRenderBufferBitmap('),'Upstream glReadPixels bitmap export unchanged')
photo=(U/(prefix+'ui/Components/PhotoFilterView.java')).read_text()
for cls,methods in [('CurvesValue',['interpolateCurve','getDataPoints','isDefault','saveValues','restoreValues']),('CurvesToolValue',['fillBuffer','shouldBeSkipped'])]:
 for method in methods:check(normalized(method_body(photo,method))==normalized(method_body((D/(cls+'.java')).read_text(),method)),'Upstream curves unchanged: '+method)
native=(U/'TMessagesProj/jni/image.cpp').read_text();native=body(native,'JNIEXPORT void Java_org_telegram_messenger_Utilities_calcCDT(')
current=body((R/'engine/src/main/cpp/enhancement.cpp').read_text(),'JNIEXPORT void Java_dev_oritwig_telegram_photo_NativeEnhancement_calculate(')
check(native[native.index('    float imageWidth = width;'):]==current[current.index('    float imageWidth = width;'):],'Native calcCDT kernel body unchanged after safety precondition')
for p in list(D.glob('*.java'))+list((R/'engine/src/main/cpp').glob('*')):
 check(not re.search(r'org\.telegram|AccountInstance|UserConfig|MessagesController|MessagesStorage|ConnectionsManager|FileLoader|tgnet|tmessages|java\.net|okhttp|https?://',p.read_text()),'No account/network runtime source '+p.name)
print(json.dumps({'passed':len(checks),'checks':checks},indent=2))
