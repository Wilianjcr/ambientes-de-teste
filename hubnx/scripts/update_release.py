"""Atualiza o hubnx/releases.json de uma Release GitHub publicada com tag hubnx-vX.Y.Z.
Executado automaticamente apenas via GitHub Actions com GITHUB_EVENT_PATH definido.
Pacotes aceitos: hubnx-android-vX.Y.Z.apk e hubnx-switch-vX.Y.Z.zip.
Calcula SHA-256 do arquivo da Release; nunca marca fonte sem binário como instalada.
"""
import hashlib
import json
import os
from pathlib import Path
import re
import urllib.request

MANIFEST=Path('hubnx/releases.json')
TAG=re.compile(r'^hubnx-v(\d+\.\d+\.\d+)$')

def get_sha(url):
    request=urllib.request.Request(url,headers={'User-Agent':'HubNX-release-script'})
    digest=hashlib.sha256()
    size=0
    with urllib.request.urlopen(request,timeout=35) as response:
        while True:
            block=response.read(128*1024)
            if not block:break
            size+=len(block)
            if size>150*1024*1024:raise ValueError('Pacote acima do limite de 150MB')
            digest.update(block)
    return digest.hexdigest()

def main():
    event=json.loads(Path(os.environ['GITHUB_EVENT_PATH']).read_text())
    release=event.get('release') or {}
    tag=release.get('tag_name','')
    match=TAG.fullmatch(tag)
    if not match:print('Release não é do HubNX; ignorada');return
    if release.get('draft') or release.get('prerelease'):print('Release não estável; ignorada');return
    version=match.group(1)
    data=json.loads(MANIFEST.read_text())
    updates=0
    for platform,ext in [('android','apk'),('switch','zip')]:
        filename=f'hubnx-{platform}-v{version}.{ext}'
        found=next((a for a in release.get('assets',[]) if a.get('name')==filename),None)
        if not found:continue
        download=found['browser_download_url']
        if not download.startswith('https://github.com/') or not download.endswith(filename):raise ValueError('URL inesperada')
        sha=get_sha(download)
        data['platforms'][platform]={'version':version,'published':True,'url':download,'sha256':sha,'notes':release.get('body','')[:1500]}
        updates+=1
    if updates:
        data['generated_at']=__import__('datetime').datetime.now(__import__('datetime').timezone.utc).date().isoformat()
        MANIFEST.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n')
        print(f'Manifesto atualizado: {version} ({updates} pacote(s))')
    else:print('Release sem pacotes HubNX com nome esperado; manifesto preservado')

if __name__=='__main__':main()
