from html.parser import HTMLParser
from pathlib import Path
import re

class Checker(HTMLParser):
    ids=set(); duplicates=set(); nav=set()
    def handle_starttag(self,tag,attrs):
        data=dict(attrs)
        if data.get('id'):
            if data['id'] in self.ids:self.duplicates.add(data['id'])
            self.ids.add(data['id'])
        if data.get('data-action'):self.nav.add(data['data-action'])

root=Path(__file__).resolve().parents[1]
h=root.joinpath('index.html').read_text(encoding='utf-8')
c=Checker();c.feed(h)
assert not c.duplicates,c.duplicates
assert {'home','streamings','favorites','all','add','manage','backup','import','updates','settings'}.issubset(c.nav)
assert {'drawer','modalShade','cards','siteView','siteFrame','importFile','updateDot'}.issubset(c.ids)
assert 'HubNXNative' in h
assert 'switch-media-hub.bookmarks.v1' in h
assert root.joinpath('android/app/src/main/assets/index.html').read_text(encoding='utf-8') == h
k=root.joinpath('android/app/src/main/java/br/com/hubnx/MainActivity.kt').read_text(encoding='utf-8')
assert k.count('addJavascriptInterface(')==1
assert 'home.addJavascriptInterface' in k
assert 'browser.addJavascriptInterface' not in k
assert 'allowedSiteUrl' in k
assert 'MessageDigest.getInstance("SHA-256")' in k
switch=root.joinpath('switch/tools/prepare_chromium_nx.py').read_text(encoding='utf-8')
assert 'hubnx' in switch and 'DEFAULT_COMMAND' in switch
print('PASSOU: DOM, itens do menu, migração, isolamento de bridge, validador de pacote e patch Switch')
