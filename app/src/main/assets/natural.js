/* Offline presentation. The native touch gate is the only unlock authority. */
(()=>{'use strict';
document.addEventListener('selectstart',event=>event.preventDefault());
document.addEventListener('contextmenu',event=>event.preventDefault());
const $=id=>document.getElementById(id),app=$('app'),hero=$('hero'),root=document.body,ns='http://www.w3.org/2000/svg';
let state={},motion=false,nativeMotion=false,firstHome=true,lastPage='',lastBlocked=0,holdTotal=300000,holding=false,fogTimer=null,pendingFog=false,releaseTimer=null,lastDays='',lastJar=0,lastMinutes=-1,activeGameOpen=false;
function layer(id,markup){const s=document.createElementNS(ns,'svg');s.id=id;s.setAttribute('class','scene-layer');s.setAttribute('viewBox','0 0 500 470');s.setAttribute('preserveAspectRatio','xMidYMax slice');s.setAttribute('aria-hidden','true');s.innerHTML=markup;hero.append(s);return s;}
const dawn=$('valley').cloneNode(true);dawn.id='dawn';dawn.setAttribute('class','scene-layer');dawn.querySelectorAll('[id]').forEach(el=>el.id='dawn-'+el.id);dawn.querySelectorAll('*').forEach(el=>{for(const a of Array.from(el.attributes)){let v=a.value.replace(/url\(#([^)]*)\)/g,'url(#dawn-$1)');if(a.name==='href'&&v.startsWith('#'))v='#dawn-'+v.slice(1);if(v!==a.value)el.setAttribute(a.name,v);}});hero.append(dawn);
layer('breeze','<g opacity=".14" fill="var(--mist)"><path d="M-50 285 Q100 257 240 282T570 277v11Q370 298 210 289T-50 300Z"/><path d="M-30 352Q80 333 176 347T540 327v10Q310 366 165 357T-30 364Z"/></g><path d="M138 315q2-5-3-8t-1-10q3-6-1-9" fill="none" stroke="var(--mist)" stroke-width="2" opacity=".4"/><g fill="var(--deep)" opacity=".8"><path d="M465 435v-55l-15 35 11-4-15 17 16-3-14 14 14-4v12Z"/></g>');
layer('sparks','<g fill="#f5e9bc"><circle cx="112" cy="56" r="1.1"/><circle cx="418" cy="101" r="1.2"/><circle cx="285" cy="30" r="1"/></g><g fill="#d6e9a8"><circle cx="328" cy="366" r="1.6"/><circle cx="355" cy="351" r="1.25"/></g>');
layer('bird','<g fill="none" stroke="var(--trees)" stroke-width="1.3" stroke-linecap="round"><path d="M0 110q6-6 12 0 6-6 12 0"/><path class="return-birds" d="M-30 122q6-6 12 0 6-6 12 0M-50 100q5-5 10 0 5-5 10 0"/></g>');
const branches='M0 0V-47M0-12l-17-11M0-23l16-11M0-35l-10-9',crown='<path d="M0-77C-7-65-24-56-23-45c-10 8-8 24 3 26-7 10 6 19 20 16 14 3 27-6 20-16 11-2 13-18 3-26C24-56 7-65 0-77Z" fill="currentColor"/>';
layer('treeLayer','<g transform="translate(392 427)"><g id="growingTree" style="color:#9cbe8d">'+crown+'<path d="'+branches+'" fill="none" stroke="#c2c79b" stroke-width="2" stroke-linecap="round"/><g class="blossoms" fill="#f4d8b0"><circle cy="-53" r="4"/><circle cx="-13" cy="-35" r="3"/><circle cx="13" cy="-26" r="3.5"/><circle cx="6" cy="-64" r="3"/></g></g></g>');
hero.insertAdjacentHTML('beforeend','<div id="sunriseWash" aria-hidden="true"></div><div id="intercept" role="status"><strong>That can wait. Breathe.</strong><small id="interceptTime"></small></div><svg id="lantern" viewBox="0 0 80 110" aria-hidden="true"><defs><radialGradient id="lanternHalo"><stop stop-color="#ffd78b" stop-opacity=".7"/><stop offset="1" stop-color="#ffd78b" stop-opacity="0"/></radialGradient></defs><ellipse class="lantern-light" cx="40" cy="65" rx="40" ry="44" fill="url(#lanternHalo)"/><path d="M26 42v-7a14 14 0 0128 0v7M24 44h32l5 45H19ZM21 93h38M24 41h32" fill="none" stroke="#c8b587" stroke-width="2"/><path class="lantern-flame" d="M40 49c3 9 9 14 5 22-4 7-13 0-10-7 2-5 5-7 5-15Z" fill="#ffdb8a"/></svg><div id="setupSteps"><span id="adminStep">Guard</span><span id="accessStep">Access</span></div>');
$('ring').insertAdjacentHTML('afterbegin','<span class="ring-progress" aria-hidden="true"><span class="ring-half right"><span class="ring-arc" id="arcRight"></span></span><span class="ring-half left"><span class="ring-arc" id="arcLeft"></span></span></span>');$('ring').setAttribute('aria-label','Hold continuously to end this session');$('ring').setAttribute('aria-describedby','holdHelp');
$('weekSummary').insertAdjacentHTML('afterend','<div id="weekTrees" aria-hidden="true"></div>');
$('pause').insertAdjacentHTML('afterbegin','<svg id="sprig" viewBox="-27 -80 54 84" aria-hidden="true"><path d="M0 0V-66M0-10l-20-17M0-22l19-17M0-35l-13-13M0-44l11-14M0-53l-8-10" fill="none" stroke="currentColor" stroke-width="2.6" stroke-linecap="round"/></svg>');
const complete=document.createElement('section');complete.id='complete';complete.hidden=true;complete.innerHTML='<div class="small">A little space, reclaimed</div><h2>Unlocked</h2><p class="copy">Your session is complete.<br>Take this quiet with you.</p><button class="primary" data-act="exit">Exit</button><button class="secondary" data-act="continue">Back to Home</button>';document.querySelector('main').append(complete);
$('block').insertAdjacentHTML('beforeend','<div id="jarGame" hidden><div>Catch a little light<small id="jarHint">Five quiet taps. No score to chase.</small></div><button class="jar-button" data-act="jar" aria-label="Catch a firefly"><span class="jar-count" id="jarCount">○○○○○</span></button></div>');
$('active').insertAdjacentHTML('beforeend','<button type="button" class="secondary" id="activeGameToggle">Calm games</button>');
for(const b of document.querySelectorAll('#complete [data-act],#jarGame [data-act]'))b.onclick=()=>{if(!state.active&&!holding)act(b.dataset.act);};
function write(id,text){if($(id).textContent!==text)$(id).textContent=text;}
function clearFog(){clearTimeout(fogTimer);fogTimer=null;pendingFog=false;$('intercept').classList.remove('visible');}
function showFog(){clearTimeout(fogTimer);fogTimer=null;if(document.hidden){pendingFog=true;return;}pendingFog=false;$('intercept').hidden=false;requestAnimationFrame(()=>{$('intercept').classList.add('visible');});fogTimer=setTimeout(clearFog,2500);}
function useMotion(){motion=nativeMotion&&!document.hidden&&!matchMedia('(prefers-reduced-motion: reduce)').matches;root.classList.toggle('motion-off',!motion);if(!motion){if(document.hidden&&!pendingFog)clearFog();clearTimeout(releaseTimer);releaseTimer=null;root.classList.remove('releasing');}else if(pendingFog&&state.active){showFog();}}
window.setMotion=value=>{nativeMotion=!!value;useMotion();};document.addEventListener('visibilitychange',()=>{useMotion();if(document.hidden){holding=false;root.classList.remove('holding');cgStopAll();}else if(pendingFog&&state.active){showFog();}});matchMedia('(prefers-reduced-motion: reduce)').addEventListener('change',useMotion);
function progress(p,scenery=true){p=Math.max(0,Math.min(1,Number(p)||0));root.style.setProperty('--p',p);const right=$('arcRight'),left=$('arcLeft');right.style.transform='rotate('+Math.min(180,p*360)+'deg)';left.style.transform='rotate('+Math.max(0,(p-.5)*360)+'deg)';right.style.setProperty('--cap-gap',p>=.5?'0px':'1px');left.style.setProperty('--cap-gap',p>=1?'0px':'1px');if(!root.classList.contains('releasing')){right.style.opacity=p>0?'1':'0';left.style.opacity=p>.5?'1':'0';}if(scenery)root.style.setProperty('--scenic-p',p);}

/* Unified Calm Games widget: Focus Tiles, Memory Pairs, and Alpine Glide (Alto-inspired) */
const cgHost=document.createElement('div');cgHost.id='calmGames';
cgHost.innerHTML='<div class="cg-head"><div><div class="cg-title">Calm games</div><div class="cg-sub" id="cgSub">Quiet games to ride out an urge.</div></div><button type="button" class="cg-btn" id="cgToggle">Play</button></div><div id="cgBody" hidden><div class="cg-tabs" role="tablist" aria-label="Calm games"><button type="button" class="cg-tab" role="tab" data-game="tiles" aria-selected="true">Tiles</button><button type="button" class="cg-tab" role="tab" data-game="pairs" aria-selected="false">Pairs</button><button type="button" class="cg-tab" role="tab" data-game="glide" aria-selected="false">Alpine Glide</button></div><div class="cg-board g3" id="cgBoard"></div><div class="cg-foot"><div class="cg-status" id="cgStatus"></div><button type="button" class="cg-reset" id="cgReset">New</button></div></div>';
$('home').append(cgHost);
const cgBody=$('cgBody'),cgToggle=$('cgToggle'),cgBoard=$('cgBoard'),cgStatus=$('cgStatus'),cgReset=$('cgReset'),cgTabs=Array.from(cgHost.querySelectorAll('.cg-tab')),activeGameToggle=$('activeGameToggle');
let cgMode='tiles',cgTimers=[],cgLocked=false,cgGlideStop=null,cgGlideBest=0;
function cgClearTimers(){cgTimers.forEach(clearTimeout);cgTimers=[];}
function cgStopAll(){cgClearTimers();if(cgGlideStop){cgGlideStop();cgGlideStop=null;}}
function cgShuffle(arr){const a=arr.slice();for(let i=a.length-1;i>0;i--){const j=Math.floor(Math.random()*(i+1));[a[i],a[j]]=[a[j],a[i]];}return a;}

function cgStartTiles(){
 cgStopAll();cgBoard.className='cg-board g3';cgBoard.textContent='';
 const cells=[];for(let i=0;i<9;i++){const b=document.createElement('button');b.type='button';b.className='cg-cell';b.setAttribute('aria-label','Tile '+(i+1));cgBoard.append(b);cells.push(b);}
 let round=0,seq=[],step=0;
 function playRound(){
  cgClearTimers();cgLocked=true;step=0;cells.forEach(c=>c.classList.remove('lit','ok','err'));
  const len=3+round;seq=[];
  while(seq.length<len){const n=Math.floor(Math.random()*9);if(!seq.length||seq[seq.length-1]!==n)seq.push(n);}
  cgStatus.textContent='Round '+(round+1)+' of 4 · Watch';
  seq.forEach((idx,i)=>{
   cgTimers.push(setTimeout(()=>{cells[idx].classList.add('lit');},360+i*520));
   cgTimers.push(setTimeout(()=>{cells[idx].classList.remove('lit');if(i===seq.length-1){cgLocked=false;cgStatus.textContent='Repeat the '+len+' tiles';}},700+i*520));
  });
 }
 cells.forEach((cell,idx)=>{cell.onclick=()=>{
  if(cgLocked)return;
  if(idx===seq[step]){
   cell.classList.add('ok');cgTimers.push(setTimeout(()=>cell.classList.remove('ok'),200));
   step++;
   if(step===seq.length){
    cgLocked=true;round++;
    if(round>=4){cgStatus.textContent='Mind reset. Take a breath.';}
    else{cgStatus.textContent='Nice focus…';cgTimers.push(setTimeout(playRound,600));}
   }
  }else{
   cgLocked=true;cell.classList.add('err');cgStatus.textContent='Try that pattern again';
   cgTimers.push(setTimeout(()=>{cell.classList.remove('err');playRound();},600));
  }
 };});
 playRound();
}

function cgStartPairs(){
 cgStopAll();cgLocked=false;cgBoard.className='cg-board g4';cgBoard.textContent='';
 const deck=cgShuffle(['✦','✦','☽','☽','✿','✿','❋','❋','◈','◈','☀','☀']);
 let open=[],matched=0,moves=0;
 cgStatus.textContent='Find all 6 calm pairs';
 deck.forEach((sym,i)=>{
  const b=document.createElement('button');b.type='button';b.className='cg-cell';b.textContent='·';b.setAttribute('aria-label','Card '+(i+1));
  b.onclick=()=>{
   if(cgLocked||b.classList.contains('flipped')||b.classList.contains('ok'))return;
   b.classList.add('flipped');b.textContent=sym;open.push({b,sym});
   if(open.length<2)return;
   moves++;cgLocked=true;
   const [a,c]=open;open=[];
   if(a.sym===c.sym){
    cgTimers.push(setTimeout(()=>{
     a.b.classList.remove('flipped');c.b.classList.remove('flipped');
     a.b.classList.add('ok');c.b.classList.add('ok');
     matched++;cgLocked=false;
     cgStatus.textContent=matched>=6?'All 6 pairs matched in '+moves+' moves.':matched+' of 6 pairs · '+moves+' moves';
    },220));
   }else{
    cgTimers.push(setTimeout(()=>{
     a.b.classList.remove('flipped');c.b.classList.remove('flipped');
     a.b.textContent='·';c.b.textContent='·';cgLocked=false;
     cgStatus.textContent=matched+' of 6 pairs · '+moves+' moves';
    },560));
   }
  };
  cgBoard.append(b);
 });
}

/* Alpine Glide 2.0: Alto-inspired steep downhill runner with slope momentum, ramps, bunting grinds, dynamic sky & zero idle battery */
function cgStartGlide(){
 cgStopAll();cgLocked=false;cgBoard.className='cg-board glide-wrap';cgBoard.textContent='';
 const cvs=document.createElement('canvas');cvs.className='cg-canvas';cvs.id='glideCanvas';
 cvs.setAttribute('tabindex','-1');
 cvs.setAttribute('aria-label','Alpine Glide game canvas. Tap to jump, hold in air to backflip.');
 cgBoard.append(cvs);
 const ctx=cvs.getContext('2d');
 const dpr=Math.min(2,window.devicePixelRatio||1);
 let W=300,H=224;
 function resize(){
  const rect=cvs.getBoundingClientRect();
  W=Math.max(240,Math.round(rect.width||300));
  H=224;
  cvs.width=Math.round(W*dpr);cvs.height=Math.round(H*dpr);
  ctx.setTransform(dpr,0,0,dpr,0,0);
 }
 resize();

 const stars=[[0.09,0.14,1.1],[0.19,0.09,0.9],[0.31,0.18,1.3],[0.44,0.08,1.0],[0.58,0.15,1.2],[0.71,0.10,1.0],[0.84,0.19,1.2],[0.92,0.11,0.9],[0.26,0.26,0.8],[0.64,0.24,0.9],[0.49,0.22,0.8]];
 const palettes=[
  {top:[20,46,41],mid:[48,79,69],hor:[166,184,154],sun:[247,230,189],far:[36,61,53],midM:[26,48,41],nearM:[18,37,31],snow:[198,218,178],starA:0.65},
  {top:[14,28,44],mid:[33,56,74],hor:[118,144,148],sun:[236,240,224],far:[26,45,59],midM:[19,35,47],nearM:[13,26,35],snow:[184,207,204],starA:0.95},
  {top:[46,48,68],mid:[120,96,104],hor:[235,184,142],sun:[255,226,168],far:[72,62,74],midM:[50,47,58],nearM:[32,35,44],snow:[236,210,182],starA:0.25},
  {top:[30,60,58],mid:[82,118,104],hor:[210,214,174],sun:[255,240,196],far:[52,82,74],midM:[37,63,56],nearM:[23,44,39],snow:[218,228,196],starA:0.12}
 ];
 function mixRGB(a,b,t){return 'rgb('+Math.round(a[0]+(b[0]-a[0])*t)+','+Math.round(a[1]+(b[1]-a[1])*t)+','+Math.round(a[2]+(b[2]-a[2])*t)+')';}

 const riderSX=82;
 const BASE_SLOPE=0.25; // true steep downhill descent
 let raf=0,lastTs=0,phase='ready';
 let worldX=0,camY=0,speed=155,boost=0,score=0,orbsGot=0,flips=0,combo=0;
 let ry=0,vy=0,grounded=true,grinding=null,coyote=0,jumpBuf=0,angle=0,flipAccum=0,flipCount=0,pressing=false;
 let obstacles=[],ramps=[],lines=[],orbs=[],puffs=[],scarf=[],cabins=[];
 let nextRockX=360,nextRampX=520,nextLineX=780,nextOrbX=190,nextCabinX=290;
 let banner='',bannerT=0,sprayAccum=0;

 // Steep rolling mountain terrain in world coordinates (y increases downward)
 function terrainY(wx){
  return wx*BASE_SLOPE
   + Math.sin(wx*0.0052)*34
   + Math.sin(wx*0.0135+0.9)*14
   + Math.sin(wx*0.027+2.1)*5;
 }
 function slopeAngle(wx){
  return Math.atan2(terrainY(wx+8)-terrainY(wx-8),16);
 }
 function addPuff(wx,wy,col,n,spd){
  for(let i=0;i<n&&puffs.length<24;i++){
   puffs.push({wx,wy,vx:-25-Math.random()*(spd||55),vy:-20-Math.random()*(spd||45),r:1.8+Math.random()*2.5,a:0.9,col});
  }
 }
 function resetRun(){
  worldX=0;speed=155;boost=0;score=0;orbsGot=0;flips=0;combo=0;
  const startX=riderSX;
  ry=terrainY(startX);camY=ry-H*0.64;
  vy=0;grounded=true;grinding=null;coyote=0;jumpBuf=0;
  angle=slopeAngle(startX);flipAccum=0;flipCount=0;pressing=false;
  obstacles=[];ramps=[];lines=[];orbs=[];puffs=[];scarf=[];cabins=[];
  nextRockX=340;nextRampX=490;nextLineX=740;nextOrbX=180;nextCabinX=280;
  banner='';bannerT=0;sprayAccum=0;
  for(let i=0;i<10;i++)scarf.push({wx:startX-i*5,wy:ry-13});
  spawnAhead();
 }
 function spawnAhead(){
  const horizon=worldX+W+320;
  while(nextRampX<horizon){
   ramps.push({x:nextRampX,w:42,h:17});
   // Arc of light orbs off every jump ramp
   for(let k=0;k<3;k++){
    const ox=nextRampX+55+k*34;
    orbs.push({x:ox,y:terrainY(ox)-54-Math.sin(k*1.1)*16,got:false});
   }
   nextRampX+=420+Math.random()*260;
  }
  while(nextLineX<horizon){
   const len=135+Math.random()*65;
   const x1=nextLineX,x2=nextLineX+len;
   const y1=terrainY(x1)-34,y2=terrainY(x2)-34;
   lines.push({x1,y1,x2,y2});
   orbs.push({x:(x1+x2)*0.5,y:(y1+y2)*0.5-10,got:false});
   nextLineX+=560+Math.random()*320;
  }
  while(nextRockX<horizon){
   const clearRamp=ramps.every(r=>Math.abs(r.x-nextRockX)>85);
   if(clearRamp){
    const r=8+Math.random()*4.5;
    obstacles.push({x:nextRockX,r});
    if(Math.random()<0.6)orbs.push({x:nextRockX+4,y:terrainY(nextRockX)-38-Math.random()*12,got:false});
   }
   nextRockX+=210+Math.random()*140;
  }
  while(nextOrbX<horizon){
   if(obstacles.every(o=>Math.abs(o.x-nextOrbX)>34)){
    orbs.push({x:nextOrbX,y:terrainY(nextOrbX)-22-Math.random()*22,got:false});
   }
   nextOrbX+=135+Math.random()*100;
  }
  while(nextCabinX<horizon){
   cabins.push({x:nextCabinX,w:22+Math.random()*8});
   nextCabinX+=380+Math.random()*290;
  }
 }
 function stopLoop(){
  if(raf){cancelAnimationFrame(raf);raf=0;}
  pressing=false;
 }
 cgGlideStop=stopLoop;

 function drawMountainLayer(scroll,baseY,amp,freq,color,trees,snowCaps){
  ctx.fillStyle=color;ctx.beginPath();ctx.moveTo(0,H);
  for(let x=0;x<=W+12;x+=12){
   const wx=x+scroll;
   const y=baseY+Math.sin(wx*freq)*amp+Math.sin(wx*freq*2.15+0.8)*(amp*0.44);
   ctx.lineTo(x,y);
  }
  ctx.lineTo(W,H);ctx.closePath();ctx.fill();
  if(snowCaps){
   ctx.strokeStyle='rgba(244,237,221,0.16)';ctx.lineWidth=1.5;ctx.beginPath();
   for(let x=0;x<=W+12;x+=12){
    const wx=x+scroll;
    const y=baseY+Math.sin(wx*freq)*amp+Math.sin(wx*freq*2.15+0.8)*(amp*0.44);
    if(x===0)ctx.moveTo(x,y);else ctx.lineTo(x,y);
   }
   ctx.stroke();
  }
  if(trees){
   const step=42,first=Math.floor(scroll/step)*step;
   for(let tx=first;tx<scroll+W+step;tx+=step){
    const hHash=Math.abs(Math.sin(tx*0.19));
    if(hHash<0.28)continue;
    const sx=tx-scroll;
    const ty=baseY+Math.sin(tx*freq)*amp+Math.sin(tx*freq*2.15+0.8)*(amp*0.44)+2;
    const th=10+hHash*12;
    ctx.beginPath();ctx.moveTo(sx,ty-th);ctx.lineTo(sx-5,ty+3);ctx.lineTo(sx+5,ty+3);ctx.closePath();ctx.fill();
   }
  }
 }

 function draw(){
  // Smooth dynamic sky cycle every ~140m
  const cycle=(score/140)%palettes.length;
  const idxA=Math.floor(cycle),idxB=(idxA+1)%palettes.length,t=cycle-idxA;
  const pA=palettes[idxA],pB=palettes[idxB];

  const sky=ctx.createLinearGradient(0,0,0,H);
  sky.addColorStop(0,mixRGB(pA.top,pB.top,t));
  sky.addColorStop(0.55,mixRGB(pA.mid,pB.mid,t));
  sky.addColorStop(1,mixRGB(pA.hor,pB.hor,t));
  ctx.fillStyle=sky;ctx.fillRect(0,0,W,H);

  // Stars
  const starAlpha=pA.starA+(pB.starA-pA.starA)*t;
  if(starAlpha>0.05){
   ctx.fillStyle='rgba(245,238,214,'+starAlpha.toFixed(2)+')';
   for(const s of stars){ctx.beginPath();ctx.arc(s[0]*W,s[1]*H,s[2],0,Math.PI*2);ctx.fill();}
  }

  // Sun / Moon orb drifting subtly across sky
  const mx=W*0.77,my=40+Math.sin(cycle*1.5)*10;
  const sunCol=mixRGB(pA.sun,pB.sun,t);
  const glow=ctx.createRadialGradient(mx,my,4,mx,my,40);
  glow.addColorStop(0,'rgba(255,234,179,0.48)');glow.addColorStop(1,'rgba(255,234,179,0)');
  ctx.fillStyle=glow;ctx.beginPath();ctx.arc(mx,my,40,0,Math.PI*2);ctx.fill();
  ctx.fillStyle=sunCol;ctx.beginPath();ctx.arc(mx,my,11.5,0,Math.PI*2);ctx.fill();

  // Parallax mountain silhouettes (descend gently with slope)
  const parY=(camY*0.04)%24;
  drawMountainLayer(worldX*0.10,92-parY*0.2,24,0.007,mixRGB(pA.far,pB.far,t),false,true);
  drawMountainLayer(worldX*0.26,118-parY*0.4,18,0.011,mixRGB(pA.midM,pB.midM,t),true,false);
  drawMountainLayer(worldX*0.48,144-parY*0.5,14,0.015,mixRGB(pA.nearM,pB.nearM,t),true,false);

  // Background cabins along the slope
  for(const c of cabins){
   const sx=c.x-worldX;if(sx<-35||sx>W+35)continue;
   const sy=terrainY(c.x)-camY;
   ctx.fillStyle='#192e27';
   ctx.beginPath();ctx.moveTo(sx-c.w*0.5,sy+4);ctx.lineTo(sx-c.w*0.5,sy-11);ctx.lineTo(sx,sy-20);ctx.lineTo(sx+c.w*0.5,sy-11);ctx.lineTo(sx+c.w*0.5,sy+4);ctx.closePath();ctx.fill();
   ctx.fillStyle='#ffdf94';ctx.fillRect(sx-3,sy-9,5,5);
  }

  // Grindable bunting / alpine wire lines
  for(const ln of lines){
   const x1=ln.x1-worldX,x2=ln.x2-worldX;
   if(x2<-20||x1>W+20)continue;
   const y1=ln.y1-camY,y2=ln.y2-camY;
   const g1=terrainY(ln.x1)-camY,g2=terrainY(ln.x2)-camY;
   ctx.strokeStyle='#4e695c';ctx.lineWidth=2;
   ctx.beginPath();ctx.moveTo(x1,g1+4);ctx.lineTo(x1,y1-4);ctx.moveTo(x2,g2+4);ctx.lineTo(x2,y2-4);ctx.stroke();
   ctx.strokeStyle='#e6d9b8';ctx.lineWidth=1.8;
   ctx.beginPath();ctx.moveTo(x1,y1);ctx.lineTo(x2,y2);ctx.stroke();
   // Tiny alpine flags along the line
   const flags=6;
   for(let f=1;f<flags;f++){
    const ft=f/flags,fx=x1+(x2-x1)*ft,fy=y1+(y2-y1)*ft;
    ctx.fillStyle=f%2?'#e8a87c':'#c3d6ad';
    ctx.beginPath();ctx.moveTo(fx-3,fy+1);ctx.lineTo(fx+3,fy+1);ctx.lineTo(fx,fy+7);ctx.closePath();ctx.fill();
   }
  }

  // Steep foreground snow/alpine slope
  ctx.fillStyle='#10221c';ctx.beginPath();ctx.moveTo(0,H);
  for(let x=0;x<=W+6;x+=6)ctx.lineTo(x,terrainY(worldX+x)-camY);
  ctx.lineTo(W,H);ctx.closePath();ctx.fill();

  // Subtle slope depth shadow band under crest
  ctx.strokeStyle='rgba(255,255,255,0.06)';ctx.lineWidth=9;ctx.beginPath();
  for(let x=0;x<=W+6;x+=6){const y=terrainY(worldX+x)-camY+6;if(x===0)ctx.moveTo(x,y);else ctx.lineTo(x,y);}
  ctx.stroke();

  // Sunlit snow crest line
  ctx.strokeStyle=mixRGB(pA.snow,pB.snow,t);ctx.lineWidth=2.6;ctx.beginPath();
  for(let x=0;x<=W+6;x+=6){const y=terrainY(worldX+x)-camY;if(x===0)ctx.moveTo(x,y);else ctx.lineTo(x,y);}
  ctx.stroke();

  // Launch ramps
  for(const rm of ramps){
   const sx=rm.x-worldX;if(sx<-50||sx>W+50)continue;
   const y0=terrainY(rm.x)-camY,y1=terrainY(rm.x+rm.w)-camY;
   ctx.fillStyle='#2a453b';ctx.beginPath();
   ctx.moveTo(sx,y0+1);ctx.lineTo(sx+rm.w,y1-rm.h);ctx.lineTo(sx+rm.w,y1+4);ctx.closePath();ctx.fill();
   ctx.strokeStyle='#dce8c8';ctx.lineWidth=2;ctx.beginPath();ctx.moveTo(sx,y0);ctx.lineTo(sx+rm.w,y1-rm.h);ctx.stroke();
  }

  // Light orbs
  for(const o of orbs){
   if(o.got)continue;
   const sx=o.x-worldX,sy=o.y-camY;if(sx<-15||sx>W+15)continue;
   const og=ctx.createRadialGradient(sx,sy,1,sx,sy,11);
   og.addColorStop(0,'rgba(255,234,170,0.95)');og.addColorStop(1,'rgba(255,234,170,0)');
   ctx.fillStyle=og;ctx.beginPath();ctx.arc(sx,sy,11,0,Math.PI*2);ctx.fill();
   ctx.fillStyle='#fff6d8';ctx.beginPath();ctx.arc(sx,sy,3.2,0,Math.PI*2);ctx.fill();
  }

  // Alpine boulders
  for(const rock of obstacles){
   const sx=rock.x-worldX;if(sx<-20||sx>W+20)continue;
   const sy=terrainY(rock.x)-camY;
   ctx.fillStyle='#344f44';ctx.beginPath();
   ctx.moveTo(sx-rock.r,sy+4);ctx.lineTo(sx-rock.r*0.55,sy-rock.r*0.95);
   ctx.lineTo(sx+rock.r*0.28,sy-rock.r*1.08);ctx.lineTo(sx+rock.r,sy+4);ctx.closePath();ctx.fill();
   ctx.fillStyle='#789687';ctx.beginPath();
   ctx.moveTo(sx-rock.r*0.55,sy-rock.r*0.95);ctx.lineTo(sx+rock.r*0.28,sy-rock.r*1.08);
   ctx.lineTo(sx+rock.r*0.05,sy-rock.r*0.22);ctx.closePath();ctx.fill();
  }

  // Snow spray & shimmer particles
  for(const p of puffs){
   ctx.globalAlpha=Math.max(0,p.a);ctx.fillStyle=p.col;
   ctx.beginPath();ctx.arc(p.wx-worldX,p.wy-camY,p.r,0,Math.PI*2);ctx.fill();
  }
  ctx.globalAlpha=1;

  // Flowing Alto-style scarf
  const rsy=ry-camY;
  if(scarf.length>1){
   ctx.strokeStyle=boost>0?'#ffe3a3':'#e8a87c';ctx.lineCap='round';ctx.lineJoin='round';
   for(let i=0;i<scarf.length-1;i++){
    ctx.lineWidth=Math.max(1.2,4.4-i*0.38);
    ctx.globalAlpha=Math.max(0.18,1-i*0.09);
    ctx.beginPath();ctx.moveTo(scarf[i].wx-worldX,scarf[i].wy-camY);ctx.lineTo(scarf[i+1].wx-worldX,scarf[i+1].wy-camY);ctx.stroke();
   }
   ctx.globalAlpha=1;
  }

  // Rider silhouette + board
  ctx.save();ctx.translate(riderSX,rsy);ctx.rotate(angle);
  if(boost>0||grinding){
   ctx.fillStyle='rgba(255,227,163,0.24)';ctx.beginPath();ctx.arc(0,-9,17,0,Math.PI*2);ctx.fill();
  }
  ctx.strokeStyle='#eef5df';ctx.lineWidth=2.6;ctx.lineCap='round';
  ctx.beginPath();ctx.moveTo(-11,0);ctx.quadraticCurveTo(0,2.2,12,-1.6);ctx.stroke();
  ctx.fillStyle='#f4eddd';
  ctx.beginPath();ctx.moveTo(-5,-1);ctx.lineTo(-1,-13.5);ctx.lineTo(4.2,-11.5);ctx.lineTo(3,-1);ctx.closePath();ctx.fill();
  ctx.beginPath();ctx.arc(1.2,-16.5,3.4,0,Math.PI*2);ctx.fill();
  ctx.restore();

  // Clean HUD
  ctx.fillStyle='rgba(244,237,221,0.92)';ctx.font='500 12px Outfit,sans-serif';ctx.textAlign='left';
  ctx.fillText(Math.floor(score)+' m',12,20);
  if(cgGlideBest>0){ctx.textAlign='right';ctx.fillStyle='rgba(195,214,173,0.85)';ctx.fillText('Best '+cgGlideBest+' m',W-12,20);}
  if(bannerT>0&&banner){
   ctx.textAlign='center';ctx.fillStyle='rgba(255,233,174,'+Math.min(1,bannerT*1.6)+')';
   ctx.font='500 13px Outfit,sans-serif';ctx.fillText(banner,W/2,44);
  }

  if(phase!=='run'){
   ctx.fillStyle='rgba(14,30,25,0.48)';ctx.fillRect(0,0,W,H);
   ctx.textAlign='center';ctx.fillStyle='#f4eddd';ctx.font='500 15px Outfit,sans-serif';
   ctx.fillText(phase==='ready'?'Tap to glide':'Nice run · '+Math.floor(score)+' m',W/2,H/2-6);
   ctx.fillStyle='#c3d6ad';ctx.font='400 12px Outfit,sans-serif';
   ctx.fillText(phase==='ready'?'Tap to jump · Hold in air to backflip':'Tap anywhere to glide again',W/2,H/2+15);
  }
 }

 function step(ts){
  if(phase!=='run'){raf=0;return;}
  const dt=Math.min(0.033,lastTs?(ts-lastTs)/1000:0.016);lastTs=ts;
  if(boost>0)boost=Math.max(0,boost-dt);
  if(bannerT>0)bannerT=Math.max(0,bannerT-dt);
  if(jumpBuf>0)jumpBuf=Math.max(0,jumpBuf-dt);

  const rwxPrev=worldX+riderSX;
  const gSlope=slopeAngle(rwxPrev);
  // Slope-driven momentum: accelerate faster on steep drops, coast gently on climbs
  if(grounded){
   speed+=Math.sin(gSlope)*195*dt;
   speed=Math.max(138,Math.min(275,speed+(170-speed)*dt*0.35));
  }
  const curSpeed=speed+(boost>0?64:0)+(grinding?28:0);
  worldX+=curSpeed*dt;
  score+=curSpeed*dt*0.078;

  const rwx=worldX+riderSX;
  const groundY=terrainY(rwx);
  const nextSlope=slopeAngle(rwx);

  // Check launch ramp boost when grounded
  let onRamp=false;
  if(grounded){
   for(const rm of ramps){
    if(rwx>=rm.x&&rwx<=rm.x+rm.w){
     const rt=(rwx-rm.x)/rm.w;
     ry=groundY-rt*rm.h;
     angle=nextSlope-0.32;
     onRamp=true;
     if(rt>0.82){
      grounded=false;vy=-265-Math.min(75,(curSpeed-150)*0.65);
      flipAccum=0;flipCount=0;
      addPuff(rwx,ry,'#e8f2d8',7,65);
     }
     break;
    }
   }
  }

  if(grinding){
   if(rwx>grinding.x2){
    grinding=null;grounded=false;vy=-195;boost=Math.max(boost,0.8);
    addPuff(rwx,ry,'#ffe3a3',6,55);
   }else{
    const lt=(rwx-grinding.x1)/(grinding.x2-grinding.x1);
    ry=grinding.y1+(grinding.y2-grinding.y1)*lt;
    angle=Math.atan2(grinding.y2-grinding.y1,grinding.x2-grinding.x1);
    vy=0;
    sprayAccum+=dt;if(sprayAccum>0.06){sprayAccum=0;addPuff(rwx,ry,'#ffe3a3',1,35);}
    if(jumpBuf>0){grinding=null;grounded=false;jumpBuf=0;vy=-260;flipAccum=0;flipCount=0;}
   }
  }else if(grounded){
   if(!onRamp){
    ry=groundY;vy=0;coyote=0.11;
    angle+=(nextSlope-angle)*Math.min(1,dt*14);
   }
   flipAccum=0;flipCount=0;
   // Snow spray while carving downhill
   sprayAccum+=dt*(curSpeed/150);
   if(sprayAccum>0.09){sprayAccum=0;addPuff(rwx-6,ry,'rgba(220,235,208,0.75)',1,32);}
   if(jumpBuf>0){
    grounded=false;coyote=0;jumpBuf=0;vy=-268-Math.min(45,(curSpeed-150)*0.3);
    addPuff(rwx,ry,'#dce8c8',6,55);
   }
  }else{
   coyote=Math.max(0,coyote-dt);
   vy+=610*dt;ry+=vy*dt;
   if(pressing){
    const spin=6.1*dt;angle-=spin;flipAccum+=spin;
    const completed=Math.floor((flipAccum+0.42)/(Math.PI*2));
    if(completed>flipCount)flipCount=completed;
   }else{
    const norm=((angle-nextSlope+Math.PI*3)%(Math.PI*2))-Math.PI;
    angle-=norm*Math.min(1,dt*3.4);
   }
   // Catch bunting line grind when descending onto it
   if(vy>0){
    for(const ln of lines){
     if(rwx>=ln.x1&&rwx<=ln.x2){
      const lt=(rwx-ln.x1)/(ln.x2-ln.x1);
      const ly=ln.y1+(ln.y2-ln.y1)*lt;
      if(Math.abs(ry-ly)<12){
       grinding=ln;ry=ly;vy=0;score+=3;banner='Wire grind! +3';bannerT=1.1;
       addPuff(rwx,ry,'#ffe3a3',6,50);break;
      }
     }
    }
   }
   if(!grinding&&ry>=groundY){
    ry=groundY;vy=0;grounded=true;
    const err=Math.abs((((angle-nextSlope+Math.PI*3)%(Math.PI*2))-Math.PI));
    if(err>1.22){endRun('Landed off-balance');return;}
    if(flipCount>0){
     flips+=flipCount;combo++;
     const pts=flipCount>1?25:10;
     score+=pts;boost=2.3;
     banner=(flipCount>1?'Double backflip! +':'Clean backflip! +')+pts;bannerT=1.5;
     addPuff(rwx,ry,'#ffe3a3',10,75);
    }else{
     addPuff(rwx,ry,'#d8e6c8',5,45);
    }
    angle=nextSlope;
   }
  }

  // Smooth dynamic camera tracking steep downhill + air height
  const airLift=Math.max(0,groundY-ry);
  const targetCamY=Math.min(groundY-H*0.64,ry-H*0.44)-Math.min(20,airLift*0.18);
  camY+=(targetCamY-camY)*Math.min(1,dt*7.5);

  // Flowing multi-segment scarf in world coords
  const neckX=rwx-Math.sin(angle)*13,neckY=ry-Math.cos(angle)*13;
  scarf[0].wx=neckX;scarf[0].wy=neckY;
  for(let i=1;i<scarf.length;i++){
   const prev=scarf[i-1];
   const targetX=prev.wx-5.2-(boost>0?1.4:0);
   const targetY=prev.wy-1.2+Math.sin(worldX*0.06+i*0.9)*1.9;
   scarf[i].wx+=(targetX-scarf[i].wx)*Math.min(1,dt*20);
   scarf[i].wy+=(targetY-scarf[i].wy)*Math.min(1,dt*20);
  }

  // Collect light orbs
  for(const o of orbs){
   if(o.got)continue;
   const dx=o.x-rwx,dy=o.y-(ry-10);
   if(dx*dx+dy*dy<290){
    o.got=true;orbsGot++;score+=2;addPuff(o.x,o.y,'#ffe3a3',5,45);
   }
  }
  orbs=orbs.filter(o=>!o.got&&o.x>worldX-40);

  // Boulder collisions (forgiving hitbox; boost smashes through rocks!)
  for(let k=obstacles.length-1;k>=0;k--){
   const rock=obstacles[k],dx=rock.x-rwx,sy=terrainY(rock.x);
   if(Math.abs(dx)<rock.r*0.72&&ry>sy-rock.r*0.82){
    if(boost>0){
     obstacles.splice(k,1);score+=3;banner='Rock smash! +3';bannerT=1.0;
     addPuff(rock.x,sy-4,'#c3d6ad',9,75);
    }else{
     endRun('Bumped a stone');return;
    }
   }
  }
  obstacles=obstacles.filter(o=>o.x>worldX-50);
  ramps=ramps.filter(r=>r.x+r.w>worldX-50);
  lines=lines.filter(l=>l.x2>worldX-50);
  cabins=cabins.filter(c=>c.x>worldX-60);

  for(const p of puffs){p.wx+=p.vx*dt;p.wy+=p.vy*dt;p.a-=dt*1.9;}
  puffs=puffs.filter(p=>p.a>0);

  spawnAhead();
  cgStatus.textContent=Math.floor(score)+' m · '+orbsGot+' light'+(flips?' · '+flips+(flips===1?' flip':' flips'):'');
  draw();
  raf=requestAnimationFrame(step);
 }

 function endRun(reason){
  phase='over';stopLoop();
  const s=Math.floor(score);if(s>cgGlideBest)cgGlideBest=s;
  cgStatus.textContent=reason+' · '+s+' m'+(cgGlideBest?' (best '+cgGlideBest+' m)':'');
  draw();
 }

 function beginOrJump(){
  if(phase!=='run'){
   resetRun();phase='run';lastTs=0;
   cgStatus.textContent='Tap to jump · Hold in air to backflip';
   draw();raf=requestAnimationFrame(step);return;
  }
  if(grounded||coyote>0||grinding){
   grinding=null;grounded=false;coyote=0;jumpBuf=0;
   vy=-268-Math.min(45,(speed-150)*0.3);flipAccum=0;flipCount=0;
   addPuff(worldX+riderSX,ry,'#dce8c8',6,55);
  }else{
   jumpBuf=0.12;
  }
 }

 cvs.onpointerdown=e=>{
  e.preventDefault();if(holding)return;
  pressing=true;beginOrJump();
 };
 cvs.onpointerup=()=>{pressing=false;};
 cvs.onpointercancel=()=>{pressing=false;};
 cvs.onpointerleave=()=>{pressing=false;};

 resetRun();phase='ready';
 cgStatus.textContent='Tap to glide · Hold in air to backflip';
 draw();
}

function cgStart(mode){
 cgMode=mode||cgMode;
 cgTabs.forEach(t=>t.setAttribute('aria-selected',String(t.dataset.game===cgMode)));
 if(cgMode==='tiles')cgStartTiles();
 else if(cgMode==='pairs')cgStartPairs();
 else cgStartGlide();
}
function cgSyncHost(page){
 if(page==='session'){
  if(cgHost.parentNode!==$('active'))$('active').append(cgHost);
  cgHost.hidden=!activeGameOpen;
  cgBody.hidden=!activeGameOpen;
  cgToggle.hidden=true;
  root.classList.toggle('active-game-open',activeGameOpen);
  activeGameToggle.textContent=activeGameOpen?'Close calm games':'Calm games';
 }else{
  activeGameOpen=false;root.classList.remove('active-game-open');
  if(cgHost.parentNode!==$('home'))$('home').append(cgHost);
  cgHost.hidden=false;cgToggle.hidden=false;
  if(page!=='home'){cgStopAll();cgBody.hidden=true;cgToggle.textContent='Play';}
 }
}
cgTabs.forEach(tab=>{tab.onclick=()=>{cgStart(tab.dataset.game);};});
cgReset.onclick=()=>{cgStart(cgMode);};
cgToggle.onclick=()=>{
 if(!cgBody.hidden){cgStopAll();cgBody.hidden=true;cgToggle.textContent='Play';return;}
 cgBody.hidden=false;cgToggle.textContent='Hide';cgStart(cgMode);
};
activeGameToggle.onclick=()=>{
 if(holding)return;
 activeGameOpen=!activeGameOpen;
 cgSyncHost('session');
 if(activeGameOpen)cgStart(cgMode);else cgStopAll();
 reportRing();requestAnimationFrame(reportRing);
};

function holdText(ms){const sec=Math.max(10,Math.round((ms||300000)/1000));if(sec<60)return sec+' seconds';const m=Math.floor(sec/60),r=sec%60;return r?m+' min '+r+' sec':m+(m===1?' minute':' minutes');}
window.setHoldDuration=ms=>{if(holding)return;holdTotal=ms||300000;write('holdTitle',clock(holdTotal,false));write('holdHelp','Keep one finger in the circle for '+holdText(holdTotal)+'. Lifting resets the hold.');};
window.holdProgress=(p,left,total)=>{if(!state.active)return;holdTotal=total;holding=true;root.classList.add('holding');root.classList.remove('releasing');progress(p,false);write('holdTitle',clock(left,false));write('holdNote','Keep holding');};
window.sceneEvent=name=>{if(name==='hold-start'&&state.active){clearFog();cgStopAll();$('intercept').hidden=true;clearTimeout(releaseTimer);holding=true;root.classList.remove('releasing');root.classList.add('holding');write('holdNote','Keep holding');}if(name==='hold-lift'){holding=false;root.classList.remove('holding');root.classList.toggle('releasing',motion);progress(0);write('holdTitle',clock(holdTotal,false));write('holdNote','Hold to end');clearTimeout(releaseTimer);if(motion)releaseTimer=setTimeout(()=>{root.classList.remove('releasing');progress(0,false);},1500);}if(name==='setup-failed')root.classList.add('setup','setup-failed');};
window.times=(session,hold,pause)=>{write('clock',clock(session,true));write('interceptTime',clock(session,true)+' remaining');if(!holding){write('holdTitle',clock(holdTotal,false));write('holdNote','Hold to end');}if(app.dataset.page==='pause'){write('pauseTime',pause>0?clock(pause,false):'Time to check in');root.style.setProperty('--pause-p',String(Math.max(.38,Math.min(1,1-pause/600000))));}};
function history(s,enter){const key=JSON.stringify(s.days||[]);if(key===lastDays&&!enter)return;lastDays=key;$('days').textContent='';$('weekTrees').textContent='';(s.days||[]).forEach((d,i)=>{const li=document.createElement('li'),label=document.createElement('strong'),value=document.createElement('span');label.textContent=d[0];value.textContent=d[1];if(d[3]){const tick=document.createElement('span');tick.className='session-tick';tick.textContent=' ✓';tick.setAttribute('aria-label',d[3]+' completed sessions');label.append(tick);}li.append(label,value);$('days').append(li);const day=document.createElement('div');day.className='day-tree '+(d[2]||'unknown')+(enter?' tree-enter':'');day.style.setProperty('--delay',(i*40)+'ms');const tree=document.createElementNS(ns,'svg');tree.setAttribute('viewBox','-27 -82 54 89');tree.innerHTML=(d[2]==='free'?crown:'')+'<path d="'+branches+'" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>';const caption=document.createElement('span');caption.textContent=String(d[0]).slice(0,3);day.append(tree,caption);$('weekTrees').append(day);});}
window.screenState=s=>{
 state=s;holdTotal=s.holdDuration||300000;if(s.preview){root.classList.add('preview');root.dataset.scene='Dusk';app.dataset.page='preview';setMotion(!s.still);return;}
 const page=s.active?'session':s.page||'block',changed=page!==lastPage;root.classList.remove('preview');root.dataset.scene=s.active?'Night':page==='complete'?'Dawn':'Dusk';app.dataset.page=page;root.classList.toggle('setup',!!s.setup);root.classList.toggle('admin-ready',!!s.adminReady);root.classList.toggle('access-ready',!!s.accessReady);if(s.setup)root.classList.remove('setup-failed');setMotion(!s.still);
 for(const id of ['block','active','home','pause','week','complete'])$(id).hidden=id!==(page==='session'?'active':page);$('sessionSky').hidden=!s.active;$('bank').hidden=page==='complete';document.querySelector('nav').hidden=!!s.active||page==='complete';write('sceneLabel',s.setup?'Lantern light':page==='complete'?'Sunrise':s.active?'Night':'Quiet dusk');$('restore').hidden=!s.interrupted;write('guardStatus',s.guardMissing?'Uninstall guard was revoked in Android Settings.':s.interrupted?'Blocking access is off. Restore it to protect your session.':'');
 const minutes=Math.max(1,Math.min(1439,Number(s.minutes)||25)),h=Math.floor(minutes/60),m=minutes%60;write('sessionTotal',(h?h+' h ':'')+(m?m+' min ':'')+'session');if(minutes!==lastMinutes){lastMinutes=minutes;$('durationValue').innerHTML=(h?h+' <span class="unit">h</span> ':'')+(m||!h?m+' <span class="unit">min</span>':'');}write('selected',s.count?s.count+' selected':'Choose apps');$('start').firstChild.textContent=s.pending?'Continue setup':s.release?'Releasing access…':'Start blocking';$('start').disabled=!s.loaded||s.release;$('cancelSetup').hidden=!s.pending;write('status',s.pending?'Grant the requested permissions. Your timer starts when access connects.':'Selected apps and uninstall routes stay protected during your session.');write('reason',s.reason||'');write('step',s.step||'');write('pauseStep',s.pauseStep||'');write('pauseStart',s.paused?'Return to my pause':'Take a pause');write('scheduleLabel',s.schedule||'Off');write('holdHelp','Keep one finger in the circle for '+holdText(holdTotal)+'. Lifting resets the hold.');write('weekSummary',(s.free||0)+' porn-free days · '+(s.logged||0)+' days logged');if(page==='week')history(s,changed);document.querySelectorAll('nav button').forEach(b=>b.setAttribute('aria-current',String(b.dataset.act===(page==='pause'?'home':page))));
 cgSyncHost(page);
 const jar=$('jarGame').querySelector('button');if((s.jar||0)>lastJar&&motion){jar.classList.remove('jar-pop');void jar.offsetWidth;jar.classList.add('jar-pop');}lastJar=s.jar||0;$('jarGame').hidden=true;jar.disabled=lastJar>=5;write('jarCount','●'.repeat(Math.min(5,lastJar))+'○'.repeat(Math.max(0,5-lastJar)));write('jarHint',lastJar>=5?'A little light collected. Take a breath.':'Five quiet taps. No score to chase.');
 if(page==='block'&&firstHome&&!s.pending&&motion){firstHome=false;for(const [i,el] of [$('block').querySelector('.duration'),$('block').querySelector('.rows'),$('start')].entries()){el.classList.add('home-enter');el.style.setProperty('--delay',(i*60)+'ms');}$('start').classList.add('intro-glow');}
 if(page==='complete'&&changed){$('sunriseWash').classList.add('wash');holding=false;root.classList.remove('holding','releasing');root.style.setProperty('--scenic-p','1');complete.classList.add('complete-enter');}if(!s.active&&page!=='complete'){holding=false;root.classList.remove('holding','releasing');progress(0);}if(s.active&&changed){progress(0);write('holdTitle',clock(holdTotal,false));}if(s.active&&s.blockedSignal&&s.blockedSignal!==lastBlocked){lastBlocked=s.blockedSignal;showFog();}
 if((changed&&lastPage==='block')||!motion){document.querySelectorAll('.home-enter').forEach(el=>el.classList.remove('home-enter'));$('start').classList.remove('intro-glow');}lastPage=page;requestAnimationFrame(reportRing);
};
document.addEventListener('animationend',e=>{if(e.animationName==='sunrise-wash')$('sunriseWash').classList.remove('wash');if(e.animationName==='soft-rise')e.target.classList.remove('home-enter','complete-enter');if(e.animationName==='once-glow')$('start').classList.remove('intro-glow');if(e.animationName==='jar-catch')e.target.classList.remove('jar-pop');});
setMotion(false);
})();
