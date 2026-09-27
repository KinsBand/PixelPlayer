import subprocess, pathlib, sys, time, json, re, xml.etree.ElementTree as ET
ROOT=pathlib.Path(__file__).resolve().parent
ADB=r'C:\Users\trai\AppData\Local\Android\Sdk\platform-tools\adb.exe'
SERIAL='5A150DLCH006BF'
PKG='com.theveloper.pixelplay.debug'
def adb(*args,timeout=35):
    return subprocess.run([ADB,'-s',SERIAL,*map(str,args)],capture_output=True,timeout=timeout)
def shell(*args):
    return adb('shell',*args).stdout.decode(errors='replace')
def snapshot(label):
    (ROOT/(label+'.png')).write_bytes(adb('exec-out','screencap','-p').stdout)
    dump=adb('shell','uiautomator','dump','/sdcard/pixel-audit.xml')
    if b'dumped to' not in dump.stdout:
        print('DUMP FAILED',dump.stdout.decode(errors='replace')); return
    raw=adb('exec-out','cat','/sdcard/pixel-audit.xml').stdout
    (ROOT/(label+'.xml')).write_bytes(raw)
    tree=ET.fromstring(raw)
    for node in tree.iter('node'):
        a=node.attrib
        if a.get('text') or a.get('content-desc'):
            print(a.get('text',''), '|',a.get('content-desc',''),'|',a.get('bounds'),'|',a.get('checked'))
def measure(label, actions):
    shell('dumpsys','gfxinfo',PKG,'reset')
    start=time.time()
    for action in actions:
        p=action.split(',')
        if p[0]=='wait': time.sleep(float(p[1]))
        elif p[0]=='tap': shell('input','tap',*p[1:])
        elif p[0]=='swipe': shell('input','swipe',*p[1:])
        elif p[0]=='key': shell('input','keyevent',p[1])
        elif p[0]=='text': shell('input','text',p[1])
        if p[0]!='wait': time.sleep(0.6)
    time.sleep(1)
    raw=shell('dumpsys','gfxinfo',PKG)
    (ROOT/(label+'-gfx.txt')).write_text(raw,encoding='utf-8')
    summary={'label':label,'seconds':round(time.time()-start,1),'actions':actions}
    for line in raw.splitlines():
        if re.match(r'(Total frames|Janky frames:|\d+th percentile|Number Slow UI|Number Missed|Number Frame deadline)',line):
            print(line); summary[line.split(':')[0]]=line.split(':',1)[1].strip()
    with (ROOT/'journeys.jsonl').open('a',encoding='utf-8') as f:f.write(json.dumps(summary)+'\n')
if __name__=='__main__':
    mode,label,*actions=sys.argv[1:]
    if mode=='snap': snapshot(label)
    elif mode=='run': measure(label,actions);snapshot(label)
    elif mode=='measure': measure(label,actions)
