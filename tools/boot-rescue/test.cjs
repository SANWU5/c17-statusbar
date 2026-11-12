// SPDX-License-Identifier: GPL-3.0-only
'use strict';
const fs=require('fs');
(async()=>{
 const {instance}=await WebAssembly.instantiate(fs.readFileSync(process.argv[2]));
 const s=instance.exports;
 let checks=0,time=100,fires=0;
 const test=(ok,why)=>{++checks;if(!ok)throw Error(why);};
 const POWER=116,UP=115,DOWN=114;
 function reset(){s.reset();time=100;fires=0;}
 function input(fd,key,value,t=time){const result=s.input(fd,key,value,BigInt(t));fires+=result;return result;}
 function tap(key,fd=0,spacing=200){input(fd,key,1);const r=input(fd,key,0,time+50);time+=spacing;return r;}
 const command=[UP,UP,UP,UP,UP];
 // The command: five Volume Up presses; only the fifth release fires, never a key down.
 reset();for(let i=0;i<5;i++){test(!input(0,UP,1),'never trigger on key down');const r=input(0,UP,0,time+50);test(r===(i===4?1:0),'only the fifth release fires');test(s.stage()===i+1,'stage counts presses '+i);time+=200;}
 test(fires===1,'one trigger');for(let n=0;n<10;n++)tap(UP);test(fires===1,'same process never retriggers');
 for(let n=1;n<5;n++){reset();for(let i=0;i<n;i++)tap(UP);test(fires===0,n+' presses do not fire');}
 reset();for(let i=0;i<7;i++)tap(UP);test(fires===1,'extra presses after the fifth change nothing');
 // Keys the system acts on while booting cancel the command: Power, Volume Down, any other key.
 for(const other of [POWER,DOWN,30]){for(let position=1;position<5;position++){reset();for(let i=0;i<5;i++){if(i===position)tap(other);tap(UP);}test(fires===0,'key '+other+' before press '+position+' cancels');}}
 for(const other of [POWER,DOWN,30]){reset();tap(UP);tap(UP);tap(other);command.forEach(k=>tap(k));test(fires===1,'a fresh five after key '+other+' still fires');}
 reset();for(let i=0;i<4;i++)tap(UP);input(0,UP,1);input(0,DOWN,1,time+10);input(0,DOWN,0,time+20);input(0,UP,0,time+50);test(fires===0,'Volume Down pressed while the fifth is held cancels it');
 reset();[POWER,DOWN,DOWN,POWER].forEach(k=>tap(k));test(fires===0&&s.stage()===0,'the old power and volume sequence does nothing');
 // Both volume keys together (the accessibility shortcut) never count.
 reset();input(0,UP,1);input(1,DOWN,1,time+1);input(0,UP,0,time+50);input(1,DOWN,0,time+51);time+=200;for(let i=0;i<4;i++)tap(UP);test(fires===0,'overlapping distinct keys reject');
 // Autorepeat and duplicate downs are not presses: holding Volume Up never fires.
 reset();input(0,UP,1);for(let n=0;n<50;n++)input(0,UP,2,time+10+n*30);input(0,UP,0,time+1600);time+=1800;test(fires===0&&s.stage()===1,'a long hold is one press');
 reset();for(let i=0;i<5;i++){input(0,UP,1);input(0,UP,1,time+1);for(let n=0;n<10;n++)input(0,UP,2,time+2+n);input(0,UP,0,time+50);time+=200;}test(fires===1,'repeat/down duplicates ignored');
 // Five seconds from the first press to the last release; a late run starts again from the late press.
 reset();command.forEach(k=>tap(k,0,1300));test(fires===0,'slow presses past five seconds do not fire');
 reset();tap(UP);time+=5200;command.forEach(k=>tap(k));test(fires===1,'after a timeout a fresh five fires');
 reset();for(let i=0;i<4;i++)tap(UP,0,1000);time+=900;input(0,UP,1);input(0,UP,0,time+120);test(fires===0,'the fifth released after five seconds does not fire');
 reset();for(let i=0;i<4;i++)tap(UP,0,1100);input(0,UP,1);input(0,UP,0,time+50);test(fires===1,'four gaps of 1.1 s still fit five seconds');
 reset();for(let i=0;i<4;i++)tap(UP);input(0,UP,1);test(fires===0,'the last key must be released');time+=6000;input(0,UP,0);test(fires===0,'the last held past the window rejects');
 // Mirrored input devices are one key.
 reset();for(let i=0;i<5;i++){input(0,UP,1);input(1,UP,1,time+1);input(0,UP,0,time+50);input(1,UP,0,time+51);time+=200;}test(fires===1,'mirrored device held dedup');
 reset();for(let i=0;i<5;i++){input(0,UP,1);input(0,UP,0,time+10);input(1,UP,1,time+20);input(1,UP,0,time+30);time+=200;}test(fires===1,'late whole mirror packet dedup');
 reset();command.forEach((k,i)=>tap(k,i%3));test(fires===1,'presses split across real devices count');
 // Lost events, a clock going back and orphan releases never complete the command.
 reset();tap(UP);tap(UP);s.dropped(0);[UP,UP,UP].forEach(k=>tap(k));test(fires===0,'SYN_DROPPED invalidates the run');
 reset();tap(UP);tap(UP);s.dropped(0);command.forEach(k=>tap(k));test(fires===1,'fresh run after a device drop');
 reset();tap(UP);tap(UP);time=10;[UP,UP,UP].forEach(k=>tap(k));test(fires===0,'nonmonotonic event clock rejects');
 reset();input(0,UP,0);[UP,UP,UP,UP].forEach(k=>tap(k));test(fires===0,'an orphan release is not a press');
 // Every 5-tap stream of the two volume keys: only five Volume Up presses fire.
 for(let mask=0;mask<32;mask++){reset();for(let n=0;n<5;n++)tap(mask&(1<<n)?UP:DOWN);test(fires===(mask===31?1:0),'enumerated 5-tap candidate '+mask);}
 // Every 6-tap stream of Volume Up and Power: fires only when five Volume Up presses come in a row.
 for(let mask=0;mask<64;mask++){reset();let run=0,expect=0;for(let n=0;n<6;n++){const up=mask&(1<<n);tap(up?UP:POWER);run=up?run+1:0;if(run>=5)expect=1;}test(fires===expect,'enumerated 6-tap candidate '+mask);}
 // Random streams with fewer than five Volume Up presses can never fire.
 let seed=0x41c17;function random(){seed=(Math.imul(seed,1664525)+1013904223)>>>0;return seed;}
 for(let run=0;run<500;run++){reset();let ups=0;for(let n=0;n<8;n++){let key=[POWER,UP,DOWN,30][random()%4];if(key===UP&&ups>=4)key=DOWN;if(key===UP)ups++;tap(key,random()%4,100+random()%200);}test(fires===0,'randomized stream with four or fewer Volume Up '+run);}
 s.probeReset();s.probeObserve(0,0,7);test(s.probeReadable()===0&&s.probeUsable()===0&&s.probeCoverage()===0,'unreadable input provides no evidence');
 s.probeObserve(1,0,7);test(s.probeReadable()===1&&s.probeUsable()===0&&s.probeCoverage()===0,'readable device without clock support cannot provide usable keys');
 s.probeObserve(1,1,1);test(s.probeReadable()===2&&s.probeUsable()===1&&s.probeCoverage()===1,'clock accepted power device contributes only power');
 s.probeObserve(1,1,6);test(s.probeReadable()===3&&s.probeUsable()===2&&s.probeCoverage()===7,'split physical devices can prove complete key coverage');
 s.probeObserve(1,1,8);test(s.probeReadable()===4&&s.probeUsable()===3&&s.probeCoverage()===7,'unknown key bits cannot expand the proof');
 s.probeReset();s.probeObserve(1,1,4);s.probeObserve(1,0,3);test(s.probeReadable()===2&&s.probeUsable()===1&&s.probeCoverage()===4,'clock rejected power and up stay unavailable');
 console.log(JSON.stringify({checks,productionSequenceWasm:true,productionProbeAccumulatorWasm:true,destructiveCodeExecuted:false}));
})().catch(e=>{console.error(e);process.exit(1);});
