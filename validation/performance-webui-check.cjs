const fs=require('fs'),vm=require('vm'),assert=require('assert');
const root=process.cwd(), html=fs.readFileSync(root+'/app/src/main/assets/performance-module/webroot/index.html','utf8'),js=fs.readFileSync(process.env.C17_WEBUI_SCRIPT || root+'/app/src/main/assets/performance-module/webroot/app.js','utf8');
const ids=new Set([...html.matchAll(/id="([^"]+)"/g)].map(m=>m[1]));
let checks=0;
const nodes={}; for(const id of ids)nodes[id]={textContent:'',disabled:false,hidden:false,dataset:{},attributes:{},setAttribute(name,value){this.attributes[name]=String(value)},addEventListener(){},scrollIntoView(){}};
const document={documentElement:{dataset:{}},hidden:false,getElementById(id){assert(ids.has(id),'unknown element '+id);return nodes[id]},querySelector(){return{classList:{toggle(){}}}},addEventListener(){}};
const window={setTimeout(){return 0},clearTimeout(){},addEventListener(){}};
vm.runInNewContext(js,{window,document,console,Date,Set,Promise,Number,Error,Object,String},{filename:'app.js'});
const api=window.C17WebUI;
const fields={installed:'1',pending:'0',disabled:'0',remove:'0',boot:'11111111-1111-1111-1111-111111111111',busy:'0',active_job:'0',module_version:'2.3',module_version_code:'14',record_present:'1',last_state:'current',last_time:'1791858000',last_compiled:'0',last_packages:'2',last_error:'none',last_boot:'11111111-1111-1111-1111-111111111111',last_mode:'auto',last_module_version_code:'14',last_reused:'2',last_verified:'2',last_deferred:'0',last_verification:'verified',last_priority:'background',schedule_kind:'on_demand',schedule_state:'scheduled'};
function raw(f){return'C17_WEB:begin\n'+Object.entries(f).map(([k,v])=>k+'='+v).join('\n')+'\nC17_WEB:end\n'}
function test(patch,title,canRun,canToggle){const parsed=api.parse(raw({...fields,...patch})),view=api.present(parsed,false);assert.equal(view.title,title);assert.equal(view.canRun,canRun);assert.equal(view.canToggle,canToggle);checks+=3;}
test({},'无需编译',true,true);
test({busy:'1',last_state:'running',active_job:'1'},'优化中',false,false);
test({last_state:'running',last_boot:'22222222-2222-2222-2222-222222222222'},'已安装',true,true);
test({last_state:'running',last_module_version_code:'4'},'已安装',true,true);
test({last_state:'waiting',last_mode:'boot',busy:'1'},'等待空闲',false,false);
test({last_state:'waiting',last_mode:'run',busy:'1'},'优化中',false,false);
test({last_state:'running'},'结果待确认',false,false);
test({last_state:'failed',last_error:'compile_cancel_failed',active_job:'1'},'取消未确认',false,false);
test({last_state:'failed',last_error:'compile_cancel_failed',active_job:'0'},'未完成',true,true);
test({last_state:'failed',last_error:'restore_cancel_failed',active_job:'0'},'未完成',true,true);
test({last_state:'failed',last_error:'compile_failed'},'未完成',true,true);
test({last_state:'deferred',last_error:'device_hot'},'已延期',true,true);
for (const reason of ['art_cancel_unavailable','timeout_unavailable']) {
  test({last_state:'deferred',last_error:reason},'编译未开始',true,true);
  assert(!api.present({...fields,last_state:'deferred',last_error:reason},false).message.includes('空闲条件'));checks++;
}
test({disabled:'1'},'已停用',false,true);
test({disabled:'1',pending:'1'},'已停用',false,false);
assert(api.present({...fields,disabled:'1',busy:'1'},false).message.includes('当前任务仍需确认结束'));checks++;
// Real backend limits are bytes before hex encoding. Optional logs never invalidate core state.
for (const size of [512,513,2048]) {
  const hex=Buffer.from('x'.repeat(size),'utf8').toString('hex');
  const parsed=api.parse(raw({...fields,trace_hex:hex}));
  assert.equal(parsed.trace_hex,hex);assert.equal(api.present(parsed,false).canRun,true);checks+=2;
}
for (const [key,limit] of [['trace_hex',2048],['failure_hex',512]]) {
  for (const broken of ['0','gg','<script>', 'aa'.repeat(limit+1), 'a\u202eb']) {
    const parsed=api.parse(raw({...fields,[key]:broken}));
    assert.equal(parsed[key],'');assert.equal(api.present(parsed,false).title,'无需编译');checks+=2;
  }
  const duplicate=raw({...fields,[key]:'41'}).replace('C17_WEB:end',key+'=42\n'+key+'=43\nC17_WEB:end');
  assert.equal(api.parse(duplicate)[key],'');checks++;
}
// Unicode, truncated UTF-8 and encoded markup remain display-only text.
for(const value of ['天气 ☁️ 中文\n失败：未获得权限','<img src=x onerror=alert(1)>']) {
  const hex=Buffer.from(value).toString('hex');
  assert.equal(api.logText(api.parse(raw({...fields,trace_hex:hex})).trace_hex,2048),value);checks++;
}
assert(api.logText('e4b8',2048).includes('日志编码不完整'));checks++;
assert.equal(api.logText('4100421b43',2048),'ABC');checks++;
assert.equal(api.parse(raw({...fields,trace_hex:'61'.repeat(2048),failure_hex:'62'.repeat(512)})).failure_hex.length,1024);checks++;
assert.throws(()=>api.parse(raw({...fields,trace_hex:'gg',last_compiled:'evil'})));checks++;
test({remove:'1',disabled:'1',pending:'1',busy:'1'},'待卸载',false,false);
test({pending:'1',busy:'1'},'待重启',false,false);
test({module_version_code:'4'},'待更新',false,false);
test({module_version_code:'12',last_module_version_code:'12'},'待更新',false,false);
test({module_version_code:'13',last_module_version_code:'13'},'待更新',false,false);
assert(!api.currentRecord({...fields,last_module_version_code:'12'}));checks++;
test({busy:'unknown'},'无法确认',false,false);
test({active_job:'unknown'},'无法确认',false,false);
test({active_job:'1'},'取消未确认',false,false);
test({record_present:'0'},'已安装',true,true);
test({lock_available:'0'},'系统暂不支持',false,false);
assert.throws(()=>api.parse(raw({...fields,lock_available:'unknown'})));checks++;
test({installed:'0',pending:'0'},'未安装',false,false);
test({installed:'0',pending:'1'},'待重启',false,false);
for(const payload of [raw(fields).replace('installed=1','installed=x'),raw(fields).replace('last_compiled=0','last_compiled=NaN'),raw(fields).replace('last_compiled=0','last_compiled=-1'),raw(fields).replace('last_mode=auto','last_mode=evil'),raw(fields).replace('last_error=none','last_error=<script>'),raw(fields).replace('C17_WEB:end','installed=1\nC17_WEB:end'),raw(fields).replace('C17_WEB:end',''),raw(fields)+raw(fields),'x'.repeat(16385)]){assert.throws(()=>api.parse(payload));checks++;}
assert(!/\beval\s*\(|new\s+Function\b|\.innerHTML\b|\bfetch\s*\(|XMLHttpRequest|WebSocket/.test(js));checks++;
assert(!/<(?:script|link)[^>]*(?:src|href)="https?:/.test(html));checks++;
assert(nodes.optimize.disabled);checks++;
assert.equal(nodes['status-title'].textContent,'请在 C17 查看');checks++;
assert.equal(api.parse(raw({...fields,last_mode:'uninstall'})).last_mode,'uninstall');checks++;
assert.equal(api.parse(raw({...fields,last_idle_remaining:'600',last_diagnostic:'uuid_missing'})).last_diagnostic,'uuid_missing');checks++;
assert(js.includes('document.hidden || window.c17HostPaused === true'));checks++;
assert(js.includes('c17 ? 50000 : 12000'));checks++;
for(const state of ['scheduled','inactive','unavailable','deferred','blocked','running']){assert(js.includes(state));checks++;}
assert(!js.includes('interval_hours=24'));checks++;
{
  const ledger={total_since:'1792257000',total_compiles:'3',total_wall_ms:'12500',total_cpu_ms:'900',total_size_kb:'6144',total_reuses:'8',total_avoided_ms:'30000',runtime_sampled:'1792257900',runtime_jit_ms:'6000',runtime_proc_s:'600',runtime_aot:'1'};
  const view=api.benefitView(api.parse(raw({...fields,...ledger})));
  assert.equal(view.compiles,'3 次');assert.equal(view.saved,'8 次');assert.equal(view.aot,'已使用');assert.equal(view.jit,'600 ms/分');
  assert(view.compilesNote.includes('13 秒')&&view.compilesNote.includes('6 MB'));assert(view.savedNote.includes('30 秒')&&view.savedNote.includes('估算'));
  assert(view.jitNote.includes('10 分钟'));checks+=7;
  const empty=api.benefitView(api.parse(raw(fields)));
  assert.equal(empty.compiles,'—');assert.equal(empty.aot,'—');assert.equal(empty.jit,'—');assert.equal(empty.since,'尚无记录');checks+=4;
  assert.equal(api.benefitView(api.parse(raw({...fields,...ledger,runtime_proc_s:'30'}))).jit,'—');checks++;
  assert.equal(api.benefitView(api.parse(raw({...fields,...ledger,runtime_aot:'0'}))).aot,'未使用');checks++;
  assert.equal(api.benefitView(api.parse(raw({...fields,total_since:'x',total_compiles:'-4'}))).compiles,'—');checks++;
  const ring=api.ringView(api.parse(raw(fields)),true);assert.equal(ring.label,'2/2');assert(ring.dash.startsWith('213.6'));assert.equal(ring.opacity,'1');checks+=3;
  assert.equal(api.ringView(api.parse(raw({...fields,last_verified:'1'})),false).opacity,'0.55');checks++;
  assert.equal(api.ringView(api.parse(raw({...fields,record_present:'0'})),false).label,'—');checks++;
  assert.equal(api.parse(raw({...fields,last_diagnostic:'client_failed'})).last_diagnostic,'client_failed');checks++;
}
{
  const rescue={rescue_enabled:'1',rescue_state:'waiting_unlock',rescue_error:'user_locked',rescue_time:'1793850000'};
  let view=api.recoveryView(api.parse(raw({...fields,...rescue})));
  assert(view.available&&view.enabled&&view.canToggle);assert(view.message.includes('首次解锁'));checks+=2;
  assert(!api.recoveryView(api.parse(raw(fields))).available);checks++;
  assert(!api.recoveryView(api.parse(raw({...fields,...rescue,module_version_code:'11'}))).available);checks++;
  assert(api.recoveryView({...fields,...rescue,busy:'1',active_job:'1',lock_available:'0'}).canToggle);checks++;
  assert(api.recoveryView({...fields,...rescue,disabled:'1'}).title.includes('停用'));checks++;
  for(const patch of [{rescue_enabled:'yes'},{rescue_state:'<script>'},{rescue_error:'random_shell_output'},{rescue_time:'-1'}]) {
    assert(!api.recoveryView(api.parse(raw({...fields,...rescue,...patch}))).available);checks++;
  }
  for(const patch of [{pending:'1'},{remove:'1'},{installed:'0'}]) {assert(!api.recoveryView({...fields,...rescue,...patch}).canToggle);checks++;}
  assert(!api.recoveryView({...fields,...rescue,rescue_enabled:'0'}).enabled);checks++;
  assert.equal(api.present({...fields,...rescue},false).title,'无需编译');checks++;
  assert(nodes['rescue-toggle'].disabled);checks++;
  assert(js.includes('c17.setRecoveryEnabled'));assert(js.includes('webui.sh rescue-enable'));assert(js.includes('webui.sh rescue-disable'));checks+=3;
  assert(!js.includes('boot-rescue.sh --trigger'));assert(!html.includes('立即恢复'));checks+=2;
}
// The default page stays brief; technical records and the full recovery procedure are opt-in.
const defaults=html.split('<body>')[1].split('<details')[0];
assert(!/<details[^>]*\bopen\b/.test(html));assert(!defaults.includes('收益记录')&&!defaults.includes('自动规则'));checks+=2;
assert(defaults.includes('系统UI防炸')&&defaults.includes('触发后清空 SystemUI 数据'));checks++;
assert(!html.includes('开机按键恢复')&&!js.includes('开机按键恢复'));checks++;
assert(defaults.replace(/<[^>]+>/g,'').replace(/\s/g,'').length<150);checks++;
assert(html.includes('role="switch"')&&nodes['rescue-toggle'].attributes['aria-checked']==='false');checks++;
if(process.argv[2]){const real=api.parse(fs.readFileSync(process.argv[2],'utf8'));assert.equal(real.module_version_code,'8');assert.equal(real.last_state,'current');checks+=2;}
console.log('WebUI protocol and safety: '+checks+' assertions passed; no Root/device operations.');
