from playwright.sync_api import sync_playwright
from pathlib import Path
import json,time,re
root=Path(__file__).resolve().parents[1]
errors=[]
with sync_playwright() as p:
    browser=p.chromium.launch()
    page=browser.new_page(viewport={"width":1440,"height":1050},device_scale_factor=1)
    page.on('pageerror',lambda e:errors.append(str(e)))
    page.on('console',lambda m:errors.append(m.text) if m.type=='error' else None)
    page.goto('http://127.0.0.1:5173')
    page.get_by_role('heading',name='Scheduling laboratory').wait_for()
    page.get_by_text('Live connection',exact=True).wait_for()
    page.screenshot(path=str(root/'docs/dashboard-idle.png'),full_page=True)
    page.get_by_label('Tasks',exact=True).fill('200')
    page.get_by_label('Workers',exact=True).fill('2')
    page.get_by_label('Duration (ms)',exact=True).fill('40')
    page.get_by_role('button',name='Run experiment',exact=True).click()
    page.get_by_role('button',name=re.compile('Experiment running')).wait_for()
    page.get_by_text('Running',exact=True).first.wait_for()
    page.wait_for_timeout(700)
    page.screenshot(path=str(root/'docs/dashboard-desktop.png'),full_page=True)
    page.get_by_role('button',name='Run experiment',exact=True).wait_for(timeout=20000)
    assert page.locator('tbody tr').count()>0
    page.get_by_label('Duration (ms)',exact=True).fill('20')
    page.get_by_role('button',name='Recommend configuration').click()
    page.locator('.advice').wait_for()
    page.get_by_text('random-forest',exact=False).wait_for()
    page.get_by_role('button',name='Experiments',exact=True).click()
    page.locator('.row-link').first.click()
    page.get_by_role('dialog').wait_for()
    page.get_by_role('button',name='Close details').click()
    with page.expect_download() as info:
        page.get_by_role('button',name='Export JSON').click()
    assert info.value.suggested_filename=='threadopt-experiments.json'
    page.get_by_role('button',name='Methodology',exact=True).click()
    page.get_by_role('heading',name='Reproducible workloads').wait_for()
    page.get_by_role('button',name='Overview',exact=True).click()
    page.get_by_label('Workers',exact=True).fill('1')
    page.get_by_label('Duration (ms)',exact=True).fill('100')
    page.get_by_role('button',name='Run experiment',exact=True).click()
    page.get_by_role('button',name='Cancel experiment',exact=True).click()
    page.get_by_role('button',name='Run experiment',exact=True).wait_for()
    page.set_viewport_size({'width':390,'height':844})
    page.wait_for_function('document.documentElement.scrollWidth <= window.innerWidth')
    page.wait_for_timeout(300)
    page.screenshot(path=str(root/'docs/dashboard-mobile.png'),full_page=True)
    assert page.evaluate('document.documentElement.scrollWidth <= window.innerWidth')
    print(json.dumps({'title':page.title(),'url':page.url,'console_errors':errors,'interaction':'run/live metrics/completion/advisor/history/details/export/methodology/cancel','viewports':['1440x1050','390x844']}))
    assert not errors
    browser.close()
